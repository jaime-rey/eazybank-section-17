<#
    create-keycloak-client.ps1

    Creates an OIDC client in Keycloak with the "client-credentials" flow (M2M),
    creates any listed roles that don't yet exist in the realm, assigns them to
    the client's service account, and returns the client_secret. Requires
    Keycloak running in the cluster (default: deployment/keycloak in default).

    Usage:
      .\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc `
          -Roles ACCOUNTS,CARDS,LOANS,default-roles-master

      .\create-keycloak-client.ps1 -ClientId my-app -Roles ACCOUNTS

      .\create-keycloak-client.ps1 -ClientId internal-svc -Roles READ -Realm my-realm

    Behavior:
    - If the client already exists in the realm, aborts (does not overwrite).
    - Listed roles that don't yet exist in the realm are created.
    - standard/direct/implicit flows are left disabled; only client-credentials.
    - default-roles-master is assigned only if you list it explicitly (Keycloak
      applies it to human users by default, not to service accounts).
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
        throw "kcadm failed"
    }
    return $out
}

Write-Host "== kcadm login ==" -ForegroundColor Cyan
KC config credentials --server http://localhost:8080 --realm master `
    --user $AdminUser --password $AdminPassword | Out-Null

Write-Host "== Checking client '$ClientId' does not exist ==" -ForegroundColor Cyan
$found = (KC get clients -r $Realm -q "clientId=$ClientId" --fields id) | ConvertFrom-Json
if ($found.Count -gt 0) {
    Write-Host "Client '$ClientId' already exists in realm '$Realm'. Aborting." -ForegroundColor Yellow
    exit 1
}

Write-Host "== Creating/verifying roles ==" -ForegroundColor Cyan
$existingRoleNames = ((KC get roles -r $Realm --fields name) | ConvertFrom-Json) | ForEach-Object { $_.name }
foreach ($r in $Roles) {
    if ($existingRoleNames -contains $r) {
        Write-Host "  - $r already exists"
    } else {
        Write-Host "  - creating $r"
        KC create roles -r $Realm -s "name=$r" | Out-Null
    }
}

Write-Host "== Creating client '$ClientId' ==" -ForegroundColor Cyan
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
if (-not $cid) { throw "Could not extract the client UUID. Output: $createOutput" }
Write-Host "  Internal UUID: $cid"

Write-Host "== Retrieving client_secret ==" -ForegroundColor Cyan
$secret = ((KC get "clients/$cid/client-secret" -r $Realm) | ConvertFrom-Json).value

$saUsername = "service-account-$ClientId"
if ($Roles.Count -gt 0) {
    Write-Host "== Assigning roles to service account '$saUsername' ==" -ForegroundColor Cyan
    $roleArgs = @()
    foreach ($r in $Roles) { $roleArgs += @("--rolename", $r) }
    KC add-roles -r $Realm --uusername $saUsername @roleArgs | Out-Null
    Write-Host "  assigned: $($Roles -join ', ')"
}

Write-Host ""
Write-Host "=========================================================" -ForegroundColor Green
Write-Host " Client created" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
Write-Host " Realm     : $Realm"
Write-Host " clientId  : $ClientId"
Write-Host " UUID      : $cid"
Write-Host " secret    : $secret"
Write-Host " SA user   : $saUsername"
Write-Host " Roles     : $($Roles -join ', ')"
Write-Host ""
Write-Host " Request a token:" -ForegroundColor Cyan
Write-Host "   curl.exe -s -X POST 'http://localhost/realms/$Realm/protocol/openid-connect/token' \`"
Write-Host "     -H 'Content-Type: application/x-www-form-urlencoded' \`"
Write-Host "     -d 'grant_type=client_credentials&client_id=$ClientId&client_secret=$secret'"
