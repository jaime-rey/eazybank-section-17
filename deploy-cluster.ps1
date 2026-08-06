<#
    deploy-cluster.ps1
    Despliega en el Kubernetes de Docker Desktop toda la infraestructura
    (Kafka, Keycloak, Prometheus, Grafana, Redis + observabilidad opcional) y
    los microservicios de EazyBank usando los Helm charts del repo, con tag :s17.

    Todo queda configurado en los charts (sin parches manuales):
      - Tag s17 fijado en cada values.yaml de servicio
      - eurekaserver eliminado (se usa el discovery server nativo de K8s)
      - URL del discovery server inyectada via global.discoveryServerURL
      - Redis desplegado y conectado al gateway (rate limiting)
      - Keycloak con 2Gi de memoria (evita OOMKilled)
      - H2 server compartido (evita split-brain con replicas > 1)

    Requisitos:
      - Docker Desktop con Kubernetes habilitado
      - kubectl y helm instalados
      - Imagenes s17 ya construidas (ejecuta primero .\build-images-s17.ps1)

    Uso (desde la carpeta section_17):
      .\deploy-cluster.ps1
      .\deploy-cluster.ps1 -WithObservability   # ademas loki, tempo, alloy
#>

param(
    [string]$Namespace = "default",
    [switch]$WithObservability
)

$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot
$helm  = Join-Path $root "helm"

function Assert-Ctx {
    $ctx = (kubectl config current-context).Trim()
    Write-Host "Contexto kubectl: $ctx" -ForegroundColor Cyan
    if ($ctx -ne "docker-desktop") {
        throw "Contexto '$ctx' != docker-desktop. Ejecuta: kubectl config use-context docker-desktop"
    }
}

Assert-Ctx

# --- 1. INFRAESTRUCTURA -----------------------------------------------------
# Los nombres de release importan: los servicios resuelven por DNS fijo.

Write-Host "`n== Kafka ==" -ForegroundColor Green
helm upgrade --install kafka (Join-Path $helm "kafka") -n $Namespace --wait --timeout 5m

Write-Host "`n== Keycloak (2Gi) ==" -ForegroundColor Green
helm upgrade --install keycloak (Join-Path $helm "keycloak") -n $Namespace --wait --timeout 5m

Write-Host "`n== Prometheus (kube-prometheus) ==" -ForegroundColor Green
helm upgrade --install kube-prometheus (Join-Path $helm "kube-prometheus") -n $Namespace --wait --timeout 5m

Write-Host "`n== Grafana ==" -ForegroundColor Green
helm upgrade --install grafana (Join-Path $helm "grafana") -n $Namespace --wait --timeout 5m

Write-Host "`n== Redis (rate limiting del gateway) ==" -ForegroundColor Green
kubectl apply -f (Join-Path $root "redis.yaml") -n $Namespace
kubectl rollout status deployment/redis -n $Namespace --timeout=120s

Write-Host "`n== H2 server (BD compartida para accounts/cards/loans) ==" -ForegroundColor Green
kubectl apply -f (Join-Path $root "h2-server.yaml") -n $Namespace
kubectl rollout status deployment/h2 -n $Namespace --timeout=120s

Write-Host "`n== Discovery Server (Spring Cloud K8s) ==" -ForegroundColor Green
kubectl apply -f (Join-Path $root "discovery-server.yaml") -n $Namespace
kubectl rollout status deployment/spring-cloud-kubernetes-discoveryserver-deployment -n $Namespace --timeout=120s

if ($WithObservability) {
    foreach ($c in @("grafana-loki","grafana-tempo","grafana-alloy")) {
        Write-Host "`n== $c ==" -ForegroundColor Green
        helm dependency build (Join-Path $helm $c)
        helm upgrade --install $c (Join-Path $helm $c) -n $Namespace --wait --timeout 5m
    }
}

# --- 2. REEMPAQUETAR CHARTS DE MICROSERVICIOS -------------------------------
# eazybank-common cambio (discovery URL + redis), asi que hay que regenerar
# el .tgz de cada servicio y luego el del umbrella dev-env.

$services = @("configserver","accounts","cards","loans","gatewayserver","message")
foreach ($s in $services) {
    Write-Host "`n== dep update: $s ==" -ForegroundColor DarkCyan
    helm dependency update (Join-Path $helm "eazybank-services\$s")
}

$devEnv = Join-Path $helm "environments\dev-env"
# Quitar el subchart de eureka sobrante si existiera (Helm carga todo .tgz de charts/)
Remove-Item (Join-Path $devEnv "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "`n== dep update: dev-env ==" -ForegroundColor DarkCyan
helm dependency update $devEnv

# --- 3. MICROSERVICIOS ------------------------------------------------------
Write-Host "`n== Microservicios EazyBank (tag s17) ==" -ForegroundColor Green
Write-Host "NOTA: algunos pods reiniciaran hasta que configserver este listo (normal)." -ForegroundColor Yellow
helm upgrade --install dev-env $devEnv -n $Namespace --timeout 8m

Write-Host "`n== Estado del cluster ==" -ForegroundColor Cyan
kubectl get pods -n $Namespace
Write-Host "`nListo. Accesos y verificacion en DEPLOY-README.md" -ForegroundColor Green
