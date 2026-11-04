param([ValidateSet('start','stop','status')][string]$Action='start')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$dbRoot=Join-Path $projectRoot '.codex-build\mysql-dev'
$secretPath=Join-Path $dbRoot 'secrets.json'
$mysqlBin='C:\Program Files\MySQL\MySQL Server 8.0\bin'
$dbPort=3307

function Protect-LocalFile([string]$path) {
    $sid=[System.Security.Principal.WindowsIdentity]::GetCurrent().User
    # The Codex sandbox identity may differ from the desktop account that owns the project.
    $desktopAccount=New-Object System.Security.Principal.NTAccount($env:COMPUTERNAME,$env:USERNAME)
    $desktopSid=$desktopAccount.Translate([System.Security.Principal.SecurityIdentifier])
    & icacls.exe $path /inheritance:r /grant:r ('*'+$sid.Value+':F') ('*'+$desktopSid.Value+':F') '*S-1-5-18:F' | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Could not restrict local credential file access.' }
}
function New-Secret {
    $bytes=New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}
function Invoke-LocalMysql([string[]]$sqlArgs) {
    $savedPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$secrets.adminPassword
        & "$mysqlBin\mysql.exe" --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$dbPort" --user=root --connect-timeout=3 @sqlArgs
        return $LASTEXITCODE
    } finally { $env:MYSQL_PWD=$savedPassword }
}

if(-not (Test-Path -LiteralPath "$mysqlBin\mysqld.exe")) { throw 'Existing MySQL 8.0 installation was not found.' }
if($Action -eq 'status') {
    $pidPath=Join-Path $dbRoot 'mysql.pid'
    if(Test-Path -LiteralPath $pidPath) { $dbProcess=Get-Process -Id ([int](Get-Content -LiteralPath $pidPath)) -ErrorAction SilentlyContinue; if($dbProcess) { Write-Output "Project MySQL is running on 127.0.0.1:$dbPort (PID $($dbProcess.Id)).";exit 0 } }
    Write-Output 'Project MySQL is stopped.'; exit 0
}
if($Action -eq 'stop') {
    if(-not (Test-Path -LiteralPath $secretPath)) { throw 'No project database credentials found.' }
    $secrets=Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
    $stopPidPath=Join-Path $dbRoot 'mysql.pid'
    $stoppingProcess=$null
    if(Test-Path -LiteralPath $stopPidPath) { $stoppingProcess=Get-Process -Id ([int](Get-Content -LiteralPath $stopPidPath)) -ErrorAction SilentlyContinue }
    $savedPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$secrets.adminPassword
        & "$mysqlBin\mysqladmin.exe" --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$dbPort" --user=root --connect-timeout=3 shutdown
        if($LASTEXITCODE -ne 0) { throw 'Could not stop the project instance.' }
    } finally { $env:MYSQL_PWD=$savedPassword }
    if($stoppingProcess -and -not $stoppingProcess.HasExited) { $stoppingProcess.WaitForExit(30000) | Out-Null; if(-not $stoppingProcess.HasExited) { throw 'Project MySQL is still shutting down; wait before restarting.' } }
    Write-Output 'Project MySQL stopped; the original MySQL service was not changed.'; exit 0
}

