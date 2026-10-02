# Keycloak demo realm

Keycloak 26 expects import files to be named `<realm>-realm.json`, while the
source file in this repository is `spring-boot-microservices-realm.json`. Copy
it to a temporary import directory with the expected name:

```bash
import_dir="$(mktemp -d)"
cp realms/spring-boot-microservices-realm.json \
  "$import_dir/spring-boot-microservices-realm-realm.json"
docker run --rm -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v "$import_dir:/opt/keycloak/data/import:ro" \
  quay.io/keycloak/keycloak:latest start-dev --import-realm
```

The public `gap-pos` OpenID Connect client uses the authorization-code flow with
PKCE S256. Its local redirect and web origins support the Vite app at
`http://localhost:5173` and `http://127.0.0.1:5173`. It has no client secret.

The realm enables brute-force protection and locks an account after five
failures; permanent lockout is disabled.

These are demo-only accounts and passwords. Do not use them outside a local
development environment:

| Username | Password | Role | POS claims |
| --- | --- | --- | --- |
| `associate1` | `GapDemo-Associate1` | `pos-associate` | associate `A-4471`, store `1969`, register `04` |
| `manager1` | `GapDemo-Manager1` | `pos-associate`, `pos-manager` | associate `A-1002`, store `1969`, register `04` |
| `catalogadmin` | `GapDemo-Catalog1` | `product-admin` | no POS identity claims |

The service-account-only `spring-cloud-client` has no committed client secret.
If an integration needs it, generate a replacement in the Keycloak admin
console and provision it outside this repository.

Run the stdlib-only authorization-code and PKCE verifier after starting
Keycloak:

```bash
python3 realms/verify_pkce_login.py \
  --base http://localhost:8080 \
  --user associate1 \
  --password 'GapDemo-Associate1'
```

The verifier prints the decoded access-token identity and realm roles. It also
checks that a code exchange without a verifier fails, an unregistered redirect
is rejected, and the realm discovery document is available.
