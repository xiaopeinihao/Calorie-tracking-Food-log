$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_ascii-drive.ps1')
$proj = Split-Path -Parent $PSScriptRoot
Write-Output "proj=$proj"
$dir = Resolve-AsciiWorkDir -ProjectDir $proj
Write-Output ("resolved=[" + ($dir -join '|') + "] type=" + $dir.GetType().Name)
Write-Output '--- subst now ---'
subst
Write-Output '--- probe X: ---'
foreach ($letter in @('X', 'Y', 'Z', 'W', 'V', 'U', 'T', 'S')) {
  $root = "${letter}:\"
  Write-Output ("{0} exists={1} props={2} settings={3}" -f $letter, (Test-Path $root), (Test-Path "${root}gradle.properties"), (Test-Path "${root}settings.gradle.kts"))
}
