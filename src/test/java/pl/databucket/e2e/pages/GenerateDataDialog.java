package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Screenshots;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The dialog "Generate data" of the data grid (Import/Export menu, view feature Generate): a table with a generator
 * per property (name, path, generator, settings), the number of rows, Preview and Generate.
 */
public final class GenerateDataDialog {

    private final Page page;

    public GenerateDataDialog(Page page) {
        this.page = page;
    }

    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Generate data"));
    }

    /** The rows of the generators' table (the first table of the dialog), without its header. */
    public Locator generators() {
        return dialog().getByRole(AriaRole.TABLE).first().locator("tbody tr");
    }

    /** The generator row of a property path. */
    public Locator generator(String path) {
        return generators().filter(new Locator.FilterOptions().setHas(
                page.locator("input[aria-label='Property path'][value='" + path + "']")));
    }

    /** The settings input of a property, e.g. the text of a text generator. */
    public Locator settings(String path) {
        return generator(path).locator("td").nth(3).locator("input").first();
    }

    /** The preview table (the second table of the dialog). */
    public Locator preview() {
        return dialog().getByRole(AriaRole.TABLE).nth(1);
    }

    public Locator rows() {
        return dialog().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName("Rows"));
    }

    public Locator error() {
        return dialog().getByRole(AriaRole.ALERT);
    }

    public void setRows(int count) {
        Screenshots.step("Rows: " + count, () -> rows().fill(String.valueOf(count)));
    }

    public void setText(String path, String text) {
        Screenshots.step(path + ": " + text, () -> settings(path).fill(text));
    }

    public void addProperty(String path, String text) {
        Screenshots.step("Add property " + path + ": " + text, () -> {
            dialog().locator("[aria-label='Add property'] button").click();
            Locator added = generators().last();
            added.locator("input[aria-label='Property path']").fill(path);
            added.locator("td").nth(3).locator("input").first().fill(text);
        });
    }

    public void remove(String path) {
        Screenshots.step("Remove property " + path, () ->
                generator(path).locator("[aria-label='Remove'] button").click());
    }

    public void previewRows() {
        Screenshots.step("Preview", () -> {
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Preview")).click();
            assertThat(preview()).isVisible();
        });
    }

    /** Generates and waits until the dialog closes. */
    public void generate(int count) {
        Screenshots.step("Generate " + count + " rows", () -> {
            dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                    .setName(Pattern.compile("^Generate " + count + " rows$"))).click();
            assertThat(dialog()).isHidden();
        });
    }

    /** Clicks Generate expecting a refusal shown in the dialog. */
    public void tryGenerate() {
        Screenshots.step("Try to generate", () -> dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Generate"))).click());
    }
}
