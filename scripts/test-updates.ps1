$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    New-Item -ItemType Directory -Force build/update-tests | Out-Null
    if (-not (Test-Path build/update-test.jks)) {
        & keytool -genkeypair -keystore build/update-test.jks -storetype JKS -storepass testpass -keypass testpass -alias test -dname 'CN=localhost' -ext 'SAN=dns:localhost' -keyalg RSA -validity 3650 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Test TLS key generation failed' }
    }
    & javac --add-modules jdk.httpserver -d build/update-tests app/src/main/java/com/fairway/monitor/UpdateDownload.java tests/UpdateDownloadTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & java --add-modules jdk.httpserver -cp build/update-tests UpdateDownloadTest
    if ($LASTEXITCODE -ne 0) { throw 'Update tests failed' }
} finally { Pop-Location }
