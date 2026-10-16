import Keycloak from 'keycloak-js';
import {
  AUTH_TOKEN,
  KEYCLOAK_CLIENT_ID,
  KEYCLOAK_REALM,
  KEYCLOAK_URL,
  USE_MOCK_DATA,
} from './config';

/** Refresh when the access token has less than this many seconds left. */
const MIN_TOKEN_VALIDITY_SECONDS = 30;

let keycloak: Keycloak | null = null;

/**
 * Live mode: Authorization Code + PKCE (S256) redirect to Keycloak before the app
 * renders. Tokens stay in memory (keycloak-js instance), never in web storage.
 * Mock mode and a `VITE_API_TOKEN` override skip the login entirely.
 */
export async function initAuth(): Promise<void> {
  if (USE_MOCK_DATA || AUTH_TOKEN || keycloak) return;

  const instance = new Keycloak({
    url: KEYCLOAK_URL,
    realm: KEYCLOAK_REALM,
    clientId: KEYCLOAK_CLIENT_ID,
  });
  instance.onTokenExpired = () => {
    instance.updateToken(MIN_TOKEN_VALIDITY_SECONDS).catch(() => instance.login());
  };
  await instance.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  });
  keycloak = instance;
}

/** Bearer token for gateway calls; refreshes it first when close to expiry. */
export async function getAccessToken(): Promise<string | undefined> {
  if (AUTH_TOKEN) return AUTH_TOKEN;
  if (!keycloak) return undefined;
  try {
    await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
  } catch {
    await keycloak.login();
    return undefined;
  }
  return keycloak.token;
}

/** Test-only: drop the in-memory session. */
export function resetAuthForTests(): void {
  keycloak = null;
}
