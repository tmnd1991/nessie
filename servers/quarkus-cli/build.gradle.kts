/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.quarkus.gradle.tasks.QuarkusBuild
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  `java-library`
  `maven-publish`
  signing
  alias(libs.plugins.quarkus)
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - Quarkus CLI"

val jacocoRuntime by configurations.creating { description = "Jacoco task runtime" }

dependencies {
  implementation(project(":nessie-quarkus-common"))
  implementation(project(":nessie-services"))
  implementation(project(":nessie-server-store"))
  implementation(project(":nessie-versioned-persist-adapter"))
  implementation(project(":nessie-versioned-spi"))
  implementation(project(":nessie-versioned-transfer"))
  implementation(project(":nessie-versioned-transfer-proto"))
  implementation(project(":nessie-model"))
  implementation(project(":nessie-versioned-persist-non-transactional"))
  implementation(project(":nessie-versioned-persist-in-memory"))
  implementation(project(":nessie-versioned-persist-dynamodb"))
  implementation(project(":nessie-versioned-persist-mongodb"))
  implementation(project(":nessie-versioned-persist-rocks"))
  implementation(project(":nessie-versioned-persist-transactional"))

  implementation(enforcedPlatform(libs.quarkus.bom))
  implementation(enforcedPlatform(libs.quarkus.amazon.services.bom))
  implementation("io.quarkus:quarkus-picocli")

  implementation(libs.protobuf.java)

  // javax/jakarta
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)

  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.microprofile.openapi)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.databind)
  implementation(libs.jackson.annotations)

  implementation(libs.agroal.pool)
  implementation(libs.h2)
  implementation(libs.postgresql)

  compileOnly(libs.immutables.builder)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  testImplementation(project(":nessie-quarkus-tests"))
  testImplementation(project(":nessie-versioned-persist-mongodb-test"))
  testImplementation(project(":nessie-versioned-tests"))
  testImplementation("io.quarkus:quarkus-jacoco")
  testImplementation("io.quarkus:quarkus-junit5")
  testCompileOnly(libs.microprofile.openapi)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly(libs.junit.jupiter.engine)

  jacocoRuntime(libs.jacoco.report)
  jacocoRuntime(libs.jacoco.ant)
}

buildForJava11()

tasks.withType<ProcessResources>().configureEach {
  from("src/main/resources") {
    expand("nessieVersion" to version)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
}

// nessie-quarkus-cli module needs to be adopted before we can generate a native runner
val packageType = quarkusNonNativePackageType()
val quarkusBuilderImage = libs.versions.quarkusBuilderImage.get()

quarkus { quarkusBuildProperties.put("quarkus.package.type", packageType) }

tasks.withType<QuarkusBuild>().configureEach {
  outputs.doNotCacheIf("Do not add huge cache artifacts to build cache") { true }
  inputs.property("quarkus.package.type", packageType)
  inputs.property("final.name", quarkus.finalName())
}

val prepareJacocoReport by
  tasks.registering {
    doFirst {
      // Must delete the Jacoco data file before running tests, because
      // quarkus.jacoco.reuse-data-file=true in application.properties.
      file("${project.buildDir}/jacoco-quarkus.exec").delete()
      val reportDir = file("${project.buildDir}/jacoco-report")
      delete { delete(reportDir) }
      reportDir.mkdirs()
    }
  }

val jacocoReport by
  tasks.registering(JacocoReport::class) {
    dependsOn(
      tasks.named("compileIntegrationTestJava"),
      tasks.named("compileNativeTestJava"),
      tasks.named("compileQuarkusGeneratedSourcesJava")
    )
    executionData.from(file("${project.buildDir}/jacoco-quarkus.exec"))
    jacocoClasspath = jacocoRuntime
    classDirectories.from(layout.buildDirectory.dir("classes"))
    sourceDirectories
      .from(layout.projectDirectory.dir("src/main/java"))
      .from(layout.projectDirectory.dir("src/test/java"))
    reports {
      xml.required.set(true)
      xml.outputLocation.set(layout.buildDirectory.file("jacoco-report/jacoco.xml"))
      csv.required.set(true)
      csv.outputLocation.set(layout.buildDirectory.file("jacoco-report/jacoco.csv"))
      html.required.set(true)
      html.outputLocation.set(layout.buildDirectory.dir("jacoco-report"))
    }
  }

tasks.withType<Test>().configureEach {
  dependsOn(prepareJacocoReport)
  finalizedBy(jacocoReport)
}

tasks.named<Test>("intTest") {
  // Quarkus accumulates stuff in QuarkusClassLoader.transformedClasses throughout CLI
  // re-invocations during testing. Therefore, we restart the test JVM after running 2 test
  // classes. The number is rather arbitrary since the real factor seems to be the number
  // of CLI launches performed in the same JVM.
  setForkEvery(2)
}

if (quarkusFatJar()) {
  afterEvaluate {
    publishing {
      publications {
        named<MavenPublication>("maven") {
          val quarkusBuild = tasks.getByName<QuarkusBuild>("quarkusBuild")
          artifact(quarkusBuild.runnerJar) {
            classifier = "runner"
            builtBy(quarkusBuild)
          }
        }
      }
    }
  }
}

listOf("javadoc", "sourcesJar").forEach { name ->
  tasks.named(name) { dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava")) }
}

listOf("checkstyleTest", "compileTestJava").forEach { name ->
  tasks.named(name) { dependsOn(tasks.named("compileQuarkusTestGeneratedSourcesJava")) }
}

// Testcontainers is not supported on Windows :(
if (Os.isFamily(Os.FAMILY_WINDOWS)) {
  tasks.withType<Test>().configureEach { this.enabled = false }
}
