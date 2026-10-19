param([string]$Model = 'qwen2.5:3b')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Run Run-Backend.ps1 once to initialize this workspace first.' }
$compose = Join-Path $projectRoot 'infra/compose.local.yml'
docker compose --env-file $envFile -f $compose --profile local-ai up -d ollama
if ($LASTEXITCODE -ne 0) { throw 'Could not start the local model service.' }
docker compose --env-file $envFile -f $compose exec -T ollama ollama pull $Model
if ($LASTEXITCODE -ne 0) { throw 'Could not download the model. Existing generation settings were not changed.' }
$settings = [ordered]@{
 GENERATION_ENABLED='true'
 GENERATION_BASE_URL='http://127.0.0.1:11434/v1'
 GENERATION_API_KEY='ollama-local'
 GENERATION_MODEL=$Model
 GENERATION_MAX_TOKENS='450'
}
$lines = @(Get-Content -LiteralPath $envFile | Where-Object { ($_ -split '=',2)[0] -notin $settings.Keys })
foreach ($entry in $settings.GetEnumerator()) { $lines += "$($entry.Key)=$($entry.Value)" }
Set-Content -LiteralPath $envFile -Value $lines -Encoding utf8
Write-Output 'Local AI is ready. Restart Folio using Run-Backend.ps1 to activate it.'
