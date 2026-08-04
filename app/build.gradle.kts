plugins {
    java
    application
    id("org.openjfx.javafxplugin")
    id("org.springframework.boot")
}

javafx {
    version = property("javafxVersion") as String
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":store"))
    implementation(project(":service"))
    implementation(project(":ui"))
    implementation(project(":config"))
    implementation(project(":fintx"))
    implementation("org.springframework.boot:spring-boot-starter:${property("springBootVersion")}")
    implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
}

application {
    mainClass.set("org.fintrax.app.FintraxApplication")
}
