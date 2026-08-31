package io.kelta.worker.module.testmodule;

import io.kelta.runtime.module.service.CountingPort;
import io.kelta.runtime.module.service.GreetingPort;
import io.kelta.runtime.workflow.module.KeltaModule;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes two services, so a refusal of the second can be shown to withdraw the first. Ordered
 * deliberately: {@code CountingPort} is registered before {@code GreetingPort}, which the test
 * arranges to collide with an incumbent.
 */
public class TwoServiceTestModule implements KeltaModule {

    @Override
    public String getId() {
        return "two-service-module";
    }

    @Override
    public String getName() {
        return "Two Service Test Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<Class<?>, Object> getServices() {
        // LinkedHashMap: the registration order is the point of this fixture.
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(CountingPort.class, (CountingPort) () -> 42);
        services.put(GreetingPort.class, (GreetingPort) name -> "hello " + name + ", from the module");
        return services;
    }
}
