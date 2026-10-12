# Dump the emulator UI hierarchy and print visible text + clickable centers.
param(
  [string]$Name = 'ui',
  [switch]$Shot
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$adb = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain\android-sdk\platform-tools\adb.exe'
$d = '127.0.0.1:16384'
$xml = Join-Path $root "tools\data\$Name.xml"

& $adb -s $d shell "uiautomator dump /sdcard/$Name.xml" | Out-Null
& $adb -s $d pull "/sdcard/$Name.xml" $xml | Out-Null

if ($Shot) {
  & $adb -s $d shell screencap -p /sdcard/$Name.png
  & $adb -s $d pull "/sdcard/$Name.png" (Join-Path $root "docs\screenshots\$Name.png") | Out-Null
}

Push-Location $root
python tools\ui-text.py $xml
python tools\ui-bounds.py $xml
Write-Output '--- nav ---'
& $adb -s $d logcat -d -s CalorieNav | Select-Object -Last 3
Pop-Location
