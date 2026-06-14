import org.gradle.api.tasks.Exec
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    jacoco
}

val generatedBridgeProtocolDir = layout.buildDirectory.dir("generated/source/bridgeProtocolContract/main/kotlin")
val generatedBridgeProtocolFile =
    generatedBridgeProtocolDir.map {
        it.file("party/qwer/iris/generated/GeneratedBridgeProtocolContract.kt")
    }

val generateBridgeProtocolContract =
    tasks.register<Exec>("generateBridgeProtocolContract") {
        val generator = rootProject.layout.projectDirectory.file("scripts/generate-bridge-kotlin-contract.py")
        val constants = rootProject.layout.projectDirectory.file("native/iris-bridge-core/src/protocol/constants.rs")
        val actions = rootProject.layout.projectDirectory.file("native/iris-bridge-core/src/protocol/actions.rs")

        inputs.file(generator).withPropertyName("generator")
        inputs.file(constants).withPropertyName("bridgeProtocolConstants")
        inputs.file(actions).withPropertyName("bridgeProtocolActions")
        outputs.file(generatedBridgeProtocolFile).withPropertyName("generatedBridgeProtocolContract")

        commandLine(
            "python3",
            generator.asFile.absolutePath,
            "--repo-root",
            rootProject.layout.projectDirectory.asFile.absolutePath,
            "--output",
            generatedBridgeProtocolFile.get().asFile.absolutePath,
        )
    }

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
    sourceSets {
        named("main") {
            kotlin.srcDir(generatedBridgeProtocolDir)
        }
    }
}

tasks
    .matching {
        it.name in
            setOf(
                "compileKotlin",
                "compileTestKotlin",
                "ktlintCheck",
                "ktlintMainSourceSetCheck",
                "runKtlintCheckOverMainSourceSet",
            )
    }.configureEach {
        dependsOn(generateBridgeProtocolContract)
    }

tasks.test {
    dependsOn(generateBridgeProtocolContract)
    useJUnit()
}

ktlint {
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.org.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.org.json)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val jacocoMainExcludes =
    listOf(
        "**/generated/**",
        "**/GeneratedWireConstants*",
        "**/GeneratedBridgeProtocolContract*",
    )

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java", generatedBridgeProtocolDir))
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(jacocoMainExcludes)
            },
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(jacocoMainExcludes)
            },
        ),
    )
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec", "**/*.ec") })
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java", generatedBridgeProtocolDir))
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(jacocoMainExcludes)
            },
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(jacocoMainExcludes)
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
