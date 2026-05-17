package com.firmae.validation.processor.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 约束模型
 */
public class FieldValidationModel {

    /**
     * 单个约束
     */
    public static class Constraint {
        private final String type;
        private final String message;
        private final Map<String, Object> attributes;

        public Constraint(String type, String message) {
            this.type = type;
            this.message = message;
            this.attributes = new HashMap<>();
        }

        public void addAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getAttributes() {
            return Collections.unmodifiableMap(attributes);
        }

        public Object getAttribute(String name) {
            return attributes.get(name);
        }
    }
}
