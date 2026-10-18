import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
    java
    jacoco
    id("jacoco-report-aggregation")
    id("org.sonarqube") version "7.5.0.8588"
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
    id("org.springframework.boot") apply false
}

val jacocoVersion = "0.8.15"

extensions.configure<JacocoPluginExtension> {
    toolVersion = jacocoVersion
}

allprojects {
    group = property("group") as String
    version = property("version") as String

    repositories {
        mavenCentral()
    }
}

dependencies {
    jacocoAggregation(project(":app"))
    jacocoAggregation(project(":testkit"))
}

tasks.named<JacocoReport>("testCodeCoverageReport") {
    reports {
        xml.required.set(true)
    }
}

tasks.named("sonar") {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
        annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")
        testCompileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
        testAnnotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

        implementation("org.slf4j:slf4j-api:${property("slf4jVersion")}")

        testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:${property("junitPlatformVersion")}")
        testRuntimeOnly("ch.qos.logback:logback-classic:${property("logbackVersion")}")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = jacocoVersion
    }

    extensions.configure<org.sonarqube.gradle.SonarExtension> {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                rootProject.layout.buildDirectory
                    .file("reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml")
                    .get().asFile.absolutePath
            )
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "markusblock_fintrax")
        property("sonar.organization", "markusblock")
    }
}
