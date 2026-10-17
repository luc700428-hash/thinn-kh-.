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
  return a;
}
function checkHmac(key, timestamp, raw, sig, now=Date.now()) {
  if(typeof timestamp!=='string' || !/^\d{13}$/.test(timestamp) || Math.abs(now-Number(timestamp))>60000) return false;
  if(!/^[a-f\d]{64}$/i.test(sig||'') || !key || key.length<32) return false;
  const expected=crypto.createHmac('sha256',key).update(timestamp+'.').update(raw).digest();
  return crypto.timingSafeEqual(expected, Buffer.from(sig,'hex'));
}
module.exports={validateAlert,checkHmac,REGIONS};
