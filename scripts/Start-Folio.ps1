param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$mutex = New-Object System.Threading.Mutex($false, 'Local\FolioLocalStartup')
$locked = $false

function Test-Ready([int]$Port) {
    try { return (Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3).status -eq 'UP' }
    catch { return $false }
}
function Test-Listening([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $task = $client.ConnectAsync('127.0.0.1', $Port); return $task.Wait(500) -and $client.Connected }
    catch { return $false } finally { $client.Dispose() }
}
function Test-PortFree([int]$Port) {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
    try { $listener.Start(); return $true } catch { return $false } finally { $listener.Stop() }
}
function Invoke-DockerCheck([string[]]$DockerArgs) {
    $out = Join-Path $runtime 'docker-check.log'
    $err = Join-Path $runtime 'docker-check-error.log'
    $process = Start-Process -FilePath (Get-Command docker).Source -ArgumentList $DockerArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    # Keep the native handle open so Windows PowerShell retains the exit code.
    $null = $process.Handle
    if (!$process.WaitForExit(8000)) { $process.Kill(); return $null }
    if ($process.ExitCode -ne 0) { return $null }
    return (Get-Content -LiteralPath $out -Raw).Trim()
}
function Start-ServiceScript([string]$Name) {
    $scriptPath = Join-Path $PSScriptRoot "Run-$Name.ps1"
    return Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList '-NoProfile','-ExecutionPolicy','RemoteSigned','-File',('"'+$scriptPath+'"') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$Name.log") -RedirectStandardError (Join-Path $runtime "$Name-error.log")
}
try {
    try { $locked = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $locked = $true }
    if (!$locked) { throw 'Folio is already starting. Please wait for the other startup window.' }
    Write-Host 'Folio - preparing your workspace' -ForegroundColor Cyan
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install Docker Desktop, then open Start Folio again.' }
    if (!(Test-Ready 8081)) {
        if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js, then open Start Folio again.' }
        if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
            $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
            if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
            else { throw 'Install Java 17 or set JAVA_HOME to your Java 17 folder, then try again.' }
        }
        Write-Host 'Checking Docker Desktop...'
        if (!(Invoke-DockerCheck @('info','--format','{{.ServerVersion}}'))) {
            $dockerApp = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
            if (!(Test-Path -LiteralPath $dockerApp)) { throw 'Open Docker Desktop, wait until it is running, then try again.' }
            Start-Process -FilePath $dockerApp -WindowStyle Hidden
            $deadline = (Get-Date).AddMinutes(2)
            do {
                Start-Sleep -Seconds 3
                $dockerReady = Invoke-DockerCheck @('info','--format','{{.ServerVersion}}')
            } until ($dockerReady -or (Get-Date) -ge $deadline)
            if (!$dockerReady) { throw 'Docker could not start. Open Docker Desktop and check its error. Do not reset its data. Your Folio files depend on its stored volumes.' }
        }
        # Reuse the current database mapping; otherwise probe a small set of ports.
        $envFile = Join-Path $projectRoot '.env.local'
        $configuredPort = if ($env:LOCAL_DB_PORT) { [int]$env:LOCAL_DB_PORT } else { 25432 }
        if (Test-Path -LiteralPath $envFile) {
            foreach ($line in Get-Content -LiteralPath $envFile) {
                if ($line -match '^LOCAL_DB_PORT=(\d+)$') { $configuredPort = [int]$matches[1] }
            }
        }
        $mapping = Invoke-DockerCheck @('port','ekp-development-postgres-1','5432')
        $databaseRunning = Invoke-DockerCheck @('inspect','--format={{.State.Running}}','ekp-development-postgres-1')
        $reuseMapping = $databaseRunning -eq 'true' -and $mapping -match ('127\.0\.0\.1:'+$configuredPort+'$')
        if (!$reuseMapping -and !(Test-PortFree $configuredPort)) {
            $replacement = 25432..25451 | Where-Object { Test-PortFree $_ } | Select-Object -First 1
            if (!$replacement) { throw 'No local database port is available. See LOCAL_DEVELOPMENT.md for LOCAL_DB_PORT setup.' }
            if (!(Test-Path -LiteralPath $envFile)) { throw 'Complete the initial Run-Backend.ps1 setup before selecting a custom database port.' }
            $lines = @(Get-Content -LiteralPath $envFile | Where-Object { $_ -notmatch '^LOCAL_DB_PORT=' })
            $lines += "LOCAL_DB_PORT=$replacement"
            Set-Content -LiteralPath $envFile -Value $lines -Encoding utf8
            Write-Host 'Adjusted the local database connection. Your stored data is preserved.'
        }
        if (!(Test-Listening 8081)) {
            Write-Host 'Starting Folio services. The first launch may take a few minutes...'
            $backendProcess = Start-ServiceScript 'Backend'
        } else { Write-Host 'Waiting for the existing backend to become ready...' }
        $deadline = (Get-Date).AddMinutes(5)
        do {
            if (Test-Ready 8081) { break }
            if ($backendProcess -and $backendProcess.HasExited) { throw "Folio could not start. See $runtime\Backend-error.log and Backend.log." }
            Start-Sleep -Seconds 2
        } until ((Get-Date) -ge $deadline)
        if (!(Test-Ready 8081)) { throw "Folio is not ready yet. See $runtime\Backend.log. Another app may be using port 8081." }
    }
    if (!(Test-Listening 5174)) {
        if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js to start the Folio page.' }
        Write-Host 'Opening the workspace...'
        $frontendProcess = Start-ServiceScript 'Frontend'
    }
    $deadline = (Get-Date).AddSeconds(45)
    do {
        if (Test-Ready 5174) { break }
        if ($frontendProcess -and $frontendProcess.HasExited) { throw "The page could not start. See $runtime\Frontend-error.log." }
        Start-Sleep -Seconds 2
    } until ((Get-Date) -ge $deadline)
    if (!(Test-Ready 5174)) { throw 'The page cannot reach Folio. Close any older Folio page server and try again. See LOCAL_DEVELOPMENT.md.' }
    Write-Host 'Folio is ready: http://127.0.0.1:5174/' -ForegroundColor Green
    if (!$NoBrowser) { Start-Process 'http://127.0.0.1:5174/' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
