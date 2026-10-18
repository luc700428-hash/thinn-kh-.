/** thinn-khì server-side Azure AI Speech broker. No Azure key in the APK.
 * Bind D1 as DB; secret AZURE_SPEECH_KEY; vars AZURE_REGION,FIREBASE_PROJECT_ID.
 * Disabled by default until deployed explicitly. Free-tier budgets are deliberately
 * lower than Azure allowance, and D1 UPDATE is atomic per user/month.
 */
const VOICES=new Set(['zh-TW-HsiaoChenNeural','zh-TW-HsiaoYuNeural']);
const MONTHLY_HARD_LIMIT=400000; // reserve headroom against 500k F0 allowance
const USER_MONTHLY_LIMIT=12000;
const USER_DAILY_LIMIT=1500;
const GLOBAL_DAILY_LIMIT=18000;
const MAX_REQUEST=220;
const ALLOWED_REGIONS=new Set(['eastasia','southeastasia','japaneast','eastus','westus','westus2','westeurope','northeurope','centralus','australiaeast','uksouth']);
function json(body,status=200,headers={}){return new Response(JSON.stringify(body),{status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store',...headers}})}
function xml(s){return s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&apos;')}
function b64url(v){const str=atob(v.replaceAll('-','+').replaceAll('_','/')+'='.repeat((4-v.length%4)%4));return Uint8Array.from(str,c=>c.charCodeAt(0))}
function decode(b){return JSON.parse(new TextDecoder().decode(b))}
let jwks={keys:null,until:0};
async function verifyFirebase(token,project){
  if(!/^[A-Za-z0-9_.\-]{100,3500}$/.test(token) || !/^[a-z0-9-]{3,100}$/.test(project))throw Error('auth');
  const pieces=token.split('.');if(pieces.length!==3)throw Error('auth');
  const header=decode(b64url(pieces[0]));const payload=decode(b64url(pieces[1]));
  const now=Math.floor(Date.now()/1000);
  if(header.alg!=='RS256'||!header.kid||payload.iss!==`https://securetoken.google.com/${project}`||
    payload.aud!==project||!payload.sub||payload.sub.length>128||
    !(payload.exp>now && payload.iat<=now && payload.auth_time<=now) ||
    payload.iat < now-30*24*3600)throw Error('auth');
  if(jwks.until<Date.now()){
    const response=await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
    if(!response.ok)throw Error('auth');
    const data=await response.json();if(!Array.isArray(data.keys))throw Error('auth');
    jwks={keys:data.keys,until:Date.now()+30*60*1000};
  }
  const jwk=jwks.keys.find(k=>k.kid===header.kid&&k.kty==='RSA');if(!jwk)throw Error('auth');
  const key=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  const verified=await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,b64url(pieces[2]),new TextEncoder().encode(`${pieces[0]}.${pieces[1]}`));
  if(!verified)throw Error('auth');
  return payload.sub;
}
function bucket(day,month,uid){return [`global-month:${month}`,`global-day:${day}`,`user-month:${uid}:${month}`,`user-day:${uid}:${day}`]}
async function reserve(db,uid,chars){
  const utc=new Date().toISOString();const day=utc.slice(0,10),month=utc.slice(0,7);
  const names=bucket(day,month,uid);
  const caps=[MONTHLY_HARD_LIMIT,GLOBAL_DAILY_LIMIT,USER_MONTHLY_LIMIT,USER_DAILY_LIMIT];
  // D1 batch executes as a transaction. Use guarded updates so simultaneous requests
  // never spend beyond any single bucket. If one fails, D1 batch is rolled back.
  const actions=[];
  for(let i=0;i<names.length;i++){
    actions.push(db.prepare('INSERT OR IGNORE INTO speech_usage (bucket, used_chars) VALUES (?,0)').bind(names[i]));
    actions.push(db.prepare('UPDATE speech_usage SET used_chars=used_chars+? WHERE bucket=? AND used_chars+?<=?').bind(chars,names[i],chars,caps[i]));
  }
  // Read estimates first for friendlier denial; guarded updates handle races.
  const previous=await db.prepare('SELECT bucket,used_chars FROM speech_usage WHERE bucket IN (?,?,?,?)')
    .bind(...names).all();
  for(let i=0;i<names.length;i++){
    const used=(previous.results||[]).find(v=>v.bucket===names[i])?.used_chars||0;
    if(used+chars>caps[i])return false;
  }
  const result=await db.batch(actions);
  return result.every((row,i)=>i%2===0||row.meta?.changes===1);
}
async function handle(request,env){
  if(request.method!=='POST'||new URL(request.url).pathname!=='/v1/speech')return json({error:'not_found'},404);
  const region=(env.AZURE_REGION||'').trim().toLowerCase();
  if(!ALLOWED_REGIONS.has(region)||!env.AZURE_SPEECH_KEY||!env.FIREBASE_PROJECT_ID||!env.DB)
    return json({error:'service_unconfigured'},503);
  const length=Number(request.headers.get('content-length')||'0');
  if(length>4096)return json({error:'request_too_large'},413);
  const bearer=request.headers.get('authorization')||'';
  if(!bearer.startsWith('Bearer '))return json({error:'unauthorized'},401);
  let uid;
  try {uid=await verifyFirebase(bearer.slice(7),env.FIREBASE_PROJECT_ID)}
  catch {return json({error:'unauthorized'},401)}
  let payload;
  try {payload=JSON.parse(await request.text());}catch{return json({error:'invalid_json'},400)}
  const {text,voice,rate}=payload;
  const pitch=payload.pitchSemitones ?? 0;
  const pause=payload.pauseMs ?? 120;
  if(typeof text!=='string'||text.length<1||text.length>MAX_REQUEST ||
     /[\u0000-\u0008\u000B-\u001F]/.test(text)||!VOICES.has(voice)||
     typeof rate!=='number'||rate<0.8||rate>1.15||
     typeof pitch!=='number'||pitch< -3||pitch>3||
     !Number.isInteger(pause)||pause<0||pause>350)
     return json({error:'invalid_request'},400);
  const allowed=await reserve(env.DB,uid,[...text].length).catch(()=>false);
  if(!allowed)return json({error:'quota_exhausted'},429);
  const pct=Math.round((rate-1)*100);
  // Only basic SSML prosody, not unsupported 'gentle' style on zh-TW voices.
  const safeText=text.split(/(?<=[。！？!?])/u).filter(Boolean).map(text => xml(text)).join(`<break time="${pause}ms"/>`);
  const ssml=`<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-TW"><voice name="${voice}"><prosody rate="${pct>=0?'+':''}${pct}%" pitch="${pitch>=0?'+':''}${pitch}st">${safeText}</prosody></voice></speak>`;
  const url=`https://${region}.tts.speech.microsoft.com/cognitiveservices/v1`;
  let response;
  try {
    response=await fetch(url,{method:'POST',headers:{
      'Ocp-Apim-Subscription-Key':env.AZURE_SPEECH_KEY,
      'Content-Type':'application/ssml+xml',
      'X-Microsoft-OutputFormat':'audio-24khz-96kbitrate-mono-mp3',
      'User-Agent':'thinn-khi-azure-voice/1.0'
    },body:ssml,signal:AbortSignal.timeout(5500)});
    if(!response.ok)return json({error:'upstream_unavailable'},503);
    const bytes=await response.arrayBuffer();if(bytes.byteLength<1000||bytes.byteLength>900000)
      return json({error:'invalid_audio'},502);
    return new Response(bytes,{headers:{'content-type':'audio/mpeg','cache-control':'private, no-store','x-tts-engine':'azure-neural'}});
  } catch {return json({error:'upstream_timeout'},503)}
}
export default {fetch(request,env){return handle(request,env)}};
export const test={xml,bucket,handle};
