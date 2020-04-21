import com.moowork.gradle.node.npm.NpmTask

application {
  mainClassName = "dev.fuelyour.service.MyServiceKt"
}

plugins {
  kotlin("jvm") version "1.3.50"
  id("com.moowork.node") version "1.3.1"
  id("com.palantir.docker") version "0.22.1"
  id("com.palantir.docker-run") version "0.22.1"
  application
}

repositories {
  mavenLocal()
  mavenCentral()
  jcenter()
}

group = "dev.fuelyour"
version = "1.0-SNAPSHOT"

val vertxVersion = "3.8.1"

dependencies {
  implementation("dev.fuelyour:vertx-kuickstart-core:0.0.2")

  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))

  implementation("io.vertx:vertx-core:$vertxVersion")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
  implementation("io.vertx:vertx-unit:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
  implementation("io.vertx:vertx-pg-client:$vertxVersion")
  implementation("io.vertx:vertx-config:$vertxVersion")
  implementation("io.vertx:vertx-auth-jwt:$vertxVersion")
  implementation("org.flywaydb:flyway-core:6.0.0")
  implementation("postgresql:postgresql:9.1-901-1.jdbc4")
  implementation("org.koin:koin-core:2.0.1")

  implementation("org.reflections:reflections:0.9.11")
  implementation("org.slf4j:slf4j-jdk14:1.7.28")
  implementation("io.vertx:vertx-web-client:$vertxVersion")

  implementation("org.apache.commons:commons-collections4:4.0")

  testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
}

tasks {
  test {
    useJUnitPlatform { }
  }
  compileKotlin {
    kotlinOptions.jvmTarget = "11"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "11"
  }
}

node {
  version = "10.16.3"
  npmVersion = "6.9.0"
  download = true
  nodeModulesDir = File("src/main/frontend")
}

val buildFrontend by tasks.creating(NpmTask::class) {
  setArgs(listOf("run", "build"))
  dependsOn("npmInstall")
}

val copyToWebRoot by tasks.creating(Copy::class) {
  from("src/main/frontend/build")
  destinationDir = File("src/main/resources/webroot")
  dependsOn(buildFrontend)
}

val processResources by tasks.getting(ProcessResources::class) {
  dependsOn(copyToWebRoot)
}
