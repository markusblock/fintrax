package org.fintrax.testkit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class FinTsMockExtension implements ParameterResolver {
    private FinTsMockServer mockServer;

    public FinTsMockExtension withServer(FinTsMockServer server) {
        this.mockServer = server;
        return this;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == FinTsMockServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (mockServer == null) {
            mockServer = new FinTsMockServer();
        }
        return mockServer;
    }
}
