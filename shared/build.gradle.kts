import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val bytedecoArchitecture = when (System.getProperty("os.arch").lowercase()) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "arm64"
    else -> error("Unsupported desktop architecture: " + System.getProperty("os.arch"))
}
val bytedecoPlatform = when {
    System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "windows-$bytedecoArchitecture"
    System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "macosx-$bytedecoArchitecture"
    System.getProperty("os.name").contains("Linux", ignoreCase = true) -> "linux-$bytedecoArchitecture"
    else -> error("Unsupported desktop operating system: " + System.getProperty("os.name"))
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }
    
    jvm()
    
    android {
       namespace = "com.sunnychung.application.easytransfer.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.core.ktx)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.zxing.core)
            implementation(libs.zxing.cpp.android)
        }
        jvmMain.dependencies {
            implementation(libs.appdirs)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
            implementation(libs.camera.core)
            implementation("org.bytedeco:javacv:${libs.versions.javacv.get()}") {
                isTransitive = false
            }
            implementation("org.bytedeco:javacpp:${libs.versions.javacv.get()}") {
                isTransitive = false
            }
            implementation("org.bytedeco:javacpp:${libs.versions.javacv.get()}:$bytedecoPlatform") {
                isTransitive = false
            }
            implementation("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}") {
                isTransitive = false
            }
            implementation("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$bytedecoPlatform") {
                isTransitive = false
            }
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonMain.dependencies {
            api(project(":decimen-optical-transfer-kmp"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.fileKit.dialogsCompose)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

configurations.configureEach {
    exclude(group = "io.coil-kt.coil3")
    exclude(group = "org.bytedeco", module = "javacv-platform")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
}

sqldelight {
    databases {
        create("HistoryDatabase") {
            packageName.set("com.sunnychung.application.easytransfer.db")
        }
    }
}

compose.resources {
    packageOfResClass = "com.sunnychung.application.easytransfer.generated.resources"
}
