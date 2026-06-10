import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    jacoco
}

apply(from = rootProject.file("gradle/android-release-signing.gradle"))

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

private data class AndroidNdkToolchain(
    val ndkHome: File,
    val clang: File,
    val clangxx: File,
    val ar: File,
)

private val versionTokenRegex = Regex("""\d+|\D+""")

private fun compareVersionNames(
    left: String,
    right: String,
): Int {
    val leftTokens = versionTokenRegex.findAll(left).map { it.value }.toList()
    val rightTokens = versionTokenRegex.findAll(right).map { it.value }.toList()
    for (index in 0 until maxOf(leftTokens.size, rightTokens.size)) {
        val leftToken = leftTokens.getOrNull(index) ?: return -1
        val rightToken = rightTokens.getOrNull(index) ?: return 1
        val leftIsNumber = leftToken.all { it.isDigit() }
        val rightIsNumber = rightToken.all { it.isDigit() }
        val comparison =
            if (leftIsNumber && rightIsNumber) {
                val leftNumber = leftToken.trimStart('0').ifEmpty { "0" }
                val rightNumber = rightToken.trimStart('0').ifEmpty { "0" }
                leftNumber.length.compareTo(rightNumber.length).takeIf { it != 0 }
                    ?: leftNumber.compareTo(rightNumber)
            } else {
                leftToken.compareTo(rightToken)
            }
        if (comparison != 0) {
            return comparison
        }
    }
    return left.length.compareTo(right.length)
}

private fun latestNdkUnder(candidate: File): File? =
    if (candidate.isDirectory) {
        candidate
            .listFiles { file -> file.isDirectory }
            ?.maxWithOrNull { left, right -> compareVersionNames(left.name, right.name) }
    } else {
        null
    }

