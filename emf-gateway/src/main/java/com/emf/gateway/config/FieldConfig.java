package com.emf.gateway.config;

/**
 * Configuration for a field within a collection.
 */
public class FieldConfig {
    
    private String name;
    private String type;
    
    public FieldConfig() {
    }
    
    public FieldConfig(String name, String type) {
        this.name = name;
        this.type = type;
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
    
    @Override
    public String toString() {
        return "FieldConfig{" +
               "name='" + name + '\'' +
               ", type='" + type + '\'' +
               '}';
    }
}
