$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Run Run-Backend.ps1 first.' }
$compose = Join-Path $projectRoot 'infra/compose.local.yml'
docker compose --env-file $envFile -f $compose --profile local-ai up -d ollama
if ($LASTEXITCODE -ne 0) { throw 'Could not start local AI.' }
docker compose --env-file $envFile -f $compose exec -T ollama ollama pull nomic-embed-text:v1.5
if ($LASTEXITCODE -ne 0) { throw 'Model download failed; settings were not changed.' }
$settings = [ordered]@{
 EMBEDDINGS_ENABLED='true'
 EMBEDDING_BASE_URL='http://127.0.0.1:11434/v1'
 EMBEDDING_API_KEY=''
 EMBEDDING_MODEL='nomic-embed-text:v1.5'
 EMBEDDING_DIMENSIONS='1536'
 EMBEDDING_BATCH_SIZE='8'
 EMBEDDING_READ_TIMEOUT='60s'
 EMBEDDING_MAX_RETRIES='0'
}
$lines = @(Get-Content -LiteralPath $envFile | Where-Object { ($_ -split '=',2)[0] -notin $settings.Keys })
foreach ($entry in $settings.GetEnumerator()) { $lines += "$($entry.Key)=$($entry.Value)" }
Set-Content -LiteralPath $envFile -Value $lines -Encoding utf8
Write-Output 'Local search is configured. Restart Run-Backend.ps1; existing passages will be indexed in the background.'
