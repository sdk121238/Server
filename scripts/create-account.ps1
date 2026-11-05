param([string]$Account,[ValidateSet('PATIENT','HOSPITAL_STAFF')][string]$Role,[string]$DisplayName)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$jar=Join-Path $projectRoot 'target\followup-server-0.1.0.jar'
if(-not (Test-Path -LiteralPath $jar)) {throw 'Build the backend JAR first.'}
. (Join-Path $PSScriptRoot 'local.ps1')
if(-not $Account) {$Account=Read-Host 'New account / 新账号'}
if(-not $DisplayName) {$DisplayName=Read-Host 'Display name / 显示名'}
if(-not $Role) {
    $choice=Read-Host 'Role: 1 patient / 患者, 2 doctor / 医生'
    if($choice -eq '1') {$Role='PATIENT'} elseif($choice -eq '2') {$Role='HOSPITAL_STAFF'} else {throw 'Choose 1 or 2.'}
}
$securePassword=Read-Host 'Initial password / 初始密码（至少12字符）' -AsSecureString
$confirmedPassword=Read-Host 'Confirm password / 再次输入密码' -AsSecureString
$passwordPointer=[IntPtr]::Zero
$confirmationPointer=[IntPtr]::Zero
$savedProvisionPassword=$env:PROVISION_PASSWORD
$savedProvisionAccount=$env:APP_PROVISION_ACCOUNT
$savedProvisionName=$env:APP_PROVISION_NAME
$savedProvisionRole=$env:APP_PROVISION_ROLE
try {
    $passwordPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $confirmationPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmedPassword)
    $plainPassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if($plainPassword -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmationPointer)) {throw 'Passwords do not match.'}
    $env:PROVISION_PASSWORD=$plainPassword
    $env:APP_PROVISION_ACCOUNT=$Account
    $env:APP_PROVISION_NAME=$DisplayName
    $env:APP_PROVISION_ROLE=$Role
    $projectJava=$env:PROJECT_JAVA_HOME
    if(-not $projectJava) {$projectJava='D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\jbr'}
    Push-Location -LiteralPath $projectRoot
    try {
        & "$projectJava\bin\java.exe" '-jar' $jar '--spring.main.web-application-type=none' '--spring.main.banner-mode=off' '--logging.level.root=WARN'
        if($LASTEXITCODE -ne 0) {throw 'Account creation failed; existing accounts were not changed.'}
    } finally {Pop-Location}
} finally {
    $env:PROVISION_PASSWORD=$savedProvisionPassword
    $env:APP_PROVISION_ACCOUNT=$savedProvisionAccount
    $env:APP_PROVISION_NAME=$savedProvisionName
    $env:APP_PROVISION_ROLE=$savedProvisionRole
    $plainPassword=$null
    if($passwordPointer -ne [IntPtr]::Zero) {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)}
    if($confirmationPointer -ne [IntPtr]::Zero) {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmationPointer)}
    $securePassword.Dispose();$confirmedPassword.Dispose()
}
