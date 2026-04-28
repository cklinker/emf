package io.kelta.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * Tiny helpers that build {@link ToolAnnotations} for kelta-mcp tools.
 *
 * <p>The MCP protocol lets a server hint at a tool's safety / semantics so
 * clients can make smarter UX choices: which tools to preload, which need
 * an explicit user-confirm before invocation, which are safe to retry, etc.
 * Today every kelta tool ships with no annotations at all, leaving clients
 * with no signal that {@code query_collection} is a safe read or that
 * {@code delete_record} is destructive.
 *
 * <p>Two factories cover everything in this codebase — every tool is either
 * a read against the gateway ({@link #read()}) or a mutation
 * ({@link #write(boolean, boolean)}). All tools call out to the in-cluster
 * gateway, so {@code openWorldHint} is always {@code true}.
 *
 * <p>The friendly display title is set via {@code Tool.builder().title(...)}
 * at the top level of the Tool object, not duplicated here — the SDK's
 * {@code ToolAnnotations.title} field is a redundant alias.
 *
 * <p>Field reference (from the MCP spec):
 * <ul>
 *   <li>{@code readOnlyHint} — {@code true} means the tool does not modify
 *       any state.</li>
 *   <li>{@code destructiveHint} — only meaningful when
 *       {@code readOnlyHint=false}; {@code true} means the tool may make
 *       destructive updates (delete, overwrite, mass-mutate).</li>
 *   <li>{@code idempotentHint} — {@code true} means repeating a call with
 *       the same arguments has no additional effect; clients can retry
 *       freely.</li>
 *   <li>{@code openWorldHint} — {@code true} means the tool talks to systems
 *       outside the LLM's own process (a gateway, an external API).</li>
 *   <li>{@code returnDirect} — left {@code null}; we don't currently bypass
 *       the model with raw tool output.</li>
 * </ul>
 */
public final class ToolHints {

    private ToolHints() {}

    /**
     * Annotations for a read-only tool: {@code readOnlyHint=true},
     * {@code idempotentHint=true}, {@code openWorldHint=true}.
     * {@code destructiveHint} is left {@code null} since it's only
     * meaningful for mutating tools.
     */
    public static ToolAnnotations read() {
        return new ToolAnnotations(null, true, null, true, true, null);
    }

    /**
     * Annotations for a mutating tool: {@code readOnlyHint=false},
     * {@code openWorldHint=true}. The caller declares whether the
     * mutation may be destructive (deletes, overwrites) and whether
     * repeating the call with the same arguments lands the system in
     * the same state.
     *
     * @param destructive {@code true} if the call may delete or overwrite
     *     existing data; {@code false} for additive operations like
     *     creating a new record
     * @param idempotent {@code true} if calling with the same arguments
     *     produces the same end state; {@code false} when each call has a
     *     fresh effect (e.g. creating a new record with auto-generated id,
     *     starting a new flow execution)
     */
    public static ToolAnnotations write(boolean destructive, boolean idempotent) {
        return new ToolAnnotations(null, false, destructive, idempotent, true, null);
    }
}
