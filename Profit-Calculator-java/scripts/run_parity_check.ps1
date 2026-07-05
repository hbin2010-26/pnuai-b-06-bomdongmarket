param(
    [string]$PythonExe = "C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe",
    [string]$PythonProject = "",
    [string]$JavaProject = "",
    [string]$JavaExe = "C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
)

if ([string]::IsNullOrWhiteSpace($JavaProject)) {
    $JavaProject = Split-Path -Parent $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($PythonProject)) {
    $workspace = Split-Path -Parent $JavaProject
    $PythonProject = Join-Path $workspace "Profit_Calculator"
}

$cases = @(
    @{ space = 1; crop = "LETTUCE" },
    @{ space = 1; crop = "BASIL" },
    @{ space = 1; crop = "SPROUT_GINSENG" },
    @{ space = 2; crop = "LETTUCE" },
    @{ space = 2; crop = "BASIL" },
    @{ space = 2; crop = "SPROUT_GINSENG" },
    @{ space = 3; crop = "LETTUCE" },
    @{ space = 3; crop = "BASIL" },
    @{ space = 3; crop = "SPROUT_GINSENG" }
)

$reportDir = Join-Path $JavaProject "reports"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$report = Join-Path $reportDir "parity-report.md"
$lines = @("# Parity Report", "", "| Case | Result |", "|---|---|")

Push-Location $JavaProject
try {
    foreach ($case in $cases) {
        $space = $case.space
        $crop = $case.crop
        $pythonCode = "import json, sys; sys.path.insert(0, r'$PythonProject'); from main import build_result; print(json.dumps(build_result($space, '$crop'), ensure_ascii=False, indent=2))"
        $pythonJson = & $PythonExe -c $pythonCode 2>$null
        $javaJson = & $JavaExe -cp "target\classes" "com.farmbroker.profit.ProfitCalculatorApplication" --space-id $space --crop-code $crop 2>$null
        $same = (($pythonJson -join "`n").Replace("`r`n", "`n") -eq ($javaJson -join "`n").Replace("`r`n", "`n"))
        $result = if ($same) { "PASS" } else { "FAIL" }
        $lines += "| space $space / $crop | $result |"
    }
}
finally {
    Pop-Location
}

$lines | Set-Content -LiteralPath $report -Encoding UTF8
Write-Output "Report written to $report"
