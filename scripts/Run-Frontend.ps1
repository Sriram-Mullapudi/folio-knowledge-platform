$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:VITE_PROXY_TARGET='http://127.0.0.1:8081'
Push-Location (Join-Path $projectRoot 'frontend')
try {
    if (!(Test-Path node_modules)) { npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' } }
    npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort
} finally { Pop-Location }
