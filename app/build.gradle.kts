plugins { id("com.android.application") }
android {
    namespace = "com.fairway.monitor"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fairway.monitor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
