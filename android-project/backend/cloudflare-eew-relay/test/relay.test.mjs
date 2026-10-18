import {test} from 'node:test';
import assert from 'node:assert/strict';
import relay, {validateAlert,checkHmac,fcmPayload} from '../src/index.mjs';
const now=Date.now();
const valid={kind:'EEW',source:'EXPTECH_VERIFIED',trustedUpstream:true,taiwanImpactVerified:true,
 eventId:'verify_event-001',revision:1,region:'HUALIEN',issuedAtMillis:now-1200,
 expiresAtMillis:now+45000,title:'花蓮地震速報',detail:'花蓮縣預估震度3級，請採取防護',
 magnitude:5.6,countyIntensities:{'花蓮縣':'3','臺東縣':'2'}};
test('valid verified Taiwan event',()=>{
 const v=validateAlert(valid,now);assert.equal(v.region,'HUALIEN');
 assert.deepEqual(v.countyIntensities[0],['花蓮縣','3']);
});
test('never trust unverified or synthetic events',()=>{
 for(const change of [{training:true},{test:true},{trustedUpstream:false},{source:'UNVERIFIED'},
   {taiwanImpactVerified:false},{countyIntensities:{'東京':'5'}},
   {countyIntensities:{'花蓮縣':'8'}},{countyIntensities:{'花蓮縣':'0'}},
   {expiresAtMillis:now-1},{issuedAtMillis:now+50000}]) {
     assert.throws(()=>validateAlert({...valid,...change},now));
   }
});
test('foreign events cannot trigger broad Taiwan topic',()=>{
 assert.throws(()=>validateAlert({...valid,region:'TAIWAN',title:'日本附近地震'},now));
});
test('FCM payload is data-only, short lived, and matches Android policy',()=>{
 const o=fcmPayload(validateAlert(valid,now),now);
 assert.equal(o.message.topic,'thinn_eew_tw_v1');
 assert.equal(o.message.android.priority,'HIGH');
 assert.equal(o.message.data.kind,'EEW');
 assert.equal(o.message.android.ttl,'45s');
 assert.equal(o.message.notification,undefined);
});
test('HMAC authenticates exact payload and resists stale timestamp',async()=>{
 const body=new TextEncoder().encode(JSON.stringify(valid));
 const stamp=String(now),secret='super_long_test_secret_0000000000000000';
 const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);
 const bytes=await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(stamp+'.'+new TextDecoder().decode(body)));
 const signature=Buffer.from(bytes).toString('hex');
 assert.equal(await checkHmac(secret,stamp,body,signature,now),true);
 assert.equal(await checkHmac(secret,stamp,body,signature,now+70000),false);
 assert.equal(await checkHmac(secret,stamp,new Uint8Array([1,2]),signature,now),false);
});
test('health and dry-run accept no outgoing FCM delivery',async()=>{
 const env={DRY_RUN:'true',INGEST_HMAC_SECRET:'long_secret_that_needs_to_be_at_least_32_characters'};
 const health=await relay.fetch(new Request('https://x.example/health'),env);
 assert.equal((await health.json()).mode,'dry-run');
 const body=JSON.stringify(valid),stamp=String(Date.now());
 const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(env.INGEST_HMAC_SECRET),{name:'HMAC',hash:'SHA-256'},false,['sign']);
 const signature=Buffer.from(await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(stamp+'.'+body))).toString('hex');
 const res=await relay.fetch(new Request('https://x.example/v1/ingest',{method:'POST',headers:{'content-type':'application/json','x-thinn-timestamp':stamp,'x-thinn-hmac':signature},body}),env);
 assert.deepEqual(await res.json(),{dryRun:true,acceptedForValidation:true,delivered:false});
});

test('missing DRY_RUN flag remains fail-closed dry-run',async()=>{
 const h=await relay.fetch(new Request('https://x.example/health'),{});
 assert.equal((await h.json()).mode,'dry-run');
});