$pidPath=Join-Path $dbRoot 'mysql.pid'
if(Test-Path -LiteralPath $pidPath) {
    $dbProcess=Get-Process -Id ([int](Get-Content -LiteralPath $pidPath)) -ErrorAction SilentlyContinue
    if($dbProcess) { Write-Output "Project MySQL already running on port $dbPort.";exit 0 }
}
$listener=Get-NetTCPConnection -LocalPort $dbPort -State Listen -ErrorAction SilentlyContinue
if($listener) { throw "Port $dbPort is already occupied; no existing process will be stopped." }
New-Item -ItemType Directory -Force -Path $dbRoot | Out-Null
$dataPath=Join-Path $dbRoot 'data'
$initialized=Test-Path -LiteralPath (Join-Path $dataPath 'mysql')
if(-not (Test-Path -LiteralPath $secretPath)) {
    if($initialized) { throw 'Initialized data exists without credentials; refusing to overwrite or reset it.' }
    $secrets=[ordered]@{adminPassword=(New-Secret);appPassword=(New-Secret);signingKey=(New-Secret);account='local-owner';accountPassword=(New-Secret)}
    [IO.File]::WriteAllText($secretPath,($secrets|ConvertTo-Json))
    Protect-LocalFile $secretPath
}
$secrets=Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
$nativeData=$dataPath.Replace('\','/')
$nativeRoot=$dbRoot.Replace('\','/')
if(-not $initialized) {
    & "$mysqlBin\mysqld.exe" --no-defaults --initialize-insecure "--basedir=C:/Program Files/MySQL/MySQL Server 8.0" "--datadir=$nativeData" "--log-error=$nativeRoot/initialize.log"
    if($LASTEXITCODE -ne 0) { throw 'Project MySQL initialization failed. See .codex-build/mysql-dev/initialize.log.' }
}
# Only this new instance is affected. The old root password and the original service remain untouched.
$initPath=Join-Path $dbRoot 'startup.sql'
$sql=@"
CREATE DATABASE IF NOT EXISTS derm_followup CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER IF NOT EXISTS 'derm_app'@'localhost' IDENTIFIED BY '$($secrets.appPassword)';
GRANT ALL PRIVILEGES ON derm_followup.* TO 'derm_app'@'localhost';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($secrets.adminPassword)';
"@
[IO.File]::WriteAllText($initPath,$sql,[Text.Encoding]::ASCII)
Protect-LocalFile $initPath
$dbArguments=@('--no-defaults','--basedir="C:/Program Files/MySQL/MySQL Server 8.0"',"--datadir=`"$nativeData`"",'--bind-address=127.0.0.1',"--port=$dbPort",'--mysqlx=0','--skip-log-bin',"--pid-file=`"$nativeRoot/mysql.pid`"","--log-error=`"$nativeRoot/server.log`"","--init-file=`"$($initPath.Replace('\','/'))`"")
$started=Start-Process -FilePath "$mysqlBin\mysqld.exe" -ArgumentList $dbArguments -WindowStyle Hidden -PassThru
$ready=$false
for($attempt=0;$attempt -lt 30;$attempt++) {
    Start-Sleep -Milliseconds 500
    if($started.HasExited) { break }
    $savedPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$secrets.appPassword
        # Windows PowerShell 5.1 treats native stderr as an error record. A refused
        # readiness probe is expected while MySQL starts, and must not abort the loop.
        $savedErrorAction=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            & "$mysqlBin\mysql.exe" --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$dbPort" --user=derm_app --connect-timeout=1 --database=derm_followup '--execute=SELECT 1' 2>$null | Out-Null
            $probeExit=$LASTEXITCODE
        } finally { $ErrorActionPreference=$savedErrorAction }
        if($probeExit -eq 0) { $ready=$true;break }
    } finally { $env:MYSQL_PWD=$savedPassword }
}
if(-not $ready) { throw 'Project MySQL did not become ready; see .codex-build/mysql-dev/server.log. No existing service was changed.' }
Remove-Item -LiteralPath $initPath -Force
$localPath=Join-Path $PSScriptRoot 'local.ps1'
if(Test-Path -LiteralPath $localPath) { Write-Output 'scripts/local.ps1 already exists and was left unchanged.' }
else {
    $config=@"
`$env:DB_URL='jdbc:mysql://127.0.0.1:$dbPort/derm_followup?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&sslMode=DISABLED'
`$env:DB_USERNAME='derm_app'
`$env:DB_PASSWORD='$($secrets.appPassword)'
`$env:APP_SIGNING_KEY='$($secrets.signingKey)'
`$env:BOOTSTRAP_ACCOUNT='$($secrets.account)'
`$env:BOOTSTRAP_PASSWORD='$($secrets.accountPassword)'
"@
    [IO.File]::WriteAllText($localPath,$config)
    Protect-LocalFile $localPath
}
Write-Output "Project MySQL 8.0 is ready on 127.0.0.1:$dbPort; database derm_followup and local configuration created."
Write-Output 'Credentials are stored only in ignored, user-restricted local files; they are not printed.'
