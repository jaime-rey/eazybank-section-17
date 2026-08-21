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

    NOTA: fail-fast. Cualquier comando helm/kubectl que devuelva exit code != 0
    aborta el script inmediatamente con mensaje claro (via Invoke-Native).
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

# Ejecuta un comando nativo (helm/kubectl) y aborta si sale con exit code != 0.
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

# Verificar que el apiserver responde antes de nada.
kubectl cluster-info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "El cluster kubectl no responde. Verifica el contexto (kubectl config current-context) y que el cluster este arrancado."
}

# Crear el namespace si no existe
kubectl get namespace $Namespace *> $null
if ($LASTEXITCODE -ne 0) {
    Invoke-Native { kubectl create namespace $Namespace } "kubectl create namespace $Namespace fallo"
}

# 1) Refrescar el eazybank-common empaquetado dentro de cada servicio
$services = @("configserver", "accounts", "cards", "loans", "gatewayserver", "message")
foreach ($s in $services) {
    Write-Host "== dep update: $s ==" -ForegroundColor DarkCyan
    Invoke-Native { helm dependency update (Join-Path $helm "eazybank-services\$s") } "helm dependency update $s fallo"
}

# 2) Limpiar el subchart de eureka sobrante y reempaquetar el entorno
$envPath = Join-Path $helm "environments\$Env"
Remove-Item (Join-Path $envPath "charts\eurekaserver-0.1.0.tgz") -ErrorAction SilentlyContinue
Write-Host "== dep update: $Env ==" -ForegroundColor DarkCyan
Invoke-Native { helm dependency update $envPath } "helm dependency update $Env fallo"

# 3) Instalar el entorno
Write-Host "== Desplegando $Env como release '$Release' en namespace '$Namespace' ==" -ForegroundColor Green
Invoke-Native { helm upgrade --install $Release $envPath -n $Namespace --timeout 8m } "helm upgrade $Release fallo"

kubectl get pods -n $Namespace
Write-Host "`nListo." -ForegroundColor Green
