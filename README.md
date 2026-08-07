# EazyBank — Sección 17 (Microservicios en Kubernetes)

Proyecto del curso **eazybytes** (Spring Boot Microservices, Section 17). Toma la
base del curso y la extiende con un despliegue **completo, reproducible y aislado
por entornos** en Kubernetes, tanto en **Docker Desktop** como en **GKE**.

## Qué hay dentro

Seis microservicios Spring Boot descubriéndose vía **Spring Cloud Kubernetes
Discovery Server** (no Eureka), autenticación **OAuth2** con Keycloak, mensajería
con **Kafka**, config externa desde un config server, rate limiting con Redis, y
observabilidad opcional con Prometheus/Grafana/Loki/Tempo.

```
                              ┌─────────────┐
                       ┌──────│  Keycloak   │  (OAuth2 / JWT — client credentials)
                       │      └─────────────┘
                       │
  cliente HTTP  ──►  Gateway  ─────►  accounts  ┐
                    (Spring         ─►  cards    │  ─►  H2 server (shared, JDBC TCP)
                     Cloud          ─►  loans    ┘        └─ una BD lógica por servicio
                     Gateway,       ─►  message   ─►  Kafka
                     Redis rate                       (eventos comm)
                     limiting)
                       │
                       └─►  configserver  ─►  git (config externa)

  Descubrimiento:  Spring Cloud Kubernetes Discovery Server (Role a nivel namespace)
```

## Stack

- **Java 17 + Spring Boot 3** (Cloud Gateway, Config, Kubernetes Discovery, Security OAuth2 RS)
- **Docker + Jib** para las imágenes (`eazybytes/<svc>:s17`)
- **Kubernetes** — Docker Desktop y GKE
- **Helm** — chart común `eazybank-common` + 3 umbrella charts (`dev-env`, `qa-env`, `prod-env`)
- **Kafka** (mensajería), **Keycloak** (auth), **Redis** (rate limiting)
- **Bruno** — colección de tests end-to-end contra el gateway

## Highlights del despliegue

- **3 entornos** paralelos (`default`, `qa`, `prod`) con puertos e infra distintos,
  aislados vía RBAC de namespace en el discovery server (Role, no ClusterRole).
- **Split-brain de H2 resuelto**: manifiesto `h2-server.yaml` corre 1 H2 aparte;
  cada servicio se conecta a su propia BD lógica en él. Evita que con `replicas > 1`
  cada pod tenga su propio H2 en memoria.
- **Deploy en GKE** con overlay `gke-values.yaml` apuntando a Artifact Registry
  regional. Un `gcloud artifacts repositories add-iam-policy-binding` (una sola
  vez) resuelve el 403 típico de pull sin recrear el node pool.
- Todo consolidado en los charts — sin parches manuales tras `helm install`.

## Arrancar

**Local (Docker Desktop con Kubernetes habilitado):**

```powershell
.\build-images-s17.ps1   # 6 imágenes con Jib
.\deploy-cluster.ps1     # infra + microservicios
curl.exe http://localhost:8072/actuator/health
```

Detalle completo → [`DEPLOY-README.md`](DEPLOY-README.md).

**GKE (público):** ver la sección "Deploy en GKE" en
[`HANDOFF.md`](HANDOFF.md) — cluster + IAM + deploy + Bruno.

**Probar los endpoints:** colección en [`bruno-collection/`](bruno-collection/README.md)
con envs `Local` y `Remote-GKE`.

## Documentación

- [`DEPLOY-README.md`](DEPLOY-README.md) — guía de despliegue local paso a paso.
- [`HANDOFF.md`](HANDOFF.md) — arquitectura detallada, problemas encontrados y
  cómo se resolvieron (Eureka → Discovery Server, split-brain H2, ImagePull 403
  en GKE, etc.), tres entornos, y receta reproducible para GKE.
- [`bruno-collection/README.md`](bruno-collection/README.md) — cómo probar los
  endpoints con Bruno (una request a una, o runner data-driven con 5 clientes).
