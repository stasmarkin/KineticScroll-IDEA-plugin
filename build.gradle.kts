plugins {
  kotlin("jvm") version "1.4.31"
  id("org.jetbrains.intellij") version "0.7.2"
}

repositories {
  mavenCentral()
}

group = "com.stasmarkin"
version = "1.0.2"

intellij {
  version = "IC"
  version = "2021.1"
  pluginName = "com.stasmarkin.kineticscroll"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
  kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=all")
}

tasks {
  patchPluginXml {
    // do not patch supported versions
    sinceBuild(null)
    untilBuild(null)
  }
}