// OAuth2 client-credentials token provider for the api-gateway (Keycloak), with expiry-aware caching.

export const EXPIRY_SKEW_SECONDS = 30;

export function createTokenProvider({ tokenUrl, clientId, clientSecret, fetchImpl = fetch, now = Date.now }) {
  let cached = null;

  return async function getToken() {
    if (cached && cached.expiresAt > now()) {
      return cached.accessToken;
    }
    const response = await fetchImpl(tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: clientId,
        client_secret: clientSecret,
      }).toString(),
    });
    if (!response.ok) {
      throw new Error(`Token request failed with HTTP ${response.status}`);
    }
    const body = await response.json();
    const lifetimeSeconds = Math.max((body.expires_in ?? 0) - EXPIRY_SKEW_SECONDS, 0);
    cached = { accessToken: body.access_token, expiresAt: now() + lifetimeSeconds * 1000 };
    return cached.accessToken;
  };
}
