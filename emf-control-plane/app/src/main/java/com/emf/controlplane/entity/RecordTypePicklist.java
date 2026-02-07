package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "record_type_picklist")
public class RecordTypePicklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_type_id", nullable = false)
    private RecordType recordType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @Column(name = "available_values", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String availableValues;

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    public RecordTypePicklist() {
        super();
    }

    public RecordTypePicklist(RecordType recordType, Field field, String availableValues) {
        super();
        this.recordType = recordType;
        this.field = field;
        this.availableValues = availableValues;
    }

    public RecordType getRecordType() { return recordType; }
    public void setRecordType(RecordType recordType) { this.recordType = recordType; }

    public Field getField() { return field; }
    public void setField(Field field) { this.field = field; }

    public String getAvailableValues() { return availableValues; }
    public void setAvailableValues(String availableValues) { this.availableValues = availableValues; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
