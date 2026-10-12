# Tap on the emulator at the given coordinates (landscape 1600x900 space).
param(
  [Parameter(Mandatory = $true)][int]$X,
  [Parameter(Mandatory = $true)][int]$Y,
  [int]$WaitSeconds = 3
)
$adb = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain\android-sdk\platform-tools\adb.exe'
$d = '127.0.0.1:16384'
& $adb -s $d shell input tap $X $Y
Start-Sleep -Seconds $WaitSeconds
Write-Output "tapped ($X,$Y)"
