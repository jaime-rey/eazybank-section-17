<#
    teardown-cluster.ps1
    Elimina todos los releases de Helm desplegados por deploy-cluster.ps1.
    Uso:  .\teardown-cluster.ps1
#>
param([string]$Namespace = "default")

$releases = @("dev-env","grafana-alloy","grafana-tempo","grafana-loki",
              "grafana","kube-prometheus","keycloak","kafka")

foreach ($r in $releases) {
    Write-Host "Desinstalando $r ..." -ForegroundColor Yellow
    helm uninstall $r -n $Namespace 2>$null
}
Write-Host "Eliminando Redis ..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $PSScriptRoot "redis.yaml") -n $Namespace 2>$null

Write-Host "Hecho. Pods restantes:" -ForegroundColor Cyan
kubectl get pods -n $Namespace
