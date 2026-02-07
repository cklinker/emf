package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "picklist_dependency")
public class PicklistDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "controlling_field_id", nullable = false)
    private Field controllingField;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dependent_field_id", nullable = false)
    private Field dependentField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping", nullable = false, columnDefinition = "jsonb")
    private String mapping;

    public PicklistDependency() {
        super();
    }

    public PicklistDependency(Field controllingField, Field dependentField, String mapping) {
        super();
        this.controllingField = controllingField;
        this.dependentField = dependentField;
        this.mapping = mapping;
    }

    public Field getControllingField() { return controllingField; }
    public void setControllingField(Field controllingField) { this.controllingField = controllingField; }

    public Field getDependentField() { return dependentField; }
    public void setDependentField(Field dependentField) { this.dependentField = dependentField; }

    public String getMapping() { return mapping; }
    public void setMapping(String mapping) { this.mapping = mapping; }
}
