# 直连验证 TokenHub 的 OpenAI 兼容接口（文本 + 视觉）
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$base = 'https://tokenhub.tencentmaas.com/v1'
$key = $env:TOKENHUB_KEY

if (-not $key) { Write-Output '缺少 TOKENHUB_KEY 环境变量'; exit 1 }

$body = @{
  model = 'glm-5.3-flash'
  messages = @(@{ role = 'user'; content = '只回答四个字：接口正常' })
  stream = $false
} | ConvertTo-Json -Depth 6 -Compress
$bodyFile = Join-Path $root 'tools\data\probe-text.json'
$body | Set-Content -Path $bodyFile -Encoding UTF8

Write-Output '--- 1) 文本 /chat/completions ---'
& curl.exe -sS --max-time 60 -X POST "$base/chat/completions" `
  -H "Authorization: Bearer $key" -H 'Content-Type: application/json' `
  --data-binary "@$bodyFile"

Write-Output ''
Write-Output '--- 2) 列出模型 /models ---'
& curl.exe -sS --max-time 30 "$base/models" -H "Authorization: Bearer $key"
Write-Output ''
