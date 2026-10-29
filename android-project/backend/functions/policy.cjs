'use strict';
const crypto = require('node:crypto');
const SOURCES = new Set(['CWA_OFFICIAL', 'EXPTECH_VERIFIED']);
const REGIONS = new Set(['TAIWAN','TAIPEI','NEW_TAIPEI','TAOYUAN','TAICHUNG','TAINAN','KAOHSIUNG','KEELUNG','HSINCHU','CHIAYI','MIAOLI','CHANGHUA','NANTOU','YUNLIN','PINGTUNG','YILAN','HUALIEN','TAITUNG','PENGHU','KINMEN','LIENCHIANG']);
const clean = (str, n) => typeof str === 'string' && str.length>0 && str.length<=n && !/[<>]/.test(str);
function validateAlert(a, now=Date.now()) {
  if(!a || a.kind !== 'EEW' || !SOURCES.has(a.source)) throw Error('invalid or unverified event source');
  if(!clean(a.eventId,128) || !/^[A-Za-z0-9_.-]{5,128}$/.test(a.eventId)) throw Error('invalid event ID');
  if(!Number.isInteger(a.revision) || a.revision < 0 || a.revision > 999) throw Error('invalid revision');
  if(!REGIONS.has(a.region)) throw Error('region not allowlisted');
  // Generic Taiwan broadcast may not carry an unrelated Japan EEW. A cross-border
  // event can only use a specific, validated Taiwan county routing key.
  const japanNamed = /日本|沖繩|琉球|japan|okinawa/i.test(String(a.title || ''));
  if (japanNamed && a.region === 'TAIWAN') throw Error('foreign event not routed to a verified Taiwan county');

  if(!Number.isFinite(a.issuedAtMillis) || !Number.isFinite(a.expiresAtMillis) ||
     a.issuedAtMillis > now+30000 || now-a.issuedAtMillis>120000 ||
     a.expiresAtMillis<=now || a.expiresAtMillis-a.issuedAtMillis>180000 ||
     a.expiresAtMillis<=a.issuedAtMillis) throw Error('stale/future event');
  if(!clean(a.title,120) || a.title.length<3 || !clean(a.detail,400)) throw Error('invalid text');
  if(a.magnitude!==undefined && (!Number.isFinite(a.magnitude) || a.magnitude<0 || a.magnitude>10)) throw Error('invalid magnitude');
  if(a.test || a.replay)throw Error('not a live event');
  const counties=new Set(['基隆市','臺北市','新北市','桃園市','新竹市','新竹縣','苗栗縣','臺中市','彰化縣','南投縣','雲林縣','嘉義市','嘉義縣','臺南市','高雄市','屏東縣','宜蘭縣','花蓮縣','臺東縣','澎湖縣','金門縣','連江縣']);
  const grades=new Set(['0','1','2','3','4','5弱','5強','6弱','6強','7']);
  for(const key of ['countyIntensities','countyArrivalTimes']) {
    if(a[key]===undefined)continue;
    const map=a[key];if(!map||Array.isArray(map)||typeof map!=='object'||Object.keys(map).length>22)throw Error('invalid county map');
    for(const [name,value] of Object.entries(map)) {
      if(!counties.has(name.replaceAll('台','臺')))throw Error('unknown county');
      if(key==='countyIntensities' ? !grades.has(value) : !Number.isSafeInteger(value)||value<a.issuedAtMillis-120000||value>a.expiresAtMillis+180000)throw Error('invalid county prediction');
    }
  }
  for(const [key,min,max] of [['depth',0,700],['lat',-90,90],['lon',-180,180]])if(a[key]!==undefined&&(!Number.isFinite(a[key])||a[key]<min||a[key]>max))throw Error('invalid hypocenter');
  if(a.epicenter!==undefined&&!clean(a.epicenter,160))throw Error('invalid epicenter');
  if(a.maximumIntensity!==undefined&&!grades.has(a.maximumIntensity))throw Error('invalid maximum intensity');
  if(a.originTime!==undefined&&(!Number.isSafeInteger(a.originTime)||a.originTime>a.issuedAtMillis||a.originTime<a.issuedAtMillis-120000))throw Error('invalid origin time');
  if(a.cancelled!==undefined&&typeof a.cancelled!=='boolean')throw Error('invalid cancellation');
  if(a.agency!==undefined&&(!['cwa','trem','nied'].includes(a.agency)||(a.source==='CWA_OFFICIAL'&&a.agency!=='cwa')))throw Error('invalid publishing agency');
  if(a.emergency!==undefined&&typeof a.emergency!=='boolean')throw Error('invalid emergency flag');
  return a;
}
function checkHmac(key, timestamp, raw, sig, now=Date.now()) {
  if(typeof timestamp!=='string' || !/^\d{13}$/.test(timestamp) || Math.abs(now-Number(timestamp))>60000) return false;
  if(!/^[a-f\d]{64}$/i.test(sig||'') || !key || key.length<32) return false;
  const expected=crypto.createHmac('sha256',key).update(timestamp+'.').update(raw).digest();
  return crypto.timingSafeEqual(expected, Buffer.from(sig,'hex'));
}
module.exports={validateAlert,checkHmac,REGIONS};
