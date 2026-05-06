import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    jacoco
}

android {
    namespace = "party.qwer.iris.bridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "party.qwer.iris.bridge"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("PrivateApi", "SdCardPath")
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi",
            )
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

private fun registerAssembleOutputCopyTask(variantName: String) {
    val assembleTaskName = "assemble${variantName.replaceFirstChar { it.uppercase() }}"
    val copyTaskName = "sync${variantName.replaceFirstChar { it.uppercase() }}ApkToOutput"
    val copyTask =
        tasks.register<Copy>(copyTaskName) {
            from(layout.buildDirectory.dir("outputs/apk/$variantName"))
            include("*.apk")
            into(rootProject.layout.projectDirectory.dir("output"))
            rename { "IrisBridge-$variantName.apk" }
        }
    tasks.matching { it.name == assembleTaskName }.configureEach {
        finalizedBy(copyTask)
    }
}

registerAssembleOutputCopyTask("debug")
registerAssembleOutputCopyTask("release")

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("ktlintCheck")
    dependsOn("lint")
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation(project(":imagebridge-protocol"))
    compileOnly(libs.xposed.api)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.org.json)
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val jacocoDebugExcludes =
    listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/generated/**",
    )

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(jacocoDebugExcludes)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                exclude(jacocoDebugExcludes)
            },
        ),
    )
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec", "**/*.ec") })
}

tasks.register<JacocoCoverageVerification>("jacocoDebugUnitTestCoverageVerification") {
    dependsOn("jacocoDebugUnitTestReport")

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(jacocoDebugExcludes)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                exclude(jacocoDebugExcludes)
            },
        ),
    )
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec", "**/*.ec") })

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.05".toBigDecimal()
            }
        }
    }
}
