# 迁移测试：把一条记录写进设备上的 v1 数据库，然后验证升级到 v2 后它还在。
# 用法：先装 v1 版（版本号=1、不注册迁移），跑 -Prepare；
#       再装 v2 版（版本号=2、注册 MIGRATION_1_2），跑 -Verify。
param([switch]$Prepare, [switch]$Verify)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$adb = 'C:\Users\cloud\Documents\Codex\2026-08-26\ji-yu\work\toolchain\android-sdk\platform-tools\adb.exe'
$d = '127.0.0.1:16384'
$pkg = 'com.calorietracker.app'
$local = Join-Path $root 'tools\data\migration_probe.db'

function Pull-Db([string]$target) {
  Remove-Item "$target", "$target-wal", "$target-shm" -ErrorAction SilentlyContinue
  foreach ($suffix in @('', '-wal', '-shm')) {
    $txt = (& $adb -s $d exec-out "run-as $pkg base64 databases/calorie.db$suffix") -join ''
    if ($txt.Length -gt 0) {
      [IO.File]::WriteAllBytes(($target + $suffix), [Convert]::FromBase64String($txt))
    }
  }
}

function Push-Db([string]$source) {
  # 走 shell 管道：shell 能读 /sdcard，run-as 负责写进应用私有目录
  & $adb -s $d shell "run-as $pkg rm -f databases/calorie.db databases/calorie.db-wal databases/calorie.db-shm"
  & $adb -s $d push $source /sdcard/migration_probe.db | Out-Null
  & $adb -s $d shell "cat /sdcard/migration_probe.db | run-as $pkg sh -c 'cat > databases/calorie.db'"
  & $adb -s $d shell "rm -f /sdcard/migration_probe.db"
}

if ($Prepare) {
  $adb_shell = $null
  & $adb -s $d shell am force-stop $pkg
  Pull-Db $local
  Push-Location $root
  python tools\insert-test-row.py $local
  Pop-Location
  Push-Db $local
  Write-Output '--- 写回后回读 ---'
  & $adb -s $d shell am start -n "$pkg/.MainActivity" | Out-Null
  Start-Sleep -Seconds 10
  & $adb -s $d shell am force-stop $pkg
  Pull-Db $local
  Push-Location $root
  python tools\check-device-db.py $local 2>&1 | Select-Object -Last 8
  Pop-Location
}

if ($Verify) {
  & $adb -s $d shell am force-stop $pkg
  & $adb -s $d shell am start -n "$pkg/.MainActivity" | Out-Null
  Start-Sleep -Seconds 14
  & $adb -s $d shell am force-stop $pkg
  Pull-Db $local
  Push-Location $root
  Write-Output '--- 升级后数据检查 ---'
  python tools\check-device-db.py $local 2>&1 | Select-Object -Last 10
  Pop-Location
}
