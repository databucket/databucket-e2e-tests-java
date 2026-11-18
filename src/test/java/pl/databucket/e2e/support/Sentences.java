package pl.databucket.e2e.support;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Test names from the method names, underscores as spaces, without the parameter types:
 * {@code a_wrong_password_is_refused(TestProject)} → "a wrong password is refused". {@code @DisplayName} still wins.
 */
public final class Sentences extends DisplayNameGenerator.ReplaceUnderscores {

    @Override
    public String generateDisplayNameForMethod(List<Class<?>> enclosingInstanceTypes, Class<?> testClass, Method testMethod) {
        return testMethod.getName().replace('_', ' ');
    }
}
