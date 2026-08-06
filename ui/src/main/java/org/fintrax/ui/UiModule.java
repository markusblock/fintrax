package org.fintrax.ui;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UiModule.class)
public final class UiModule {
    private UiModule() {}
}
