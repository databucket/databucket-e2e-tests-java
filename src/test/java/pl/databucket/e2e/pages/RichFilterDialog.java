package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Screenshots;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** The dialog "Data filter" of the data grid (Rich filter): rules in the query builder, Properties, API request. */
public final class RichFilterDialog {

    private final Page page;

    public RichFilterDialog(Page page) {
        this.page = page;
    }

    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Data filter"));
    }

    /** "2 data rows match the rules" under the rules. */
    public Locator matches() {
        return dialog().getByText(java.util.regex.Pattern.compile("\\d+ data rows? match"));
    }

    /** Adds a rule "field == number" (a field of the list "Select field", e.g. Id). */
    public void addNumberRule(String field, long value) {
        Screenshots.step("Rule " + field + " == " + value, () -> {
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add rule")).click();
            Locator fieldBox = dialog().getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName("Select field")).last();
            fieldBox.click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(field).setExact(true)).click();
            dialog().getByRole(AriaRole.SPINBUTTON, new Locator.GetByRoleOptions().setName("Enter number")).last()
                    .fill(String.valueOf(value));
        });
    }

    /** The JSON body of the API request tab. */
    public Locator apiBody() {
        return dialog().getByText(java.util.regex.Pattern.compile("^\\{.*\"rules\"", java.util.regex.Pattern.DOTALL));
    }

    public void showApiRequest() {
        Screenshots.step("Show the API request", () ->
                dialog().getByRole(AriaRole.TAB, new Locator.GetByRoleOptions().setName("API request")).click());
    }

    /** Applies the rules to the grid (the dialog stays open). */
    public void apply() {
        Screenshots.step("Apply", () ->
                dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Apply")).click());
    }

    public void close() {
        Screenshots.step("Close the rich filter", () -> {
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close")).click();
            assertThat(dialog()).isHidden();
        });
    }
}
