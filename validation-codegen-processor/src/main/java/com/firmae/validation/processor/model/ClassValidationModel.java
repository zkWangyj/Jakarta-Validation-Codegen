package com.firmae.validation.processor.model;

import java.util.List;

/**
 * 类的校验模型
 */
public class ClassValidationModel {
    private final String className;
    private final List<MethodValidationModel> methods;

    public ClassValidationModel(String className, List<MethodValidationModel> methods) {
        this.className = className;
        this.methods = methods;
    }

    public String getClassName() {
        return className;
    }

    public List<MethodValidationModel> getMethods() {
        return methods;
    }
}
