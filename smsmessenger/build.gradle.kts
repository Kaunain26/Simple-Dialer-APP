import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        buildConfigField("String", "APPLICATION_ID", "\"${project.libs.versions.app.version.appId.get()}\"")
        buildConfigField("int", "VERSION_CODE", project.libs.versions.app.version.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${project.libs.versions.app.version.versionName.get()}\"")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()
    }

    namespace = "com.simplemobiletools.smsmessenger"

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

}

dependencies {
    implementation(libs.simple.tools.commons)
    implementation(libs.eventbus)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.android.smsmms)
    implementation(libs.shortcut.badger)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    //Dynamic layout
    implementation(libs.ssp.android)
    implementation(libs.sdp.android)
}
