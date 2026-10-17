import java.util.Properties

plugins { id("com.android.application") }
val releaseVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
android {
    namespace = "com.fairway.monitor"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fairway.monitor"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".signing/fairway.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
