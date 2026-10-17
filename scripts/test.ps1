$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $modelSources = @('BallType','Shot','BallTracker','LiveShotDetector','GolfGame') | ForEach-Object { "app/src/main/java/com/fairway/monitor/$_.java" }
    $testSources = @(Get-ChildItem tests -Filter '*Test.java' | ForEach-Object FullName)
    & javac -d build/tests @modelSources @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    foreach ($test in @('ShotTest','BallTrackerTest','LiveShotDetectorTest','GolfGameTest')) {
        & java -cp build/tests $test
        if ($LASTEXITCODE -ne 0) { throw "$test failed." }
    }
} finally { Pop-Location }
