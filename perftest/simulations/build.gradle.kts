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

import com.google.common.collect.Maps
import io.gatling.gradle.GatlingRunTask

plugins {
  `maven-publish`
  signing
  alias(libs.plugins.gatling)
  `nessie-conventions`
  alias(libs.plugins.nessie.run)
}

extra["maven.name"] = "Nessie - Perf Test - Simulations"

dependencies {
  gatling(project(":nessie-model"))
  gatling(project(":nessie-client"))
  gatling(project(":nessie-perftest-gatling"))
  gatling(libs.gatling.charts.highcharts) {
    exclude("io.netty", "netty-tcnative-boringssl-static")
    exclude("commons-logging", "commons-logging")
  }
  gatling(libs.microprofile.openapi)

  gatling(platform(libs.jackson.bom))
  gatling(libs.jackson.annotations)

  nessieQuarkusServer(project(":nessie-quarkus", "quarkusRunner"))
}

nessieQuarkusApp {
  if (!System.getProperties().containsKey("nessie.uri")) {
    includeTasks(tasks.withType<GatlingRunTask>()) {
      jvmArgs = listOf("-Dsim.users=10", "-Dnessie.uri=${extra["quarkus.http.test-url"]}/api/v2")
    }
    environmentNonInput.put("HTTP_ACCESS_LOG_LEVEL", testLogLevel())
    jvmArgumentsNonInput.add("-XX:SelfDestructTimer=30")
    System.getProperties()
      .map { e -> Maps.immutableEntry(e.key.toString(), e.value.toString()) }
      .filter { e -> e.key.startsWith("nessie.") || e.key.startsWith("quarkus.") }
      .forEach { e -> systemProperties.put(e.key, e.value) }
  }
}

gatling {
  gatlingVersion = libs.versions.gatling.get()
  // Null is OK (io.gatling.gradle.LogbackConfigTask checks for it)
  logLevel = System.getProperty("gatling.logLevel")

  jvmArgs =
    System.getProperties()
      .map { e -> Maps.immutableEntry(e.key.toString(), e.value.toString()) }
      .filter { e ->
        e.key.startsWith("nessie.") || e.key.startsWith("gatling.") || e.key.startsWith("sim.")
      }
      .map { e ->
        if (e.key.startsWith("nessie.") || e.key.startsWith("sim.")) {
          "-D${e.key}=${e.value}"
        } else if (e.key.startsWith("gatling.jvmArg")) {
          e.value
        } else if (e.key.startsWith("gatling.")) {
          "-D${e.key.substring("gatling.".length)}=${e.value}"
        } else {
          throw IllegalStateException("Unexpected: ${e.key}")
        }
      }
}
