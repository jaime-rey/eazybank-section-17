# EazyBank – Section 17: image build (s17) and Kubernetes deploy

Guide to build the Docker images of the microservices with the **`s17`** tag and
bring up the cluster on **Docker Desktop's Kubernetes** with Kafka, Keycloak,
Prometheus, Grafana, Redis and (optionally) Loki/Tempo/Alloy, using the Helm
charts in the repo.

The charts are already tuned so a from-scratch deploy works **without manual
patches**:

- `s17` tag pinned in each `helm/eazybank-services/*/values.yaml`.
- `eurekaserver` removed from `dev-env` (this project uses the native **Spring
  Cloud Kubernetes Discovery Server**, not Eureka).
- Discovery server URL injected via `global.discoveryServerURL` in the common template.
- **Redis** deployed and wired to the gateway (rate limiting) via `redis_enabled` + `global.redisHost`.
- **Keycloak** with `2Gi` of memory (with 1Gi it died with `OOMKilled`).

## Prerequisites

- Docker Desktop with **Kubernetes enabled** (`Settings → Kubernetes → Enable`).
  Assigning **8 GB+ of RAM** is recommended (`Settings → Resources`).
- `kubectl` pointing at docker-desktop: `kubectl config use-context docker-desktop`.
- `helm` v3+ installed.
- JDK 17+ (for the Jib build).

## 1. Build the `:s17` images

```powershell
cd "C:\Users\User\spring boot\section_17"
.\build-images-s17.ps1
```

Builds: **configserver, accounts, cards, loans, gatewayserver, message**. Verify:

```powershell
docker images "eazybytes/*" | Select-String s17
```

## 2. Deploy the cluster

```powershell
.\deploy-cluster.ps1                    # infra + Redis + microservices
.\deploy-cluster.ps1 -WithObservability # + Loki, Tempo, Alloy
```

The script does, in order:

1. `kafka`, `keycloak`, `kube-prometheus`, `grafana` (Helm).
2. `redis` (`redis.yaml` manifest).
3. *(optional)* `grafana-loki`, `grafana-tempo`, `grafana-alloy`.
4. `helm dependency update` for each service and for the `dev-env` umbrella
   (needed because `eazybank-common` changed).
5. `dev-env` → all microservices with the `s17` tag.

It is normal for some pods to restart a couple of times until `configserver` is ready.

## 3. Verification

```powershell
kubectl get pods
curl.exe http://localhost:8072/actuator/health   # should return {"status":"UP"}
```

All pods should end up `1/1 Running`.

## 4. Tool access

Everything is exposed on `localhost` (Docker Desktop, LoadBalancer type):

| Component | URL | Notes |
|---|---|---|
| Gateway (API entry point) | http://localhost:8072 | routes to accounts/cards/loans |
| Grafana | http://localhost:3000 | default chart credentials |
| Prometheus | http://localhost:9090 | |
| Keycloak | http://localhost | admin: `user` / `password` |

Check actual ports with `kubectl get svc`.

## 5. Teardown

```powershell
.\teardown-cluster.ps1
```

## Notes

- **Discovery server:** the `spring-cloud-kubernetes-discoveryserver` pod must be
  running in the cluster (deployed separately, not part of these charts). If it
  is not there, deploy it first; the microservices need it to discover each other.
- **`tempo` errors in the logs:** if you do NOT use `-WithObservability`, you will
  see OpenTelemetry traces failing to export to `tempo` (`UnknownHostException`).
  It is **harmless noise**: it does not affect health or functionality. It goes
  away when you deploy observability, or you can just ignore it.
