# HANDOFF — EazyBank Section 17 (s17 build + Kubernetes on Docker Desktop)

Context to continue in Claude Code (IDE integrated terminal). Read this file
and pick up from "Pending". State as of the last session.

## Goal
Build the Docker images of the 6 microservices with the `s17` tag and deploy
the full cluster on Docker Desktop's Kubernetes (Kafka, Keycloak, Prometheus,
Grafana, Redis + microservices). EazyBytes course, project at
`C:\Users\User\spring boot\section_17`.

## Relevant architecture
- 6 microservices (Spring Boot, built with Jib): configserver, accounts, cards, loans, gatewayserver, message.
  Each `pom.xml` builds `eazybytes/<svc>:s17`.
- **No Eureka**: discovery via **Spring Cloud Kubernetes Discovery Server**
  (service `spring-cloud-kubernetes-discoveryserver:80`, deployed separately, NOT in these charts).
- External config: config server pulls from git repo `https://github.com/eazybytes/eazybytes-config.git`.
- Gateway uses **Redis** for rate limiting.

## Current cluster state
Three environments deployed and healthy in parallel:

| Namespace | Release         | Gateway            | Own infra                             | Health |
|-----------|-----------------|--------------------|---------------------------------------|--------|
| `default` | `dev-env`       | http://localhost:8072 | redis + discovery in `default`      | UP     |
| `qa`      | `eazybank-qa`   | http://localhost:8073 | shares `default`'s redis+discovery via FQDN | UP |
| `prod`    | `eazybank-prod` | http://localhost:8074 | own redis + discovery in `prod`     | UP     |

Shared infra in `default`: `kafka`, `keycloak`, `kube-prometheus`, `grafana`, `redis` (manifest),
`spring-cloud-kubernetes-discoveryserver` (manifest, see below).

Access (Docker Desktop, LoadBalancer on localhost):
- Gateways: dev 8072 · qa 8073 · prod 8074
- Grafana: http://localhost:3000 · Prometheus: http://localhost:9090
- Keycloak: http://localhost (admin `user` / `password`)

## Problems solved (and how)
1. **s14 vs s17 tag**: the service charts pointed to `s14`. Pinned `s17` in each
   `helm/eazybank-services/*/values.yaml`.
2. **Non-existent eurekaserver**: removed from `dev-env/Chart.yaml` and its `.tgz`
   deleted from `charts/`. (The native K8s discovery server is used.)
3. **Missing discovery URL** (crash `DiscoveryServerUrlInvalidException`): added
   `global.discoveryServerURL` and its injection as env
   `SPRING_CLOUD_KUBERNETES_DISCOVERY_DISCOVERYSERVERURL` in
   `helm/eazybank-common/templates/deployment.yaml`.
4. **Keycloak OOMKilled**: memory bumped to 2Gi in `helm/keycloak/values.yaml`.
5. **Redis DOWN in the gateway**: created `redis.yaml` (Deployment+Service `redis:6379`);
   gateway with `redis_enabled: true` + `global.redisHost/redisPort`.
6. **Full Eureka cleanup**: after fix #3 there were inert leftovers (an orphan
   key in the ConfigMap, an `{{ if .Values.eureka_enabled }}` block in
   deployment.yaml, an `eureka_enabled` flag in all 6 service values files —with
   the latent inconsistency that accounts/cards/loans had it `true`—, a
   `eurekaServerURL` in the 3 envs, and a Prometheus eureka scrape job). All
   removed; `grep -i eureka helm/` returns 0 results. No functional change (it
   was all dead code), but it removes future confusion.
7. **H2 split-brain with `replicas: 2`** (data bug, not code): each accounts/cards/loans
   pod carried its own in-memory H2 (`jdbc:h2:mem:testdb`), and the K8s Service
   round-robins → Create hit pod A, Fetch hit pod B → 404. In Bruno it showed
   as pseudo-random flakiness (~50% failures) impossible to debug without
   looking at the replica manifest. Fix: `h2-server.yaml` (image
   `oscarfonts/h2`, service `h2:1521`) that runs 1 H2 server on the side; each
   service connects to **its own logical DB** in it via
   `jdbc:h2:tcp://h2:1521/mem:{accounts,cards,loans}db`. The override is
   injected via the `SPRING_DATASOURCE_URL` env in
   `eazybank-common/templates/deployment.yaml` with a `{{ if .Values.h2_enabled }}`
   block (same mechanism as Redis). Enabled in accounts/cards/loans
   `values.yaml` with `h2_enabled: true` + `h2_dbName: XXXdb`. No Java code
   changes. Caveat: it is still `mem:` (if the H2 pod restarts the data is
   lost) — enough for dev; for persistence switch to `file:` with a PVC.

All of the above is **consolidated in the charts** (no hot patches). One
`build-images-s17.ps1` + `deploy-cluster.ps1` brings everything up from scratch.

