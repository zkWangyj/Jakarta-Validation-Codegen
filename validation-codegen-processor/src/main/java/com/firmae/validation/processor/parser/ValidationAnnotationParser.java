package com.firmae.validation.processor.parser;

import com.firmae.validation.processor.model.FieldValidationModel;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * 校验注解解析器
 */
public class ValidationAnnotationParser {

    private final Types typeUtils;
    private final Elements elementUtils;
    private final Messager messager;
    
    // Jakarta Validation 约束注解
    private static final Set<String> CONSTRAINT_ANNOTATIONS = new HashSet<>(Arrays.asList(
        // 空值检查
        "jakarta.validation.constraints.NotNull",
        "jakarta.validation.constraints.NotEmpty",
        "jakarta.validation.constraints.NotBlank",
        
        // 范围检查
        "jakarta.validation.constraints.Min",
        "jakarta.validation.constraints.Max",
        "jakarta.validation.constraints.DecimalMin",
        "jakarta.validation.constraints.DecimalMax",
        "jakarta.validation.constraints.Size",
        "jakarta.validation.constraints.Digits",
        
        // 模式检查
        "jakarta.validation.constraints.Pattern",
        "jakarta.validation.constraints.Email",
        
        // 时间检查
        "jakarta.validation.constraints.Future",
        "jakarta.validation.constraints.FutureOrPresent",
        "jakarta.validation.constraints.Past",
        "jakarta.validation.constraints.PastOrPresent",
        
        // 布尔检查
        "jakarta.validation.constraints.AssertTrue",
        "jakarta.validation.constraints.AssertFalse",
        
        // 正负检查
        "jakarta.validation.constraints.Positive",
        "jakarta.validation.constraints.PositiveOrZero",
        "jakarta.validation.constraints.Negative",
        "jakarta.validation.constraints.NegativeOrZero"
    ));
    
    // 默认错误消息
    private static final Map<String, String> DEFAULT_MESSAGES = new HashMap<>();
    static {
        DEFAULT_MESSAGES.put("NotNull", "不能为 null");
        DEFAULT_MESSAGES.put("NotEmpty", "不能为空");
        DEFAULT_MESSAGES.put("NotBlank", "不能为空白");
        DEFAULT_MESSAGES.put("Min", "必须大于或等于 {value}");
        DEFAULT_MESSAGES.put("Max", "必须小于或等于 {value}");
        DEFAULT_MESSAGES.put("DecimalMin", "必须大于或等于 {value}");
        DEFAULT_MESSAGES.put("DecimalMax", "必须小于或等于 {value}");
        DEFAULT_MESSAGES.put("Size", "长度必须在 {min} 到 {max} 之间");
        DEFAULT_MESSAGES.put("Digits", "数字格式不正确");
        DEFAULT_MESSAGES.put("Pattern", "格式不正确");
        DEFAULT_MESSAGES.put("Email", "必须是有效的邮箱地址");
        DEFAULT_MESSAGES.put("Future", "必须是未来时间");
        DEFAULT_MESSAGES.put("FutureOrPresent", "必须是未来或当前时间");
        DEFAULT_MESSAGES.put("Past", "必须是过去时间");
        DEFAULT_MESSAGES.put("PastOrPresent", "必须是过去或当前时间");
        DEFAULT_MESSAGES.put("AssertTrue", "必须为 true");
        DEFAULT_MESSAGES.put("AssertFalse", "必须为 false");
        DEFAULT_MESSAGES.put("Positive", "必须为正数");
        DEFAULT_MESSAGES.put("PositiveOrZero", "必须为正数或零");
        DEFAULT_MESSAGES.put("Negative", "必须为负数");
        DEFAULT_MESSAGES.put("NegativeOrZero", "必须为负数或零");
    }

    public ValidationAnnotationParser(Types typeUtils, Elements elementUtils, Messager messager) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
        this.messager = messager;
    }

    /**
     * 解析元素上的约束注解
     */
    public List<FieldValidationModel.Constraint> parseConstraints(Element element) {
        List<FieldValidationModel.Constraint> constraints = new ArrayList<>();
        
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            String annotationType = annotation.getAnnotationType().toString();
            
            // 检查是否是约束注解
            if (isConstraintAnnotation(annotationType)) {
                FieldValidationModel.Constraint constraint = parseConstraint(annotation);
                if (constraint != null) {
                    constraints.add(constraint);
                }
            }
        }
        
        return constraints;
    }

    /**
     * 解析单个约束注解
     */
    private FieldValidationModel.Constraint parseConstraint(AnnotationMirror annotation) {
        String annotationType = annotation.getAnnotationType().toString();
        String constraintType = annotationType.substring(annotationType.lastIndexOf('.') + 1);
        
        // 提取注解属性
        Map<String, Object> attributes = extractAttributes(annotation);
        
        // 获取消息
        String message = (String) attributes.getOrDefault("message", "");
        if (message.isEmpty() || message.startsWith("{")) {
            // 使用默认消息
            message = formatDefaultMessage(constraintType, attributes);
        }
        
        FieldValidationModel.Constraint constraint = new FieldValidationModel.Constraint(
            constraintType, message
        );
        
        // 添加属性（排除 message）
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!"message".equals(entry.getKey()) && !"groups".equals(entry.getKey()) && !"payload".equals(entry.getKey())) {
                constraint.addAttribute(entry.getKey(), entry.getValue());
            }
        }
        
        return constraint;
    }

    /**
     * 提取注解属性
     */
    private Map<String, Object> extractAttributes(AnnotationMirror annotation) {
        Map<String, Object> attributes = new HashMap<>();
        
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : 
                annotation.getElementValues().entrySet()) {
            String attrName = entry.getKey().getSimpleName().toString();
            Object attrValue = entry.getValue().getValue();
            
            // 处理不同类型的值
            if (attrValue instanceof String) {
                attributes.put(attrName, attrValue);
            } else if (attrValue instanceof Number) {
                attributes.put(attrName, attrValue);
            } else if (attrValue instanceof Boolean) {
                attributes.put(attrName, attrValue);
            } else if (attrValue instanceof TypeMirror) {
                attributes.put(attrName, attrValue.toString());
            } else if (attrValue instanceof List) {
                // 处理数组值
                List<?> list = (List<?>) attrValue;
                if (!list.isEmpty()) {
                    attributes.put(attrName, list);
                }
            } else if (attrValue instanceof VariableElement) {
                // 处理枚举值
                attributes.put(attrName, attrValue.toString());
            } else {
                attributes.put(attrName, attrValue.toString());
            }
        }
        
        return attributes;
    }

    /**
     * 格式化默认消息
     */
    private String formatDefaultMessage(String constraintType, Map<String, Object> attributes) {
        String message = DEFAULT_MESSAGES.getOrDefault(constraintType, "校验失败");
        
        // 替换占位符
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (message.contains(placeholder)) {
                message = message.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        
        return message;
    }

    /**
     * 检查是否是约束注解
     */
    private boolean isConstraintAnnotation(String annotationType) {
        return CONSTRAINT_ANNOTATIONS.contains(annotationType);
    }
}
