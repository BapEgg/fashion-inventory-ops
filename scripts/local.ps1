[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('setup', 'seed-check', 'db-up', 'db-down', 'db-status', 'db-logs', 'backend', 'frontend', 'test', 'test-db-free')]
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

    Write-Output 'Imported .env (values not printed).'
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

# Read-only status check used by the full local verifier. Never starts, stops, recreates or
# deletes the Oracle container/volume -- `db-up`/`db-down` remain the only explicit lifecycle
# actions, per the Phase 7 verification contract.
function Assert-OracleHealthy {
    Assert-DockerEngine
    Push-Location $stockpilotRepository
    try {
        $stockpilotComposeJsonLines = & docker compose --env-file $stockpilotEnvFile ps --format json oracle
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to query Docker Compose status for the oracle service.'
        }
    }
    finally {
        Pop-Location
    }

    $stockpilotOracleStatus = $null
    foreach ($stockpilotJsonLine in $stockpilotComposeJsonLines) {
        if ([string]::IsNullOrWhiteSpace($stockpilotJsonLine)) {
            continue
        }
        $stockpilotEntry = $stockpilotJsonLine | ConvertFrom-Json
        if ($stockpilotEntry.Service -eq 'oracle') {
            $stockpilotOracleStatus = $stockpilotEntry
        }
    }

    if (
        $null -eq $stockpilotOracleStatus -or
        $stockpilotOracleStatus.State -ne 'running' -or
        $stockpilotOracleStatus.Health -ne 'healthy'
    ) {
        throw 'Oracle service is not running and healthy. Run: .\scripts\local.ps1 db-up'
    }

    Write-Output 'Oracle service is running and healthy.'
}

