package com.costplatform.lite.extension;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A selectable option exposed by the host business system.
 *
 * <p>The value is the stable business code persisted in a rule. The label is
 * only used by the maintenance UI and must not be used as the calculation key.</p>
 */
public class CostLiteOption {
    private String value;
    private String label;
    private boolean disabled;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public CostLiteOption() {
    }

    public CostLiteOption(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : attributes;
    }
}
