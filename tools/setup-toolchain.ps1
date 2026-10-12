# Prepare local build toolchain: JDK17 + Android SDK platform 36 + gradle wrapper
# NOTE: keep this file ASCII-only, PowerShell 5.1 reads .ps1 as ANSI/GBK on this host.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$proj = Split-Path -Parent $PSScriptRoot
$TC   = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain'
$SDK  = Join-Path $TC 'android-sdk'
$JDK  = Join-Path $TC 'jdk-17.0.20.1+1'
$GRADLE = Join-Path $TC 'gradle-8.9\bin\gradle.bat'
$env:JAVA_HOME = $JDK
$env:ANDROID_HOME = $SDK
$env:ANDROID_SDK_ROOT = $SDK

Write-Output "PROJ=$proj"
Write-Output "JAVA_HOME=$env:JAVA_HOME"
& (Join-Path $JDK 'bin\java.exe') -version 2>&1 | Select-Object -First 1

$sm = Join-Path $SDK 'cmdline-tools\latest\bin\sdkmanager.bat'

Write-Output '--- accepting licenses ---'
('y' + [Environment]::NewLine) * 30 | & $sm --licenses --sdk_root="$SDK" 2>&1 | Select-Object -Last 3

Write-Output '--- installing packages ---'
& $sm --sdk_root="$SDK" --install 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools' 2>&1 | Select-Object -Last 12

Write-Output '--- installed platforms ---'
Get-ChildItem (Join-Path $SDK 'platforms') | Select-Object -ExpandProperty Name
Write-Output '--- installed build-tools ---'
Get-ChildItem (Join-Path $SDK 'build-tools') | Select-Object -ExpandProperty Name

# local.properties
$lp = Join-Path $proj 'local.properties'
$escaped = $SDK -replace '\\', '\\'
"sdk.dir=$escaped" | Set-Content -Path $lp -Encoding ASCII
Write-Output "wrote $lp"

# gradle wrapper
Push-Location $proj
& $GRADLE wrapper --gradle-version 8.9 --distribution-type bin 2>&1 | Select-Object -Last 6
Pop-Location
Get-ChildItem (Join-Path $proj 'gradle\wrapper') | Select-Object -ExpandProperty Name
Write-Output 'SETUP DONE'
