'use strict';
// Cloud Functions v2: verified device ownership via Firebase anonymous auth.
// Important: this is a deployable transport, NOT a licensed EEW feed or guarantee of timely delivery.
const crypto = require('node:crypto');
const {onCall, onRequest, HttpsError} = require('firebase-functions/v2/https');
const {defineSecret} = require('firebase-functions/params');
const admin = require('firebase-admin');
const {validateAlert,checkHmac,REGIONS} = require('./policy.cjs');
admin.initializeApp();
const store=admin.firestore();
const secret=defineSecret('ALERT_GATEWAY_HMAC_KEY');
const cwaSecret=defineSecret('CWA_API_KEY');
const {datasetRule,requestDataset}=require('./cwa_official.cjs');
const REGION='asia-east1';
function tokenKey(token){return crypto.createHash('sha256').update(token).digest('hex');}
function uid(request){
 if(!request.auth?.uid) throw new HttpsError('unauthenticated','請先使用 Firebase Auth 登入');
 return request.auth.uid;
}
exports.registerDevice = onCall({region:REGION,enforceAppCheck:false},async request=>{
 const user=uid(request), v=request.data||{};
 if(typeof v.token!=='string'||v.token.length<70||v.token.length>4096) throw new HttpsError('invalid-argument','裝置權杖格式錯誤');
 if(!REGIONS.has(v.region)) throw new HttpsError('invalid-argument','未知地區');
 const id=tokenKey(v.token);
 await store.collection('devices').doc(id).set({uid:user,token:v.token,region:v.region,
    locale:'zh-TW',updatedAt:admin.firestore.FieldValue.serverTimestamp()});
 return {ok:true};
});
exports.unregisterDevice = onCall({region:REGION},async request=>{
 const user=uid(request);
 const docs=await store.collection('devices').where('uid','==',user).limit(25).get();
 const batch=store.batch(); docs.forEach(d=>batch.delete(d.ref)); await batch.commit();
 return {ok:true,removed:docs.size};
});
exports.ingestTrustedEEW = onRequest({region:REGION,secrets:[secret],timeoutSeconds:60},async (req,res)=>{
 if(req.method!=='POST') return res.status(405).send('POST only');
 const raw=req.rawBody;
 if(!raw || raw.length>8192) return res.status(400).send('invalid size');
 const stamp=req.get('x-thinn-timestamp')||'';
 const signature=req.get('x-thinn-hmac')||'';
 if(!checkHmac(secret.value(),stamp,raw,signature)) return res.status(401).send('invalid signature');
 let e; try{e=validateAlert(req.body);}catch(err){return res.status(400).send(err.message);}
 // Only a separately operated, licensed and verifiable upstream gateway may sign this payload.
 // Do not call this API with ordinary CWA post-earthquake reports or simulated events.
 const doc=store.collection('earthquake-alerts').doc(e.eventId);
 const decision=await store.runTransaction(async tr=>{
   const old=await tr.get(doc);const previous=old.exists?old.data().revision:-1;
   if(previous>=e.revision) return false;
   tr.set(doc,{revision:e.revision,issuedAtMillis:e.issuedAtMillis,source:e.source,
     region:e.region, expiresAtMillis:e.expiresAtMillis,updatedAt:admin.firestore.FieldValue.serverTimestamp()});
   return true;
 });
 if(!decision) return res.status(200).json({sent:0,duplicate:true});
 const docs=await store.collection('devices').where('region','in',e.region==='TAIWAN'?['TAIWAN']:['TAIWAN',e.region]).limit(500).get();
 const deviceTokens=docs.docs.map(d=>d.data().token).filter(Boolean);
 const data={kind:'EEW',eventId:e.eventId,revision:String(e.revision),
   issuedAtMillis:String(e.issuedAtMillis),expiresAtMillis:String(e.expiresAtMillis),
   region:e.region,source:e.source,magnitude:String(e.magnitude??''),title:e.title,detail:e.detail};
 let accepted=0,failed=0;
 for(let i=0;i<deviceTokens.length;i+=500) {
   const tokens=deviceTokens.slice(i,i+500);
   const out=await admin.messaging().sendEachForMulticast({tokens,data,
      android:{priority:'high',ttl:Math.max(0,e.expiresAtMillis-Date.now())}});
   accepted+=out.successCount;failed+=out.failureCount;
   out.responses.forEach((response,index)=>{
       if(response.error && /registration-token-not-registered/.test(response.error.code||''))
          store.collection('devices').doc(tokenKey(tokens[index])).delete().catch(()=>{});
   });
 }
 res.json({accepted,failed,checked:deviceTokens.length,guaranteedDelivery:false});
});

// CWA Open Data query proxy. No shared CWA key is included in the APK / public GitHub.
// Requires authenticated Firebase user. No arbitrary upstream URLs or caller parameters.
exports.readOfficialCwa = onCall({region:REGION,secrets:[cwaSecret],timeoutSeconds:35},async request=>{
  const user=uid(request);
  const dataset=request.data?.dataset;
  try{datasetRule(dataset);}catch {throw new HttpsError('invalid-argument','不支援的官方資料集');}
  // Per-user throttle reduces abuse and protects a shared CWA API allocation.
  const quota=store.collection('cwa-query-throttle').doc(crypto.createHash('sha256').update(user+'|'+dataset).digest('hex'));
  const now=Date.now();
  const accepted=await store.runTransaction(async tx=>{
    const last=await tx.get(quota);
    const lastAt=last.exists?Number(last.data().at)||0:0;
    if(now-lastAt<3_000) return false;
    tx.set(quota,{at:now,updatedAt:admin.firestore.FieldValue.serverTimestamp()});
    return true;
  });
  if(!accepted) throw new HttpsError('resource-exhausted','查詢太頻繁，請稍後再試');
  try {
    const response=await requestDataset(dataset,cwaSecret.value());
    return response;
  } catch {
    // Deliberately do not forward CWA URLs, access tokens or raw upstream errors.
    throw new HttpsError('unavailable','中央氣象署資料暫時無法取得，請稍後再試');
  }
});
