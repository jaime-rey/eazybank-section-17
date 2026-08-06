<#
    build-images-s17.ps1
    Construye las imagenes Docker de los 6 microservicios de section_17
    con la etiqueta :s17 usando Jib (jib-maven-plugin ya configurado en cada pom.xml).

    Requisitos:
      - Docker Desktop en ejecucion
      - JDK 17+ instalado
    Uso (desde la carpeta section_17):
      .\build-images-s17.ps1
#>

$ErrorActionPreference = "Stop"
$services = @("configserver", "accounts", "cards", "loans", "gatewayserver", "message")
$root = $PSScriptRoot

Write-Host "== Comprobando Docker ==" -ForegroundColor Cyan
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker no responde. Abre Docker Desktop y reintenta." }

$built = @()
foreach ($s in $services) {
    $dir = Join-Path $root $s
    Write-Host "`n== Construyendo $s -> eazybytes/${s}:s17 ==" -ForegroundColor Green
    Push-Location $dir
    try {
        & ".\mvnw.cmd" -q clean compile jib:dockerBuild "-Djib.to.image=eazybytes/${s}:s17"
        if ($LASTEXITCODE -ne 0) { throw "Fallo el build de $s" }
        $built += "eazybytes/${s}:s17"
    }
    finally { Pop-Location }
}

Write-Host "`n== Imagenes construidas ==" -ForegroundColor Cyan
docker images "eazybytes/*" --format "table {{.Repository}}:{{.Tag}}`t{{.ID}}`t{{.Size}}" | Select-String "s17"
Write-Host "`nOK: $($built.Count) imagenes con tag s17." -ForegroundColor Green
