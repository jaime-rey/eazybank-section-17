# GKE — recreate from scratch (personal recipe)

Doc for bringing the GKE cluster back up months from now without remembering
anything. Assumes you have the repo cloned at
`C:\Users\User\spring boot\section_17`.

Structure:
- **§0 Prereqs** — what you need installed on the machine.
- **§1 One-time setup** — things that persist across cluster deletions. If they
  already exist (check below), jump straight to §2.
- **§2 Every recreation** — cluster + deploy + test + kill switch.
- **§3 Quick troubleshooting** — the three failures that have already happened.

Context notes (persist): account `jreycasa@gmail.com`, project
`project-aaa96c1a-20d1-43bf-819` (project number `594159792471`), region
`us-central1`, zone `us-central1-a`.

---

## §0 Prereqs

On the Windows machine:

- **gcloud CLI** with the `gke-gcloud-auth-plugin` plugin. They ship together
  in the Google Cloud SDK. If `gcloud` is there but `kubectl` can't find the
  plugin, add it to the session PATH:
  ```powershell
  $env:PATH += ";C:\Users\User\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
  ```
- **kubectl** — install with `gcloud components install kubectl` or use the one
  that comes with Docker Desktop.
- **helm** v3+.
- **JDK 17+** and **Docker Desktop** (only if you are going to rebuild/repush
  images in §1).

Login:
```powershell
gcloud auth login
gcloud config set project project-aaa96c1a-20d1-43bf-819
gcloud config set compute/region us-central1
gcloud config set compute/zone us-central1-a
```

## §1 One-time setup (state 2026-08-07: ALL deleted, has to be redone)

These four items **can** survive cluster deletion. Last time (2026-08-07)
everything was deleted — including the AR repo — to get to $0 cost. Check in
order before recreating anything:

```powershell
# 1. Enabled APIs
gcloud services list --enabled --filter="name:(container.googleapis.com OR artifactregistry.googleapis.com OR compute.googleapis.com)" --format="value(config.name)"

# 2. Artifact Registry repo exists
gcloud artifacts repositories describe eazybank --location=us-central1

# 3. The 6 images are pushed
gcloud artifacts docker images list us-central1-docker.pkg.dev/project-aaa96c1a-20d1-43bf-819/eazybank --format="value(package)"

# 4. IAM binding on the default compute SA
gcloud artifacts repositories get-iam-policy eazybank --location=us-central1
```

If any of them is missing, apply only what is missing:

### 1a. Enable APIs

```powershell
gcloud services enable container.googleapis.com artifactregistry.googleapis.com compute.googleapis.com
```

### 1b. Create the Artifact Registry repo

```powershell
gcloud artifacts repositories create eazybank `
  --repository-format=docker --location=us-central1 `
  --description="EazyBank microservices Docker images"
```

### 1c. Build and push the 6 images

Configure Docker for AR once:
```powershell
gcloud auth configure-docker us-central1-docker.pkg.dev
```

Local build (produces `eazybytes/<svc>:s17` tags) and push retagging:
```powershell
cd "C:\Users\User\spring boot\section_17"
.\build-images-s17.ps1
$svcs = @("configserver","accounts","cards","loans","gatewayserver","message")
foreach ($s in $svcs) {
    $dst = "us-central1-docker.pkg.dev/project-aaa96c1a-20d1-43bf-819/eazybank/${s}:s17"
    docker tag "eazybytes/${s}:s17" $dst
    docker push $dst
}
```

### 1d. IAM binding so GKE nodes can pull

Without this → `ErrImagePull` with 403 on every pod. The node pool's default
scopes (`devstorage.read_only`) do work for AR — only the IAM is missing:

```powershell
gcloud artifacts repositories add-iam-policy-binding eazybank `
  --location=us-central1 `
  --member=serviceAccount:594159792471-compute@developer.gserviceaccount.com `
  --role=roles/artifactregistry.reader
```

## §2 Every cluster recreation

```powershell
cd "C:\Users\User\spring boot\section_17"

