pluginManagement {
    plugins {
        id("org.springframework.boot") version providers.gradleProperty("springBootVersion").get()
    }
}

rootProject.name = "fintrax"

include("app")
include("ui")
include("service")
include("model")
include("store")
include("fintx")
include("config")
include("testkit")
