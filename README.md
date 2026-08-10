# EazyBank — Cloud-Native Microservices Platform

[![CI](https://github.com/jaime-rey/eazybank-section-17/actions/workflows/ci.yml/badge.svg)](https://github.com/jaime-rey/eazybank-section-17/actions/workflows/ci.yml)

A 6-microservice Spring Boot banking platform deployed to Kubernetes — locally on Docker Desktop and on **Google Kubernetes Engine (GKE)** — with Helm-managed multi-environment releases (dev / qa / prod), OAuth2 security, event streaming and observability.

> Based on the [EazyBytes microservices course](https://github.com/eazybytes), extended with my own deployment engineering: multi-environment Helm setup with namespace isolation, GKE deployment with Artifact Registry, distributed-systems debugging and full deploy automation.

## Architecture

```
                        ┌──────────────┐
   client ─── JWT ────▶ │ gatewayserver │ ── Redis (rate limiting)
                        └──────┬───────┘
                               │  service discovery: Spring Cloud Kubernetes
              ┌────────────┬───┴────────┬─────────────┐
              ▼            ▼            ▼             ▼
         ┌─────────┐  ┌───────┐   ┌────────┐   ┌──────────┐
         │ accounts │  │ cards │   │ loans  │   │ message  │◀── Kafka
         └────┬────┘  └───┬───┘   └───┬────┘   └──────────┘
              └───────────┴───────────┘
                          │
                    ┌─────▼─────┐         ┌────────────┐
                    │ H2 server │         │configserver│ ◀── git config repo
                    └───────────┘         └────────────┘

   Security: Keycloak (OAuth2 client-credentials, JWT validated at gateway)
   Observability: Prometheus + Grafana
```

**Services:** `configserver` · `accounts` · `cards` · `loans` · `gatewayserver` · `message`

**Infrastructure:** Kafka · Keycloak · Redis · Prometheus / Grafana · Spring Cloud Kubernetes Discovery Server

## Highlights

- **Multi-environment Helm setup** — three parallel releases (dev/qa/prod) with per-namespace isolation, environment-specific values, and a reusable discovery-server manifest using namespace-scoped RBAC (`Role`, not `ClusterRole`).
- **GKE deployment** — images built with Jib, pushed to Artifact Registry, pulled by GKE nodes via IAM binding on the repository. Validated end-to-end from outside the cluster (token → create → fetch → delete) with Bruno.
- **Real debugging stories** — documented in [HANDOFF.md](HANDOFF.md), including a split-brain caused by per-pod in-memory H2 databases behind a round-robin Service (fixed with a shared H2 server and env-injected datasource URLs), Keycloak OOMKills, and Artifact Registry `403 ImagePull` IAM issues.
- **One-command automation** — PowerShell scripts to build all images, deploy the full cluster, deploy additional environments, and tear everything down.
- **Integration tests with Testcontainers** — `accounts` spins up a real **MySQL 8.4** in Docker during CI (via `@ServiceConnection`), validating the production `schema.sql` and JPA layer against the real database engine, not H2 in compatibility mode. See [`AccountsRepositoryIT`](accounts/src/test/java/com/eazybytes/accounts/AccountsRepositoryIT.java).

## Quick start (Docker Desktop Kubernetes)

```powershell
# Build the 6 service images
.\build-images-s17.ps1

# Deploy infra + dev environment
.\deploy-cluster.ps1

# Health check
curl.exe http://localhost:8072/actuator/health

# Tear down
.\teardown-cluster.ps1
```

Deploy additional environments:

```powershell
.\deploy-env.ps1 -Env qa-env   -Release eazybank-qa   -Namespace qa
.\deploy-env.ps1 -Env prod-env -Release eazybank-prod -Namespace prod
```

## Documentation

- [DEPLOY-README.md](DEPLOY-README.md) — full deployment guide (local and GKE)
- [GKE-FROM-SCRATCH.md](GKE-FROM-SCRATCH.md) — from-scratch recipe to recreate the GKE cluster (markdown)
- [MANUAL-GKE.html](MANUAL-GKE.html) — same recipe as a self-contained, styled HTML manual (open locally in a browser)
- [HANDOFF.md](HANDOFF.md) — architecture decisions, problems solved and lessons learned
- [bruno-collection/](bruno-collection/README.md) — end-to-end API tests (Local and Remote-GKE environments)

## Tech stack

Java · Spring Boot · Spring Cloud Kubernetes · Docker · Kubernetes · Helm · GKE · Jib · Kafka · Keycloak (OAuth2/JWT) · Redis · Prometheus · Grafana · H2 · MySQL · Testcontainers · JUnit 5 · Bruno · GitHub Actions
