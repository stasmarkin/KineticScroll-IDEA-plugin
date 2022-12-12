import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.7.21"
  id("org.jetbrains.intellij") version "1.10.1"
}

repositories {
  mavenCentral()
}

group = "com.stasmarkin"
version = "1.0.3"

intellij {
  type.set("IC")
  version.set("2022.3")
  updateSinceUntilBuild.set(false)
}

tasks.withType<PublishPluginTask> {
  token.set(System.getProperty("PUBLISH_TOKEN"))
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
  kotlinOptions.freeCompilerArgs = kotlinOptions.freeCompilerArgs + listOf("-Xjvm-default=all")
}