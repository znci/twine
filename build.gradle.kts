plugins {
    `maven-publish`
    kotlin("jvm") version "2.1.10"
}

group = "dev.znci"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            group
            artifactId = "twine"
            version
        }
    }

    repositories {
        mavenLocal()
    }
}
