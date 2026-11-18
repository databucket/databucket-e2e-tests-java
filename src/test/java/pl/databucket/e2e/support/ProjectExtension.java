package pl.databucket.e2e.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Gives a test (and its {@code @BeforeEach} methods: the same instance) a {@link TestProject} parameter: created when
 * first asked for, removed after the test.
 */
public final class ProjectExtension implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ProjectExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == TestProject.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        // the store of the test method: closed (TestProject.close) when the test is over
        return context.getStore(NAMESPACE).computeIfAbsent(TestProject.class, type -> TestProject.create(), TestProject.class);
    }
}
