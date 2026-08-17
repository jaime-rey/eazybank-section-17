<#
    teardown-cluster.ps1
    Removes all Helm releases deployed by deploy-cluster.ps1.
    Usage:  .\teardown-cluster.ps1
#>
param([string]$Namespace = "default")

$releases = @("dev-env","grafana-alloy","grafana-tempo","grafana-loki",
              "grafana","kube-prometheus","keycloak","kafka")

foreach ($r in $releases) {
    Write-Host "Uninstalling $r ..." -ForegroundColor Yellow
    helm uninstall $r -n $Namespace 2>$null
}
Write-Host "Removing Redis ..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $PSScriptRoot "redis.yaml") -n $Namespace 2>$null

Write-Host "Done. Remaining pods:" -ForegroundColor Cyan
kubectl get pods -n $Namespace
