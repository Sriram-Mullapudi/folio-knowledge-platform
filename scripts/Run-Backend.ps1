$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'
if (!(Test-Path -LiteralPath $envFile)) {
    $lines = foreach ($key in @('DB_PASSWORD','S3_SECRET_KEY','JWT_SECRET')) {
        $bytes = New-Object byte[] 48
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($bytes)
        $rng.Dispose()
        $key + '=' + [Convert]::ToBase64String($bytes)
    }
    Set-Content -LiteralPath $envFile -Value $lines
}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process') }
}
$composeArgs = @('--env-file', $envFile, '-f', (Join-Path $projectRoot 'infra/compose.local.yml'))
if (($env:GENERATION_ENABLED -eq 'true' -and $env:GENERATION_BASE_URL -eq 'http://127.0.0.1:11434/v1') -or ($env:EMBEDDINGS_ENABLED -eq 'true' -and $env:EMBEDDING_BASE_URL -eq 'http://127.0.0.1:11434/v1')) { $composeArgs += @('--profile','local-ai') }
docker compose @composeArgs up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Local services failed to start' }
$env:DB_PORT='55432'
$env:DB_USER='ekp'
$env:DB_NAME='ekp'
$env:REDIS_PORT='56379'
$env:S3_ENDPOINT='http://127.0.0.1:59000'
$env:S3_ACCESS_KEY='ekp_local'
$env:S3_BUCKET='ekp-development'
$env:SERVER_PORT='8081'
$env:SERVER_ADDRESS='127.0.0.1'
$env:CORS_ALLOWED_ORIGINS='http://localhost:5174,http://127.0.0.1:5174'
if (!$env:EMBEDDINGS_ENABLED) { $env:EMBEDDINGS_ENABLED='false' }
if (!$env:GENERATION_ENABLED) { $env:GENERATION_ENABLED='false' }
$env:LOG_LEVEL_APP='INFO'
if (!$env:JAVA_HOME) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -B package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
    try { & (Join-Path $PSScriptRoot 'Warm-LocalAI.ps1') }
    catch { Write-Warning 'Local AI could not be prepared. Folio will still start; check Docker and run scripts/Warm-LocalAI.ps1 to retry.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar target/ekp-backend.jar
} finally { Pop-Location }

