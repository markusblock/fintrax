plugins {
    java
}

dependencies {
    implementation(project(":model"))
    implementation("com.github.hbci4j:hbci4j-core:${property("hbci4javaVersion")}")
}
