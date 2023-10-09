import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    id("org.jetbrains.compose") version "1.5.1"
}

group = "top.myrest"
version = "1.0.0"
val entry = "$name.jar"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

val myflowVersion = "1.0.0"
val okhttpVersion = "4.9.0"

dependencies {
    implementation("com.unfbx:chatgpt-java:1.1.0") {
        exclude(group = "cn.hutool", module = "hutool-all")
        exclude(group = "com.squareup.okhttp3", module = "okhttp-sse")
        exclude(group = "com.squareup.okhttp3", module = "logging-interceptor")
    }
    implementation("com.squareup.okhttp3:okhttp-sse:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    compileOnly(compose.desktop.currentOs)
    compileOnly("top.myrest:myflow-kit:$myflowVersion")
    testImplementation("top.myrest:myflow-baseimpl:$myflowVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.jar {
    archiveFileName.set(entry)
    from(
        configurations.runtimeClasspath.get().allDependencies.flatMap { dependency ->
            configurations.runtimeClasspath.get().files(dependency).map { file ->
                if (file.isDirectory) file else zipTree(file)
            }
        },
    )
}

tasks.build {
    doLast {
        copy {
            from("./build/libs/$entry")
            into(".")
        }
        val specFile = file("./plugin-spec.yml")
        val specContent = specFile.readLines(Charsets.UTF_8).joinToString(separator = System.lineSeparator()) {
            if (it.startsWith("version:")) {
                "version: $version"
            } else if (it.startsWith("entry:")) {
                "entry: ./$entry"
            } else it
        }
        specFile.writeText(specContent, Charsets.UTF_8)
        specFile.appendText(System.lineSeparator())
    }
}
