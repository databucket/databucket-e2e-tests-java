package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/** The dialog "Data history [Id: N]" of a row: one row per change (Id, Modified at, Modified by, ...). */
public final class DataHistoryDialog {

    private final Page page;

    public DataHistoryDialog(Page page) {
        this.page = page;
    }

    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("Data history \\[Id: \\d+]")));
    }

    /** The changes: the rows of its table, without the header. */
    public Locator changes() {
        return dialog().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHas(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show changes"))));
    }
}
