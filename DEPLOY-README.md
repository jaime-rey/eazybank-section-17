# EazyBank – Section 17: build de imágenes (s17) y despliegue en Kubernetes

Guía para construir las imágenes Docker de los microservicios con la etiqueta **`s17`** y
levantar el clúster en el **Kubernetes de Docker Desktop** con Kafka, Keycloak, Prometheus,
Grafana, Redis y (opcional) Loki/Tempo/Alloy, usando los Helm charts del repo.

Los charts ya vienen ajustados para que un despliegue desde cero funcione **sin parches
manuales**:

- Tag `s17` fijado en cada `helm/eazybank-services/*/values.yaml`.
- `eurekaserver` eliminado del `dev-env` (este proyecto usa el **Spring Cloud Kubernetes
  Discovery Server** nativo, no Eureka).
- URL del discovery server inyectada vía `global.discoveryServerURL` en el template común.
- **Redis** desplegado y conectado al gateway (rate limiting) vía `redis_enabled` + `global.redisHost`.
- **Keycloak** con `2Gi` de memoria (con 1Gi moría por `OOMKilled`).

## Requisitos previos

- Docker Desktop con **Kubernetes habilitado** (`Settings → Kubernetes → Enable`).
  Se recomienda asignar **8 GB+ de RAM** (`Settings → Resources`).
- `kubectl` apuntando a docker-desktop: `kubectl config use-context docker-desktop`.
- `helm` v3+ instalado.
- JDK 17+ (para el build con Jib).

## 1. Construir las imágenes `:s17`

```powershell
cd "C:\Users\User\spring boot\section_17"
.\build-images-s17.ps1
```

Construye: **configserver, accounts, cards, loans, gatewayserver, message**. Verifica:

```powershell
docker images "eazybytes/*" | Select-String s17
```

## 2. Desplegar el clúster

```powershell
.\deploy-cluster.ps1                    # infra + Redis + microservicios
.\deploy-cluster.ps1 -WithObservability # + Loki, Tempo, Alloy
```

El script hace, en orden:

1. `kafka`, `keycloak`, `kube-prometheus`, `grafana` (Helm).
2. `redis` (manifiesto `redis.yaml`).
3. *(opcional)* `grafana-loki`, `grafana-tempo`, `grafana-alloy`.
4. `helm dependency update` de cada servicio y del umbrella `dev-env` (necesario porque
   `eazybank-common` cambió).
5. `dev-env` → todos los microservicios con tag `s17`.

Es normal que algunos pods reinicien un par de veces hasta que `configserver` esté listo.

## 3. Verificación

```powershell
kubectl get pods
curl.exe http://localhost:8072/actuator/health   # debe responder {"status":"UP"}
```

Todos los pods deben quedar `1/1 Running`.

## 4. Acceso a las herramientas

Todo se publica en `localhost` (Docker Desktop, tipo LoadBalancer):

| Componente | URL | Notas |
|---|---|---|
| Gateway (entrada API) | http://localhost:8072 | enruta a accounts/cards/loans |
| Grafana | http://localhost:3000 | credenciales por defecto del chart |
| Prometheus | http://localhost:9090 | |
| Keycloak | http://localhost | admin: `user` / `password` |

Comprueba puertos reales con `kubectl get svc`.

## 5. Desmontar

```powershell
.\teardown-cluster.ps1
```

## Notas

- **Discovery server:** el pod `spring-cloud-kubernetes-discoveryserver` debe estar
  corriendo en el clúster (se despliega aparte, no forma parte de estos charts). Si no
  existe, despliégalo antes; los microservicios lo necesitan para descubrirse.
- **Errores de `tempo` en los logs:** si NO usas `-WithObservability`, verás trazas de
  OpenTelemetry fallando al exportar a `tempo` (`UnknownHostException`). Es **ruido
  inofensivo**: no afecta la salud ni el funcionamiento. Desaparece al desplegar la
  observabilidad, o puedes ignorarlo.