# 1. Create cluster (Standard zonal, 4 nodes — with 3 the stack does not fit, Keycloak stays Pending)
gcloud container clusters create cluster-1 `
  --zone us-central1-a --num-nodes 4 --machine-type e2-medium `
  --disk-size 32 --release-channel regular

# 2. Wire kubectl
gcloud container clusters get-credentials cluster-1 --zone us-central1-a
kubectl get nodes    # should show 4 Ready

# 3. Base infra (redis, h2, discovery — independent, any order)
kubectl apply -f redis.yaml
kubectl apply -f h2-server.yaml
kubectl apply -f discovery-server.yaml

# 4. Kafka and Keycloak (Helm)
helm upgrade --install kafka helm/kafka --wait --timeout 5m
helm upgrade --install keycloak helm/keycloak --wait --timeout 5m

# 5. The 6 microservices (GKE overlay points at Artifact Registry + replicas=1)
helm upgrade --install dev-env helm/environments/dev-env `
  -f helm/environments/dev-env/gke-values.yaml --timeout 8m

# 6. Gateway external IP (takes 1-2 min to be assigned)
kubectl get svc gatewayserver keycloak
```

### Validate end-to-end (optional, with Bruno)

Keycloak runs H2 in memory — every recreation loses clients and roles:

```powershell
.\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS
# Copy the secret it prints.
```

In Bruno (collection `bruno-collection/`):
1. Env `Remote-GKE` → update `gatewayUrl` and `keycloakUrl` with the current
   EXTERNAL-IPs (`kubectl get svc gatewayserver keycloak`).
2. Paste the `clientSecret` returned by the script.
3. `Auth/Get Token` → `Accounts/Create` → `Accounts/Fetch` → `Accounts/Delete`.

### Kill switch (ALWAYS when done — avoid cost bleeding)

Two levels depending on how long you'll be away:

**Level A — pause (coming back in days/weeks):** just delete the cluster and
orphan disks. Keeps the AR repo with the images (~$0.06/month cost) to skip §1c
on the next recreation.

```powershell
gcloud container clusters delete cluster-1 --zone us-central1-a --quiet

# The PVC disks (Kafka/Keycloak) are NOT deleted with the cluster — orphans:
gcloud compute disks list --format="value(name,zone)"
# Delete the ones that appear (change the name):
# gcloud compute disks delete <PVC-NAME> --zone=us-central1-a --quiet
```

**Level B — $0 cost (coming back in months, or maybe never):** also delete the AR repo.

```powershell
gcloud artifacts repositories delete eazybank --location=us-central1 --quiet
```

Survives Level B: enabled APIs (free), IAM binding (orphan — harmless). The
next recreation has to run all of §1 (create AR repo + build + push).

## §3 Quick troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Pods in `ErrImagePull`, `describe` shows `403 Forbidden ... fetch oauth token` | Missing IAM binding on the compute SA | Apply §1d |
| `kubectl` error `gke-gcloud-auth-plugin not found` | Plugin not on PATH | `$env:PATH += ";C:\Users\User\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"` or new PowerShell |
| Keycloak `Pending` with no schedule | Cluster with 3 nodes (~4.5 usable vCPU), doesn't fit | Recreate with `--num-nodes 4` |
| Gateway `EXTERNAL-IP: <pending>` for >5 min | GCP LB is slow | Wait. Check quota with `gcloud compute forwarding-rules list` |
| Bruno returns 401 on Accounts | Token expired (~1 min in Keycloak master) or Keycloak restarted (H2 in memory) | Re-`Auth/Get Token`; if it still fails, re-run `create-keycloak-client.ps1` and update the secret |
| OpenTelemetry traces failing to `tempo` | Observability not deployed | Ignore (harmless noise) or `.\deploy-cluster.ps1 -WithObservability` (local) |

## Approximate cost

- Running cluster: **~$0.10/hour** for compute (4× e2-medium spot ≈ less if you
  use spot; standard is more). Control plane is free (first zonal cluster free tier).
- GCP LB: free for the first 5 forwarding rules.
- Artifact Registry repo (persistent, ~0.63 GB): **~$0.07/month**.

Rule: if you're not using it right now, apply the kill switch.
