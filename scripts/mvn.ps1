# 复用本机JDK与Maven，加载本地配置并执行构建命令。
param([switch]$SkipLocal,[Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localConfig = Join-Path $PSScriptRoot 'local.ps1'
if (-not $SkipLocal -and (Test-Path -LiteralPath $localConfig)) { . $localConfig }
$projectJava = $env:PROJECT_JAVA_HOME
if (-not $projectJava) { $projectJava = 'D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\jbr' }
$projectMaven = $env:PROJECT_MAVEN_HOME
if (-not $projectMaven) { $projectMaven = 'D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3' }
if (-not (Test-Path -LiteralPath "$projectJava\bin\javac.exe")) { throw 'Set PROJECT_JAVA_HOME to an existing JDK 21 directory.' }
if (-not (Test-Path -LiteralPath "$projectMaven\bin\mvn.cmd")) { throw 'Set PROJECT_MAVEN_HOME to an existing Maven directory.' }
$env:JAVA_HOME = $projectJava
$env:PATH = "$projectJava\bin;$env:PATH"
Push-Location $projectRoot
try {
    & "$projectMaven\bin\mvn.cmd" "-Dmaven.repo.local=$projectRoot\.codex-build\m2" @MavenArgs
    $mavenExit = $LASTEXITCODE
} finally { Pop-Location }
exit $mavenExit
