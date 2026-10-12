# 共享：为含非 ASCII 字符的工程路径找一个 ASCII 盘符，规避 Windows 下
# Gradle test worker 的 classpath 编码问题（否则单测会 ClassNotFoundException）。
# 由 build.ps1 / test.ps1 点源引入。
function Resolve-AsciiWorkDir {
  param([Parameter(Mandatory = $true)][string]$ProjectDir)

  if ($ProjectDir -notmatch '[^\x00-\x7F]') { return $ProjectDir }

  $sentinel = 'gradle.properties'
  foreach ($letter in @('X', 'Y', 'Z', 'W', 'V', 'U', 'T', 'S')) {
    $root = "${letter}:\"
    $exists = Test-Path $root
    if (-not $exists) {
      # 注意：这里必须写成 "X:"（带冒号）。写成 "X" 时 subst 会报
      # "Invalid parameter - X" 并且被管道吞掉，表现为「映射静默失败」。
      & subst.exe "${letter}:" $ProjectDir 2>&1 | Out-Null
      $exists = Test-Path $root
    }
    if ($exists -and (Test-Path "${root}${sentinel}") -and (Test-Path "${root}settings.gradle.kts")) {
      # 注意：函数只能返回路径本身，日志必须走 Write-Host，
      # 否则返回值会变成数组，Push-Location 会失败并静默退回原路径。
      Write-Host "non-ASCII project path -> building via $letter"
      return $root
    }
  }
  Write-Host 'WARN: could not map an ASCII drive; unit tests will likely fail on this path'
  return $ProjectDir
}
