'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {officialKmzUrl,requestKmz,clearKmzForTest}=require('../../backend/functions/cwa_official.cjs');
const KEY='CWA-12345678-1234-1234-1234-123456789012';
function mocked(data,url='https://opendata.cwa.gov.tw/fileapi/fixture'){
  const chunks=[new Uint8Array(data)];
  return {ok:true,url,headers:{get:()=>String(data.length)},body:{getReader:()=>({read:async()=>chunks.length?{done:false,value:chunks.shift()}:{done:true}})}};
}
test('KMZ server URL has a fixed dataset and never accepts a caller URL',()=>{
  const u=officialKmzUrl(KEY);
  assert.equal(u.hostname,'opendata.cwa.gov.tw');
  assert.equal(u.pathname,'/fileapi/v1/opendataapi/W-C0034-003');
  assert.equal(u.searchParams.get('format'),'KMZ');
  assert.throws(()=>officialKmzUrl('demo-key'));
});
test('requires a valid ZIP signature and bounds server response',async()=>{
  clearKmzForTest();
  await assert.rejects(requestKmz(KEY,{fetchImpl:async()=>mocked(Buffer.from('notzip'))}));
  clearKmzForTest();
  await assert.rejects(requestKmz(KEY,{fetchImpl:async()=>mocked(Buffer.from('PK'))}));
  clearKmzForTest();
  await assert.rejects(requestKmz(KEY,{fetchImpl:async()=>mocked(Buffer.alloc(2_000_001))}));
});
test('returns validated source and opaque base64 without exposing CWA key',async()=>{
  clearKmzForTest();
  const file=Buffer.from([0x50,0x4b,0x03,0x04,0x00]);
  const x=await requestKmz(KEY,{fetchImpl:async()=>mocked(file)});
  assert.equal(x.dataset,'W-C0034-003');
  assert.equal(x.source,'CWA_OFFICIAL_KMZ');
  assert.deepEqual(Buffer.from(x.payloadBase64,'base64'),file);
  assert.equal(JSON.stringify(x).includes(KEY),false);
});
