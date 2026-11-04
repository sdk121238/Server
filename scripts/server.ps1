param([ValidateSet('start','stop','status')][string]$Action='start')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$statePath=Join-Path $projectRoot '.codex-build\server-process.json'
$local=Join-Path $PSScriptRoot 'local.ps1'
if(Test-Path -LiteralPath $local) { . $local }
$projectJava=$env:PROJECT_JAVA_HOME
if(-not $projectJava) { $projectJava='D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\jbr' }
$serverProcess=$null
if(Test-Path -LiteralPath $statePath) {
    $state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $candidate=Get-Process -Id $state.processId -ErrorAction SilentlyContinue
    if($candidate -and $candidate.StartTime.ToUniversalTime().Ticks -eq $state.startTicks -and $candidate.Path -eq "$projectJava\bin\java.exe") { $serverProcess=$candidate }
}
if($Action -eq 'status') {
    if($serverProcess) { Write-Output "Backend running (PID $($serverProcess.Id)); health: http://127.0.0.1:8080/api/v1/system/health" }
    else { Write-Output 'Backend stopped.' }
    exit 0
}
if($Action -eq 'stop') {
    if($serverProcess) { Stop-Process -Id $serverProcess.Id; Remove-Item -LiteralPath $statePath; Write-Output 'Backend stopped.' }
    else { Write-Output 'Backend already stopped; no process was terminated.' }
    exit 0
}
if($serverProcess) { Write-Output "Backend already running (PID $($serverProcess.Id)).";exit 0 }
$jar=Join-Path $projectRoot 'target\followup-server-0.1.0.jar'
if(-not (Test-Path -LiteralPath $jar)) { throw 'Build the executable jar with scripts/mvn.ps1 package first.' }
if(-not $env:APP_SIGNING_KEY) { throw 'Configure APP_SIGNING_KEY and DB credentials, or run scripts/dev-db.ps1 start.' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
$started=Start-Process -FilePath "$projectJava\bin\java.exe" -ArgumentList @('-jar',"`"$jar`"") -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $projectRoot '.codex-build\server.stdout.log') -RedirectStandardError (Join-Path $projectRoot '.codex-build\server.stderr.log')
@{processId=$started.Id;startTicks=$started.StartTime.ToUniversalTime().Ticks} | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
Write-Output "Backend starting (PID $($started.Id)); logs are in .codex-build/server.stdout.log."
