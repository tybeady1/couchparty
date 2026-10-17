# Version control and phone updates

Git tracks source locally on `main`. The first commit records the existing app; earlier changes cannot be reconstructed as separate commits. A remote backup and downloadable releases require a configured host.

## Version and signing

`version.properties` is the single source for both direct SDK and Gradle builds. Increment `versionCode` for EVERY distributed update, and change `versionName` to the desired semantic version. Never distribute different APKs under the same release version.

`.signing/fairway.jks` preserves the signing identity used by the existing direct-build APK. It is ignored by Git, as are builds and release output. Back it up privately: losing it prevents seamless updates to existing installations. This is the existing development key/password, retained for this personal testing channel; it is not a production store-release signing setup. Both build paths now use the same key. Old Gradle-signed installations made with another key cannot update with this channel.

The app reads a default HTTPS feed from `app/src/main/assets/update-url.txt` if configured, or a saved URL in Settings > App version & updates. There are no background checks or silent installs. Downloads stay in app-private storage and only the verified APK is shared read-only with Android's installer. Permission to install from Fairway requires one-time user action in Android settings.

## GitHub Releases

Use a PUBLIC releases repository for unauthenticated phone downloads. Source can stay in a separate private repository. Never put a GitHub token in the Android app or feed. Hosting the project source publicly requires a deliberate choice by its owner.

1. Create/select the GitHub repository and configure `git remote add origin <repository-url>` for source if appropriate. Push source only to the intended repository.
2. Set the new code/name in `version.properties`; update `CHANGELOG.md` (or pass a short notes file).
3. Prepare artifacts, replacing OWNER/REPO with the release repository:

```powershell
./scripts/prepare-release.ps1 `
  -AssetBaseUrl 'https://github.com/OWNER/REPO/releases/download/v1.1.0' `
  -FeedUrl 'https://github.com/OWNER/REPO/releases/latest/download/update.json' `
  -Sdk D:\Android_SDK -JavaHome D:\Androids\jbr
```

4. Review/test the APK, commit the version/feed changes and tag the exact source `v1.1.0`. Create a non-draft, non-prerelease GitHub release and attach BOTH generated files from `release-output/v1.1.0`: `Fairway-1.1.0.apk` and `update.json`. The APK URL is version-specific so an older manifest never points at a newer APK. Publish only when both assets are present.
5. Install this updater-enabled APK manually once on existing phones. Thereafter open Settings > App version & updates > Check > Download > Install. Android confirms each install. If no default URL was embedded, paste the feed URL once.

Any HTTPS file host works with the same two files. Upload the APK first and replace the feed last. The prepare script only builds local artifacts; it does not publish, create a remote or tag automatically.

## Validation and limits

The updater rejects HTTP URLs/redirects, oversized payloads, malformed metadata, checksum mismatch, wrong package, non-newer versions, and a different signing certificate. HTTPS uses Android's normal certificate validation. Native installer remains the final authority for installation. Interrupted downloads are retried manually; no resume or automatic install. A feed/server can be unavailable; the installed app keeps working.

Protocol references: [GitHub release asset links](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases), [Android install-source permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
