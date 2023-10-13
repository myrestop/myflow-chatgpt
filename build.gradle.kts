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
    implementation("com.unfbx:SparkDesk-Java:1.0.0")
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
    val excludeJars = setOf("annotations-24.0.1.jar", "slf4j-api-2.0.6.jar", "kotlin-stdlib-1.8.20.jar", "kotlin-stdlib-common-1.8.20.jar", "kotlin-stdlib-jdk8-1.8.20.jar", "kotlin-stdlib-jdk7-1.8.20.jar")
    val exists = mutableSetOf<String>()
    val files = mutableListOf<Any>()
    configurations.runtimeClasspath.get().allDependencies.forEach { dependency ->
        configurations.runtimeClasspath.get().files(dependency).forEach { file ->
            if (exists.add(file.name) && !excludeJars.contains(file.name)) {
                println(file.name)
                files.add(if (file.isDirectory) file else zipTree(file))
            }
        }
    }
    from(files)

    exclude(
        "module-info.class",
        "META-INF/NOTICE",
        "META-INF/LICENSE",
        "META-INF/versions/9/module-info.class",
        "release-timestamp.txt",
        "README.md",
        "LICENSE",
        "latestchanges.html",
        "changelog.txt",
        "AUTHORS",
        ".gitkeep",
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
