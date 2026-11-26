package pl.databucket.e2e.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.ColumnProperty;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.FilterConfig;
import pl.databucket.e2e.model.Task;
import pl.databucket.e2e.model.TaskConfig;
import pl.databucket.e2e.pages.JobsPage;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.pages.TaskExecutionDialog;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.UiSuite;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** Tasks run from the data grid (Task execution) and jobs of Settings &gt; Jobs. */
@UiSuite
@DisplayName("Tasks and jobs")
@SuppressWarnings({"rawtypes", "unchecked"})
class TasksAndJobsTest {

    private static final String BUCKET = "orders";
    private static final String TASK = "remove used";
    private static final ColumnProperty STATUS = new ColumnProperty().path("$.status").title("Status").type("string").uuid("u-status");

    private long bucketId;
    private Task task;

    // two used orders and an active one; the task "remove used" (filter: status == used) assigned to the bucket
    @BeforeEach
    void data(TestProject project) {
        bucketId = project.createBucket(BUCKET);
        project.createView(bucketId, "all orders", obj("number", "Number", "status", "Status"));
        orders(project).insertAll(
                Map.of("number", 1, "status", "used"),
                Map.of("number", 2, "status", "used"),
                Map.of("number", 3, "status", "active"));
        Long filterId = project.quietly(ADMIN).settings().filters().create(new Filter().name("status used")
                .configuration(new FilterConfig().properties(List.of(STATUS))
                        .logic(Map.of("==", List.of(Map.of("var", "$.status"), "used"))))).id;
        task = project.quietly(ADMIN).settings().tasks().create(new Task().name(TASK).bucketsIds(Set.of(bucketId))
                .filterId(filterId).configuration(new TaskConfig().properties(List.of(STATUS)).actions(Map.of("type", "remove"))));
    }

    private static BucketApi<Map> orders(TestProject project) {
        return project.as(ROBOT).bucket(BUCKET, Map.class);
    }

    private static List<Object> numbersLeft(TestProject project) {
        return orders(project).properties(Search.all().sort("id")).stream().map(row -> row.get("number")).toList();
    }

    @Nested
    @Area(Area.DATA)
    @DisplayName("Task execution in the data grid")
    class TaskExecution {

        @Test
        void runs_a_saved_task_on_the_rows_of_its_filter(TestProject project, Page page) {
            ProjectPage ui = new ProjectPage(page);
            ui.openAs(project, MEMBER);
            ui.openBucket(BUCKET);
            TaskExecutionDialog dialog = ui.openTaskExecution();

            dialog.selectTask(TASK);
            verifyAll(v -> v
                    .check("the action is Remove data", () -> assertThat(dialog.action("Remove data")).isChecked())
                    .check("2 data rows match", () -> assertThat(dialog.matches()).hasText("2 data rows match the rules")));
            dialog.execute();

            verifyAll(v -> v
                    .check("the message says 2 rows removed", () -> assertThat(dialog.message()).containsText("Removed 2 data row(s)"))
                    .that("numbers left", numbersLeft(project), List.of(3)));
        }

        @Test
        void reserves_the_rows_of_a_rule(TestProject project, Page page) {
            long secondId = orders(project).find(Search.all().sort("id").limit(10)).data().get(1).id();
            ProjectPage ui = new ProjectPage(page);
            ui.openAs(project, MEMBER);
            ui.openBucket(BUCKET);
            TaskExecutionDialog dialog = ui.openTaskExecution();

            dialog.chooseAction("Reserve data");
            verifyAll(v -> v.check("without rules: 3 free rows match",
                    () -> assertThat(dialog.matches()).hasText("3 free data rows match the rules")));
            dialog.addNumberRule("Id", secondId);
            verifyAll(v -> v.check("1 free data row matches",
                    () -> assertThat(dialog.matches()).hasText("1 free data row matches the rules")));
            dialog.execute();

            List<DataRow<Map>> rows = orders(project).find(Search.all().sort("id").limit(10)).data();
            verifyAll(v -> v
                    .that("reserved", rows.stream().map(DataRow::reserved).toList(), List.of(false, true, false))
                    .that("owner of the second", rows.get(1).owner(), project.user(MEMBER).username()));
        }
    }

