<#
    deploy-env.ps1
    Despliega uno de los entornos de microservicios (dev-env, qa-env, prod-env).
    La infraestructura (kafka, keycloak, prometheus, grafana, redis) se despliega
    aparte con deploy-cluster.ps1; este script solo instala el conjunto de
    microservicios del entorno elegido.

    - Reempaqueta las dependencias (por si eazybank-common cambio).
    - Elimina el subchart sobrante eurekaserver-0.1.0.tgz si existiera.
    - Instala/actualiza el release con Helm.

    Uso (desde la carpeta section_17):
      .\deploy-env.ps1 -Env dev-env
      .\deploy-env.ps1 -Env qa-env   -Release eazybank-qa   -Namespace qa
      .\deploy-env.ps1 -Env prod-env -Release eazybank-prod -Namespace prod

    IMPORTANTE: no despliegues dos entornos en el MISMO namespace: los deployments
    se llaman igual (accounts-deployment, etc.) y colisionarian. Usa un namespace
    distinto por entorno, o desinstala el anterior primero.
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("dev-env", "qa-env", "prod-env")]
    [string]$Env,
    [string]$Release,
    [string]$Namespace = "default"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$helm = Join-Path $root "helm"
if (-not $Release) { $Release = $Env }

# Crear el namespace si no existe
kubectl get namespace $Namespace *> $null
if ($LASTEXITCODE -ne 0) { kubectl create namespace $Namespace }

# 1) Refrescar el eazybank-common empaquetado dentro de cada servicio
$services = @("configserver", "accounts", "cards", "loans", "gatewayserver", "message")
foreach ($s in $services) {
    Write-Host "== dep update: $s ==" -ForegroundColor DarkCyan
    helm dependency update (Join-Path $helm "eazybank-services\$s")
}

# 2) Limpiar el subchart de eureka sobrante y reempaquetar el entorno
$envPath = Join-Path $helm "environments\$Env"
Remove-Item (Join-Path $envPath "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "== dep update: $Env ==" -ForegroundColor DarkCyan
helm dependency update $envPath

# 3) Instalar el entorno
Write-Host "== Desplegando $Env como release '$Release' en namespace '$Namespace' ==" -ForegroundColor Green
helm upgrade --install $Release $envPath -n $Namespace --timeout 8m

kubectl get pods -n $Namespace
Write-Host "`nListo." -ForegroundColor Green
