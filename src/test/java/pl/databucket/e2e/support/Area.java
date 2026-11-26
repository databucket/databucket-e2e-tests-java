package pl.databucket.e2e.support;

import io.qameta.allure.LabelAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The area of the application a test class checks: the top level of the tree of the report (Suites), with the
 * classes under it. The API and the WWW (GUI) tests have separate reports (gradle -Ptype=api / -Ptype=www).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@LabelAnnotation(name = "parentSuite")
public @interface Area {

    String DATA = "Data";
    String SETTINGS = "Settings";
    String MANAGEMENT = "Management";
    String ACCESS = "Access";
    String MCP = "MCP";
    String FIXED_BUGS = "Fixed bugs";

    String value();
}
