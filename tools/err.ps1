$root = Split-Path -Parent $PSScriptRoot
Get-Content (Join-Path $root 'tools\build.log') -ErrorAction SilentlyContinue |
  Select-String -Pattern '^e: |error:|What went wrong|FAILED|Caused by|Uninitialized' |
  ForEach-Object { $_.Line } | Select-Object -Last 40
