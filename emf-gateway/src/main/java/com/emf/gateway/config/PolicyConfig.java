package com.emf.gateway.config;

import java.util.Map;

/**
 * Configuration for an authorization policy.
 */
public class PolicyConfig {
    
    private String id;
    private String name;
    private Map<String, Object> rules;
    
    public PolicyConfig() {
    }
    
    public PolicyConfig(String id, String name, Map<String, Object> rules) {
        this.id = id;
        this.name = name;
        this.rules = rules;
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
    
    public Map<String, Object> getRules() {
        return rules;
    }
    
    public void setRules(Map<String, Object> rules) {
        this.rules = rules;
    }
    
    @Override
    public String toString() {
        return "PolicyConfig{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", rules=" + rules +
               '}';
    }
}
