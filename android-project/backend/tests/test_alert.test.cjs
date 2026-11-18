'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {validateTestRequest,cooldownLeft,testMessage,COOLDOWN_MS,LIFETIME_MS}=require('../functions/test_alert.cjs');
const token='f'.repeat(40)+':APA91b'+'x'.repeat(100);
const nonce='0123abcd-4567-89ab-cdef-0123456789ab';
test('only a real-looking token and a UUID nonce are accepted',()=>{
  assert.deepEqual(validateTestRequest({token,nonce}),{token,nonce});
  assert.throws(()=>validateTestRequest({token:'short',nonce}),/token/);
  assert.throws(()=>validateTestRequest({token:token+' x',nonce}),/token/);
  assert.throws(()=>validateTestRequest({token,nonce:'abc'}),/nonce/);
  assert.throws(()=>validateTestRequest(null),/request/);
});
test('requests are at least five minutes apart',()=>{
  const now=1_800_000_000_000;
  assert.equal(cooldownLeft(0,now),0);
  assert.equal(cooldownLeft(now-COOLDOWN_MS,now),0);
  assert.equal(cooldownLeft(now-60_000,now),COOLDOWN_MS-60_000);
  assert.equal(cooldownLeft(now+5_000,now),COOLDOWN_MS);
  assert.equal(COOLDOWN_MS,300_000);
});
test('the push goes to one phone, is marked as a test and expires in three minutes',()=>{
  const now=1_800_000_000_000, m=testMessage(token,nonce,now);
  assert.equal(m.token,token);
  assert.equal(m.topic,undefined);
  assert.equal(m.data.kind,'EEW_TEST');
  assert.equal(m.data.nonce,nonce);
  assert.equal(Number(m.data.expiresAtMillis)-Number(m.data.issuedAtMillis),LIFETIME_MS);
  assert.equal(m.android.priority,'high');
  assert.match(m.data.detail,/並非真實地震/);
  for(const v of Object.values(m.data))assert.equal(typeof v,'string');
});
