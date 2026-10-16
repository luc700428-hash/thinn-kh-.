const {test} = require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const {validateAlert,checkHmac}=require('../functions/policy.cjs');
const now=1800000000000;
const event={kind:'EEW',eventId:'TREM_2026_1201',revision:1,region:'TAIWAN',
 source:'EXPTECH_VERIFIED',issuedAtMillis:now-1000,expiresAtMillis:now+30000,title:'臺灣地震速報',detail:'請注意防護'};
test('valid event',()=>assert.equal(validateAlert(event,now).revision,1));
test('stale event rejected',()=>assert.throws(()=>validateAlert({...event,expiresAtMillis:now-2},now)));
test('unverified source rejected',()=>assert.throws(()=>validateAlert({...event,source:'SIMULATION'},now)));
test('invalid region rejected',()=>assert.throws(()=>validateAlert({...event,region:'UNKNOWN'},now)));
test('future date rejected',()=>assert.throws(()=>validateAlert({...event,issuedAtMillis:now+31000},now)));
test('high revision bounded',()=>assert.throws(()=>validateAlert({...event,revision:1000},now)));
test('valid HMAC',()=>{
 const key='Q'.repeat(64),timestamp=String(now), raw=Buffer.from(JSON.stringify(event));
 const sig=crypto.createHmac('sha256',key).update(timestamp+'.').update(raw).digest('hex');
 assert.equal(checkHmac(key,timestamp,raw,sig,now),true);
 assert.equal(checkHmac(key,timestamp,raw,sig.substring(0,63)+'f',now),false);
});
