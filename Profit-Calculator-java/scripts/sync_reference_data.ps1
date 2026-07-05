param(
    [string]$PythonProject = "",
    [string]$JavaProject = ""
)

if ([string]::IsNullOrWhiteSpace($JavaProject)) {
    $JavaProject = Split-Path -Parent $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($PythonProject)) {
    $workspace = Split-Path -Parent $JavaProject
    $PythonProject = Join-Path $workspace "Profit_Calculator"
}

$source = Join-Path $PythonProject "data"
$target = Join-Path $JavaProject "src\main\resources\data"

New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem -LiteralPath $source -Filter *.csv | Copy-Item -Destination $target -Force

Write-Output "Reference CSV files synced from $source to $target"
