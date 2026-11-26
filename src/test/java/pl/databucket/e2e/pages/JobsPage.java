package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Screenshots;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Settings &gt; Jobs: a table of the jobs (task + bucket + rules + cron and/or keyword), a row editor (Add, Edit), the
 * row actions Run, Executions, Edit, Delete and the toolbar button Run keyword.
 */
public final class JobsPage {

    private final Page page;

    public JobsPage(Page page) {
        this.page = page;
    }

    public Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Jobs").setExact(true));
    }

    /** The row of a job by its name. */
    public Locator job(String name) {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(name))
                .filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Run"))));
    }

    /** The row being edited (the one with Save and Cancel). */
    public Locator editor() {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"))));
    }

    /** The message shown after an action (e.g. "\"clean orders\" has run"). */
    public Locator message() {
        return page.getByRole(AriaRole.ALERT);
    }

    /** Adds a job in the row editor: name, task, bucket, keyword (cron empty: the job runs only by Run / keyword). */
    public void addJob(String name, String task, String bucket, String keyword) {
        Screenshots.step("Add the job " + name + " (" + task + " on " + bucket + ", keyword " + keyword + ")", () -> {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add").setExact(true)).click();
            Locator row = editor();
            row.getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName("Name")).fill(name);
            row.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName("Task")).click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(task).setExact(true)).click();
            row.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName("Bucket")).click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(bucket).setExact(true)).click();
            // the keyword: a combobox offering the keywords in use, any text allowed; its name is its placeholder
            Locator keywordBox = row.getByRole(AriaRole.COMBOBOX).last();
            keywordBox.fill(keyword);
            keywordBox.press("Tab");
            Screenshots.screenshot("filled");
            row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(editor()).isHidden();
        });
    }

    /** Runs the job (the row action Run) and waits for its message. */
    public void run(String name) {
        Screenshots.step("Run the job " + name, () -> {
            job(name).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Run")).click();
            assertThat(message()).isVisible();
        });
    }

    /** Opens the executions of the job. */
    public Locator openExecutions(String name) {
        Locator dialog = page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions()
                .setHasText("Executions of \"" + name + "\""));
        Screenshots.step("Open the executions of " + name, () -> {
            job(name).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Executions")).click();
            assertThat(dialog).isVisible();
        });
        return dialog;
    }

    /** The dialog "Run the jobs of a keyword". */
    public Locator keywordDialog() {
        return page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Run the jobs of a keyword"));
    }

    /** Opens Run keyword and chooses the keyword. */
    public void openRunKeyword(String keyword) {
        Screenshots.step("Run keyword: " + keyword, () -> {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Run keyword")).click();
            Locator box = keywordDialog().getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName("Keyword"));
            box.fill(keyword);
            box.press("Tab");
        });
    }

    /** A button of the keyword dialog (Preview, Run, Close). */
    public void keywordButton(String name) {
        Screenshots.step(name, () -> keywordDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(name).setExact(true)).click());
    }
}
