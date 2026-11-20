package pl.databucket.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gives a test a {@link Page} parameter: a new browser context (own cookies and storage) in Chromium, with the
 * Databucket URL as base URL. Playwright isn't thread-safe, so every thread running tests has its own Playwright and
 * browser, closed when the tests are over.
 * <p>
 * In the report: a screenshot of the end of every test, the Playwright trace of every test (open it at
 * https://trace.playwright.dev or with {@code npx playwright show-trace}), a video of the failed ones.
 */
public final class BrowserExtension implements ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);
    private static final List<Playwright> ALL = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Browser> BROWSER = ThreadLocal.withInitial(BrowserExtension::launch);

    static {
        PlaywrightAssertions.setDefaultAssertionTimeout(10_000);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> ALL.forEach(Playwright::close)));
    }

    private static Browser launch() {
        Playwright playwright = Playwright.create();
        ALL.add(playwright);
        return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!Env.HEADED));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).computeIfAbsent(Page.class, type -> newPage(), Page.class);
    }

    private static Page newPage() {
        try {
            Path videos = Files.createTempDirectory("e2e-video");
            BrowserContext browserContext = BROWSER.get().newContext(new Browser.NewContextOptions()
                    .setBaseURL(Env.BASE_URL)
                    // English whatever the system language: the server translates some messages (Accept-Language)
                    .setLocale("en-US")
                    .setViewportSize(1440, 900)
                    .setRecordVideoDir(videos)
                    .setRecordVideoSize(1440, 900));
            browserContext.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
            return browserContext.newPage();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        Page page = context.getStore(NAMESPACE).remove(Page.class, Page.class);
        if (page == null) return;
        BrowserContext browserContext = page.context();
        boolean failed = context.getExecutionException().isPresent();

        attach("screenshot", "image/png", ".png", page.screenshot());

        Path trace = Files.createTempFile("e2e-trace", ".zip");
        browserContext.tracing().stop(new Tracing.StopOptions().setPath(trace));
        attach("trace", "application/zip", ".zip", trace);

        // the video is written when the context is closed
        Path video = page.video() != null ? page.video().path() : null;
        browserContext.close();
        if (video != null) {
            if (failed) attach("video", "video/webm", ".webm", video);
            Files.deleteIfExists(video);
        }
        Files.deleteIfExists(trace);
    }

    private static void attach(String name, String type, String extension, byte[] content) {
        Allure.addAttachment(name, type, new java.io.ByteArrayInputStream(content), extension);
    }

    private static void attach(String name, String type, String extension, Path file) throws IOException {
        try (InputStream content = Files.newInputStream(file)) {
            Allure.addAttachment(name, type, content, extension);
        }
    }
}
