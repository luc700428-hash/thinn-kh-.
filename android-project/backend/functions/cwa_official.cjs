'use strict';
/** Only allow official CWA dataset identifiers; caller can never control an upstream URL. */
const DATASETS = Object.freeze({
  'E-A0015-001': {label:'顯著有感地震報告', ttl:90_000},
  'E-A0016-001': {label:'小區域有感地震報告', ttl:90_000},
  'W-C0034-001': {label:'颱風警報', ttl:300_000},
  'W-C0034-002': {label:'颱風消息與路徑', ttl:300_000},
  'W-C0034-003': {label:'颱風侵襲機率', ttl:600_000},
  'W-C0034-005': {label:'熱帶氣旋路徑資料', ttl:600_000},
});
const cache = new Map();
const pending = new Map();
const MAX_BYTES = 4 * 1024 * 1024;
function datasetRule(dataset) {
  if (typeof dataset !== 'string' || !Object.prototype.hasOwnProperty.call(DATASETS,dataset))
    throw new Error('unsupported dataset');
  return DATASETS[dataset];
}
function officialUrl(dataset,key) {
  datasetRule(dataset);
  if (typeof key !== 'string' || !/^CWA-[A-Za-z0-9-]{18,90}$/.test(key)) throw new Error('CWA server key unavailable');
  // CWA's W-C0034-005 Tropical Cyclone product is a FILE API dataset.
  // Using the REST datastore path for this product can return unavailable/invalid data.
  // Other allowlisted datasets retain the established REST route.
  const path = dataset === 'W-C0034-005'
    ? `/fileapi/v1/opendataapi/${dataset}`
    : `/api/v1/rest/datastore/${dataset}`;
  const url = new URL(`https://opendata.cwa.gov.tw${path}`);
  url.searchParams.set('Authorization', key);
  url.searchParams.set('format', 'JSON');
  if (dataset.startsWith('E-')) url.searchParams.set('limit','15');
  return url;
}
/** This function NEVER emits the URL or API key into client errors/logs. */
async function requestDataset(dataset,key,{fetchImpl=globalThis.fetch,clock=Date.now,ttlOverride}={}) {
  const rule=datasetRule(dataset), now=clock();
  const existing=cache.get(dataset);
  if (existing && existing.expiry>now) return {...existing.value,fromCache:true};
  if (pending.has(dataset)) return pending.get(dataset);
  const work=(async()=>{
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),12_000);
    try {
      const response=await fetchImpl(officialUrl(dataset,key),{
        method:'GET',redirect:'error',signal:controller.signal,
        headers:{Accept:'application/json','User-Agent':'thinn-khi-official-gateway/23'}
      });
      if (!response.ok) throw new Error('upstream HTTP failure');
      const announced=Number(response.headers?.get?.('content-length'));
      if (Number.isFinite(announced) && announced>MAX_BYTES) throw new Error('upstream too large');
      const reader=response.body?.getReader?.();
      if (!reader) throw new Error('upstream streaming unavailable');
      const chunks=[]; let count=0;
      while(true){
        const {done,value}=await reader.read();
        if(done) break;
        count+=value.byteLength;
        if(count>MAX_BYTES){controller.abort();throw new Error('upstream too large');}
        chunks.push(value);
      }
      const raw=Buffer.concat(chunks.map(c=>Buffer.from(c))).toString('utf8');
      const parsed=JSON.parse(raw);
      if (!parsed || typeof parsed!=='object' || parsed.success===false || parsed.success==='false')
        throw new Error('official feed rejected query');
      const snapshot={dataset,source:'CWA_OFFICIAL_POSTED_DATA',fetchedAtMillis:clock(),payload:raw,fromCache:false};
      cache.set(dataset,{expiry:clock()+(ttlOverride??rule.ttl),value:snapshot});
      return snapshot;
    } finally {clearTimeout(timeout);}
  })();
  pending.set(dataset,work);
  try{return await work;}finally{pending.delete(dataset);}
}
function clearForTest(){cache.clear();pending.clear();}
const MAX_KMZ_BYTES=2_000_000;
function officialKmzUrl(key) {
  if (typeof key !== 'string' || !/^CWA-[A-Za-z0-9-]{18,90}$/.test(key)) throw Error('CWA key unavailable');
  const url=new URL('https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/W-C0034-003');
  url.searchParams.set('Authorization',key);
  url.searchParams.set('format','KMZ');
  return url;
}
const kmzCache={at:0,result:null,pending:null};
async function requestKmz(key,{fetchImpl=globalThis.fetch,clock=Date.now}={}) {
  const now=clock();
  if(kmzCache.result && now-kmzCache.at<600_000) return {...kmzCache.result,fromCache:true};
  if(kmzCache.pending) return kmzCache.pending;
  const load=(async()=>{
    const ctrl=new AbortController();const timer=setTimeout(()=>ctrl.abort(),12_000);
    try {
      const r=await fetchImpl(officialKmzUrl(key),{signal:ctrl.signal,redirect:'follow',
        headers:{Accept:'application/vnd.google-earth.kmz,application/zip,application/octet-stream'}});
      if(!r.ok || !r.url?.startsWith('https://')) throw Error('CWA KMZ request failed');
      if(Number(r.headers?.get?.('content-length'))>MAX_KMZ_BYTES) throw Error('KMZ too large');
      const reader=r.body?.getReader?.();if(!reader) throw Error('KMZ stream unavailable');
      const chunks=[];let count=0;
      while(true){const {done,value}=await reader.read();if(done) break;
        count+=value.byteLength;if(count>MAX_KMZ_BYTES){ctrl.abort();throw Error('KMZ too large');}
        chunks.push(Buffer.from(value));}
      const blob=Buffer.concat(chunks);
      if(blob.length<4 || blob[0]!==0x50 || blob[1]!==0x4b) throw Error('Invalid KMZ signature');
      const result={dataset:'W-C0034-003',source:'CWA_OFFICIAL_KMZ',fetchedAtMillis:clock(),
        payloadBase64:blob.toString('base64'),fromCache:false};
      kmzCache.at=clock();kmzCache.result=result;return result;
    } finally {clearTimeout(timer);}
  })();
  kmzCache.pending=load;
  try{return await load;}finally{kmzCache.pending=null;}
}
function clearKmzForTest(){kmzCache.at=0;kmzCache.result=null;kmzCache.pending=null;}

module.exports={DATASETS,datasetRule,officialUrl,requestDataset,clearForTest,officialKmzUrl,requestKmz,clearKmzForTest};
