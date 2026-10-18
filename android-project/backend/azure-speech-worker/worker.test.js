import test from 'node:test';
import assert from 'node:assert/strict';
import {test as t} from './src.js';
test('XML injection is escaped',()=>{
  assert.equal(t.xml('<voice> & "x"'), '&lt;voice&gt; &amp; &quot;x&quot;');
});
test('separate global and per-user budgets',()=>{
  assert.equal(t.bucket('2026-10-19','2026-10','alice')[3],'user-day:alice:2026-10-19');
});
test('disallowed endpoint never contacts Azure',async()=>{
  const r=await t.handle(new Request('https://broker/v1/not-speech',{method:'POST'}),{});
  assert.equal(r.status,404);
});
test('missing service configuration fails closed',async()=>{
  const r=await t.handle(new Request('https://broker/v1/speech',{method:'POST'}),{});
  assert.equal(r.status,503);
});
test('auth must be provided',async()=>{
  const r=await t.handle(new Request('https://broker/v1/speech',{method:'POST'}),{
    AZURE_REGION:'eastasia',AZURE_SPEECH_KEY:'secret',FIREBASE_PROJECT_ID:'test-project',DB:{}});
  assert.equal(r.status,401);
});
