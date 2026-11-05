$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$secretPath=Join-Path $projectRoot '.codex-build\mysql-dev\secrets.json'
if(-not (Test-Path -LiteralPath $secretPath)) { throw 'Run scripts/dev-db.ps1 start first.' }
$secrets=Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
$savedPassword=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$secrets.adminPassword
    # Only the disposable test schema in our separate development instance is reset.
    & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --no-defaults --protocol=TCP --host=127.0.0.1 --port=3307 --user=root --connect-timeout=3 '--execute=DROP DATABASE IF EXISTS derm_followup_test; CREATE DATABASE derm_followup_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin; GRANT ALL PRIVILEGES ON derm_followup_test.* TO ''derm_app''@''localhost'';'
    if($LASTEXITCODE -ne 0) { throw 'Could not prepare the disposable MySQL test schema.' }
} finally { $env:MYSQL_PWD=$savedPassword }
$env:RUN_MYSQL_TESTS='true'
& "$PSScriptRoot\mvn.ps1" -MavenArgs @('-o','test','-Dtest=MySqlIntegrationTest,MySqlPatientDoctorTest','-q')
exit $LASTEXITCODE
