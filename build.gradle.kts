plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

// the sources have "→" in the names of the steps: UTF-8 also on Windows, where the default is cp1250 / cp1252
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// the Allure 2 command line (classic report), run by the tasks allureReport / allureOpen
val allureCli = configurations.create("allureCli")

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.rest-assured:rest-assured:6.3.0")
    testImplementation(platform("com.fasterxml.jackson:jackson-bom:2.23.0"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("com.microsoft.playwright:playwright:1.65.0")
    testImplementation("io.qameta.allure:allure-junit5:2.35.6")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")

    allureCli("io.qameta.allure:allure-commandline:2.46.2@zip")
}

val allureResults = layout.buildDirectory.dir("allure-results")
val allureReportDir = layout.buildDirectory.dir("allure-report")

// Threads running the tests: -Pthreads=8; half of the processors by default (as Playwright's workers)
val threads = providers.gradleProperty("threads")
    .orElse(maxOf(2, Runtime.getRuntime().availableProcessors() / 2).toString())

// Databucket under test: -PbaseUrl=... -PsuperUser=... -PsuperPassword=... or the environment variables
// BASE_URL, SUPER_USER, SUPER_PASSWORD; the docker compose of databucket-server (super / super) by default
val settings = listOf("baseUrl", "superUser", "superPassword", "headed", "showPassedChecks")

// Chromium for the GUI tests (once; quick when already installed)
val installBrowsers = tasks.register<JavaExec>("installBrowsers") {
    group = "verification"
    description = "Installs Chromium for Playwright"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "chromium")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    defaultCharacterEncoding = "UTF-8"
    // the default 512 MB ran out once in a full run (the results with their attachments are kept until written)
    maxHeapSize = "1g"
    dependsOn(installBrowsers)
    systemProperty("allure.results.directory", allureResults.get().asFile.absolutePath)
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", threads.get())
    settings.forEach { name -> providers.gradleProperty(name).orNull?.let { systemProperty("databucket.$name", it) } }
    // browsers are installed by installBrowsers, Playwright must not download all of them on its own
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    // end-to-end tests check a running server: always run them
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    doFirst {
        // every run starts a new report
        val results = allureResults.get().asFile
        results.deleteRecursively()
        results.mkdirs()
        val baseUrl = providers.gradleProperty("baseUrl").orNull ?: System.getenv("BASE_URL") ?: "http://databucket.localhost"
        results.resolve("environment.properties").writeText("Databucket\\ URL=$baseUrl\nThreads=${threads.get()}\n")
    }
}

tasks.test {
    description = "Runs all the tests (API and GUI)"
}

val apiTest = tasks.register<Test>("apiTest") {
    group = "verification"
    description = "Runs the API tests"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("api") }
}

val uiTest = tasks.register<Test>("uiTest") {
    group = "verification"
    description = "Runs the GUI tests"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ui") }
}

val unpackAllure = tasks.register<Sync>("unpackAllure") {
    from({ zipTree(allureCli.singleFile) })
    into(layout.buildDirectory.dir("allure-cli"))
}

fun JavaExec.allure(vararg arguments: Any) {
    dependsOn(unpackAllure)
    val home = unpackAllure.map { it.destinationDir.listFiles()!!.single() }
    classpath = files(home.map { it.resolve("lib").listFiles()!!.filter { f -> f.name.endsWith(".jar") } })
    mainClass = "io.qameta.allure.CommandLine"
    doFirst { systemProperty("app.home", home.get().absolutePath) }
    args(*arguments)
}

val allureReport = tasks.register<JavaExec>("allureReport") {
    group = "verification"
    description = "Generates the Allure report of the last run: build/allure-report/index.html (one file)"
    allure(
        "generate", allureResults.get().asFile, "--clean", "--single-file",
        "--name", "Databucket E2E", "--lang", "en", "-o", allureReportDir.get().asFile,
    )
}

val allureOpen = tasks.register<JavaExec>("allureOpen") {
    group = "verification"
    description = "Generates the Allure report of the last run and opens it in the browser"
    dependsOn(allureReport)
    allure("open", allureReportDir.get().asFile)
}
