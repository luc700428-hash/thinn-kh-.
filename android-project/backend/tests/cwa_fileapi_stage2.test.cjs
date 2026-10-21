'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {officialUrl,requestDataset,clearForTest}=require('../functions/cwa_official.cjs');
const dummy='CWA-EXAMPLE-1234567890-TEST-KEY-ONLY';
test('W-C0034-005 goes through the documented CWA file API with member Authorization',()=>{
  const u=officialUrl('W-C0034-005',dummy);
  assert.equal(u.protocol,'https:');
  assert.equal(u.hostname,'opendata.cwa.gov.tw');
  assert.equal(u.pathname,'/fileapi/v1/opendataapi/W-C0034-005');
  assert.equal(u.searchParams.get('Authorization'),dummy);
  assert.equal(u.searchParams.get('format'),'JSON');
});
test('post-earthquake report keeps REST datastore route',()=>{
  const u=officialUrl('E-A0015-001',dummy);
  assert.equal(u.pathname,'/api/v1/rest/datastore/E-A0015-001');
  assert.equal(u.searchParams.get('limit'),'15');
});
test('does not cache rejected official response as valid forecast',async()=>{
  clearForTest();
  const bad=async()=>new Response(JSON.stringify({success:'false'}),{status:200});
  await assert.rejects(requestDataset('W-C0034-005',dummy,{fetchImpl:bad}));
});
test('allows empty official cyclone dataset without inventing storm data',async()=>{
  clearForTest();
  const payload={cwaopendata:{Sent:'2026-10-22T01:00:00+08:00',Dataset:{TropicalCyclones:{}}}};
  const fake=async()=>new Response(JSON.stringify(payload),{status:200});
  const got=await requestDataset('W-C0034-005',dummy,{fetchImpl:fake});
  assert.equal(JSON.parse(got.payload).cwaopendata.Dataset.TropicalCyclones.constructor,Object);
});
