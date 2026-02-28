package com.emf.runtime.flow;

/**
 * Describes an {@link com.emf.runtime.workflow.ActionHandler}'s UI integration contract.
 * <p>
 * Every action handler (built-in or runtime-loaded) can provide a descriptor that
 * tells the visual flow builder how to render its configuration form, what inputs
 * it expects, and what outputs it produces.
 * <p>
 * Handlers that return {@code null} from {@code ActionHandler.getDescriptor()}
 * get a generic raw JSON config editor. Handlers that return a descriptor get
 * a rich, schema-driven form.
 *
 * @since 1.0.0
 */
public interface ActionHandlerDescriptor {

    /**
     * JSON Schema defining the handler's configuration fields.
     * The UI renders a form from this schema instead of showing raw JSON.
     *
     * @return JSON Schema string, or null for default raw JSON editor
     */
    String getConfigSchema();

    /**
     * JSON Schema defining the input this handler expects.
     * Used by the UI for InputPath autocomplete suggestions.
     *
     * @return JSON Schema string, or null if unspecified
     */
    String getInputSchema();

    /**
     * JSON Schema defining the output this handler produces.
     * Used by downstream steps for ResultPath/InputPath autocomplete
     * and by Choice rules for variable path suggestions.
     *
     * @return JSON Schema string, or null if unspecified
     */
    String getOutputSchema();

    /**
     * Human-readable display name (e.g., "Create Charge").
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Category for grouping in the resource selector (e.g., "Payment", "Data", "Communication").
     *
     * @return category name
     */
    String getCategory();

    /**
     * Short description shown as tooltip in the resource selector.
     *
     * @return description text
     */
    String getDescription();

    /**
     * Icon identifier (maps to lucide-react icon name).
     *
     * @return icon name, or null for default
     */
    default String getIcon() {
        return null;
    }
}
