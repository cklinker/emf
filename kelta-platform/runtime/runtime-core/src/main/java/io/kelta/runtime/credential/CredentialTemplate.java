package io.kelta.runtime.credential;

import tools.jackson.databind.JsonNode;

/**
 * Pre-built credential template for a popular provider (Salesforce, Slack,
 * GitHub, Stripe, etc.). Templates seed common values (auth URLs, scopes,
 * header names) so users only have to enter the secret material.
 *
 * @param key       stable identifier (e.g., "salesforce")
 * @param name      human-readable display name
 * @param type      target {@link CredentialType} key (e.g., "oauth2_authorization_code")
 * @param iconUrl   optional URL to a provider logo (rendered in the UI)
 * @param defaults  prefilled input values to merge into the form (UI sets
 *                  these as initial state; user can still edit)
 */
public record CredentialTemplate(
    String key,
    String name,
    String type,
    String iconUrl,
    JsonNode defaults
) {
}
