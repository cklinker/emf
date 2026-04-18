package io.kelta.worker.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Forces JVM exit after all ApplicationRunners complete in migrate mode.
 * NATS, Hikari, OtlpMeterRegistry, and FlowEngine all create non-daemon
 * threads that would otherwise block shutdown indefinitely.
 */
@Component
@Profile("migrate")
@Order(Integer.MAX_VALUE)
public class MigrateShutdownRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        System.exit(0);
    }
}
