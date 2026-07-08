package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Proposes a page-builder ui-page draft (app-intelligence slice 2). The apply path
 * ({@code ProposalService.applyUiPageProposal}) validates the widget tree against the
 * FE widget catalog and creates the page UNPUBLISHED — publishing stays a human act
 * in the page builder.
 */
@Component
public class ProposeUiPageHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_ui_page";
    }

    @Override
    public String description() {
        return "Propose a new UI page (page-builder draft) composed of layout and content widgets. " +
                "Use this when the user wants a custom page, dashboard-style overview, or landing page. " +
                "The page is created as an UNPUBLISHED draft the user reviews in the page builder. " +
                "Widget types: heading, text, button, image, card, container, table, form, grid, row, " +
                "column, divider, field-value, list, repeater, chart, tabs, tab-panel, nav, icon, link, " +
                "metric, text-input, number-input, checkbox, dropdown, datepicker, lookup, " +
                "multi-picklist, rich-text. Layout: nest children under grid/row/column/container/card. " +
                "A widget prop may be a binding object {\"$bind\": \"data.<source>[0].<field>\", " +
                "\"mode\": \"path\"}; expressions (mode \"expr\") use bare variable identifiers.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string",
                                "description", "Page name, lowercase snake_case (the slug is derived from it)"),
                        "title", Map.of("type", "string", "description", "Human page title"),
                        "description", Map.of("type", "string", "description", "Short page description"),
                        "components", Map.of(
                                "type", "array",
                                "description", "Widget tree. Node shape: {type, props?, children?}. " +
                                        "Container types (grid/row/column/container/card/tabs) may nest children.",
                                "items", Map.of("type", "object")
                        ),
                        "variables", Map.of(
                                "type", "array",
                                "description", "Page variables: {name, type: string|number|boolean|json, default?}",
                                "items", Map.of("type", "object")
                        ),
                        "dataSources", Map.of(
                                "type", "array",
                                "description", "On-load data sources: {name, collection, fields?, limit?, " +
                                        "mode?: list|single}. Fetched client-side through the authorized API.",
                                "items", Map.of("type", "object")
                        )
                ),
                "required", List.of("name", "components")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("ui_page", input);
    }
}
