$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath $projectRoot
try {
    Write-Host 'Checking rest Entity, approval Service and HTTP/Security (no database)...'

    & (Join-Path $projectRoot 'mvnw.cmd') -B -ntp '-Dtest=WorkoutDetailRestTest,WorkoutRestApprovalServiceTest,WorkoutRestApprovalControllerTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Workout rest tests failed. Maven exit code: $LASTEXITCODE"
    }

    $reportNames = @(
        'vn.id.fitnessai.entity.WorkoutDetailRestTest',
        'vn.id.fitnessai.service.WorkoutRestApprovalServiceTest',
        'vn.id.fitnessai.controller.WorkoutRestApprovalControllerTest'
    )
    foreach ($reportName in $reportNames) {
        $reportPath = Join-Path $projectRoot "target\surefire-reports\$reportName.txt"
        if (-not (Test-Path -LiteralPath $reportPath)) {
            throw "Maven finished but the expected report was not found: $reportName"
        }
        Get-Content -LiteralPath $reportPath
    }
    Write-Host '[PASS] Workout rest Entity, Service and HTTP tests completed.' -ForegroundColor Green
}
finally {
    Pop-Location
}
