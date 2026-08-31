package io.kelta.worker.module.testmodule;

import io.kelta.runtime.module.service.GreetingPort;
import io.kelta.runtime.workflow.module.KeltaModule;

import java.util.Map;

/**
 * A module that publishes a service for platform code to call, used by
 * {@code RuntimeModuleManagerServiceTest}. Its package sits outside the sandboxed classloader's
 * parent allowlist, so the class really resolves from the JAR — while {@code GreetingPort} does
 * not, and must come from the platform.
 *
 * <p>{@code getServices()} returns a NEW implementation on every call, the case unload has to
 * survive: it must withdraw the instance actually registered, not whatever a later call builds.
 */
public class ServiceProvidingTestModule implements KeltaModule {

    @Override
    public String getId() {
        return "test-module";
    }

    @Override
    public String getName() {
        return "Service Providing Test Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<Class<?>, Object> getServices() {
        return Map.of(GreetingPort.class, (GreetingPort) name -> "hello " + name + ", from the module");
    }
}
