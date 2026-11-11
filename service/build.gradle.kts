plugins {
    java
}

dependencies {
    implementation(project(":model"))
    implementation(project(":store"))
    implementation(project(":fintx"))
    implementation(project(":config"))
    implementation("org.springframework.boot:spring-boot-starter:${property("springBootVersion")}")
    implementation("org.springframework.boot:spring-boot-starter-json:${property("springBootVersion")}")
}
