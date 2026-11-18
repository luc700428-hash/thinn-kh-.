'use strict';
// Cloud test alert: one push to the requesting phone only, never to the EEW topic.
const COOLDOWN_MS = 5 * 60 * 1000;
const LIFETIME_MS = 3 * 60 * 1000;
const NONCE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** Throws on anything but an FCM registration token and a fresh UUID from the app. */
function validateTestRequest(v) {
  if (!v || typeof v !== 'object') throw new Error('invalid request');
  if (typeof v.token !== 'string' || v.token.length < 70 || v.token.length > 4096 || /\s/.test(v.token))
    throw new Error('invalid token');
  if (typeof v.nonce !== 'string' || !NONCE.test(v.nonce)) throw new Error('invalid nonce');
  return {token: v.token, nonce: v.nonce};
}

/** Milliseconds still to wait since the last request (0 = may send now). */
function cooldownLeft(lastMillis, now) {
  if (!Number.isFinite(lastMillis) || lastMillis <= 0) return 0;
  if (lastMillis > now) return COOLDOWN_MS; // clock went backwards: stay safe
  return Math.max(0, lastMillis + COOLDOWN_MS - now);
}

/** Data-only, high-priority FCM message for exactly one token; the app checks the nonce. */
function testMessage(token, nonce, now) {
  return {
    token,
    data: {
      kind: 'EEW_TEST', nonce,
      issuedAtMillis: String(now), expiresAtMillis: String(now + LIFETIME_MS),
      title: '雲端測試警報',
      detail: '這是 thinn-khì 雲端主機送出的測試，並非真實地震。',
    },
    android: {priority: 'high', ttl: LIFETIME_MS},
  };
}

module.exports = {validateTestRequest, cooldownLeft, testMessage, COOLDOWN_MS, LIFETIME_MS};
