package io.kelta.runtime.workflow;

/**
 * Thrown by a quarantined module's action handler: the module is installed and its action key is
 * registered, but its code is not running.
 *
 * <p>Thrown rather than returned as {@link ActionResult#failure(String)} because
 * {@code TaskStateExecutor} gives every failed {@code ActionResult} the generic error code
 * {@code ActionFailed}, while a thrown exception contributes its own simple name. A flow can
 * therefore Catch {@code ModuleUnavailableException} specifically, instead of catching every action
 * failure in the flow to handle one broken module.
 *
 * @since 1.0.0
 */
public class ModuleUnavailableException extends RuntimeException {

    private final String moduleId;

    public ModuleUnavailableException(String moduleId, String version, String reason) {
        super("Module '" + moduleId + "' v" + version + " is not running: " + reason);
        this.moduleId = moduleId;
    }

    public String moduleId() {
        return moduleId;
    }
}
