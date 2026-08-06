plugins {
    java
    id("org.openjfx.javafxplugin")
}

javafx {
    version = property("javafxVersion") as String
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":service"))
    implementation(project(":store"))
    implementation(project(":config"))
    implementation(project(":fintx"))
    implementation("org.springframework:spring-context:${property("springVersion")}")
    implementation("io.github.mkpaz:atlantafx-base:${property("atlantafxVersion")}")

    testImplementation(project(":testkit"))
    testImplementation("org.testfx:testfx-junit5:${property("testfxVersion")}")
    testImplementation("org.hamcrest:hamcrest:2.1")
}

tasks.withType<Test> {
    jvmArgs(
        "--add-opens", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}
