import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ksFile = rootProject.file("signing.properties")
val props = Properties()
if (ksFile.canRead()) {
    props.load(FileInputStream(ksFile))
    android.signingConfigs.create("sign").apply {
        storeFile = file(props["KEYSTORE_FILE"] as String)
        storePassword = props["KEYSTORE_PASSWORD"] as String
        keyAlias = props["KEYSTORE_ALIAS"] as String
        keyPassword = props["KEYSTORE_ALIAS_PASSWORD"] as String
    }
} else {
    android.signingConfigs.create("sign").apply {
        storeFile = android.signingConfigs.getByName("debug").storeFile
        storePassword = android.signingConfigs.getByName("debug").storePassword
        keyAlias = android.signingConfigs.getByName("debug").keyAlias
        keyPassword = android.signingConfigs.getByName("debug").keyPassword
    }
}

val gitCommitCountInt = rootProject.extra["gitCommitCountInt"] as Int
val baseVersionName = "1.4.2"
val versionNameSuffixProvider = providers.gradleProperty("versionNameSuffix")
    .orElse("-alpha$gitCommitCountInt")
val finalVersionNameProvider = versionNameSuffixProvider.map { suffix ->
    baseVersionName + suffix
}

android {
    namespace = "yangfentuozi.batteryrecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "yangfentuozi.batteryrecorder"
        minSdk = 31
        targetSdk = 36
        versionCode = gitCommitCountInt
        versionName = finalVersionNameProvider.get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sign")
        }
        debug {
            signingConfig = signingConfigs.getByName("sign")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val outputName = if (buildType.name == "release") {
                "batteryrecorder-v${versionName}.apk"
            } else {
                "batteryrecorder-v${versionName}-${name}.apk"
            }
            (this as BaseVariantOutputImpl).outputFileName = outputName
            assembleProvider.get().doLast {
                val outDir = File(rootDir, "out")
                val mappingDir = File(outDir, "mapping").absolutePath
                val apkDir = File(outDir, "apk").absolutePath

                if (buildType.isMinifyEnabled) {
                    copy {
                        from(mappingFileProvider.get())
                        into(mappingDir)
                        rename { _ -> "mapping-${versionName}.txt" }
                    }
                    copy {
                        from(outputFile)
                        into(apkDir)
                    }
                }
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

tasks.register("printVersionMetadata") {
    group = "help"
    description = "打印 app 最终版本信息，供 CI 使用"
    doLast {
        val finalVersionName = finalVersionNameProvider.get()
        println("versionName=$finalVersionName")
        println("versionCode=$gitCommitCountInt")
        println("releaseTag=v$finalVersionName")
        println("releaseTitle=v$finalVersionName")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":server"))

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.capsule)
    implementation(libs.commonmark)
    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
