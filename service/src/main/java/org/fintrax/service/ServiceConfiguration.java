package org.fintrax.service;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ServiceConfiguration.class)
public class ServiceConfiguration {
}