private fun nonBlankEnvironmentValue(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

private fun androidNdkToolchain(apiLevel: Int): AndroidNdkToolchain {
    val explicitNdkHome =
        nonBlankEnvironmentValue("ANDROID_NDK_HOME")
            ?: nonBlankEnvironmentValue("ANDROID_NDK_ROOT")
    val discoveredNdkHome =
        explicitNdkHome?.let(::File)
            ?: listOfNotNull(
                nonBlankEnvironmentValue("ANDROID_HOME")?.let { File(it, "ndk") },
                nonBlankEnvironmentValue("ANDROID_SDK_ROOT")?.let { File(it, "ndk") },
                nonBlankEnvironmentValue("HOME")?.let { File(it, "Android/Sdk/ndk") },
                File("/opt/android-sdk/ndk"),
                File("/usr/local/android-sdk/ndk"),
            ).firstNotNullOfOrNull(::latestNdkUnder)

    if (discoveredNdkHome == null || !discoveredNdkHome.isDirectory) {
        throw GradleException("Android NDK not found; set ANDROID_NDK_HOME for aarch64 build")
    }

    val toolchain = File(discoveredNdkHome, "toolchains/llvm/prebuilt/linux-x86_64/bin")
    val clang = File(toolchain, "aarch64-linux-android$apiLevel-clang")
    val clangxx = File(toolchain, "aarch64-linux-android$apiLevel-clang++")
    val ar = File(toolchain, "llvm-ar")

    if (!clang.canExecute() || !clangxx.canExecute() || !ar.canExecute()) {
        throw GradleException("Android NDK toolchain is incomplete under $toolchain")
    }

    return AndroidNdkToolchain(
        ndkHome = discoveredNdkHome,
        clang = clang,
        clangxx = clangxx,
        ar = ar,
    )
}

private fun Exec.injectAndroidNdkToolchain(apiLevel: Int = 33) {
    val toolchain = androidNdkToolchain(apiLevel)
    environment("ANDROID_NDK_HOME", toolchain.ndkHome.absolutePath)
    environment(
        "CC_aarch64_linux_android",
        nonBlankEnvironmentValue("CC_aarch64_linux_android") ?: toolchain.clang.absolutePath,
    )
    environment(
        "CXX_aarch64_linux_android",
        nonBlankEnvironmentValue("CXX_aarch64_linux_android") ?: toolchain.clangxx.absolutePath,
    )
    environment(
        "AR_aarch64_linux_android",
        nonBlankEnvironmentValue("AR_aarch64_linux_android") ?: toolchain.ar.absolutePath,
    )
    environment(
        "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER",
        nonBlankEnvironmentValue("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER") ?: toolchain.clang.absolutePath,
    )
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

val nativeProjectDirectory = rootProject.layout.projectDirectory.dir("native")
val bridgeCoreCargoInputs =
    fileTree(nativeProjectDirectory.asFile) {
        include("Cargo.toml")
        include("Cargo.lock")
        include("iris-bridge-core/**")
        include("iris-bridge-jni/**")
        exclude("target/**")
    }
val androidBridgeCoreLibrary =
    nativeProjectDirectory.file("target/aarch64-linux-android/release/libiris_bridge_core.so")
val hostBridgeCoreLibrary =
    nativeProjectDirectory.file("target/release/libiris_bridge_core.so")

val cargoBridgeCoreAndroid =
    tasks.register<Exec>("cargoBridgeCoreAndroid") {
        workingDir = nativeProjectDirectory.asFile
        commandLine(
            "cargo",
            "build",
            "--release",
            "--target",
            "aarch64-linux-android",
            "-p",
            "iris-bridge-jni",
        )
        injectAndroidNdkToolchain()
        inputs
            .files(bridgeCoreCargoInputs)
            .withPropertyName("bridgeCoreCargoInputs")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(androidBridgeCoreLibrary).withPropertyName("androidBridgeCoreLibrary")
        // toolchain/RUSTFLAGS 변경은 Gradle input에 안 잡히므로 up-to-date 판정을 cargo 증분에 위임한다.
        outputs.upToDateWhen { false }
    }

val syncBridgeCoreJniLibs =
    tasks.register<Copy>("syncBridgeCoreJniLibs") {
        dependsOn(cargoBridgeCoreAndroid)
        from(androidBridgeCoreLibrary)
        into(layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a"))
    }

tasks
    .matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach {
        dependsOn(syncBridgeCoreJniLibs)
    }

val cargoBridgeCoreHost =
    tasks.register<Exec>("cargoBridgeCoreHost") {
        workingDir = nativeProjectDirectory.asFile
        commandLine("cargo", "build", "--release", "-p", "iris-bridge-jni")
        inputs
            .files(bridgeCoreCargoInputs)
            .withPropertyName("bridgeCoreCargoInputs")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(hostBridgeCoreLibrary).withPropertyName("hostBridgeCoreLibrary")
        // toolchain/RUSTFLAGS 변경은 Gradle input에 안 잡히므로 up-to-date 판정을 cargo 증분에 위임한다.
        outputs.upToDateWhen { false }
    }

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
    compileOnly(libs.libxposed.api)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.libxposed.api)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.sqlite.jdbc)
}

tasks.withType<Test>().configureEach {
    dependsOn(cargoBridgeCoreHost)
    // host .so 내용이 Test input에 안 잡히면 Rust 변경 후 테스트가 stale green이 된다.
    inputs
        .file(hostBridgeCoreLibrary)
        .withPropertyName("hostBridgeCoreLibrary")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("java.library.path", nativeProjectDirectory.file("target/release").asFile.absolutePath)
    // libiris_bridge_core.so는 JVM당 한 classloader에서만 로드 가능하다. Robolectric 샌드박스
    // classloader와 일반 JUnit app classloader가 한 JVM을 공유하면 "already loaded in another
    // classloader" UnsatisfiedLinkError가 난다. 테스트 클래스마다 JVM을 분리해 회피한다.
    setForkEvery(1)
    // Unit tests that are not lease-policy tests exercise leaseless image sends;
    // accept the legacy raw path by default so they stay green. Lease-policy tests
    // inject an explicit BridgeImageLeaseVerifier and ignore this env.
    environment("IRIS_BRIDGE_ACCEPT_LEGACY_IMAGE_PATH", "1")
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
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
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
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
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
