# HANDOFF — EazyBank Section 17 (build s17 + Kubernetes en Docker Desktop)

Contexto para continuar en Claude Code (terminal integrada del IDE). Lee este archivo
y sigue desde "Pendientes". Estado a fecha de la última sesión.

## Objetivo
Construir las imágenes Docker de los 6 microservicios con tag `s17` y desplegar el
clúster completo en el Kubernetes de Docker Desktop (Kafka, Keycloak, Prometheus,
Grafana, Redis + microservicios). Curso eazybytes, proyecto en `C:\Users\User\spring boot\section_17`.

## Arquitectura relevante
- 6 microservicios (Spring Boot, build con Jib): configserver, accounts, cards, loans, gatewayserver, message.
  Cada `pom.xml` construye `eazybytes/<svc>:s17`.
- **No usa Eureka**: descubrimiento vía **Spring Cloud Kubernetes Discovery Server**
  (service `spring-cloud-kubernetes-discoveryserver:80`, desplegado aparte, NO en estos charts).
- Config externa: config server tira del repo git `https://github.com/eazybytes/eazybytes-config.git`.
- Gateway usa **Redis** para rate limiting.

## Estado actual del clúster
Tres entornos desplegados y sanos en paralelo:

| Namespace | Release         | Gateway            | Infra propia                          | Salud |
|-----------|-----------------|--------------------|---------------------------------------|-------|
| `default` | `dev-env`       | http://localhost:8072 | redis + discovery en `default`     | UP    |
| `qa`      | `eazybank-qa`   | http://localhost:8073 | comparte redis+discovery de `default` vía FQDN | UP |
| `prod`    | `eazybank-prod` | http://localhost:8074 | redis + discovery **propios** en `prod` | UP  |

Infra compartida en `default`: `kafka`, `keycloak`, `kube-prometheus`, `grafana`, `redis` (manifiesto),
`spring-cloud-kubernetes-discoveryserver` (manifiesto, ver más abajo).

Accesos (Docker Desktop, LoadBalancer en localhost):
- Gateways: dev 8072 · qa 8073 · prod 8074
- Grafana: http://localhost:3000 · Prometheus: http://localhost:9090
- Keycloak: http://localhost (admin `user` / `password`)

## Problemas resueltos (y cómo)
1. **Tag s14 vs s17**: los charts de servicio apuntaban a `s14`. Fijado `s17` en cada
   `helm/eazybank-services/*/values.yaml`.
2. **eurekaserver inexistente**: quitado de `dev-env/Chart.yaml` y borrado su `.tgz` de
   `charts/`. (Se usa el discovery server nativo de K8s.)
3. **Discovery URL faltante** (crash `DiscoveryServerUrlInvalidException`): añadida
   `global.discoveryServerURL` y su inyección como env `SPRING_CLOUD_KUBERNETES_DISCOVERY_DISCOVERYSERVERURL`
   en `helm/eazybank-common/templates/deployment.yaml`.
4. **Keycloak OOMKilled**: memoria subida a 2Gi en `helm/keycloak/values.yaml`.
5. **Redis DOWN en el gateway**: creado `redis.yaml` (Deployment+Service `redis:6379`);
   gateway con `redis_enabled: true` + `global.redisHost/redisPort`.
6. **Restos de Eureka completamente eliminados**: tras el fix #3 quedaban leftovers
   inertes (una key huérfana en el ConfigMap, un bloque `{{ if .Values.eureka_enabled }}`
   en el deployment.yaml, un flag `eureka_enabled` en los 6 values de servicios —con la
   inconsistencia latente de que accounts/cards/loans lo tenían en `true`—, un
   `eurekaServerURL` en los 3 envs, y un scrape job de eureka en Prometheus). Todo
   borrado; `grep -i eureka helm/` da 0 resultados. Sin cambio funcional (era todo
   código muerto), pero elimina despistes futuros.
