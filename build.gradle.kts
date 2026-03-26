import org.gradle.api.tasks.JavaExec

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room3)
  application
}

group = "io.sermilion"
version = "0.1.0"

application {
  mainClass.set("io.sermilion.telegramsearch.MainKt")
}

fun tdlightNativeClassifier(): String {
  val os = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()
  return when {
    os.contains("mac") && (arch.contains("arm64") || arch.contains("aarch64")) -> "macos_arm64"
    os.contains("mac") -> "macos_amd64"
    os.contains("linux") && (arch.contains("arm64") || arch.contains("aarch64")) -> "linux_arm64_gnu_ssl3"
    os.contains("linux") -> "linux_amd64_gnu_ssl3"
    os.contains("win") -> "windows_amd64"
    else -> error("Unsupported host for TDLight natives: $os / $arch")
  }
}

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.jdk8)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.serialization.json)
  implementation(libs.ktor.io)
  implementation(libs.room3.runtime)
  implementation(libs.sqlite.bundled)
  implementation(libs.kotlin.logging)
  implementation(libs.mcp.kotlin.server)
  implementation(libs.kotlinx.io.core)
  implementation(platform("it.tdlight:tdlight-java-bom:${libs.versions.tdlight.get()}"))
  implementation(mapOf("group" to "it.tdlight", "name" to "tdlight-java"))
  implementation(mapOf("group" to "it.tdlight", "name" to "tdlight-natives", "classifier" to tdlightNativeClassifier()))

  runtimeOnly(libs.slf4j.simple)
  ksp(libs.room3.compiler)

  testImplementation(kotlin("test"))
  testImplementation(libs.kotest.runner)
  testImplementation(libs.kotest.assertions)
}

room3 {
  schemaDirectory("$projectDir/schemas")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
  standardInput = System.`in`
}

kotlin {
  jvmToolchain(21)
}
