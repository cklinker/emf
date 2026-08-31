package io.kelta.runtime.module.service;

/**
 * A stand-in platform port for {@code RuntimeModuleManagerServiceTest}, deliberately declared in
 * {@code io.kelta.runtime.module.service} — inside {@code SandboxedModuleClassLoader}'s parent
 * allowlist — so a module loaded from a JAR resolves the platform's own class rather than a copy.
 * That is the arrangement a real port such as an entitlement provider has.
 */
public interface GreetingPort {

    String greet(String name);
}