7. **Split-brain H2 con `replicas: 2`** (bug de datos, no de código): cada pod de
   accounts/cards/loans traía su propio H2 en memoria (`jdbc:h2:mem:testdb`), y el
   Service de K8s hace round-robin → Create iba a pod A, Fetch a pod B → 404. En
   Bruno se manifestaba como flakiness pseudo-aleatoria (~50% de fallos) imposible
   de depurar sin ver el manifest de replicas. Solución: `h2-server.yaml`
   (imagen `oscarfonts/h2`, service `h2:1521`) que corre 1 H2 server aparte;
   cada servicio se conecta a **su propia BD lógica** en él vía
   `jdbc:h2:tcp://h2:1521/mem:{accounts,cards,loans}db`. El override va por env
   `SPRING_DATASOURCE_URL` inyectada desde `eazybank-common/templates/deployment.yaml`
   con un bloque `{{ if .Values.h2_enabled }}` (misma mecánica que Redis). Activado
   en `values.yaml` de accounts/cards/loans con `h2_enabled: true` + `h2_dbName: XXXdb`.
   Sin tocar código Java. Caveat: sigue siendo `mem:` (si el pod H2 reinicia se
   pierden datos) — suficiente para dev; para persistencia se pasa a `file:` con PVC.

Todo lo anterior está **consolidado en los charts** (sin parches en caliente). Un
`build-images-s17.ps1` + `deploy-cluster.ps1` levanta todo desde cero.

## Ficheros creados/editados
Creados: `build-images-s17.ps1`, `deploy-cluster.ps1`, `deploy-env.ps1`,
`teardown-cluster.ps1`, `redis.yaml`, `discovery-server.yaml`, `h2-server.yaml`,
`create-keycloak-client.ps1`, `bruno-collection/`, `DEPLOY-README.md`, este `HANDOFF.md`.
Editados (charts): `helm/eazybank-common/templates/{deployment.yaml,configmap.yaml}`,
`helm/environments/{dev,qa,prod}-env/values.yaml`, `helm/environments/dev-env/Chart.yaml`,
`helm/keycloak/values.yaml`, `helm/kube-prometheus/templates/configmap.yaml`,
`helm/eazybank-services/*/values.yaml` (tag s17, gateway con `redis_enabled: true`,
`eureka_enabled` eliminado en todos, accounts/cards/loans con `h2_enabled: true`).

`discovery-server.yaml`: manifiesto reutilizable con los 5 objetos del discovery server
(`ServiceAccount`, `Role namespace-reader`, `RoleBinding`, `Deployment`, `Service`).
Aplícalo con `kubectl apply -f discovery-server.yaml -n <ns>` en cualquier namespace donde
quieras un discovery propio. Usa `Role` (no `ClusterRole`), así solo lee pods/services/endpoints
de SU namespace — clave para el aislamiento (ver sección siguiente).

## Los 3 entornos (helm/environments)
dev-env (profile default), qa-env (qa), prod-env (prod). Difieren en `configMapName`,
`activeProfile`, el puerto del gateway y la estrategia de infra:

- **dev-env** (namespace `default`): gateway 8072. Redis y discovery en `default`
  (referenciados con nombre corto — resuelven local al namespace).
- **qa-env** (namespace `qa`): gateway 8073. `redisHost` y `discoveryServerURL` con
  **FQDN a `.default.svc.cluster.local`** → reutiliza la infra de `default`. `gatewayserver.service.port: 8073`
  overrideado desde el values del env. **NO aislado del todo** — ver sección "Aislamiento".
- **prod-env** (namespace `prod`): gateway 8074. `redisHost` y `discoveryServerURL` con
  **nombre corto** → resuelven a redis+discovery **propios** desplegados en `prod`.
  Aislamiento completo. `gatewayserver.service.port: 8074` overrideado desde el values del env.

Truco del puerto: solo se overridea `service.port` (externo). `service.targetPort` y
`containerPort` se quedan en 8072 → el contenedor sigue escuchando donde siempre, el
LoadBalancer traduce `localhost:807X` → pod:8072.

## Aislamiento entre entornos (importante)
El discovery server tiene `Role` (no `ClusterRole`) → solo ve pods/services de SU namespace.
Los servicios de los 3 entornos se llaman igual (`accounts`, `cards`, `loans`, `gatewayserver`,
`message`, `configserver`), así que **quién resuelve el discovery importa**:

