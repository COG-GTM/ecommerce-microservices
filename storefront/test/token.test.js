import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EXPIRY_SKEW_SECONDS, createTokenProvider } from '../src/token.js';

function fakeFetch(responses) {
  const calls = [];
  const impl = async (url, init) => {
    calls.push({ url, init });
    return responses.shift();
  };
  return { impl, calls };
}

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

test('requests a client-credentials token with form-encoded credentials', async () => {
  const { impl, calls } = fakeFetch([ok({ access_token: 'abc', expires_in: 300 })]);
  const getToken = createTokenProvider({ tokenUrl: 'http://kc/token', clientId: 'client', clientSecret: 's3cret', fetchImpl: impl });

  assert.equal(await getToken(), 'abc');
  assert.equal(calls[0].url, 'http://kc/token');
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers['Content-Type'], 'application/x-www-form-urlencoded');
  const form = new URLSearchParams(calls[0].init.body);
  assert.deepEqual(Object.fromEntries(form), { grant_type: 'client_credentials', client_id: 'client', client_secret: 's3cret' });
});

test('caches the token until expiry minus skew, then refreshes', async () => {
  let clock = 0;
  const { impl, calls } = fakeFetch([ok({ access_token: 'first', expires_in: 60 }), ok({ access_token: 'second', expires_in: 60 })]);
  const getToken = createTokenProvider({ tokenUrl: 'u', clientId: 'c', clientSecret: 's', fetchImpl: impl, now: () => clock });

  assert.equal(await getToken(), 'first');
  clock = (60 - EXPIRY_SKEW_SECONDS) * 1000 - 1;
  assert.equal(await getToken(), 'first');
  clock += 1;
  assert.equal(await getToken(), 'second');
  assert.equal(calls.length, 2);
});

test('treats a missing or tiny expires_in as immediately stale', async () => {
  const { impl, calls } = fakeFetch([ok({ access_token: 'a' }), ok({ access_token: 'b', expires_in: 5 })]);
  const getToken = createTokenProvider({ tokenUrl: 'u', clientId: 'c', clientSecret: 's', fetchImpl: impl, now: () => 0 });

  assert.equal(await getToken(), 'a');
  assert.equal(await getToken(), 'b');
  assert.equal(calls.length, 2);
});

test('surfaces token endpoint failures with the HTTP status', async () => {
  const { impl } = fakeFetch([{ ok: false, status: 401 }]);
  const getToken = createTokenProvider({ tokenUrl: 'u', clientId: 'c', clientSecret: 'wrong', fetchImpl: impl });

  await assert.rejects(getToken(), /HTTP 401/);
});
