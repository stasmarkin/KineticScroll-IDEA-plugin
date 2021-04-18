plugins {
  kotlin("jvm") version "1.4.31"
  id("org.jetbrains.intellij") version "0.4.8"
}

repositories {
  mavenCentral()
}

group = "com.stasmarkin"
version = "1.0.0"

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
  version = "IC-2019.3"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

tasks {
  patchPluginXml {
    // do not patch supported versions
    sinceBuild(null)
    untilBuild(null)
  }
}