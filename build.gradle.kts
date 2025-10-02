import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.22"
  // https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#ide-configuration
  id("org.jetbrains.intellij") version "1.17.1"
}

repositories {
  mavenCentral()
}

group = "com.stasmarkin"
version = "1.0.5-SNAPSHOT"

intellij {
  type.set("IC")
  version.set("2024.1")
  updateSinceUntilBuild.set(false)
}

tasks.withType<PublishPluginTask> {
  token.set(System.getProperty("PUBLISH_TOKEN"))
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
  kotlinOptions.freeCompilerArgs = kotlinOptions.freeCompilerArgs + listOf("-Xjvm-default=all")
}