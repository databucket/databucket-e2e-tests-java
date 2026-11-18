package pl.databucket.e2e.support;

import io.qameta.allure.LabelAnnotation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class of API tests: tag "api" (gradle apiTest), under "api" in the tree of the report, a {@link TestProject}
 * parameter for every test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("api")
@LabelAnnotation(name = "parentSuite", value = "api")
@ExtendWith(ProjectExtension.class)
public @interface ApiSuite {
}
