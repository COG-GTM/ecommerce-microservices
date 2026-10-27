import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { IN_STOCK, OUT_OF_STOCK } from './catalog';

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8080';
const GATEWAY_URL = process.env.GATEWAY_URL ?? 'http://localhost:8181';

function clientSecret(): string {
  if (process.env.OAUTH_CLIENT_SECRET) return process.env.OAUTH_CLIENT_SECRET;
  const realm = JSON.parse(readFileSync(join(__dirname, '..', 'realms', 'spring-boot-microservices-realm.json'), 'utf8'));
  return realm.clients.find((c: { clientId: string }) => c.clientId === 'spring-cloud-client').secret;
}

// Seeds the catalog through the real api-gateway -> product-service -> MongoDB path (idempotent).
export default async function globalSetup() {
  const tokenResponse = await fetch(`${KEYCLOAK_URL}/realms/spring-boot-microservices-realm/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'spring-cloud-client', client_secret: clientSecret() }),
  });
  if (!tokenResponse.ok) throw new Error(`Keycloak token request failed: HTTP ${tokenResponse.status}`);
  const { access_token: token } = await tokenResponse.json();
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  const existing: Array<{ name: string }> = await (await fetch(`${GATEWAY_URL}/api/product`, { headers })).json();
  for (const product of [IN_STOCK, OUT_OF_STOCK]) {
    if (!existing.some((p) => p.name === product.name)) {
      const created = await fetch(`${GATEWAY_URL}/api/product`, { method: 'POST', headers, body: JSON.stringify(product) });
      if (created.status !== 201) throw new Error(`Seeding ${product.name} failed: HTTP ${created.status}`);
    }
  }
}
