# GKE — recreación desde cero (recipe personal)

Doc para volver a montar el cluster en GKE dentro de meses sin recordar nada.
Asume que tienes el repo clonado en `C:\Users\User\spring boot\section_17`.

Estructura:
- **§0 Prereqs** — lo que necesitas instalado en la máquina.
- **§1 Setup one-time** — cosas que persisten a través de borrados de cluster.
  Si ya existen (comprobación abajo), saltar directo a §2.
- **§2 Cada recreación** — cluster + deploy + test + kill switch.
- **§3 Troubleshooting rápido** — los tres fallos que ya han pasado.

Notas de contexto (persisten): cuenta `jreycasa@gmail.com`, proyecto
`project-aaa96c1a-20d1-43bf-819` (project number `594159792471`), region
`us-central1`, zona `us-central1-a`.

---

## §0 Prereqs

En la máquina Windows:

- **gcloud CLI** con el plugin `gke-gcloud-auth-plugin`. Vienen juntos en el
  Google Cloud SDK. Si `gcloud` está pero `kubectl` no encuentra el plugin,
  añade al PATH de la sesión:
  ```powershell
  $env:PATH += ";C:\Users\User\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
  ```
- **kubectl** — se instala con `gcloud components install kubectl` o viene con Docker Desktop.
- **helm** v3+.
- **JDK 17+** y **Docker Desktop** (solo si vas a rebuild/repush imágenes en §1).

Login:
```powershell
gcloud auth login
gcloud config set project project-aaa96c1a-20d1-43bf-819
gcloud config set compute/region us-central1
gcloud config set compute/zone us-central1-a
```

## §1 Setup one-time (comprueba primero — probablemente ya está)

Estas cuatro cosas **sobreviven** al borrado del cluster. Comprueba en orden
antes de recrear nada:

```powershell
# 1. APIs habilitadas
gcloud services list --enabled --filter="name:(container.googleapis.com OR artifactregistry.googleapis.com OR compute.googleapis.com)" --format="value(config.name)"

# 2. Repo Artifact Registry existe
gcloud artifacts repositories describe eazybank --location=us-central1

# 3. Las 6 imagenes estan pusheadas
gcloud artifacts docker images list us-central1-docker.pkg.dev/project-aaa96c1a-20d1-43bf-819/eazybank --format="value(package)"

# 4. IAM binding en la default compute SA
gcloud artifacts repositories get-iam-policy eazybank --location=us-central1
```

Si alguno falta, aplica solo lo que falte:

### 1a. Habilitar APIs

```powershell
gcloud services enable container.googleapis.com artifactregistry.googleapis.com compute.googleapis.com
```

### 1b. Crear repo Artifact Registry

```powershell
gcloud artifacts repositories create eazybank `
  --repository-format=docker --location=us-central1 `
  --description="EazyBank microservices Docker images"
```

### 1c. Build y push de las 6 imágenes

Configura Docker para AR una vez:
```powershell
gcloud auth configure-docker us-central1-docker.pkg.dev
```

Build local (produce tags `eazybytes/<svc>:s17`) y push retagueando:
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

### 1d. IAM binding para que los nodos GKE puedan pull

Sin esto → `ErrImagePull` con 403 en cada pod. Los scopes por defecto del node
pool (`devstorage.read_only`) sí sirven para AR — solo falta el IAM:

```powershell
gcloud artifacts repositories add-iam-policy-binding eazybank `
  --location=us-central1 `
  --member=serviceAccount:594159792471-compute@developer.gserviceaccount.com `
  --role=roles/artifactregistry.reader
```

## §2 Cada recreación del cluster

```powershell
cd "C:\Users\User\spring boot\section_17"

# 1. Crear cluster (Standard zonal, 4 nodos — con 3 no cabe el stack, Keycloak queda Pending)
gcloud container clusters create cluster-1 `
  --zone us-central1-a --num-nodes 4 --machine-type e2-medium `
  --disk-size 32 --release-channel regular

# 2. Conectar kubectl
gcloud container clusters get-credentials cluster-1 --zone us-central1-a
kubectl get nodes    # deben salir 4 Ready

# 3. Infra base (redis, h2, discovery — independientes, en cualquier orden)
kubectl apply -f redis.yaml
kubectl apply -f h2-server.yaml
kubectl apply -f discovery-server.yaml

# 4. Kafka y Keycloak (Helm)
helm upgrade --install kafka helm/kafka --wait --timeout 5m
helm upgrade --install keycloak helm/keycloak --wait --timeout 5m

# 5. Los 6 microservicios (overlay GKE apunta al Artifact Registry + replicas=1)
helm upgrade --install dev-env helm/environments/dev-env `
  -f helm/environments/dev-env/gke-values.yaml --timeout 8m

# 6. IP externa del gateway (tarda 1-2 min en asignarse)
kubectl get svc gatewayserver keycloak
```

### Validar end-to-end (opcional, con Bruno)

Keycloak corre H2 en memoria — cada recreación pierde clientes y roles:

```powershell
.\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS
# Copiar el secret que imprime.
```

En Bruno (colección `bruno-collection/`):
1. Env `Remote-GKE` → actualizar `gatewayUrl` y `keycloakUrl` con las EXTERNAL-IP
   actuales (`kubectl get svc gatewayserver keycloak`).
2. Pegar el `clientSecret` que devolvió el script.
3. `Auth/Get Token` → `Accounts/Create` → `Accounts/Fetch` → `Accounts/Delete`.

### Kill switch (SIEMPRE al terminar — evita sangría de coste)

```powershell
gcloud container clusters delete cluster-1 --zone us-central1-a --quiet
```

Persiste tras el delete: repo AR, imágenes, IAM binding, APIs. La próxima
recreación se salta §1.

## §3 Troubleshooting rápido

| Síntoma | Causa | Fix |
|---|---|---|
| Pods en `ErrImagePull`, `describe` muestra `403 Forbidden ... fetch oauth token` | Falta IAM binding en la compute SA | Aplicar §1d |
| `kubectl` error `gke-gcloud-auth-plugin not found` | Plugin no en PATH | `$env:PATH += ";C:\Users\User\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"` o nueva PowerShell |
| Keycloak `Pending` sin schedule | Cluster con 3 nodos (~4.5 vCPU útiles), no cabe | Recrear con `--num-nodes 4` |
| Gateway `EXTERNAL-IP: <pending>` >5 min | LB de GCP tardando | Esperar. Verifica cuota `gcloud compute forwarding-rules list` |
| Bruno da 401 en Accounts | Token expiró (dura ~1 min en Keycloak master) o Keycloak reinició (H2 en memoria) | Re-`Auth/Get Token`; si sigue, re-ejecutar `create-keycloak-client.ps1` y actualizar el secret |
| Trazas de OpenTelemetry fallando a `tempo` | No hay observabilidad desplegada | Ignorar (ruido inofensivo) o `.\deploy-cluster.ps1 -WithObservability` (local) |

## Coste aproximado

- Cluster corriendo: **~$0.10/hora** por el compute (4× e2-medium spot ≈ menos si usas spot; standard es más). Control plane gratis (free tier del primer cluster zonal).
- LB de GCP: gratis las primeras 5 forwarding rules.
- Repo Artifact Registry (persistente, ~0.63 GB): **~$0.07/mes**.

Regla: si no lo estás usando ahora mismo, aplica el kill switch.
