plugins {
    java
}

dependencies {
    implementation(project(":model"))
    implementation(project(":fintx"))
    implementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
}
