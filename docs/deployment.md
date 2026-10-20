# NexusHR deployment and operations

## Local demo stack

The Compose stack builds and runs PostgreSQL, Redis, the Spring Boot API, and the React/Nginx frontend. Copy `.env.example` to `.env`, set unique database and JWT secrets, and then run:

```powershell
docker compose up --build -d
docker compose ps
```

Open the frontend at `http://localhost:5173`; Nginx serves the SPA and proxies `/api`, `/ws`, Swagger, and management paths to the backend. The API is also available at `http://localhost:8090`. PostgreSQL and application ports bind to loopback for local development.

Migrations run automatically at backend startup. Health checks gate API startup on PostgreSQL readiness and frontend startup on API readiness. The API exposes health probes and Prometheus metrics on port 8091 inside the Compose network; that management port is not published to the host.

## Monitoring

Add unique `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` values to the root `.env`, then start the monitoring overlay:

```powershell
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build -d
```

Prometheus is at `http://localhost:9090`; Grafana is at `http://localhost:3000`. Grafana provisions the Prometheus data source and the NexusHR service-health dashboard automatically. Metrics include HTTP request rates/errors and JVM memory/thread counts. No Grafana credentials are committed to the repository.

Redis is included as a ready service, but the current application does not yet use it for sessions, caching, or distributed rate limiting. The Module 10 limiter remains process-local.

## CI, backups, and rollback

GitHub Actions runs backend verification, frontend compilation, and both Docker image builds on pushes and pull requests. The dependency-review workflow separately blocks high-severity dependency changes.

Create a PostgreSQL custom-format backup with `./infrastructure/backup.ps1`. Restore with `./infrastructure/restore.ps1 -BackupFile <path>`; the restore script asks for explicit confirmation and stops the backend during the operation. Store backup files outside the repository, encrypt them, and test restoration before relying on them.

For local rollback, retain the previously built image tags or image IDs before replacing a release, then stop the new containers and start the previous backend/frontend images with the same database and environment. Flyway migrations should be additive and backward-compatible across the rollback window; a code rollback does not automatically reverse database migrations.

## Production boundary

This Compose configuration is a local demo deployment, not a public internet production stack. Before production, terminate TLS at a managed load balancer or reverse proxy, set `COOKIE_SECURE=true`, `SWAGGER_ENABLED=false`, use deployment-managed secrets, restrict database/Redis/management network access, configure durable off-host encrypted backups, and use shared storage for rate limiting. Choose a hosting target and provision its credentials before performing a live deployment; this repository does not contain cloud account or domain configuration.
