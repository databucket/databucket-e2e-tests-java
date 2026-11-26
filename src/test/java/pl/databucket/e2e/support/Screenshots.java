package pl.databucket.e2e.support;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.StepResult;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Screenshots in the step they concern, not at the end of the test.
 * <ul>
 *   <li>{@link #step(String, Runnable)}: a GUI step (used by the page objects) with a screenshot of the page when it
 *       ends, also when it fails;</li>
 *   <li>a verification made by a GUI test as a step of its own gets a screenshot of what was checked
 *       ({@link Verify});</li>
 *   <li>{@link #screenshot(String)}: one more screenshot in the current step (e.g. an open menu), or a step
 *       "screenshot: name" of the test.</li>
 * </ul>
 * The page is the one of the running GUI test ({@link BrowserExtension}); without one (API tests) nothing is taken.
 */
public final class Screenshots {

    private static final String HIDDEN = ".MuiTooltip-popper { display: none !important; }";

    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    // per open GUI step: true when the step already has its screenshot (taken by screenshot(name) inside it)
    private static final ThreadLocal<Deque<boolean[]>> STEPS = ThreadLocal.withInitial(ArrayDeque::new);

    private Screenshots() {
    }

    static void use(Page page) {
        PAGE.set(page);
        STEPS.get().clear();
    }

    static void clear() {
        PAGE.remove();
        STEPS.get().clear();
    }

    /** A GUI step: the action, then a screenshot of the page in the step (also when the action fails). */
    public static void step(String name, Runnable action) {
        step(name, () -> {
            action.run();
            return null;
        });
    }

    /** As {@link #step(String, Runnable)}, returning what the action read. */
    public static <T> T step(String name, Supplier<T> action) {
        return Allure.step(name, (Allure.ThrowableContextRunnable<T, Allure.StepContext>) context -> {
            boolean[] taken = {false};
            STEPS.get().push(taken);
            try {
                return action.get();
            } finally {
                STEPS.get().pop();
                if (!taken[0]) attachHere("screen");
            }
        });
    }

    /**
     * A screenshot now: in the current GUI step (which then takes no other at its end), or as a step "screenshot:
     * name" of the test.
     */
    public static void screenshot(String name) {
        AllureLifecycle lifecycle = Allure.getLifecycle();
        if (Objects.equals(lifecycle.getCurrentTestCaseOrStep(), lifecycle.getCurrentTestCase())) {
            Allure.step("screenshot: " + name, () -> attachHere(name));
            return;
        }
        boolean[] taken = STEPS.get().peek();
        if (taken != null) taken[0] = true;
        attachHere(name);
    }

    /** Adds a screenshot of the page to a step already made (a verification); nothing without a page. */
    static void attachTo(StepResult step, String name) {
        byte[] png = capture();
        if (png == null) return;
        String source = UUID.randomUUID() + "-attachment.png";
        Allure.getLifecycle().writeAttachment(source, new ByteArrayInputStream(png));
        step.getAttachments().add(new Attachment().setName(name).setType("image/png").setSource(source));
    }

    private static void attachHere(String name) {
        byte[] png = capture();
        if (png != null) Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), ".png");
    }

    private static byte[] capture() {
        Page page = PAGE.get();
        if (page == null || page.isClosed()) return null;
        try {
            // without the tooltip of the button just clicked (the mouse stays over it), which covers the page
            return page.screenshot(new Page.ScreenshotOptions().setStyle(HIDDEN));
        } catch (RuntimeException e) {
            // a screenshot must not hide what the step did (e.g. the page is navigating)
            return null;
        }
    }
}
