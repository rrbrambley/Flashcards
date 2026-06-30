# Local dev helpers for the Flashcards backend + web app.
#
#   make start     Start Postgres (if needed) + the backend at http://localhost:8080
#   make stop      Stop the backend
#   make restart   Restart the backend (use after changing backend code)
#   make logs      Tail the backend log
#   make status    Show what's running
#   make web       Start the web dev server at http://localhost:5173 (foreground)
#   make db        Start just the local Postgres
#   make db-stop   Stop the local Postgres container
#   make reseed    Wipe the Postgres volume + restart so the backend reseeds from scratch
#   make admin ARGS="user list"   Run the admin CLI against the local Postgres
#   make avatars   Deploy the profile-avatar set (assets/avatars/) to S3 → CloudFront
#
# The backend points at whatever host port the flashcards-postgres container publishes
# for 5432, so a non-default mapping (e.g. 5433 when 5432 is taken) is handled automatically.

SHELL := /bin/bash
BACKEND_LOG := backend.local.log

.PHONY: start stop restart logs status web db db-stop reseed admin avatars

db:
	@if docker ps --format '{{.Names}}' | grep -q '^flashcards-postgres$$'; then \
		echo "Postgres already running on :$$(docker port flashcards-postgres 5432/tcp | head -1 | sed 's/.*://')."; \
	else \
		p=$${POSTGRES_PORT:-5432}; \
		if lsof -ti tcp:$$p >/dev/null 2>&1; then echo "Port $$p is busy — using 5433 instead."; p=5433; fi; \
		echo "Starting Postgres on :$$p"; \
		POSTGRES_PORT=$$p docker compose up -d postgres; \
	fi

db-stop:
	docker compose stop postgres

# Run the admin CLI against the local Postgres. Pass the subcommand via ARGS, e.g.
#   make admin ARGS="user create --email a@b.com --password password1"
#   make admin ARGS="user list"
admin: db
	@port=$$(docker port flashcards-postgres 5432/tcp 2>/dev/null | head -1 | sed 's/.*://'); \
	port=$${port:-5432}; \
	./gradlew --quiet --console=plain :backend:admin \
		-PDB_JDBC_URL=jdbc:postgresql://localhost:$$port/flashcards --args="$(ARGS)"

# Destructive: deletes the flashcards-pgdata volume so the next backend boot recreates
# the tables and runs DatabaseFactory.seed() from scratch (demo user/token + global decks).
reseed:
	@read -p "This DELETES all local Postgres data and reseeds. Continue? [y/N] " ok; \
	[ "$$ok" = "y" ] || [ "$$ok" = "Y" ] || { echo "Aborted."; exit 1; }
	@$(MAKE) stop
	docker compose down -v
	@$(MAKE) start

start: db
	@if lsof -ti tcp:8080 >/dev/null 2>&1; then \
		echo "Backend already running on :8080 (use 'make restart' to reload code)."; exit 0; fi; \
	port=$$(docker port flashcards-postgres 5432/tcp 2>/dev/null | head -1 | sed 's/.*://'); \
	port=$${port:-5432}; \
	echo "Starting backend → http://localhost:8080 (Postgres on :$$port). Logs: $(BACKEND_LOG)"; \
	DB_JDBC_URL=jdbc:postgresql://localhost:$$port/flashcards \
		nohup ./gradlew --console=plain :backend:run > $(BACKEND_LOG) 2>&1 & \
	echo "Waiting for startup…"; \
	for i in $$(seq 1 90); do \
		if lsof -ti tcp:8080 >/dev/null 2>&1; then echo "✓ Backend up at http://localhost:8080"; exit 0; fi; \
		if ! pgrep -f "backend:run" >/dev/null 2>&1 && [ $$i -gt 3 ]; then echo "✗ Backend exited — see $(BACKEND_LOG)"; exit 1; fi; \
		sleep 1; \
	done; \
	echo "✗ Backend didn't come up in time — see $(BACKEND_LOG)"; exit 1

stop:
	@pid=$$(lsof -ti tcp:8080); \
	if [ -n "$$pid" ]; then kill $$pid && echo "Stopped backend (pid $$pid)."; else echo "Backend not running."; fi

restart: stop
	@sleep 1; $(MAKE) start

logs:
	@touch $(BACKEND_LOG); tail -f $(BACKEND_LOG)

status:
	@if lsof -ti tcp:8080 >/dev/null 2>&1; then echo "backend:  RUNNING (http://localhost:8080)"; else echo "backend:  stopped"; fi
	@if docker ps --format '{{.Names}}' | grep -q '^flashcards-postgres$$'; then \
		echo "postgres: RUNNING ($$(docker port flashcards-postgres 5432/tcp 2>/dev/null | head -1))"; \
	else echo "postgres: stopped"; fi

web:
	cd webApp && npm run dev

# Deploy the curated profile-avatar set (FLA-162/163) to S3 under the avatars/ prefix, served
# via the same CloudFront distribution as flashcard images → $CDN_BASE_URL/avatars/<key>.png.
# Needs S3_BUCKET set and AWS credentials in the environment (or ~/.aws). Long-cache + immutable,
# so re-running re-uploads changed files only.
avatars:
	@bucket="$${S3_BUCKET:?Set S3_BUCKET (and AWS creds via env or ~/.aws) first}"; \
	aws s3 sync assets/avatars/ "s3://$$bucket/avatars/" \
		--content-type image/png \
		--cache-control "public, max-age=31536000, immutable"; \
	echo "Uploaded $$(ls assets/avatars/*.png | wc -l | tr -d ' ') avatars → s3://$$bucket/avatars/"
