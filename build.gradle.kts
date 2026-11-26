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

// API or WWW (GUI) tests, each type with its own results and report: -Ptype=api or -Ptype=www; both by default
val testType = providers.gradleProperty("type").orNull?.lowercase()
require(testType == null || testType == "api" || testType == "www") { "-Ptype is api or www, not $testType" }
val typeNames = mapOf("api" to "API", "www" to "WWW")
val selectedTypes = if (testType == null) listOf("api", "www") else listOf(testType)
fun allureResults(type: String): File = layout.buildDirectory.dir("allure-results/$type").get().asFile
fun allureReportDir(type: String): File = layout.buildDirectory.dir("allure-report/$type").get().asFile

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
}

// ./gradlew test --tests ... (both types): the patterns of the test task for apiTest / wwwTest; a pattern may match
// the tests of one type only
fun Test.usesTestPatternsOfTest() {
    doFirst {
        val patterns = (tasks.test.get().filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter)
            .commandLineIncludePatterns
        if (testType == null && patterns.isNotEmpty()) {
            val own = filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
            own.setCommandLineIncludePatterns(patterns)
            own.isFailOnNoMatchingTests = false
        }
    }
}

// the tests of one type (tag api / www) into their own results: build/allure-results/<type>
fun Test.ofType(type: String) {
    useJUnitPlatform { includeTags(type) }
    val results = allureResults(type)
    systemProperty("allure.results.directory", results.absolutePath)
    doFirst {
        // every run starts a new report
        results.deleteRecursively()
        results.mkdirs()
        val baseUrl = providers.gradleProperty("baseUrl").orNull ?: System.getenv("BASE_URL") ?: "http://databucket.localhost"
        results.resolve("environment.properties")
            .writeText("Databucket\\ URL=$baseUrl\nTests=${typeNames[type]}\nThreads=${threads.get()}\n")
    }
}

val apiTest = tasks.register<Test>("apiTest") {
    group = "verification"
    description = "Runs the API tests (report: build/allure-report/api)"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    ofType("api")
    usesTestPatternsOfTest()
}

val wwwTest = tasks.register<Test>("wwwTest") {
    group = "verification"
    description = "Runs the WWW (GUI) tests (report: build/allure-report/www)"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    ofType("www")
    usesTestPatternsOfTest()
    mustRunAfter(apiTest)
}

tasks.test {
    if (testType != null) {
        description = "Runs the ${typeNames[testType]} tests (-Ptype=$testType)"
        ofType(testType)
    } else {
        description = "Runs the API tests, then the WWW tests, each with its own report (-Ptype=api / -Ptype=www: one of them)"
        dependsOn(apiTest, wwwTest)
        // runs nothing itself: apiTest and wwwTest take its --tests (usesTestPatternsOfTest)
        onlyIf { false }
    }
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

// one report per type: build/allure-report/api/index.html, build/allure-report/www/index.html (one file each)
val typeReports = listOf("api", "www").associateWith { type ->
    tasks.register<JavaExec>("allureReport${typeNames[type]!!.lowercase().replaceFirstChar { it.uppercase() }}") {
        group = "verification"
        description = "Generates the Allure report of the last ${typeNames[type]} run: build/allure-report/$type/index.html"
        allure(
            "generate", allureResults(type), "--clean", "--single-file",
            "--name", "Databucket E2E - ${typeNames[type]}", "--lang", "en", "-o", allureReportDir(type),
        )
        onlyIf { allureResults(type).listFiles()?.any { it.name.endsWith("-result.json") } == true }
    }
}

val allureReport = tasks.register("allureReport") {
    group = "verification"
    description = "Generates the reports of the last run: build/allure-report/api and /www (-Ptype: one of them)"
    dependsOn(selectedTypes.map { typeReports.getValue(it) })
}

val allureOpen = tasks.register<JavaExec>("allureOpen") {
    group = "verification"
    description = "Generates the report of the last run and opens it in the browser (-Ptype=www: the WWW one; API by default)"
    val type = testType ?: "api"
    dependsOn(typeReports.getValue(type))
    allure("open", allureReportDir(type))
}
