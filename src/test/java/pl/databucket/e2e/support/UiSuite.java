package pl.databucket.e2e.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class of GUI tests: tag "ui" (gradle uiTest), {@link TestProject} and
 * {@link com.microsoft.playwright.Page} parameters for every test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("ui")
@ExtendWith({ProjectExtension.class, BrowserExtension.class})
public @interface UiSuite {
}
