[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$logFile = Join-Path $env:TEMP "rentflow-backend.log"

function Fail {
    param([string]$Message)
    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

$portInUse = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq 8087 } |
    Select-Object -First 1

if ($null -ne $portInUse) {
    Fail "Port 8087 is already in use by PID $($portInUse.OwningProcess). Stop it before starting the backend."
}

if (Test-Path $logFile) {
    Remove-Item -LiteralPath $logFile -Force
}

$env:RENTFLOW_MAIL_ENABLED = "true"
$env:RENTFLOW_MAIL_FROM = "no-reply@rentflow.local"
$env:RENTFLOW_FRONTEND_BASE_URL = "http://localhost:3000"
$env:SPRING_MAIL_HOST = "localhost"
$env:SPRING_MAIL_PORT = "1025"
$env:SPRING_MAIL_USERNAME = ""
$env:SPRING_MAIL_PASSWORD = ""
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = "false"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "false"
$env:RENTFLOW_CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:3001,http://localhost:3002"
$env:RENTFLOW_PAYMENT_SANDBOX_TRANSFER_CONFIRMATION_ENABLED = "true"

Write-Host "Backend URL: http://localhost:8087"
Write-Host "Mailpit URL: http://localhost:8025"
Write-Host "Log file: $logFile"

Set-Location $repoRoot
mvn spring-boot:run *> $logFile
