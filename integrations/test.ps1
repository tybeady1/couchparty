$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    New-Item -ItemType Directory -Force build/integration-tests | Out-Null
    & javac -d build/integration-tests app/src/main/java/com/fairway/monitor/BallType.java app/src/main/java/com/fairway/monitor/SimProtocol.java app/src/main/java/com/fairway/monitor/SimTcp.java integrations/SimIntegrationTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Integration test compilation failed' }
    & java -cp build/integration-tests SimIntegrationTest
    if ($LASTEXITCODE -ne 0) { throw 'Integration tests failed' }
} finally { Pop-Location }
