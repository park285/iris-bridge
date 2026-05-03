import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    jacoco
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.test {
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
    toolVersion = "0.8.13"
}

val jacocoMainExcludes =
    listOf(
        "**/generated/**",
        "**/GeneratedWireConstants*",
    )

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
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

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
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
