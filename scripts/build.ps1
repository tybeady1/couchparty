param([string]$Sdk = $env:ANDROID_HOME, [string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Sdk) { throw 'Set ANDROID_HOME or pass -Sdk with your Android SDK directory.' }
$bt = Join-Path $Sdk 'build-tools\34.0.0'
$android = Join-Path $Sdk 'platforms\android-34\android.jar'
$out = Join-Path $root 'build'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
if ($JavaHome) { $env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH; $env:JAVA_HOME = $JavaHome }
New-Item -ItemType Directory -Force $out,$classes,$dex | Out-Null
function Run([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}
$sources = @(Get-ChildItem (Join-Path $root 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
Run 'javac' (@('-encoding','UTF-8','--release','8','-classpath',$android,'-d',$classes) + $sources)
Run 'jar' @('cf',(Join-Path $out 'classes.jar'),'-C',$classes,'.')
Run (Join-Path $bt 'd8.bat') @('--lib',$android,'--min-api','26','--output',$dex,(Join-Path $out 'classes.jar'))
$manifest = [System.IO.File]::ReadAllText((Join-Path $root 'app\src\main\AndroidManifest.xml')).Replace('<manifest ', '<manifest package="com.fairway.monitor" android:versionCode="1" android:versionName="1.0" ').Replace('<application ', '<application android:debuggable="true" ')
[System.IO.File]::WriteAllText((Join-Path $out 'AndroidManifest.xml'), $manifest)
Run (Join-Path $bt 'aapt.exe') @('package','-f','-M',(Join-Path $out 'AndroidManifest.xml'),'-S',(Join-Path $root 'app\src\main\res'),'-A',(Join-Path $root 'app\src\main\assets'),'-I',$android,'-F',(Join-Path $out 'unsigned.apk'))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path $out 'unsigned.apk'), 'Update')
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $dex 'classes.dex'),'classes.dex') | Out-Null } finally { $zip.Dispose() }
Run (Join-Path $bt 'zipalign.exe') @('-f','4',(Join-Path $out 'unsigned.apk'),(Join-Path $out 'aligned.apk'))
$key = Join-Path $out 'debug.jks'
if (-not (Test-Path $key)) { Run 'keytool' @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Fairway,C=US','-keyalg','RSA','-validity','10000') }
Run (Join-Path $bt 'apksigner.bat') @('sign','--ks',$key,'--ks-pass','pass:android','--out',(Join-Path $out 'Fairway-debug.apk'),(Join-Path $out 'aligned.apk'))
Run (Join-Path $bt 'apksigner.bat') @('verify',(Join-Path $out 'Fairway-debug.apk'))
Write-Output "APK: $out\Fairway-debug.apk"
