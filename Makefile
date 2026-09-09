# Flipo — local development commands.
#
# The data stack lives in docker-compose.yml (Postgres only). Targets that
# need docker (up, down, logs, psql, reset) require `docker compose` on the
# host. validate-backend/validate-frontend detect /backend and /frontend and
# run Maven/npm tooling there.

REPO_ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))

# Load .env if it exists; silently ignore if missing so a fresh clone still
# works against the defaults baked into docker-compose.yml.
-include $(REPO_ROOT).env

DB_USER     ?= flipo
DB_PASSWORD ?= flipo_dev_password
DB_NAME     ?= flipo
DB_HOST     ?= localhost
DB_PORT     ?= 5432

export DB_USER
export DB_PASSWORD
export DB_NAME
export DB_HOST
export DB_PORT

COMPOSE := docker compose

.DEFAULT_GOAL := help

.PHONY: help up down logs psql reset validate-backend validate-frontend test

help: ## Show this help.
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

##@ Stack

up: ## Start Postgres in the background. Waits for healthcheck.
	$(COMPOSE) up -d --wait
	@echo
	@echo "Postgres is up. 'make psql' to connect, 'make validate-backend' to run backend tests."

down: ## Stop the stack. Data volume is preserved.
	$(COMPOSE) down

logs: ## Tail logs from Postgres.
	$(COMPOSE) logs -f

reset: ## Stop the stack AND delete the data volume. Destructive.
	@echo "WARNING: this will DELETE all data in the 'flipo-postgres-data' volume."
	@echo "Type 'yes' and press Enter to continue, or Ctrl+C to abort."
	@read -r REPLY && [ "$$REPLY" = "yes" ] || { echo "Aborted."; exit 1; }
	$(COMPOSE) down -v

##@ Database

psql: ## Open an interactive psql shell to Postgres.
	$(COMPOSE) exec postgres psql -U $(DB_USER) -d $(DB_NAME)

##@ Backend / Frontend

validate-backend: ## Run backend build + tests via the Maven wrapper. Requires Postgres up (make up).
	@if [ ! -d $(REPO_ROOT)backend ]; then \
		echo "backend/ not yet scaffolded."; exit 1; \
	fi
	(cd $(REPO_ROOT)backend && ./mvnw test)

validate-frontend: ## Run frontend lint + build. Requires npm install first.
	@if [ ! -d $(REPO_ROOT)frontend ]; then \
		echo "frontend/ not yet scaffolded."; exit 1; \
	fi
	(cd $(REPO_ROOT)frontend && npm run build)

test: validate-backend validate-frontend ## Run both backend and frontend validation.
