[CmdletBinding()]
param(
    [switch]$AutoStartInfra,
    [int]$TimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$composeFile = Join-Path $repoRoot "docker-compose.yml"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Fail {
    param([string]$Message)
    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

function Test-TcpPort {
    param(
        [string]$Host,
        [int]$Port
    )

    $client = $null
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $async = $client.BeginConnect($Host, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1000, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $client) {
            $client.Dispose()
        }
    }
}

function Get-ContainerState {
    param([string]$ContainerName)

    $output = & docker inspect --format "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $ContainerName 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($output)) {
        return $null
    }

    $parts = $output.Trim().Split("|")
    return [pscustomobject]@{
        Status = $parts[0]
        Health = $parts[1]
    }
}

function Assert-DockerAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail "Docker CLI was not found in PATH. Install Docker Desktop and retry."
    }

    cmd /c "docker info >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
        Fail "Docker daemon is not reachable. Start Docker Desktop, wait until it is fully running, then retry."
    }
}

function Ensure-InfraStarted {
    param([switch]$AllowStart)

    $postgresState = Get-ContainerState "rentflow-postgres"
    $redisState = Get-ContainerState "rentflow-redis"

    $needsStart = (
        $null -eq $postgresState -or
        $null -eq $redisState -or
        $postgresState.Status -ne "running" -or
        $redisState.Status -ne "running"
    )

    if ($needsStart -and -not $AllowStart) {
        Fail "PostgreSQL/Redis containers are not running. Run 'docker compose up -d' or rerun this script with -AutoStartInfra."
    }

    if ($needsStart -and $AllowStart) {
        Write-Step "Starting PostgreSQL and Redis with docker compose"
        & docker compose -f $composeFile up -d
        if ($LASTEXITCODE -ne 0) {
            Fail "Failed to start docker compose services. Check Docker Desktop and retry."
        }
    }
}

function Wait-ForInfraReady {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    do {
        $postgresState = Get-ContainerState "rentflow-postgres"
        $redisState = Get-ContainerState "rentflow-redis"

        $postgresReady = (
            $null -ne $postgresState -and
            $postgresState.Status -eq "running" -and
            $postgresState.Health -eq "healthy" -and
            (Test-TcpPort -Host "localhost" -Port 5433)
        )
        $redisReady = (
            $null -ne $redisState -and
            $redisState.Status -eq "running" -and
            $redisState.Health -eq "healthy" -and
            (Test-TcpPort -Host "localhost" -Port 6379)
        )

        if ($postgresReady -and $redisReady) {
            return
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    $postgresStateText = if ($null -eq $postgresState) { "missing" } else { "$($postgresState.Status)/$($postgresState.Health)" }
    $redisStateText = if ($null -eq $redisState) { "missing" } else { "$($redisState.Status)/$($redisState.Health)" }

    Fail "Infrastructure is not ready after $TimeoutSeconds seconds. postgres=$postgresStateText port5433=$(Test-TcpPort -Host 'localhost' -Port 5433); redis=$redisStateText port6379=$(Test-TcpPort -Host 'localhost' -Port 6379)."
}

Write-Step "Checking Docker availability"
Assert-DockerAvailable

Write-Step "Checking local infrastructure containers"
Ensure-InfraStarted -AllowStart:$AutoStartInfra

Write-Step "Waiting for PostgreSQL and Redis to become reachable"
Wait-ForInfraReady

Write-Step "Local infrastructure is ready"
Write-Host "PostgreSQL: localhost:5433"
Write-Host "Redis: localhost:6379"
