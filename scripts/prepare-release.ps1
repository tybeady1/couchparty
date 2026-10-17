param(
    [Parameter(Mandatory=$true)][string]$AssetBaseUrl,
    [Parameter(Mandatory=$true)][string]$FeedUrl,
    [string]$NotesFile = 'CHANGELOG.md',
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
foreach ($value in @($AssetBaseUrl, $FeedUrl)) {
    $uri = [Uri]$value
    if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.UserInfo) { throw 'Release URLs must use HTTPS without embedded credentials.' }
}
$version = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $root 'version.properties')))
[IO.File]::WriteAllText((Join-Path $root 'app/src/main/assets/update-url.txt'), $FeedUrl, [Text.UTF8Encoding]::new($false))
& (Join-Path $PSScriptRoot 'build.ps1') -Sdk $Sdk -JavaHome $JavaHome
$destination = Join-Path $root ('release-output/v' + $version.versionName)
New-Item -ItemType Directory -Force $destination | Out-Null
$name = 'Fairway-' + $version.versionName + '.apk'
$apk = Join-Path $destination $name
Copy-Item -LiteralPath (Join-Path $root 'build/Fairway-debug.apk') -Destination $apk
$notesPath = if ([IO.Path]::IsPathRooted($NotesFile)) { $NotesFile } else { Join-Path $root $NotesFile }
$manifest = [ordered]@{
    packageName = 'com.fairway.monitor'
    versionCode = [int]$version.versionCode
    versionName = $version.versionName
    apkUrl = $AssetBaseUrl.TrimEnd('/') + '/' + $name
    sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    notes = [IO.File]::ReadAllText($notesPath)
}
$json = $manifest | ConvertTo-Json
if ([Text.Encoding]::UTF8.GetByteCount($json) -gt 65536) { throw 'Release notes are too large.' }
[IO.File]::WriteAllText((Join-Path $destination 'update.json'), $json, [Text.UTF8Encoding]::new($false))
Write-Output "Prepared $destination. Upload BOTH files to your release host. Nothing has been published."
