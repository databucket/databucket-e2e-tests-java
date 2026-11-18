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
./gradlew test              # everything; ./gradlew apiTest / ./gradlew uiTest
./gradlew allureReport      # build/allure-report/index.html (one file, opens from the disk)
./gradlew allureOpen        # generates the report and opens it in the browser
```

| Option | |
|---|---|
| `-Pthreads=8` | threads running the tests (half of the processors by default) |
| `-PbaseUrl=http://localhost:8080` or `BASE_URL=...` | another Databucket |
| `-PsuperUser=... -PsuperPassword=...` or `SUPER_USER`, `SUPER_PASSWORD` | its SUPER admin |
| `-Pheaded=true` | see the browser |
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
| `ui/` | GUI tests in Chromium (`@UiSuite`: tag `ui`) |
| `pages/` | page objects of the GUI, their actions are steps of the report |
| `support/Api.java` | REST client: every call is an Allure step with the request and the response |
| `support/Call.java` | options of a call: `call().v2().body(obj(...)).expect(201).name("...")` |
| `support/Json.java` | `obj(...)` / `arr(...)` for bodies, `response.path("data.properties.customer")` (GPath of REST Assured) |
| `support/ReportConfig.java` | what the report shows: response headers, masked headers and fields |
| `support/HttpView.java` | the HTML view of a call in the report |
| `support/TestProject.java` | the test project with its users, buckets and views |

A test:

```java
@ApiSuite
@DisplayName("Data API v2")
class DataApiTest {

    @Test
    void inserts_a_row_and_reads_it_by_id(TestProject project) {
        Api robot = project.as(ROBOT);

        ApiResponse inserted = robot.post("/api/bucket/orders", call().v2()
                .body(obj("properties", obj("customer", "Alice")))
                .expect(201));

        assertThat(inserted.<String>path("properties.customer")).isEqualTo("Alice");
    }
}
```

Test names come from the method names (`_` → space); `@DisplayName` for names that need more than that.

## The report

[Allure 2](https://allurereport.org) (the classic report, its command line is downloaded by Gradle; no Node.js needed),
tree: api / ui > class (`@DisplayName`) > test.

- API calls are steps such as `POST /api/bucket/orders/get → 200` with the request and the response attached as one
  HTML view: method, URL, status and time, then the request and the response with their headers (collapsed) and
  highlighted JSON bodies. The view is named by `call().name("GET data by rules")`, "request / response" otherwise. A
  Copy button copies the request and the response, with or without headers, as plain text. Tokens and passwords are
  masked, also in what is copied.
- GUI tests: the actions of the page objects are steps ("Open bucket orders", "Search \"Bob\""); a screenshot at the
  end of every test (and named ones from `screenshot(page, "...")`), the Playwright trace of every test (download it and
  open it at https://trace.playwright.dev) and a video of the failed ones.

## Selectors

Prefer roles and accessible names (`getByRole(AriaRole.BUTTON, ...setName("Add"))`), as a user sees the page. Where the
frontend has nothing usable, add a `data-testid` to the component rather than relying on CSS classes.
