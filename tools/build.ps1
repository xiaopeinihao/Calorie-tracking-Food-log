# Build the debug APK with the local toolchain.
# NOTE: keep this file ASCII-only (PowerShell 5.1 reads .ps1 as ANSI on this host).
param(
  [switch]$SkipTests,
  [switch]$Install,
  # KSP/Kotlin 增量缓存偶尔会坏（cleanFilenames has not been initialized），
  # 这时先 clean 再编，比手工删目录靠谱。
  [switch]$Clean
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$proj = Split-Path -Parent $PSScriptRoot
$TC = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain'
$env:JAVA_HOME = Join-Path $TC 'jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $TC 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$gradle = Join-Path $TC 'gradle-8.9\bin\gradle.bat'
$log = Join-Path $PSScriptRoot 'build.log'

# 工程目录含中文时，Gradle 的测试 JVM 会因 classpath 编码问题找不到测试类。
# 映射一个 ASCII 盘符再跑 Gradle；产物仍落在原目录。
. (Join-Path $PSScriptRoot '_ascii-drive.ps1')
$workDir = Resolve-AsciiWorkDir -ProjectDir $proj

$tasks = @()
if ($Clean) { $tasks += 'clean' }
$tasks += ':app:assembleDebug'
if (-not $SkipTests) { $tasks += ':app:testDebugUnitTest' }

Push-Location $workDir
& $gradle @tasks --console=plain 2>&1 | Tee-Object -FilePath $log
$code = $LASTEXITCODE
Pop-Location

Write-Output "GRADLE_EXIT=$code"

$apk = Join-Path $proj 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apk) {
  Write-Output ("APK {0:N0} bytes  {1}" -f (Get-Item $apk).Length, (Get-Item $apk).LastWriteTime)
} else {
  Write-Output 'APK missing'
}

if ($Install -and (Test-Path $apk)) {
  $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
  & $adb install -r $apk
}
