<#
    build-images-s17.ps1
    Builds the Docker images of the 6 section_17 microservices with the :s17
    tag using Jib (jib-maven-plugin already configured in each pom.xml).

    Prerequisites:
      - Docker Desktop running
      - JDK 17+ installed
    Usage (from the section_17 folder):
      .\build-images-s17.ps1
#>

$ErrorActionPreference = "Stop"
$services = @("configserver", "accounts", "cards", "loans", "gatewayserver", "message")
$root = $PSScriptRoot

Write-Host "== Checking Docker ==" -ForegroundColor Cyan
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker is not responding. Open Docker Desktop and retry." }

$built = @()
foreach ($s in $services) {
    $dir = Join-Path $root $s
    Write-Host "`n== Building $s -> eazybytes/${s}:s17 ==" -ForegroundColor Green
    Push-Location $dir
    try {
        & ".\mvnw.cmd" -q clean compile jib:dockerBuild "-Djib.to.image=eazybytes/${s}:s17"
        if ($LASTEXITCODE -ne 0) { throw "Build failed for $s" }
        $built += "eazybytes/${s}:s17"
    }
    finally { Pop-Location }
}

Write-Host "`n== Built images ==" -ForegroundColor Cyan
docker images "eazybytes/*" --format "table {{.Repository}}:{{.Tag}}`t{{.ID}}`t{{.Size}}" | Select-String "s17"
Write-Host "`nOK: $($built.Count) images with tag s17." -ForegroundColor Green
