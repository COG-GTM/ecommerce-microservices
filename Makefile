# Morrisons till functional-test stack. See docs/morrisons-till/TESTING.md.
TILL_DEPS := till-webapp/node_modules/.package-lock.json

.PHONY: till-up till-down till-e2e till-e2e-api till-test till-logs till-dev till-report

TILL_JAVA_HOME ?=
export TILL_JAVA_HOME

till-up: ## Build from source and start infra + services + gateway + till-webapp (waits for health)
	./scripts/till/up.sh

till-down: ## Stop the stack and drop its data volumes
	./scripts/till/down.sh

$(TILL_DEPS): till-webapp/package-lock.json
	cd till-webapp && npm ci --no-audit --no-fund

till-e2e: $(TILL_DEPS) ## Playwright UI + API suite against the running stack (videos/screenshots in till-webapp/e2e-report)
	cd till-webapp && npx playwright install chromium && npx playwright test

till-e2e-api: $(TILL_DEPS) ## Only the backend API specs through the gateway
	cd till-webapp && npx playwright test --project=api

till-test: ## Backend unit/contract tests (till-service, product/order/inventory/notification)
	./scripts/till/mvn.sh -B test -pl till-service

till-report: $(TILL_DEPS) ## Open the last Playwright HTML report
	cd till-webapp && npx playwright show-report e2e-report

till-logs: ## Follow logs of the till stack
	docker compose -f deploy/till/docker-compose.till.yml logs -f --tail 50

till-dev: $(TILL_DEPS) ## Vite dev server on :5174 against the gateway on :8181 (hot reload)
	cd till-webapp && npm run dev
