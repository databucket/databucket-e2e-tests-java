# Databucket end-to-end tests (Java)

Tests of the REST API and the GUI of a running Databucket: Java 17, JUnit 5 (6.x), [REST Assured](https://rest-assured.io),
[Playwright for Java](https://playwright.dev/java), AssertJ, an [Allure](https://allurereport.org) report, run with Gradle.
Many tests run at the same time. They are run by hand; no CI runs them.

The same tests as the TypeScript ones in [databucket-e2e-tests](https://github.com/databucket/databucket-e2e-tests),
with the same report.

## Running

Start Databucket, e.g. the docker compose of [databucket-server](https://github.com/databucket/databucket-server)
(http://databucket.localhost, user `super` / `super`):

```
cd databucket-server/docker && docker compose up -d --build
```

Then, in this repository (Gradle downloads everything, Chromium included):

```
./gradlew test              # the API tests, then the WWW (GUI) tests, each with its own report
./gradlew test -Ptype=api   # only the API tests (or ./gradlew apiTest); -Ptype=www: only the WWW tests (wwwTest)
./gradlew allureReport      # build/allure-report/api/index.html and build/allure-report/www/index.html (one file each)
./gradlew allureOpen        # generates the API report and opens it in the browser; -Ptype=www: the WWW one
```

| Option | |
|---|---|
| `-Ptype=api` / `-Ptype=www` | only the API or only the WWW (GUI) tests, and their report; both by default |
| `-Pthreads=8` | threads running the tests (half of the processors by default) |
| `-PbaseUrl=http://localhost:8080` or `BASE_URL=...` | another Databucket |
| `-PsuperUser=... -PsuperPassword=...` or `SUPER_USER`, `SUPER_PASSWORD` | its SUPER admin |
| `-Pheaded=true` | see the browser |
| `-PshowPassedChecks=false` or `SHOW_PASSED_CHECKS=false` | the checks of a response show only the failed ones (all by default, `ReportConfig.SHOW_PASSED_CHECKS`) |
| `--tests '*DataApiTest*'` | some tests only |

## How the tests are isolated and run in parallel

Every test gets its own project (a `TestProject` parameter, `ProjectExtension`): a SUPER admin creates it with four
users, ADMIN, MEMBER, ROBOT and AGENT (`project.as(ROBOT)`, `project.user(MEMBER)`), and removes it after the test
(cleared and deleted; the users are disabled, as users can't be deleted). Tests don't see each other's data, so JUnit
runs classes and methods concurrently (`junit-platform.properties`). The test data is created through the API:
`project.createBucket(...)`, `project.createView(...)`, `project.quietly(ROBOT)` for calls left out of the report.

Playwright isn't thread-safe: every thread has its own Playwright and Chromium, every test a new browser context
(`BrowserExtension`, a `Page` parameter). REST Assured is used without its global settings, every call builds its own
request.

## Structure

| Path (`src/test/java/pl/databucket/e2e/`) | |
|---|---|
| `api/` | API tests (`@ApiSuite`: tag `api`) |
| `ui/` | WWW (GUI) tests in Chromium (`@UiSuite`: tag `www`) |
| `pages/` | page objects of the GUI, their actions are steps of the report |
| `support/Api.java` | REST client: every call is an Allure step with the request and the response |
| `support/Call.java` | options of a call: `call().body(obj(...)).expect(201).name("...")` |
| `support/Json.java` | `obj(...)` / `arr(...)` for bodies, `response.path("data.properties.customer")` (GPath of REST Assured) |
| `support/ReportConfig.java` | what the report shows: response headers, masked headers and fields |
| `support/HttpView.java` | the HTML view of a call in the report |
| `support/TestProject.java` | the test project with its users, buckets and views |

A test:

```java
@ApiSuite
@DisplayName("Data API")
class DataApiTest {

    @Test
    void inserts_a_row_and_reads_it_by_id(TestProject project) {
        Api robot = project.as(ROBOT);

        ApiResponse inserted = robot.post("/api/bucket/orders", call()
                .body(obj("properties", obj("customer", "Alice")))
                .expect(201));

        assertThat(inserted.<String>path("properties.customer")).isEqualTo("Alice");
    }
}
```

Test names come from the method names (`_` → space); `@DisplayName` for names that need more than that.

## Published reports

Reports of chosen runs are kept in `reports/<date>/<api|www>/` and open in the browser at
**https://databucket.github.io/databucket-e2e-tests-java/** (GitHub Pages; `index.html` lists them). To add one, generate
it without `--single-file` (many small files; GitHub refuses files over 100 MB, and the single file of a big run with
traces can get there):
`build/allure-cli/<version>/bin/allure generate build/allure-results/www --clean -o reports/<date>/www`, then add a row to
`index.html`. In the API report of 2026-11-26 the five responses of `GET /api/manage/users` (every user of the server, ~59 MB
each) are replaced by a note (later runs shorten big bodies themselves, see below).

## The report

[Allure 2](https://allurereport.org) (the classic report, its command line is downloaded by Gradle; no Node.js needed),
two reports, API and WWW (GUI), each with the tree: area of the application (`@Area`: Data, Settings, Management,
Access, MCP, Fixed bugs) > class (`@DisplayName`) > test.

- API calls are steps such as `POST /api/bucket/orders/get → 200` with the request and the response attached as one
  HTML view: method, URL, status and time, then the request and the response with their headers (collapsed) and
  highlighted JSON bodies. The view is named by `call().name("GET data by rules")`, "request / response" otherwise. A
  Copy button copies the request and the response, with or without headers, as plain text. Tokens and passwords are
  masked, also in what is copied.
- GUI tests: the actions of the page objects are steps ("Open bucket orders", "Search \"Bob\"", written with
  `Screenshots.step`), each with a screenshot of the page when it ends (also when it fails); a verification of the GUI
  has a screenshot under its checks; `Screenshots.screenshot("menu")` adds one more inside a step (e.g. of an open
  menu). Screenshots are JPEG (`-PscreenshotQuality=80`). Of a failed test, at its end: the Playwright trace (download
  it and open it at https://trace.playwright.dev; of every test with `-Ptrace=always`, ~3 MB each) and a video.
- A request or response body over 256 KB of JSON (`-PmaxBodyKb=256`, 0 = no limit) is shortened in its view: the first
  items of a list, the start of a text; the Body line says what was left out, e.g. "shortened: 12.3 MB, the first 894
  of 43676 items".
- Every test project has two icons of Databucket (Settings > Icons): `databucket-outlined`, the icon of the buckets
  made by `TestProject.createBucket`, and `databucket-filled` (`support/DatabucketIcons`).

## Selectors

Prefer roles and accessible names (`getByRole(AriaRole.BUTTON, ...setName("Add"))`), as a user sees the page. Where the
frontend has nothing usable, add a `data-testid` to the component rather than relying on CSS classes.