# The Docker health check above only proves Oracle itself is reachable -- it says nothing about
# whether the test JVM actually received a non-empty DB contract. Without this, a blank/missing
# `DB_URL` would let every `@EnabledIfEnvironmentVariable` Oracle IT skip silently while Gradle
# still exits 0, producing a false-green full verifier. Never prints the values themselves.
function Assert-StockPilotOracleCredentialsPresent {
    if (
        [string]::IsNullOrWhiteSpace($env:DB_URL) -or
        [string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or
        [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)
    ) {
        throw 'DB_URL/DB_USERNAME/DB_PASSWORD must all be non-empty (check .env) before the full verifier runs the Oracle Backend suite.'
    }
    Write-Output 'DB_URL/DB_USERNAME/DB_PASSWORD are present (values not printed).'
}

# Aggregates every `TEST-*.xml` JUnit report Gradle wrote, so pass/fail/skip is asserted from the
# actual recorded results rather than trusted from Gradle's own process exit code alone.
function Get-StockPilotJUnitSummary {
    param([string]$ResultsDirectory)

    if (-not (Test-Path -LiteralPath $ResultsDirectory)) {
        throw "Missing test results directory: $ResultsDirectory"
    }

    $stockpilotResultFiles = @(Get-ChildItem -LiteralPath $ResultsDirectory -Filter 'TEST-*.xml' -File)
    if ($stockpilotResultFiles.Count -eq 0) {
        throw "No JUnit XML result files found in: $ResultsDirectory"
    }

    $stockpilotTests = 0
    $stockpilotSkipped = 0
    $stockpilotFailures = 0
    $stockpilotErrors = 0
    foreach ($stockpilotResultFile in $stockpilotResultFiles) {
        [xml]$stockpilotResultXml = Get-Content -LiteralPath $stockpilotResultFile.FullName -Raw
        $stockpilotTests += [int]$stockpilotResultXml.testsuite.tests
        $stockpilotSkipped += [int]$stockpilotResultXml.testsuite.skipped
        $stockpilotFailures += [int]$stockpilotResultXml.testsuite.failures
        $stockpilotErrors += [int]$stockpilotResultXml.testsuite.errors
    }

    return [pscustomobject]@{
        Tests    = $stockpilotTests
        Skipped  = $stockpilotSkipped
        Failures = $stockpilotFailures
        Errors   = $stockpilotErrors
        Passed   = $stockpilotTests - $stockpilotSkipped - $stockpilotFailures - $stockpilotErrors
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
    'test-db-free' {
        # Imports `.env` (some non-DB settings, e.g. `AI_ENABLED`, may still matter to a unit test)
        # but then strips `DB_URL` so every Oracle-only integration test's `@EnabledIfEnvironmentVariable`
        # gate skips deterministically -- this command never talks to Oracle, on purpose.
        Import-StockPilotEnvironment
        [System.Environment]::SetEnvironmentVariable('DB_URL', $null, 'Process')

        Push-Location (Join-Path $stockpilotRepository 'backend')
        try {
            & .\gradlew.bat --gradle-user-home .gradle-user-home clean test --rerun-tasks
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }

        # Report the real recorded total -- Oracle-only tests skipping here is expected, not a
        # false-green risk, but a zero-failure/zero-error result and at least one executed test are
        # still required.
        $stockpilotSummary = Get-StockPilotJUnitSummary -ResultsDirectory (Join-Path $stockpilotRepository 'backend\build\test-results\test')
        Write-Output "DB-free Backend results: tests=$($stockpilotSummary.Tests) passed=$($stockpilotSummary.Passed) skipped=$($stockpilotSummary.Skipped) failures=$($stockpilotSummary.Failures) errors=$($stockpilotSummary.Errors)"
        if ($stockpilotSummary.Tests -eq 0 -or $stockpilotSummary.Failures -gt 0 -or $stockpilotSummary.Errors -gt 0) {
            throw 'DB-free Backend verification did not produce a clean (tests > 0, zero failures/errors) result.'
        }

        Write-Output 'DB-free Backend verification passed (Oracle-only tests skipped).'
    }
    'test' {
        # The full local verifier (Phase 7): every stage below must actually run and pass, or the
        # command exits non-zero. It never starts/stops/recreates/deletes the Oracle
        # container/volume -- `db-up`/`db-down` stay the only explicit lifecycle actions.

        # 1. Require and import .env (values are never printed).
        Import-StockPilotEnvironment

        # 2. Seed validation.
        Write-Output '--- Seed validation ---'
        & (Join-Path $PSScriptRoot 'validate-seed.ps1')
        if (-not $?) { throw 'Seed validation failed.' }

        # 3. Fail early unless Docker is reachable, the compose Oracle service is healthy, and the
        #    imported environment actually carries non-empty Oracle credentials -- otherwise the
        #    Backend suite below could pass by having every Oracle-only test skip silently.
        Write-Output '--- Oracle health check ---'
        Assert-OracleHealthy
        Assert-StockPilotOracleCredentialsPresent

        # 4. Backend full suite against the imported Oracle environment.
        Write-Output '--- Backend test suite (Oracle) ---'
        Push-Location (Join-Path $stockpilotRepository 'backend')
        try {
            & .\gradlew.bat --gradle-user-home .gradle-user-home clean test --rerun-tasks
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }

        # The full verifier's whole point is proving Oracle actually ran -- a Gradle exit code of 0
        # is not enough proof by itself (every Oracle-only test could have skipped). Require at
        # least one executed test and zero skipped/failed/errored tests from the real JUnit XML.
        $stockpilotSummary = Get-StockPilotJUnitSummary -ResultsDirectory (Join-Path $stockpilotRepository 'backend\build\test-results\test')
        Write-Output "Oracle Backend results: tests=$($stockpilotSummary.Tests) passed=$($stockpilotSummary.Passed) skipped=$($stockpilotSummary.Skipped) failures=$($stockpilotSummary.Failures) errors=$($stockpilotSummary.Errors)"
        if (
            $stockpilotSummary.Tests -eq 0 -or
            $stockpilotSummary.Skipped -gt 0 -or
            $stockpilotSummary.Failures -gt 0 -or
            $stockpilotSummary.Errors -gt 0
        ) {
            throw 'Oracle Backend verification requires tests > 0 and zero skipped/failures/errors -- any skip here means an Oracle-only test did not actually run against Oracle.'
        }

        # 5. Frontend: require Node/pnpm, frozen-lockfile install, test, then build.
        if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
            throw 'Node.js is not available on PATH. Install Node.js 22 LTS or later.'
        }
        if ($null -eq (Get-Command pnpm -ErrorAction SilentlyContinue)) {
            throw 'pnpm is not available. Run: corepack enable'
        }

        Write-Output '--- Frontend install/test/build ---'
        Push-Location (Join-Path $stockpilotRepository 'frontend')
        try {
            & pnpm install --frozen-lockfile
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & pnpm test
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & pnpm build
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        finally {
            Pop-Location
        }

        Write-Output 'Full local verification passed.'
    }
}