- **qa** apunta al discovery de `default` (opción "FQDN"). Salud individual OK, pero cuando
  el gateway de qa rutee a `/accounts/...`, el discovery le devolverá los pods de
  `accounts` del namespace `default` (= dev-env). Cross-contaminación de tráfico.
  Válido para verificar despliegue; NO válido si quieres tests reales de qa.
- **prod** tiene discovery propio en `prod`. El gateway de prod solo ve pods de prod.
  Aislamiento total.

Si en algún momento quieres qa realmente aislado, migrar de "FQDN" a "infra propia":
`kubectl apply -f redis.yaml -n qa` + `kubectl apply -f discovery-server.yaml -n qa`,
y revertir las 2 líneas de `qa-env/values.yaml` a los nombres cortos.

## Pendientes / próximos pasos posibles
- [ ] Borrar en disco los `.tgz` de eureka sobrantes en qa/prod (el deploy los borra solo,
      pero para dejar limpio):
      `Remove-Item .\helm\environments\qa-env\charts\eurekaserver-0.1.0.tgz -EA SilentlyContinue`
      `Remove-Item .\helm\environments\prod-env\charts\eurekaserver-0.1.0.tgz -EA SilentlyContinue`
- [ ] (Opcional) Aislar qa de verdad: aplicar redis+discovery en `qa` y revertir a nombres
      cortos en `qa-env/values.yaml` (ver sección "Aislamiento entre entornos").
- [ ] (Cosmético) Silenciar los errores de OpenTelemetry hacia `tempo` cuando NO se usa
      observabilidad (o desplegarla con `deploy-cluster.ps1 -WithObservability`).
- [ ] Re-desplegar los 3 env-releases y `kube-prometheus` para que el clúster refleje el
      cleanup de Eureka (los ConfigMaps vivos aún contienen la key huérfana; el
      Prometheus vivo aún tiene el scrape job de eureka como `up==0`). Sin urgencia.
- [ ] (Opcional) Persistir el H2 server con PVC (ahora al reiniciar el pod se pierden
      los datos). Cambiar la URL a `jdbc:h2:tcp://h2:1521//data/XXXdb` y montar PVC.
- [x] ~~Probar `deploy-env.ps1 -Env qa-env -Release eazybank-qa -Namespace qa`.~~ Hecho.
- [x] ~~Desplegar prod-env con infra propia en el namespace prod.~~ Hecho.
- [x] ~~Limpieza total de Eureka en los charts.~~ Hecho (ver "Problemas resueltos" #6).
- [x] ~~Integrar `discovery-server.yaml` en `deploy-cluster.ps1`.~~ Hecho.
- [x] ~~Arreglar split-brain de H2.~~ Hecho (ver "Problemas resueltos" #7).

## Comandos clave
```powershell
# Build de imágenes s17
.\build-images-s17.ps1
# Desplegar todo (infra + microservicios dev-env en default)
.\deploy-cluster.ps1

# Desplegar un entorno adicional en su namespace
.\deploy-env.ps1 -Env qa-env   -Release eazybank-qa   -Namespace qa
.\deploy-env.ps1 -Env prod-env -Release eazybank-prod -Namespace prod

# Infra propia dentro de un namespace (necesario si el entorno usa nombres cortos)
kubectl create namespace <ns>
kubectl apply -f redis.yaml            -n <ns>
kubectl apply -f discovery-server.yaml -n <ns>
kubectl apply -f h2-server.yaml        -n <ns>

# Estado y salud
kubectl get pods -A
curl.exe http://localhost:8072/actuator/health   # dev
curl.exe http://localhost:8073/actuator/health   # qa
curl.exe http://localhost:8074/actuator/health   # prod

# Desmontar entornos concretos
helm uninstall eazybank-qa   -n qa   ; kubectl delete namespace qa
helm uninstall eazybank-prod -n prod ; kubectl delete namespace prod
# Desmontar todo
.\teardown-cluster.ps1
```