## Files created/edited
Created: `build-images-s17.ps1`, `deploy-cluster.ps1`, `deploy-env.ps1`,
`teardown-cluster.ps1`, `redis.yaml`, `discovery-server.yaml`, `h2-server.yaml`,
`create-keycloak-client.ps1`, `bruno-collection/`, `DEPLOY-README.md`, this `HANDOFF.md`.
Edited (charts): `helm/eazybank-common/templates/{deployment.yaml,configmap.yaml}`,
`helm/environments/{dev,qa,prod}-env/values.yaml`, `helm/environments/dev-env/Chart.yaml`,
`helm/keycloak/values.yaml`, `helm/kube-prometheus/templates/configmap.yaml`,
`helm/eazybank-services/*/values.yaml` (s17 tag, gateway with `redis_enabled: true`,
`eureka_enabled` removed from all, accounts/cards/loans with `h2_enabled: true`).

`discovery-server.yaml`: reusable manifest with the 5 discovery-server objects
(`ServiceAccount`, `Role namespace-reader`, `RoleBinding`, `Deployment`, `Service`).
Apply with `kubectl apply -f discovery-server.yaml -n <ns>` in any namespace
where you want a dedicated discovery. Uses `Role` (not `ClusterRole`), so it
only reads pods/services/endpoints from ITS namespace — key for isolation
(see next section).

## The 3 environments (helm/environments)
dev-env (default profile), qa-env (qa), prod-env (prod). They differ in
`configMapName`, `activeProfile`, gateway port and infra strategy:

- **dev-env** (namespace `default`): gateway 8072. Redis and discovery in `default`
  (referenced by short name — they resolve local to the namespace).
- **qa-env** (namespace `qa`): gateway 8073. `redisHost` and `discoveryServerURL`
  use **FQDN to `.default.svc.cluster.local`** → reuses `default`'s infra.
  `gatewayserver.service.port: 8073` overridden from the env values file.
  **NOT fully isolated** — see "Isolation" section.
- **prod-env** (namespace `prod`): gateway 8074. `redisHost` and `discoveryServerURL`
  use **short name** → resolve to **own** redis+discovery deployed in `prod`.
  Full isolation. `gatewayserver.service.port: 8074` overridden from the env values file.

Port trick: only `service.port` (external) is overridden. `service.targetPort`
and `containerPort` stay at 8072 → the container keeps listening where it
always does, the LoadBalancer translates `localhost:807X` → pod:8072.

## Isolation between environments (important)
The discovery server has a `Role` (not `ClusterRole`) → it only sees pods/services
in ITS namespace. The services in the 3 environments are all named the same
(`accounts`, `cards`, `loans`, `gatewayserver`, `message`, `configserver`), so
**which discovery resolves matters**:

- **qa** points to `default`'s discovery ("FQDN" option). Individual health is OK,
  but when qa's gateway routes to `/accounts/...`, discovery returns the `accounts`
  pods from the `default` namespace (= dev-env). Cross-contamination of traffic.
  Fine for verifying the deploy; NOT fine for real qa testing.
- **prod** has its own discovery in `prod`. prod's gateway only sees prod pods.
  Full isolation.

If at some point you want qa truly isolated, migrate from "FQDN" to "own infra":
`kubectl apply -f redis.yaml -n qa` + `kubectl apply -f discovery-server.yaml -n qa`,
and revert the 2 lines in `qa-env/values.yaml` back to short names.

## Deploy on GKE (second pass, OK end-to-end · cluster DELETED 2026-08-07)

> **Operational recipe to recreate:** [GKE-FROM-SCRATCH.md](GKE-FROM-SCRATCH.md).
> This section keeps context and history; the reproducible recipe lives there.

**Account/project** (persist): `jreycasa@gmail.com` / `project-aaa96c1a-20d1-43bf-819`
(project number `594159792471`). Default region `us-central1`.

**What survived the cluster delete:**
- Artifact Registry `us-central1-docker.pkg.dev/project-aaa96c1a-20d1-43bf-819/eazybank/*`
  with the 6 s17 images pushed (~1.25 GB, ~$0.13/month).
- **IAM binding on the `eazybank` repo**: `roles/artifactregistry.reader` for
  `594159792471-compute@developer.gserviceaccount.com` (the default compute SA
  that GKE nodes use). Persists after cluster deletion because it lives on the
  repo, not the cluster.
- Enabled APIs: container, artifactregistry, compute.
- Overlay `helm/environments/dev-env/gke-values.yaml` (points at Artifact
  Registry + `replicaCount: 1`).

**Lessons learned** (applies on recreate):
- With 3 e2-medium nodes (~4.5 usable vCPU) the full stack **does not fit** —
  Keycloak stays Pending due to lack of CPU. **Start with 4 nodes from the beginning.**
- **Standard zonal** cluster: control plane is free (first cluster free tier).
- kubectl needs `gke-gcloud-auth-plugin` on PATH — ships with gcloud SDK; if
  the terminal is new, do `set PATH=%PATH%;C:\Users\User\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin`
  or open a new PowerShell.
