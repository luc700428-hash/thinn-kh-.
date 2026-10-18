/** thinn-khì supplemental Taiwan EEW push relay.
 * No feed is bundled. Only a separately authorized publisher can submit verified EEW.
 * This Worker NEVER generates synthetic earthquake alerts or claims to be the CWA PWS.
 */
const SOURCES = new Set(['CWA_OFFICIAL', 'EXPTECH_VERIFIED']);
const REGIONS = new Set(['TAIWAN','TAIPEI','NEW_TAIPEI','TAOYUAN','TAICHUNG',
 'TAINAN','KAOHSIUNG','KEELUNG','HSINCHU','CHIAYI','MIAOLI','CHANGHUA',
 'NANTOU','YUNLIN','PINGTUNG','YILAN','HUALIEN','TAITUNG','PENGHU','KINMEN','LIENCHIANG']);
const COUNTIES = new Set(['基隆市','臺北市','新北市','桃園市','新竹市','新竹縣','苗栗縣',
 '臺中市','彰化縣','南投縣','雲林縣','嘉義市','嘉義縣','臺南市','高雄市',
 '屏東縣','宜蘭縣','花蓮縣','臺東縣','澎湖縣','金門縣','連江縣']);
const INTENSITIES = new Set(['0','1','2','3','4','5弱','5強','6弱','6強','7']);
const TOPIC = 'thinn_eew_tw_v1';
const textEncoder = new TextEncoder();
const respond = (data, status=200) => new Response(JSON.stringify(data), {
 status, headers: {'content-type':'application/json; charset=utf-8','cache-control':'no-store',
 'x-content-type-options':'nosniff'}
});
function failure(msg) { throw Error(msg); }
const countyNormalized = name => name.replaceAll('台','臺').trim();
const clean = (s,min,max) => typeof s==='string' && s.length>=min && s.length<=max && !/[<>\u0000-\u001f]/.test(s);
function validImpact(intensities) {
 if(!intensities || typeof intensities!=='object' || Array.isArray(intensities)) failure('county estimates required');
 const entries=Object.entries(intensities);
 if(entries.length<1 || entries.length>22) failure('invalid affected counties');
 let affected=0;
 for(const [raw,level] of entries) {
   const county=countyNormalized(raw);
   if(!COUNTIES.has(county) || !INTENSITIES.has(String(level))) failure('invalid Taiwanese county intensity');
   if(String(level)!=='0') affected++;
 }
 if(!affected) failure('no verified impact in Taiwan');
 return entries.map(([county,level])=>[countyNormalized(county),String(level)]);
}
export function validateAlert(a,now=Date.now()) {
 if(!a || a.kind!=='EEW' || !SOURCES.has(a.source) ||
    a.trustedUpstream!==true || a.taiwanImpactVerified!==true ||
    a.test===true || a.training===true || a.cancelled===true) failure('unverified, simulated, or unsupported EEW');
 if(!clean(a.eventId,5,128) || !/^[A-Za-z0-9_.-]{5,128}$/.test(a.eventId)) failure('invalid eventId');
 if(!Number.isInteger(a.revision) || a.revision<0 || a.revision>999) failure('invalid revision');
 if(!REGIONS.has(a.region)) failure('region not supported');
 if(!clean(a.title,3,120) || !clean(a.detail,1,400)) failure('invalid alert text');
 if(!Number.isFinite(a.issuedAtMillis) || !Number.isFinite(a.expiresAtMillis) ||
    a.issuedAtMillis>now+30_000 || now-a.issuedAtMillis>120_000 ||
    a.expiresAtMillis<=now || a.expiresAtMillis<=a.issuedAtMillis ||
    a.expiresAtMillis-a.issuedAtMillis>180_000) failure('expired or invalid time');
 if(a.magnitude!==undefined && (!Number.isFinite(a.magnitude) || a.magnitude<0 || a.magnitude>10)) failure('invalid magnitude');
 const countyIntensities=validImpact(a.countyIntensities);
 // Cross-border alerts can be routed only to a specific Taiwanese region
 // and only when a validated Taiwanese county estimate is present.
 const foreign=/日本|沖繩|琉球|japan|okinawa/i.test(`${a.title} ${a.epicenter||''}`);
 if(foreign && a.region==='TAIWAN') failure('foreign alert cannot use broad Taiwan routing');
 return {
  kind:'EEW',eventId:a.eventId,revision:a.revision,source:a.source,
  region:a.region,issuedAtMillis:a.issuedAtMillis,expiresAtMillis:a.expiresAtMillis,
  title:a.title,detail:a.detail,magnitude:a.magnitude??null,countyIntensities
 };
}
const hexToBytes=hex=>Uint8Array.from(hex.match(/../g).map(s=>parseInt(s,16)));
export async function checkHmac(secret,timestamp,body,signature,now=Date.now()) {
 if(typeof secret!=='string' || secret.length<32 || !/^\d{13}$/.test(timestamp||'') ||
   Math.abs(Number(timestamp)-now)>60_000 || !/^[a-f\d]{64}$/i.test(signature||'')) return false;
 const key=await crypto.subtle.importKey('raw',textEncoder.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['verify']);
 const signed=new Uint8Array([...textEncoder.encode(timestamp+'.'),...body]);
 return crypto.subtle.verify('HMAC',key,hexToBytes(signature),signed);
}
function base64url(bytes) {
 let out='';for(const b of bytes)out+=String.fromCharCode(b);
 return btoa(out).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
}
function decodePem(pem) {
 const raw=pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
 const bin=atob(raw);return Uint8Array.from(bin,c=>c.charCodeAt(0));
}
async function authGoogle(env) {
 if(!env.GOOGLE_SERVICE_ACCOUNT_JSON) failure('FCM service account is not configured');
 const service=JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON);
 if(!clean(service.client_email,6,320) || !service.private_key?.includes('BEGIN PRIVATE KEY')) failure('invalid service account');
 const now=Math.floor(Date.now()/1000);
 const header=base64url(textEncoder.encode(JSON.stringify({alg:'RS256',typ:'JWT'})));
 const claims=base64url(textEncoder.encode(JSON.stringify({iss:service.client_email,
   scope:'https://www.googleapis.com/auth/firebase.messaging',
   aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3000})));
 const input=`${header}.${claims}`;
 const key=await crypto.subtle.importKey('pkcs8',decodePem(service.private_key),
  {name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
 const signature=new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,textEncoder.encode(input)));
 const jwt=`${input}.${base64url(signature)}`;
 const res=await fetch('https://oauth2.googleapis.com/token',{
  method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},
  body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:jwt})
 });
 if(!res.ok) failure(`Google OAuth token error ${res.status}`);
 const token=await res.json();
 if(!token.access_token) failure('Google OAuth access token missing');
 return token.access_token;
}
export function fcmPayload(alert,now=Date.now()) {
 const ttl=Math.max(0,Math.min(60_000,alert.expiresAtMillis-now));
 if(ttl<1000) failure('event too close to expiry for safe delivery');
 return {message:{topic:TOPIC, data:{
  kind:'EEW',eventId:alert.eventId,revision:String(alert.revision),
  issuedAtMillis:String(alert.issuedAtMillis),expiresAtMillis:String(alert.expiresAtMillis),
  region:alert.region,source:alert.source,
  magnitude:alert.magnitude===null?'':String(alert.magnitude),
  title:alert.title,detail:alert.detail
 },android:{priority:'HIGH',ttl:`${Math.floor(ttl/1000)}s`}}};
}
async function claimEvent(db,alert,now) {
 // Exactly one concurrent request claims a revision; a failed submission may retry
 // after 10 seconds, before the upstream validity window expires.
 const result=await db.prepare(`INSERT INTO deliveries (event_id,revision,claimed_at,sent)
 VALUES (?1,?2,?3,0)
 ON CONFLICT(event_id) DO UPDATE SET revision=excluded.revision,claimed_at=excluded.claimed_at,sent=0
 WHERE excluded.revision>deliveries.revision OR
    (excluded.revision=deliveries.revision AND deliveries.sent=0 AND deliveries.claimed_at<?4)
 RETURNING revision`)
 .bind(alert.eventId,alert.revision,now,now-10_000).first();
 return !!result;
}
async function markSent(db,alert) {
 await db.prepare('UPDATE deliveries SET sent=1 WHERE event_id=?1 AND revision=?2')
  .bind(alert.eventId,alert.revision).run();
}
async function sendFCM(env,alert,now) {
 const token=await authGoogle(env);
 if(!/^[a-z][a-z0-9-]{4,62}$/.test(env.FIREBASE_PROJECT_ID||'')) failure('invalid Firebase project ID');
 const payload=fcmPayload(alert,now);
 const url=`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`;
 const res=await fetch(url,{
   method:'POST',headers:{'authorization':`Bearer ${token}`,'content-type':'application/json'},
   body:JSON.stringify(payload)
 });
 if(!res.ok) failure(`FCM send failed ${res.status}`);
 const result=await res.json();
 if(!result.name) failure('FCM response missing message ID');
 return result.name;
}
export default {
 async fetch(request,env) {
  const path=new URL(request.url).pathname;
  if(path==='/health' && request.method==='GET')
    return respond({service:'thinn-khi-eew-relay',mode:env.DRY_RUN==='false'?'production':'dry-run',
      warning:'Supplementary push only; not government PWS or an earthquake feed'});
  if(path!=='/v1/ingest' || request.method!=='POST') return respond({error:'not_found'},404);
  if(request.headers.get('content-type')?.split(';')[0].trim()!=='application/json')
    return respond({error:'invalid_content_type'},415);
  let body;
  try{body=new Uint8Array(await request.arrayBuffer());}catch{return respond({error:'invalid_body'},400);}
  if(body.length===0 || body.length>8192)return respond({error:'invalid_size'},413);
  const timestamp=request.headers.get('x-thinn-timestamp');
  const signature=request.headers.get('x-thinn-hmac');
  if(!await checkHmac(env.INGEST_HMAC_SECRET,timestamp,body,signature))
    return respond({error:'unauthorized'},401);
  let alert;
  try{alert=validateAlert(JSON.parse(new TextDecoder().decode(body)));}
  catch(err){return respond({error:'invalid_event',reason:String(err.message).slice(0,120)},400);}
  if(env.DRY_RUN!=='false')
    return respond({dryRun:true,acceptedForValidation:true,delivered:false});
  if(!env.ALERT_DB)return respond({error:'storage_not_configured'},503);
  let claimed;
  try{claimed=await claimEvent(env.ALERT_DB,alert,Date.now());}
  catch{return respond({error:'storage_unavailable'},503);}
  if(!claimed)return respond({duplicate:true,delivered:false,reason:'already claimed or sent'});
  try{
   const result=await sendFCM(env,alert,Date.now());
   await markSent(env.ALERT_DB,alert);
   return respond({accepted:true,upstreamFcmAccepted:true,deliveredToDeviceUnconfirmed:true,
     fcmRequestId:result});
  }catch(err){return respond({error:'delivery_failed',reason:String(err.message).slice(0,90),retryable:true},502);}
 }
};
