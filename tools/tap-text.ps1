# Find a UI element by its text/content-desc and tap its center.
param(
  [Parameter(Mandatory = $true)][string]$Label,
  [int]$Index = 0,
  [int]$WaitSeconds = 3
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$adb = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain\android-sdk\platform-tools\adb.exe'
$d = '127.0.0.1:16384'
$xml = Join-Path $root 'tools\data\probe.xml'

& $adb -s $d shell "uiautomator dump /sdcard/probe.xml" | Out-Null
& $adb -s $d pull '/sdcard/probe.xml' $xml | Out-Null

$content = Get-Content $xml -Raw -Encoding UTF8
# 优先精确匹配文案，避免误点到「……请重试或手动录入」这种包含同名词的说明文字
$exact = '<node[^>]*?text="' + [Regex]::Escape($Label) + '"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
$matches = [Regex]::Matches($content, $exact)
if ($matches.Count -eq 0) {
  $loose = '<node[^>]*?(?:text|content-desc)="[^"]*' + [Regex]::Escape($Label) + '[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
  $matches = [Regex]::Matches($content, $loose)
}
if ($matches.Count -le $Index) {
  Write-Output "NOT FOUND: $Label (matches=$($matches.Count))"
  exit 1
}
$m = $matches[$Index]
$x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
$y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
& $adb -s $d shell input tap ([int]$x) ([int]$y)
Start-Sleep -Seconds $WaitSeconds
Write-Output "tapped '$Label' at ($([int]$x),$([int]$y))"
