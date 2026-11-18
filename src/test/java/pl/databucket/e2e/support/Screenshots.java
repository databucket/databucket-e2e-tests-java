package pl.databucket.e2e.support;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

public final class Screenshots {

    private Screenshots() {
    }

    /** A screenshot of the page, attached to the report under the given name (next to the automatic end-of-test one). */
    public static void screenshot(Page page, String name) {
        Allure.addAttachment(name, "image/png", new ByteArrayInputStream(page.screenshot()), ".png");
    }
}
