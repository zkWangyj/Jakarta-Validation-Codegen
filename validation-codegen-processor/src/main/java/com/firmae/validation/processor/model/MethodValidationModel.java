package com.firmae.validation.processor.model;

import java.util.List;

/**
 * 方法的校验模型
 */
public class MethodValidationModel {
    private final String methodName;
    private final boolean isStatic;
    private final boolean isConstructor;
    private final List<ParamValidationModel> params;

    public MethodValidationModel(String methodName, boolean isStatic, boolean isConstructor, List<ParamValidationModel> params) {
        this.methodName = methodName;
        this.isStatic = isStatic;
        this.isConstructor = isConstructor;
        this.params = params;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public List<ParamValidationModel> getParams() {
        return params;
    }

    /**
     * 参数校验模型
     */
    public static class ParamValidationModel {
        private final String paramName;
        private final String paramType;
        private final int paramIndex;
        private final List<FieldValidationModel.Constraint> constraints;
        // @Validated 嵌套校验方法名（如 validateUserDTO），null 表示无嵌套校验
        private final String nestedValidateMethod;

        public ParamValidationModel(String paramName, String paramType, int paramIndex,
                                     List<FieldValidationModel.Constraint> constraints) {
            this(paramName, paramType, paramIndex, constraints, null);
        }

        public ParamValidationModel(String paramName, String paramType, int paramIndex,
                                     List<FieldValidationModel.Constraint> constraints,
                                     String nestedValidateMethod) {
            this.paramName = paramName;
            this.paramType = paramType;
            this.paramIndex = paramIndex;
            this.constraints = constraints;
            this.nestedValidateMethod = nestedValidateMethod;
        }

        public String getParamName() {
            return paramName;
        }

        public String getParamType() {
            return paramType;
        }

        public int getParamIndex() {
            return paramIndex;
        }

        public List<FieldValidationModel.Constraint> getConstraints() {
            return constraints;
        }

        public String getNestedValidateMethod() {
            return nestedValidateMethod;
        }
    }
}
