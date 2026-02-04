package com.emf.gateway.config;

/**
 * Configuration for a role.
 */
public class RoleConfig {
    
    private String id;
    private String name;
    
    public RoleConfig() {
    }
    
    public RoleConfig(String id, String name) {
        this.id = id;
        this.name = name;
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
    
    @Override
    public String toString() {
        return "RoleConfig{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               '}';
    }
}
