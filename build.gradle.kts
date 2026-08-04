plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
    id("org.springframework.boot") version "3.5.15" apply false
}

allprojects {
    group = property("group") as String
    version = property("version") as String

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

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
        testRuntimeOnly("ch.qos.logback:logback-classic:${property("logbackVersion")}")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
