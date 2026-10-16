'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {DATASETS,datasetRule,officialUrl,requestDataset,clearForTest}=require('../functions/cwa_official.cjs');
const testKey='CWA-EXAMPLE-1234567890-TEST-KEY-ONLY';
test('allowlist prevents arbitrary upstream URL / SSRF',()=>{
  assert.equal(Object.keys(DATASETS).length,6);
  assert.throws(()=>datasetRule('https://evil.example'),/unsupported/);
  assert.throws(()=>datasetRule('../..'),/unsupported/);
  assert.throws(()=>datasetRule('E-A0015-001?x=1'),/unsupported/);
});
test('URL is official HTTPS endpoint, with documented Authorization query',()=>{
  const u=officialUrl('E-A0015-001',testKey);
  assert.equal(u.hostname,'opendata.cwa.gov.tw');
  assert.equal(u.protocol,'https:');
  assert.equal(u.searchParams.get('Authorization'),testKey);
  assert.equal(u.searchParams.get('limit'),'15');
});
test('returns valid data and caches; distinct user calls use same dataset cache',async()=>{
  clearForTest();let count=0;
  const fake=async()=>{count++;return new Response(JSON.stringify({success:'true',records:{Earthquake:[]}}),{status:200});};
  const one=await requestDataset('E-A0015-001',testKey,{fetchImpl:fake});
  const two=await requestDataset('E-A0015-001',testKey,{fetchImpl:fake});
  assert.equal(one.fromCache,false);assert.equal(two.fromCache,true);assert.equal(count,1);
  assert.equal(JSON.parse(two.payload).records.Earthquake.length,0);
});
test('rejects bad upstream data and never emits a key in error',async()=>{
  clearForTest();
  const fake=async()=>new Response(JSON.stringify({success:'false'}),{status:200});
  await assert.rejects(requestDataset('W-C0034-001',testKey,{fetchImpl:fake}),err=>{
    assert.equal(err.message.includes(testKey),false);return true;
  });
});
test('rejects oversized official payloads',async()=>{
  clearForTest();
  const fake=async()=>new Response('x'.repeat(4*1024*1024+5),{status:200});
  await assert.rejects(requestDataset('W-C0034-005',testKey,{fetchImpl:fake}),/too large/);
});
test('keeps simultaneous requests coalesced',async()=>{
  clearForTest();let count=0;
  const fake=async()=>{count++;await new Promise(r=>setTimeout(r,10));return new Response('{"success":"true"}');};
  await Promise.all([requestDataset('W-C0034-002',testKey,{fetchImpl:fake}),requestDataset('W-C0034-002',testKey,{fetchImpl:fake})]);
  assert.equal(count,1);
});
