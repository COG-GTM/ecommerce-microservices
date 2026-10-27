import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../src/config.js';

const env = {
  GATEWAY_URL: 'http://gateway:8080/',
  OAUTH_TOKEN_URL: 'http://kc/token',
  OAUTH_CLIENT_ID: 'client',
  OAUTH_CLIENT_SECRET: 'secret',
};

test('builds config with defaults and trims the gateway trailing slash', () => {
  const config = loadConfig(env);
  assert.equal(config.port, 3001);
  assert.equal(config.gatewayUrl, 'http://gateway:8080');
  assert.equal(typeof config.getToken, 'function');
});

test('honours an explicit PORT', () => {
  assert.equal(loadConfig({ ...env, PORT: '4000' }).port, 4000);
});

test('lists every missing required variable', () => {
  assert.throws(() => loadConfig({ GATEWAY_URL: 'x' }),
    /Missing required environment variables: OAUTH_TOKEN_URL, OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET/);
});
