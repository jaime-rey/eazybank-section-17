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

    NOTA: fail-fast. Cualquier comando helm/kubectl que devuelva exit code != 0
    aborta el script inmediatamente con mensaje claro (via Invoke-Native).
#>

param(
    [string]$Namespace = "default",
    [switch]$WithObservability
)

$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot
$helm  = Join-Path $root "helm"

# Ejecuta un comando nativo (helm/kubectl/docker) y aborta si sale con exit code != 0.
# PowerShell no lo hace por defecto ni con $ErrorActionPreference = 'Stop'.
function Invoke-Native {
    param(
        [Parameter(Mandatory)][scriptblock]$Command,
        [string]$FailMessage = "Comando nativo fallo"
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailMessage (exit $LASTEXITCODE)"
    }
}

function Assert-Ctx {
    $ctx = (kubectl config current-context).Trim()
    Write-Host "Contexto kubectl: $ctx" -ForegroundColor Cyan
    if ($ctx -ne "docker-desktop") {
        throw "Contexto '$ctx' != docker-desktop. Ejecuta: kubectl config use-context docker-desktop"
    }
    # cluster-info habla con el apiserver: si K8s de Docker Desktop no esta
    # arrancado (settings > Kubernetes), aqui reventamos con mensaje util
    # en vez de encadenar 10 fallos de helm/kubectl.
    kubectl cluster-info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "El cluster docker-desktop no responde. Abre Docker Desktop > Settings > Kubernetes y espera al indicador verde."
    }
}

Assert-Ctx

# --- 1. INFRAESTRUCTURA -----------------------------------------------------
# Los nombres de release importan: los servicios resuelven por DNS fijo.

Write-Host "`n== Kafka ==" -ForegroundColor Green
Invoke-Native { helm upgrade --install kafka (Join-Path $helm "kafka") -n $Namespace --wait --timeout 5m } "helm upgrade kafka fallo"

Write-Host "`n== Keycloak (2Gi) ==" -ForegroundColor Green
Invoke-Native { helm upgrade --install keycloak (Join-Path $helm "keycloak") -n $Namespace --wait --timeout 5m } "helm upgrade keycloak fallo"

Write-Host "`n== Prometheus (kube-prometheus) ==" -ForegroundColor Green
Invoke-Native { helm upgrade --install kube-prometheus (Join-Path $helm "kube-prometheus") -n $Namespace --wait --timeout 5m } "helm upgrade kube-prometheus fallo"

Write-Host "`n== Grafana ==" -ForegroundColor Green
Invoke-Native { helm upgrade --install grafana (Join-Path $helm "grafana") -n $Namespace --wait --timeout 5m } "helm upgrade grafana fallo"

Write-Host "`n== Redis (rate limiting del gateway) ==" -ForegroundColor Green
Invoke-Native { kubectl apply -f (Join-Path $root "redis.yaml") -n $Namespace } "kubectl apply redis.yaml fallo"
Invoke-Native { kubectl rollout status deployment/redis -n $Namespace --timeout=120s } "redis no alcanzo Ready"

Write-Host "`n== H2 server (BD compartida para accounts/cards/loans) ==" -ForegroundColor Green
Invoke-Native { kubectl apply -f (Join-Path $root "h2-server.yaml") -n $Namespace } "kubectl apply h2-server.yaml fallo"
Invoke-Native { kubectl rollout status deployment/h2 -n $Namespace --timeout=120s } "h2 no alcanzo Ready"

Write-Host "`n== Discovery Server (Spring Cloud K8s) ==" -ForegroundColor Green
Invoke-Native { kubectl apply -f (Join-Path $root "discovery-server.yaml") -n $Namespace } "kubectl apply discovery-server.yaml fallo"
Invoke-Native { kubectl rollout status deployment/spring-cloud-kubernetes-discoveryserver-deployment -n $Namespace --timeout=120s } "discovery-server no alcanzo Ready"

if ($WithObservability) {
    foreach ($c in @("grafana-loki","grafana-tempo","grafana-alloy")) {
        Write-Host "`n== $c ==" -ForegroundColor Green
        Invoke-Native { helm dependency build (Join-Path $helm $c) } "helm dependency build $c fallo"
        Invoke-Native { helm upgrade --install $c (Join-Path $helm $c) -n $Namespace --wait --timeout 5m } "helm upgrade $c fallo"
    }
}

# --- 2. REEMPAQUETAR CHARTS DE MICROSERVICIOS -------------------------------
# eazybank-common cambio (discovery URL + redis), asi que hay que regenerar
# el .tgz de cada servicio y luego el del umbrella dev-env.

$services = @("configserver","accounts","cards","loans","gatewayserver","message")
foreach ($s in $services) {
    Write-Host "`n== dep update: $s ==" -ForegroundColor DarkCyan
    Invoke-Native { helm dependency update (Join-Path $helm "eazybank-services\$s") } "helm dependency update $s fallo"
}

$devEnv = Join-Path $helm "environments\dev-env"
# Quitar el subchart de eureka sobrante si existiera (Helm carga todo .tgz de charts/)
Remove-Item (Join-Path $devEnv "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "`n== dep update: dev-env ==" -ForegroundColor DarkCyan
Invoke-Native { helm dependency update $devEnv } "helm dependency update dev-env fallo"

# --- 3. MICROSERVICIOS ------------------------------------------------------
Write-Host "`n== Microservicios EazyBank (tag s17) ==" -ForegroundColor Green
Write-Host "NOTA: algunos pods reiniciaran hasta que configserver este listo (normal)." -ForegroundColor Yellow
Invoke-Native { helm upgrade --install dev-env $devEnv -n $Namespace --timeout 8m } "helm upgrade dev-env fallo"

Write-Host "`n== Estado del cluster ==" -ForegroundColor Cyan
kubectl get pods -n $Namespace
Write-Host "`nListo. Accesos y verificacion en DEPLOY-README.md" -ForegroundColor Green
