/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

description = "Kotlin Daemon Client New"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.8"

val ktorExcludesForDaemon : List<Pair<String, String>> by rootProject.extra

dependencies {
    compileOnly(project(":"))

    compile(project(":compiler:cli"))
    compile(project(":compiler:incremental-compilation-impl"))
    compile(project(":kotlin-build-common"))
    compile(commonDep("org.fusesource.jansi", "jansi"))
    compile(commonDep("org.jline", "jline"))
    compileOnly(project(":kotlin-scripting-compiler"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    runtime(project(":kotlin-reflect"))

    embeddedComponents(project(":daemon-common")) { isTransitive = false }
    compile(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) {
        isTransitive = false
    }
    compile(commonDep("io.ktor", "ktor-network")) {
        ktorExcludesForDaemon.forEach { (group, module) ->
            exclude(group = group, module = module)
        }
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

publish()

noDefaultJar()

runtimeJar(task<ShadowJar>("shadowJar")) {
    from(mainSourceSet.output)
    fromEmbeddedComponents()
}

sourcesJar()

javadocJar()

dist()

ideaPlugin()
