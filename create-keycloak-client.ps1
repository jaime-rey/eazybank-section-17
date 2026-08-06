<#
    create-keycloak-client.ps1

    Crea un cliente OIDC en Keycloak con flow "client-credentials" (M2M),
    crea los roles que aun no existan en el realm, se los asigna al service
    account del cliente, y devuelve el client_secret. Requiere Keycloak
    corriendo en el cluster (por defecto: deployment/keycloak en default).

    Uso:
      .\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc `
          -Roles ACCOUNTS,CARDS,LOANS,default-roles-master

      .\create-keycloak-client.ps1 -ClientId my-app -Roles ACCOUNTS

      .\create-keycloak-client.ps1 -ClientId internal-svc -Roles READ -Realm my-realm

    Comportamiento:
    - Si el cliente ya existe en el realm, aborta (no sobrescribe).
    - Los roles listados que no existan en el realm se crean.
    - Los flows standard/direct/implicit se dejan desactivados; solo client-credentials.
    - default-roles-master se asigna solo si lo listas explicitamente (Keycloak lo
      pone solo a usuarios humanos, no a service accounts).
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$ClientId,
    [string[]]$Roles = @(),
    [string]$Realm = "master",
    [string]$AdminUser = "user",
    [string]$AdminPassword = "password",
    [string]$Namespace = "default",
    [string]$KeycloakDeployment = "deployment/keycloak"
)

$ErrorActionPreference = "Stop"

function KC {
    $out = kubectl exec -n $Namespace $KeycloakDeployment -- /opt/keycloak/bin/kcadm.sh @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR kcadm: $out" -ForegroundColor Red
        throw "kcadm falló"
    }
    return $out
}

Write-Host "== Login en kcadm ==" -ForegroundColor Cyan
KC config credentials --server http://localhost:8080 --realm master `
    --user $AdminUser --password $AdminPassword | Out-Null

Write-Host "== Comprobando que el cliente '$ClientId' no existe ==" -ForegroundColor Cyan
$found = (KC get clients -r $Realm -q "clientId=$ClientId" --fields id) | ConvertFrom-Json
if ($found.Count -gt 0) {
    Write-Host "El cliente '$ClientId' ya existe en el realm '$Realm'. Abortando." -ForegroundColor Yellow
    exit 1
}

Write-Host "== Creando/verificando roles ==" -ForegroundColor Cyan
$existingRoleNames = ((KC get roles -r $Realm --fields name) | ConvertFrom-Json) | ForEach-Object { $_.name }
foreach ($r in $Roles) {
    if ($existingRoleNames -contains $r) {
        Write-Host "  - $r ya existe"
    } else {
        Write-Host "  - creando $r"
        KC create roles -r $Realm -s "name=$r" | Out-Null
    }
}

Write-Host "== Creando cliente '$ClientId' ==" -ForegroundColor Cyan
$createOutput = KC create clients -r $Realm `
    -s "clientId=$ClientId" `
    -s enabled=true `
    -s publicClient=false `
    -s serviceAccountsEnabled=true `
    -s standardFlowEnabled=false `
    -s directAccessGrantsEnabled=false `
    -s implicitFlowEnabled=false `
    -s protocol=openid-connect
$cid = ($createOutput | Select-String -Pattern "id '([^']+)'").Matches.Groups[1].Value
if (-not $cid) { throw "No se pudo extraer el UUID del cliente. Salida: $createOutput" }
Write-Host "  UUID interno: $cid"

Write-Host "== Recuperando client_secret ==" -ForegroundColor Cyan
$secret = ((KC get "clients/$cid/client-secret" -r $Realm) | ConvertFrom-Json).value

$saUsername = "service-account-$ClientId"
if ($Roles.Count -gt 0) {
    Write-Host "== Asignando roles al service account '$saUsername' ==" -ForegroundColor Cyan
    $roleArgs = @()
    foreach ($r in $Roles) { $roleArgs += @("--rolename", $r) }
    KC add-roles -r $Realm --uusername $saUsername @roleArgs | Out-Null
    Write-Host "  asignados: $($Roles -join ', ')"
}

Write-Host ""
Write-Host "=========================================================" -ForegroundColor Green
Write-Host " Cliente creado" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
Write-Host " Realm     : $Realm"
Write-Host " clientId  : $ClientId"
Write-Host " UUID      : $cid"
Write-Host " secret    : $secret"
Write-Host " SA user   : $saUsername"
Write-Host " Roles     : $($Roles -join ', ')"
Write-Host ""
Write-Host " Pedir token:" -ForegroundColor Cyan
Write-Host "   curl.exe -s -X POST 'http://localhost/realms/$Realm/protocol/openid-connect/token' \`"
Write-Host "     -H 'Content-Type: application/x-www-form-urlencoded' \`"
Write-Host "     -d 'grant_type=client_credentials&client_id=$ClientId&client_secret=$secret'"
