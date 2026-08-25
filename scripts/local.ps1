[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('setup', 'seed-check', 'db-up', 'db-down', 'db-status', 'db-logs', 'backend', 'frontend', 'test')]
    [string]$Command
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$stockpilotRepository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stockpilotEnvFile = Join-Path $stockpilotRepository '.env'
$stockpilotEnvExample = Join-Path $stockpilotRepository '.env.example'

function New-StockPilotPassword {
    $stockpilotBytes = New-Object byte[] 16
    $stockpilotGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $stockpilotGenerator.GetBytes($stockpilotBytes)
    }
    finally {
        $stockpilotGenerator.Dispose()
    }

    $stockpilotHex = -join ($stockpilotBytes | ForEach-Object { $_.ToString('X2') })
    return "Sp${stockpilotHex}9x"
}

function Initialize-StockPilotEnvironment {
    if (Test-Path -LiteralPath $stockpilotEnvFile) {
        Write-Output '.env already exists. Existing secrets were not changed.'
        return
    }

    $stockpilotTemplate = Get-Content -Raw -LiteralPath $stockpilotEnvExample
    $stockpilotTemplate = $stockpilotTemplate.Replace('CHANGE_ME_ORACLE_PASSWORD', (New-StockPilotPassword))
    $stockpilotTemplate = $stockpilotTemplate.Replace('CHANGE_ME_APP_PASSWORD', (New-StockPilotPassword))
    [System.IO.File]::WriteAllText(
        $stockpilotEnvFile,
        $stockpilotTemplate,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Output 'Created .env with local-only random passwords.'
    Write-Output 'The file is ignored by Git.'
}

function Import-StockPilotEnvironment {
    if (-not (Test-Path -LiteralPath $stockpilotEnvFile)) {
        throw 'Missing .env. Run: .\scripts\local.ps1 setup'
    }

    foreach ($stockpilotRawLine in Get-Content -LiteralPath $stockpilotEnvFile) {
        $stockpilotLine = $stockpilotRawLine.Trim()
        if ($stockpilotLine.Length -eq 0 -or $stockpilotLine.StartsWith('#')) {
            continue
        }

        $stockpilotParts = $stockpilotLine.Split('=', 2)
        if ($stockpilotParts.Count -ne 2) {
            throw "Invalid .env line: $stockpilotLine"
        }

        $stockpilotName = $stockpilotParts[0].Trim()
        $stockpilotValue = $stockpilotParts[1].Trim()
        if ($stockpilotName -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw "Invalid environment variable name: $stockpilotName"
        }

        [System.Environment]::SetEnvironmentVariable($stockpilotName, $stockpilotValue, 'Process')
    }
}

function Assert-DockerEngine {
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is not running. Start Docker Desktop and retry.'
    }
}

function Invoke-StockPilotCompose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & docker compose --env-file $stockpilotEnvFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE."
    }
}

switch ($Command) {
    'setup' {
        Initialize-StockPilotEnvironment
    }
    'seed-check' {
        & (Join-Path $PSScriptRoot 'validate-seed.ps1')
        if (-not $?) { throw 'Seed validation failed.' }
    }
    'db-up' {
        Import-StockPilotEnvironment
        Assert-DockerEngine
        Push-Location $stockpilotRepository
        try {
            Invoke-StockPilotCompose up -d --wait --wait-timeout 300 oracle
        }
        finally {
            Pop-Location
        }
    }
    'db-down' {
        Import-StockPilotEnvironment
        Assert-DockerEngine
        Push-Location $stockpilotRepository
        try {
            Invoke-StockPilotCompose down
        }
        finally {
            Pop-Location
        }
    }
    'db-status' {
        Import-StockPilotEnvironment
        Assert-DockerEngine
        Push-Location $stockpilotRepository
        try {
            Invoke-StockPilotCompose ps
        }
        finally {
            Pop-Location
        }
    }
    'db-logs' {
        Import-StockPilotEnvironment
        Assert-DockerEngine
        Push-Location $stockpilotRepository
        try {
            Invoke-StockPilotCompose logs --tail 100 oracle
        }
        finally {
            Pop-Location
        }
    }
    'backend' {
        Import-StockPilotEnvironment
        Push-Location (Join-Path $stockpilotRepository 'backend')
        try {
            & .\gradlew.bat --gradle-user-home .gradle-user-home bootRun
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }
    }
    'frontend' {
        if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
            throw 'Node.js is not available on PATH. Install Node.js 22 LTS or later.'
        }
        if ($null -eq (Get-Command pnpm -ErrorAction SilentlyContinue)) {
            throw 'pnpm is not available. Run: corepack enable'
        }

        Push-Location (Join-Path $stockpilotRepository 'frontend')
        try {
            if (-not (Test-Path -LiteralPath 'node_modules')) {
                & pnpm install --frozen-lockfile
                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            }
            & pnpm run dev
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }
    }
    'test' {
        Push-Location (Join-Path $stockpilotRepository 'backend')
        try {
            & .\gradlew.bat --gradle-user-home .gradle-user-home test
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }

        if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
            Write-Warning 'Frontend build skipped because Node.js is not available on PATH.'
            return
        }

        Push-Location (Join-Path $stockpilotRepository 'frontend')
        try {
            & pnpm run build
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }
    }
}
