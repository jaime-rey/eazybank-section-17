<#
    deploy-env.ps1
    Deploys one of the microservice environments (dev-env, qa-env, prod-env).
    The infrastructure (kafka, keycloak, prometheus, grafana, redis) is
    deployed separately by deploy-cluster.ps1; this script only installs the
    microservice bundle for the chosen environment.

    - Repackages dependencies (in case eazybank-common changed).
    - Removes the leftover eurekaserver-0.1.0.tgz subchart if present.
    - Installs/upgrades the release with Helm.

    Usage (from the section_17 folder):
      .\deploy-env.ps1 -Env dev-env
      .\deploy-env.ps1 -Env qa-env   -Release eazybank-qa   -Namespace qa
      .\deploy-env.ps1 -Env prod-env -Release eazybank-prod -Namespace prod

    IMPORTANT: do NOT deploy two environments in the SAME namespace: the
    deployments share names (accounts-deployment, etc.) and will collide. Use
    a distinct namespace per environment, or uninstall the previous one first.
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

# Create the namespace if it does not exist
kubectl get namespace $Namespace *> $null
if ($LASTEXITCODE -ne 0) { kubectl create namespace $Namespace }

# 1) Refresh the packaged eazybank-common inside each service
$services = @("configserver", "accounts", "cards", "loans", "gatewayserver", "message")
foreach ($s in $services) {
    Write-Host "== dep update: $s ==" -ForegroundColor DarkCyan
    helm dependency update (Join-Path $helm "eazybank-services\$s")
}

# 2) Clean up the leftover eureka subchart and repackage the environment
$envPath = Join-Path $helm "environments\$Env"
Remove-Item (Join-Path $envPath "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "== dep update: $Env ==" -ForegroundColor DarkCyan
helm dependency update $envPath

# 3) Install the environment
Write-Host "== Deploying $Env as release '$Release' in namespace '$Namespace' ==" -ForegroundColor Green
helm upgrade --install $Release $envPath -n $Namespace --timeout 8m

kubectl get pods -n $Namespace
Write-Host "`nDone." -ForegroundColor Green
