<#
    deploy-cluster.ps1
    Deploys the full infrastructure (Kafka, Keycloak, Prometheus, Grafana, Redis
    + optional observability) and the EazyBank microservices on Docker Desktop's
    Kubernetes using the repo's Helm charts, tagged :s17.

    Everything is configured in the charts (no manual patches):
      - s17 tag pinned in each service values.yaml
      - eurekaserver removed (the native K8s discovery server is used)
      - Discovery server URL injected via global.discoveryServerURL
      - Redis deployed and wired to the gateway (rate limiting)
      - Keycloak with 2Gi memory (avoids OOMKilled)
      - Shared H2 server (avoids split-brain with replicas > 1)

    Prerequisites:
      - Docker Desktop with Kubernetes enabled
      - kubectl and helm installed
      - s17 images already built (run .\build-images-s17.ps1 first)

    Usage (from the section_17 folder):
      .\deploy-cluster.ps1
      .\deploy-cluster.ps1 -WithObservability   # also loki, tempo, alloy
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
    Write-Host "kubectl context: $ctx" -ForegroundColor Cyan
    if ($ctx -ne "docker-desktop") {
        throw "Context '$ctx' != docker-desktop. Run: kubectl config use-context docker-desktop"
    }
}

Assert-Ctx

# --- 1. INFRASTRUCTURE ------------------------------------------------------
# Release names matter: services resolve by fixed DNS.

Write-Host "`n== Kafka ==" -ForegroundColor Green
helm upgrade --install kafka (Join-Path $helm "kafka") -n $Namespace --wait --timeout 5m

Write-Host "`n== Keycloak (2Gi) ==" -ForegroundColor Green
helm upgrade --install keycloak (Join-Path $helm "keycloak") -n $Namespace --wait --timeout 5m

Write-Host "`n== Prometheus (kube-prometheus) ==" -ForegroundColor Green
helm upgrade --install kube-prometheus (Join-Path $helm "kube-prometheus") -n $Namespace --wait --timeout 5m

Write-Host "`n== Grafana ==" -ForegroundColor Green
helm upgrade --install grafana (Join-Path $helm "grafana") -n $Namespace --wait --timeout 5m

Write-Host "`n== Redis (gateway rate limiting) ==" -ForegroundColor Green
kubectl apply -f (Join-Path $root "redis.yaml") -n $Namespace
kubectl rollout status deployment/redis -n $Namespace --timeout=120s

Write-Host "`n== H2 server (shared DB for accounts/cards/loans) ==" -ForegroundColor Green
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

# --- 2. REPACKAGE MICROSERVICE CHARTS ---------------------------------------
# eazybank-common changed (discovery URL + redis), so each service .tgz has to
# be regenerated, and then the dev-env umbrella one.

$services = @("configserver","accounts","cards","loans","gatewayserver","message")
foreach ($s in $services) {
    Write-Host "`n== dep update: $s ==" -ForegroundColor DarkCyan
    helm dependency update (Join-Path $helm "eazybank-services\$s")
}

$devEnv = Join-Path $helm "environments\dev-env"
# Remove the leftover eureka subchart if present (Helm loads every .tgz under charts/)
Remove-Item (Join-Path $devEnv "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "`n== dep update: dev-env ==" -ForegroundColor DarkCyan
helm dependency update $devEnv

# --- 3. MICROSERVICES -------------------------------------------------------
Write-Host "`n== EazyBank microservices (s17 tag) ==" -ForegroundColor Green
Write-Host "NOTE: some pods will restart until configserver is ready (normal)." -ForegroundColor Yellow
helm upgrade --install dev-env $devEnv -n $Namespace --timeout 8m

Write-Host "`n== Cluster status ==" -ForegroundColor Cyan
kubectl get pods -n $Namespace
Write-Host "`nDone. Access and verification in DEPLOY-README.md" -ForegroundColor Green
