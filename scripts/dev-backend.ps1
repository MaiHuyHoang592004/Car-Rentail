[CmdletBinding()]
param(
    [int]$InfraTimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$preflightScript = Join-Path $PSScriptRoot "dev-preflight.ps1"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

if (-not (Test-Path $preflightScript)) {
    Write-Error "Missing preflight script: $preflightScript"
    exit 1
}

if (-not (Test-Path $mavenWrapper)) {
    Write-Error "Missing Maven wrapper: $mavenWrapper"
    exit 1
}

& $preflightScript -AutoStartInfra -TimeoutSeconds $InfraTimeoutSeconds
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Set-Location $repoRoot
Write-Host "==> Starting RentFlow backend with local profile"
& $mavenWrapper spring-boot:run
exit $LASTEXITCODE
