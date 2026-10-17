$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env.local'
$settings = @{}
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^(GENERATION_ENABLED|GENERATION_BASE_URL|GENERATION_MODEL|LOCAL_AI_WARMUP)=(.*)$') {
            $settings[$matches[1]] = $matches[2]
        }
    }
}
if ($settings['GENERATION_ENABLED'] -ne 'true' -or
    $settings['GENERATION_BASE_URL'] -ne 'http://127.0.0.1:11434/v1' -or
    $settings['LOCAL_AI_WARMUP'] -eq 'false') { return }
if (!$settings['GENERATION_MODEL']) { throw 'Choose a local model with Enable-LocalAI.ps1 first.' }
Write-Host 'Preparing local AI. The first startup can take a minute...'
$timer = [Diagnostics.Stopwatch]::StartNew()
$body = @{ model = $settings['GENERATION_MODEL']; stream = $false; keep_alive = '15m' } | ConvertTo-Json
$result = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:11434/api/generate' -ContentType 'application/json' -Body $body -TimeoutSec 180
if (!$result.done) { throw 'Local AI did not finish preparing.' }
Write-Host ('Local AI ready in {0:N1}s.' -f $timer.Elapsed.TotalSeconds)
