package com.emf.controlplane.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration enabling repository scanning.
 * Note: JPA auditing is enabled in ControlPlaneApplication.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.emf.controlplane.repository")
public class JpaConfig {
}
