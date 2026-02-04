package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for planning a migration.
 * Contains the collection ID and the proposed schema definition.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.1: Request a migration plan showing steps to migrate from current to target schema</li>
 * </ul>
 */
public class PlanMigrationRequest {

    @NotBlank(message = "Collection ID is required")
    private String collectionId;

    @NotNull(message = "Proposed definition is required")
    private ProposedDefinition proposedDefinition;

    public PlanMigrationRequest() {
    }

    public PlanMigrationRequest(String collectionId, ProposedDefinition proposedDefinition) {
        this.collectionId = collectionId;
        this.proposedDefinition = proposedDefinition;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public ProposedDefinition getProposedDefinition() {
        return proposedDefinition;
    }

    public void setProposedDefinition(ProposedDefinition proposedDefinition) {
        this.proposedDefinition = proposedDefinition;
    }

    /**
     * Represents the proposed schema definition for the migration target.
     */
    public static class ProposedDefinition {
        private String name;
        private String description;
        private java.util.List<ProposedField> fields;

        public ProposedDefinition() {
            this.fields = new java.util.ArrayList<>();
        }

        public ProposedDefinition(String name, String description, java.util.List<ProposedField> fields) {
            this.name = name;
            this.description = description;
            this.fields = fields != null ? fields : new java.util.ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public java.util.List<ProposedField> getFields() {
            return fields;
        }

        public void setFields(java.util.List<ProposedField> fields) {
            this.fields = fields;
        }
    }

    /**
     * Represents a proposed field in the target schema.
     */
    public static class ProposedField {
        private String id;
        private String name;
        private String type;
        private boolean required;
        private String description;
        private String constraints;

        public ProposedField() {
        }

        public ProposedField(String id, String name, String type, boolean required) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.required = required;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getConstraints() {
            return constraints;
        }

        public void setConstraints(String constraints) {
            this.constraints = constraints;
        }
    }
}
