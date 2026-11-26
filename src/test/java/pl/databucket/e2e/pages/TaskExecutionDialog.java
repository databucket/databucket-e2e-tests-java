package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Screenshots;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The dialog "Task execution" of the data grid: a saved task (Select task) or an action chosen here (Remove, Modify,
 * Clear history, Reserve), on the rows of the rules (tab Rules), run with Execute.
 */
public final class TaskExecutionDialog {

    private final Page page;

    public TaskExecutionDialog(Page page) {
        this.page = page;
    }

    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Task execution"));
    }

    /** "2 data rows match the rules" at the bottom. */
    public Locator matches() {
        return dialog().getByText(Pattern.compile("\\d+ (free )?data rows? match"));
    }

    public Locator action(String name) {
        return dialog().getByRole(AriaRole.RADIO, new Locator.GetByRoleOptions().setName(name));
    }

    /** Loads a saved task (its action, rules and properties) from the menu "Select task". */
    public void selectTask(String name) {
        Screenshots.step("Select the task " + name, () -> {
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select task")).click();
            page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true)).click();
        });
    }

    /** Chooses the action (Remove data, Modify data, Clear history, Reserve data). */
    public void chooseAction(String name) {
        Screenshots.step("Action: " + name, () -> {
            dialog().getByRole(AriaRole.TAB, new Locator.GetByRoleOptions().setName("Action")).click();
            // the label next to the radio button (the input itself is covered by its MUI decoration)
            dialog().getByText(name, new Locator.GetByTextOptions().setExact(true)).click();
            assertThat(action(name)).isChecked();
        });
    }

    /** Adds a rule "field == number" in the tab Rules (a field of the list "Select field", e.g. Id). */
    public void addNumberRule(String field, long value) {
        Screenshots.step("Rule " + field + " == " + value, () -> {
            dialog().getByRole(AriaRole.TAB, new Locator.GetByRoleOptions().setName("Rules")).click();
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add rule")).click();
            dialog().getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName("Select field")).last().click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(field).setExact(true)).click();
            dialog().getByRole(AriaRole.SPINBUTTON, new Locator.GetByRoleOptions().setName("Enter number")).last()
                    .fill(String.valueOf(value));
        });
    }

    public Locator executeButton() {
        return dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Execute"));
    }

    /** Executes and waits for the message of the result (a confirmation, if asked, is accepted). */
    public void execute() {
        Screenshots.step("Execute", () -> {
            executeButton().click();
            Locator confirm = page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Confirm"))));
            if (confirm.isVisible())
                confirm.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Confirm")).click();
            assertThat(message()).isVisible();
        });
    }

    /** The message of the result, e.g. "Removed 1 data row(s)". */
    public Locator message() {
        return page.getByRole(AriaRole.ALERT);
    }
}
