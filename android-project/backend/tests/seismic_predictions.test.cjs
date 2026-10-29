'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const {validateAlert}=require('../functions/policy.cjs');
const now=1793250000000;
const base={kind:'EEW',eventId:'verified-event-01',source:'EXPTECH_VERIFIED',region:'TAIWAN',revision:1,
 issuedAtMillis:now,expiresAtMillis:now+90000,title:'臺灣地震速報',detail:'收到地震速報，慎防強烈搖晃',magnitude:6.1};
test('legacy authenticated envelope remains valid',()=>assert.equal(validateAlert(base,now),base));
const rich={...base,countyIntensities:{'臺北市':'3','花蓮縣':'5弱'},countyArrivalTimes:{'臺北市':now+15000},epicenter:'花蓮縣近海',lat:23.9,lon:121.6,depth:18,originTime:now-6000,maximumIntensity:'5弱',emergency:true};
test('explicit county predictions and source times remain intact',()=>assert.equal(validateAlert(rich,now),rich));
test('authenticated cancellation is forwarded for dismissal without a new alert',()=>assert.equal(validateAlert({...base,cancelled:true},now).cancelled,true));
const bad=[{countyIntensities:{日本:'7'}},{countyIntensities:{臺北市:'9'}},{countyArrivalTimes:{臺北市:now+900000}},
 {countyArrivalTimes:{臺北市:10}},{countyIntensities:[]},{depth:-1},{lat:91},{lon:181},{maximumIntensity:'unknown'},
 {originTime:now+1},{test:true},{cancelled:'true'},{replay:true},{emergency:'true'},{expiresAtMillis:now-1},{source:'UNVERIFIED'}];
for(const value of bad)test('rejects invalid prediction '+JSON.stringify(value),()=>assert.throws(()=>validateAlert({...base,...value},now)));
test('publishing agency is constrained by the verified source',()=>{
 assert.throws(()=>validateAlert({...base,source:'CWA_OFFICIAL',agency:'trem'},now));
 assert.equal(validateAlert({...base,source:'CWA_OFFICIAL',agency:'cwa'},now).agency,'cwa');
});
