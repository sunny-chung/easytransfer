import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.sunnychung.application.easytransfer.MainKt"

        buildTypes.release.proguard {
            optimize.set(false)
            obfuscate.set(false)
            configurationFiles.from(project.file("rules.pro"))
        }

        nativeDistributions {
            modules("java.sql")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EasyTransfer"
            vendor = "Sunny Chung"
            copyright = "© 2026 Sunny Chung"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/icons/AppIcon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/transfer-icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/transfer-icon.png"))
            }
        }
    }
}
