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
