package com.featureflags.model;

public class FeatureFlag {
    private String orgId;
    private String name;
    private boolean value;

    public FeatureFlag() {
    }

    public FeatureFlag(String orgId, String name, boolean value) {
        this.orgId = orgId;
        this.name = name;
        this.value = value;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}