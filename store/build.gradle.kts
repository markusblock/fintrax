plugins {
    java
}

dependencies {
    implementation(project(":model"))
    implementation("org.eclipse.store:storage-embedded:${property("eclipsestoreVersion")}")
    implementation("org.eclipse.store:storage-embedded-configuration:${property("eclipsestoreVersion")}")
}