    @Nested
    @Area(Area.SETTINGS)
    @DisplayName("Jobs")
    class Jobs {

        private long job(TestProject project, String name, String keyword) {
            return project.quietly(ADMIN).post("/api/task-schedules", call().body(obj(
                    "name", name, "taskId", task.id, "bucketId", bucketId, "cron", "", "keyword", keyword,
                    "timeZone", "UTC", "enabled", true)).expect(201)).<Number>path("id").longValue();
        }

        private JobsPage open(TestProject project, Page page) {
            new ProjectPage(page).openAs(project, ADMIN, "/settings/jobs");
            JobsPage jobs = new JobsPage(page);
            assertThat(jobs.heading()).isVisible();
            return jobs;
        }

        @Test
        void adds_a_job_with_a_keyword_and_no_cron(TestProject project, Page page) {
            JobsPage jobs = open(project, page);

            jobs.addJob("clean orders", TASK, BUCKET, "DEV cleaning");

            verifyAll(v -> v
                    .check("the row shows the task", () -> assertThat(jobs.job("clean orders")).containsText(TASK))
                    .check("the row shows the keyword", () -> assertThat(jobs.job("clean orders")).containsText("DEV cleaning")));
            ApiResponse saved = project.as(ADMIN).get("/api/task-schedules", call().expect(200));
            verifyAll(v -> v
                    .that("name", saved.<List<String>>path("name"), List.of("clean orders"))
                    .that("keyword", saved.<List<String>>path("keyword"), List.of("DEV cleaning"))
                    .that("taskId", saved.<List<Number>>path("taskId").get(0).longValue(), task.id)
                    .that("bucketId", saved.<List<Number>>path("bucketId").get(0).longValue(), bucketId));
            verify("nothing removed yet", numbersLeft(project), List.of(1, 2, 3));
        }

        @Test
        void runs_a_job_and_shows_its_execution(TestProject project, Page page) {
            job(project, "clean orders", null);
            JobsPage jobs = open(project, page);

            jobs.run("clean orders");

            verifyAll(v -> v
                    .check("the message", () -> assertThat(jobs.message()).containsText("\"clean orders\" has run"))
                    .check("the message says 2 rows removed", () -> assertThat(jobs.message()).containsText("Removed 2 data row(s)"))
                    .check("Last run: 2 rows", () -> assertThat(jobs.job("clean orders")).containsText("(2)")));
            verify("numbers left", numbersLeft(project), List.of(3));

            Locator executions = jobs.openExecutions("clean orders");
            verifyAll(v -> v
                    .check("one execution", () -> assertThat(executions.getByRole(com.microsoft.playwright.options.AriaRole.ROW)
                            .filter(new Locator.FilterOptions().setHasText("Removed 2 data row(s)"))).hasCount(1))
                    .check("trigger Run, by the admin, OK", () -> assertThat(executions).containsText(
                            "Run" + project.user(ADMIN).username() + "OK")));
        }

        @Test
        void runs_the_jobs_of_a_keyword_after_a_preview(TestProject project, Page page) {
            job(project, "clean orders", "DEV cleaning");
            JobsPage jobs = open(project, page);

            jobs.openRunKeyword("DEV cleaning");
            jobs.keywordButton("Preview");
            verifyAll(v -> v
                    .check("the preview names the job", () -> assertThat(jobs.keywordDialog()).containsText("clean orders"))
                    .that("nothing removed by the preview", numbersLeft(project), List.of(1, 2, 3)));

            jobs.keywordButton("Run");
            verifyAll(v -> v
                    .check("the result names the job", () -> assertThat(jobs.keywordDialog()).containsText("Removed 2 data row(s)"))
                    .that("numbers left", numbersLeft(project), List.of(3)));
        }
    }
}
