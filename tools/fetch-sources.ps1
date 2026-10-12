# Download authoritative food-nutrition sources (verbatim, no number editing).
# GitHub REST API is rate-limited on this host -> use git sparse checkout instead.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\data'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$repoDir = Join-Path $root 'cfct-repo'

# 1) China Food Composition Tables (6th ed.) structured JSON
if (-not (Test-Path (Join-Path $repoDir '.git'))) {
  Write-Output '--- cloning CFCT repo (sparse) ---'
  & git clone --depth 1 --filter=blob:none --sparse `
    'https://github.com/Sanotsu/china-food-composition-data.git' $repoDir 2>&1 | Select-Object -Last 5
  Push-Location $repoDir
  & git sparse-checkout set 'json_data_v3_20260825_qwen38max_kimi_k3_fixed' 'json_gi_of_foods' 'review_v3' 'support_v3' 2>&1 | Select-Object -Last 5
  Pop-Location
} else {
  Write-Output '--- repo exists, updating ---'
  Push-Location $repoDir
  & git pull --ff-only 2>&1 | Select-Object -Last 3
  Pop-Location
}

$jsonDir = Join-Path $repoDir 'json_data_v3_20260825_qwen38max_kimi_k3_fixed'
Write-Output ("CFCT json files: {0}" -f (Get-ChildItem $jsonDir -Filter *.json -ErrorAction SilentlyContinue).Count)

# 2) USDA FoodData Central - SR Legacy (public domain, per-100 g)
$usdaZip = Join-Path $root 'sr_legacy.zip'
if (-not (Test-Path $usdaZip)) {
  Write-Output '--- downloading USDA SR Legacy (6 MB) ---'
  try {
    Invoke-WebRequest -Uri 'https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip' `
      -OutFile $usdaZip -TimeoutSec 900 -UseBasicParsing
  } catch {
    Write-Output "USDA download failed: $($_.Exception.Message)"
  }
}
if (Test-Path $usdaZip) {
  Write-Output ("USDA zip: {0:N0} bytes" -f (Get-Item $usdaZip).Length)
  $usdaDir = Join-Path $root 'usda'
  if (-not (Test-Path (Join-Path $usdaDir 'food.csv'))) {
    Expand-Archive -Path $usdaZip -DestinationPath $usdaDir -Force
  }
  Get-ChildItem $usdaDir -Filter *.csv | Select-Object -First 8 | ForEach-Object { Write-Output ("  csv: {0}" -f $_.Name) }
}
Write-Output 'DONE'
