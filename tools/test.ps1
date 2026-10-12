# Run unit tests only (ASCII-only helper). Uses the same ASCII-drive trick as build.ps1.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$proj = Split-Path -Parent $PSScriptRoot
$TC = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain'
$env:JAVA_HOME = Join-Path $TC 'jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $TC 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$gradle = Join-Path $TC 'gradle-8.9\bin\gradle.bat'

. (Join-Path $PSScriptRoot '_ascii-drive.ps1')
$workDir = Resolve-AsciiWorkDir -ProjectDir $proj

Push-Location $workDir
& $gradle :app:testDebugUnitTest --console=plain 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'test.log') | Select-Object -Last 20
$code = $LASTEXITCODE
Pop-Location
Write-Output "TEST_EXIT=$code"