- **ImagePull 403 to Artifact Registry**: the node pool's default oauth scopes
  (`devstorage.read_only`) **do work** for pulls from AR — no need to recreate
  the pool with `cloud-platform`. What is missing by default is the IAM: the
  default compute SA (`<project-number>-compute@developer.gserviceaccount.com`)
  needs `roles/artifactregistry.reader` on the repo. Fix (one-time per project,
  already applied and persistent):
  ```powershell
  gcloud artifacts repositories add-iam-policy-binding eazybank `
    --location=us-central1 `
    --member=serviceAccount:594159792471-compute@developer.gserviceaccount.com `
    --role=roles/artifactregistry.reader
  ```
  Symptom without the binding: pods in `ErrImagePull`; `kubectl describe` shows
  `failed to fetch oauth token ... 403 Forbidden` on the pull.

**To recreate the cluster:** see [GKE-FROM-SCRATCH.md](GKE-FROM-SCRATCH.md) §2.

**Tested externally with Bruno** (2026-08-07):
- Env `bruno-collection/environments/Remote-GKE.bru` with the LB public IPs
  (`gatewayUrl: http://<gw-ip>:8072`, `keycloakUrl: http://<kc-ip>`). GCP
  reassigns the IPs on every cluster recreation — update the env with:
  ```powershell
  kubectl get svc gatewayserver keycloak
  ```
- Keycloak client `eazybank-callcenter-cc` is created the same way as locally
  with `.\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS`.
  The script does `kubectl exec` on the pod → respects the current kubecontext
  (works against GKE with no changes). Copy the secret it prints and paste it
  into the Bruno env.
- No JWT `iss` mismatch: the gateway only has `jwk-set-uri` (no `issuer-uri`),
  and that points to keycloak's internal service (`keycloak.default.svc...`).
  The token can carry any `iss` as long as it is signed by the same key — so
  logging in against keycloak's public IP works.
- End-to-end flow validated: `Auth/Get Token` (200) → `Accounts/Create` (201) →
  `Accounts/Fetch` (200 with branchAddress from the config server) →
  `Accounts/Delete` (200).
- Keycloak still on H2 in memory: if the pod restarts, clients/roles are lost
  and you have to re-run `create-keycloak-client.ps1`.

**Kill switch when done** (always, to avoid bleeding costs):
```powershell
gcloud container clusters delete cluster-1 --zone us-central1-a --quiet
```
Survives the delete: the Artifact Registry repo with the images, the repo IAM
binding, and the enabled APIs. The next recreation skips the 403 debug.

## Pending / possible next steps
- [ ] Delete on disk the leftover eureka `.tgz` files in qa/prod (the deploy
      removes them on its own, but for a clean state):
      `Remove-Item .\helm\environments\qa-env\charts\eurekaserver-0.1.0.tgz -EA SilentlyContinue`
      `Remove-Item .\helm\environments\prod-env\charts\eurekaserver-0.1.0.tgz -EA SilentlyContinue`
- [ ] (Optional) Truly isolate qa: apply redis+discovery in `qa` and revert to
      short names in `qa-env/values.yaml` (see "Isolation between environments").
- [ ] (Cosmetic) Silence the OpenTelemetry errors to `tempo` when observability
      is NOT deployed (or deploy it with `deploy-cluster.ps1 -WithObservability`).
- [ ] Redeploy the 3 env-releases and `kube-prometheus` so the live cluster
      reflects the Eureka cleanup (live ConfigMaps still contain the orphan
      key; live Prometheus still has the eureka scrape job as `up==0`). No rush.
- [ ] (Optional) Persist the H2 server with a PVC (right now restarting the pod
      loses the data). Change the URL to `jdbc:h2:tcp://h2:1521//data/XXXdb`
      and mount a PVC.
- [x] ~~Try `deploy-env.ps1 -Env qa-env -Release eazybank-qa -Namespace qa`.~~ Done.
- [x] ~~Deploy prod-env with its own infra in the prod namespace.~~ Done.
- [x] ~~Full Eureka cleanup in the charts.~~ Done (see "Problems solved" #6).
- [x] ~~Integrate `discovery-server.yaml` into `deploy-cluster.ps1`.~~ Done.
- [x] ~~Fix the H2 split-brain.~~ Done (see "Problems solved" #7).

## Key commands
```powershell
# Build s17 images
.\build-images-s17.ps1
# Deploy everything (infra + dev-env microservices in default)
.\deploy-cluster.ps1

# Deploy an additional environment in its namespace
.\deploy-env.ps1 -Env qa-env   -Release eazybank-qa   -Namespace qa
.\deploy-env.ps1 -Env prod-env -Release eazybank-prod -Namespace prod

# Own infra inside a namespace (needed if the env uses short names)
kubectl create namespace <ns>
kubectl apply -f redis.yaml            -n <ns>
kubectl apply -f discovery-server.yaml -n <ns>
kubectl apply -f h2-server.yaml        -n <ns>

# Status and health
kubectl get pods -A
curl.exe http://localhost:8072/actuator/health   # dev
curl.exe http://localhost:8073/actuator/health   # qa
curl.exe http://localhost:8074/actuator/health   # prod

# Teardown of specific environments
helm uninstall eazybank-qa   -n qa   ; kubectl delete namespace qa
helm uninstall eazybank-prod -n prod ; kubectl delete namespace prod
# Teardown everything
.\teardown-cluster.ps1
```
