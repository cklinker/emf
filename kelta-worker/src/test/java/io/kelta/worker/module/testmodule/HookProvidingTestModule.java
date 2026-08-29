package io.kelta.worker.module.testmodule;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.runtime.workflow.module.KeltaModule;

import java.util.List;
import java.util.Map;

/**
 * A minimal module used by {@code RuntimeModuleManagerHookTest}, which packages this class's
 * compiled bytes into a temp JAR and loads it through the real sandboxed classloader — the same
 * path a customer-uploaded module takes.
 *
 * <p>Its package is deliberately outside {@code SandboxedModuleClassLoader}'s parent allowlist, so
 * the class genuinely resolves from the JAR rather than leaking in from the test classpath.
 *
 * <p>{@code getBeforeSaveHooks()} returns a NEW instance on every call, which is the case the
 * manager has to survive: unload must remove the instances it actually registered, not whatever a
 * later call happens to build.
 */
public class HookProvidingTestModule implements KeltaModule {

    @Override
    public String getId() {
        return "test-module";
    }

    @Override
    public String getName() {
        return "Hook Providing Test Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<BeforeSaveHook> getBeforeSaveHooks() {
        return List.of(new BeforeSaveHook() {
            @Override
            public String getCollectionName() {
                return "orders";
            }

            @Override
            public BeforeSaveResult beforeCreate(String collectionName,
                                                 Map<String, Object> record, String tenantId) {
                return BeforeSaveResult.error("_record", "vetoed by module");
            }
        });
    }
}
