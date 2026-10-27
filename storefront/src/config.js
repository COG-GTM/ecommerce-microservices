import { createTokenProvider } from './token.js';

export function loadConfig(env) {
  const required = ['GATEWAY_URL', 'OAUTH_TOKEN_URL', 'OAUTH_CLIENT_ID', 'OAUTH_CLIENT_SECRET'];
  const missing = required.filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }
  return {
    port: Number(env.PORT ?? 3001),
    gatewayUrl: env.GATEWAY_URL.replace(/\/$/, ''),
    getToken: createTokenProvider({
      tokenUrl: env.OAUTH_TOKEN_URL,
      clientId: env.OAUTH_CLIENT_ID,
      clientSecret: env.OAUTH_CLIENT_SECRET,
    }),
  };
}
