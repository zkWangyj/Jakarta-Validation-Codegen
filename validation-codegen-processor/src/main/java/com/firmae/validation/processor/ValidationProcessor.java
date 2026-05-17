package com.firmae.validation.processor;

import com.firmae.validation.processor.model.ClassValidationModel;
import com.firmae.validation.processor.model.FieldValidationModel;
import com.firmae.validation.processor.model.MethodValidationModel;
import com.firmae.validation.processor.parser.ValidationAnnotationParser;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Jakarta Validation 注解处理器
 * 
 * 处理流程:
 * 1. 扫描所有带 @PreCompile 注解的方法/构造器
 * 2. 解析方法参数上的校验注解
 * 3. 直接修改源码，在方法体开头注入 ValidationHelper.validateParameter(...) 调用
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({"validation.debug", "validation.sourceDir"})
public class ValidationProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Messager messager;
    private Filer filer;
    private ValidationAnnotationParser annotationParser;
    
    private final Set<String> processedClasses = new HashSet<>();

    private static final String PRE_COMPILE_ANNOTATION = "com.firmae.validation.annotation.PreCompile";
    private static final String VALIDATED_ANNOTATION = "com.firmae.validation.annotation.Validated";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.annotationParser = new ValidationAnnotationParser(typeUtils, elementUtils, messager);
        
        messager.printMessage(Diagnostic.Kind.NOTE, "ValidationProcessor initialized");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        for (Element element : roundEnv.getRootElements()) {
            String className = ((TypeElement) element).getQualifiedName().toString();
            if (className.startsWith("com.firmae.validation.")) {
                continue;
            }
            
            ElementKind kind = element.getKind();
            if (kind == ElementKind.CLASS || kind == ElementKind.ENUM 
                    || kind == ElementKind.INTERFACE || kind == ElementKind.ANNOTATION_TYPE
                    || "RECORD".equals(kind.name())) {
                processClass((TypeElement) element);
            }
        }

        return false;
    }

    private void processClass(TypeElement classElement) {
        String className = classElement.getQualifiedName().toString();
        
        if (processedClasses.contains(className)) {
            return;
        }
        processedClasses.add(className);

        List<MethodValidationModel> methodModels = new ArrayList<>();

        for (Element enclosed : classElement.getEnclosedElements()) {
            ElementKind enclosedKind = enclosed.getKind();
            
            if (enclosedKind != ElementKind.METHOD && enclosedKind != ElementKind.CONSTRUCTOR) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;
            String methodName = method.getSimpleName().toString();
            boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
            boolean isConstructor = (enclosedKind == ElementKind.CONSTRUCTOR);
            
            boolean methodHasPreCompile = hasAnnotation(method, PRE_COMPILE_ANNOTATION);
            if (!methodHasPreCompile) {
                continue;
            }
            
            List<MethodValidationModel.ParamValidationModel> paramModels = new ArrayList<>();
            List<? extends VariableElement> parameters = method.getParameters();
            
            for (int i = 0; i < parameters.size(); i++) {
                VariableElement param = parameters.get(i);
                String paramName = param.getSimpleName().toString();
                TypeMirror paramType = param.asType();
                
                List<FieldValidationModel.Constraint> constraints = annotationParser.parseConstraints(param);
                
                boolean hasValidated = hasAnnotation(param, VALIDATED_ANNOTATION);
                String nestedValidateMethod = null;
                
                if (hasValidated && paramType instanceof DeclaredType) {
                    String paramTypeName = paramType.toString();
                    String simpleTypeName = paramTypeName.substring(paramTypeName.lastIndexOf('.') + 1);
                    nestedValidateMethod = "validate" + simpleTypeName;
                }
                
                if (!constraints.isEmpty() || nestedValidateMethod != null) {
                    paramModels.add(new MethodValidationModel.ParamValidationModel(
                        paramName, paramType.toString(), i, constraints, nestedValidateMethod));
                }
            }
            
            if (!paramModels.isEmpty()) {
                methodModels.add(new MethodValidationModel(
                    methodName, isStatic, isConstructor, paramModels));
            }
        }

        if (!methodModels.isEmpty()) {
            try {
                injectValidationCalls(className, methodModels);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to inject validation for " + className + ": " + e.getMessage());
            }
        }
    }

    private boolean hasAnnotation(Element element, String annotationFqcn) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            String annotationType = annotation.getAnnotationType().toString();
            if (annotationType.equals(annotationFqcn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 直接修改源文件，在方法体开头注入校验调用
     */
    private void injectValidationCalls(String className, List<MethodValidationModel> methods) throws IOException {
        String sourcePath = getSourcePath(className);
        if (sourcePath == null) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Could not find source file for: " + className);
            return;
        }
        
        Path path = Paths.get(sourcePath);
        if (!Files.exists(path)) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Source file does not exist: " + sourcePath);
            return;
        }
        
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String modified = content;
        
        for (MethodValidationModel method : methods) {
            modified = injectMethodValidation(modified, method);
        }
        
        if (!modified.equals(content)) {
            // 添加 import
            if (!modified.contains("import com.firmae.validation.ValidationHelper;")) {
                modified = modified.replaceFirst(
                    "(package\\s+[^;]+;)", 
                    "$1\n\nimport com.firmae.validation.ValidationHelper;");
            }
            
            Files.write(path, modified.getBytes(StandardCharsets.UTF_8));
            messager.printMessage(Diagnostic.Kind.NOTE, "Injected validation calls into: " + sourcePath);
        }
    }

    private String getSourcePath(String className) {
        String sourceDir = processingEnv.getOptions().get("validation.sourceDir");
        if (sourceDir == null) {
            sourceDir = System.getProperty("user.dir") + "/src/main/java";
        }
        String relativePath = className.replace('.', '/') + ".java";
        return sourceDir + "/" + relativePath;
    }

    private String injectMethodValidation(String content, MethodValidationModel method) {
        String methodName = method.getMethodName();
        boolean isConstructor = method.isConstructor();
        
        // 构建校验调用语句
        StringBuilder validationCalls = new StringBuilder();
        for (MethodValidationModel.ParamValidationModel param : method.getParams()) {
            for (FieldValidationModel.Constraint constraint : param.getConstraints()) {
                String call = buildValidateParameterCall(param.getParamName(), constraint);
                validationCalls.append("        ").append(call).append("\n");
            }
            if (param.getNestedValidateMethod() != null) {
                validationCalls.append("        // TODO: nested validation for " + param.getParamName()).append("\n");
            }
        }
        
        String calls = validationCalls.toString();
        
        if (isConstructor) {
            return injectIntoConstructor(content, methodName, calls);
        } else {
            return injectIntoMethod(content, methodName, calls);
        }
    }

    private String buildValidateParameterCall(String paramName, FieldValidationModel.Constraint constraint) {
        String type = constraint.getType();
        String message = escapeJava(constraint.getMessage());
        
        switch (type) {
            case "NotNull":
                return "ValidationHelper.checkNotNull(" + paramName + ", \"" + message + "\");";
                
            case "NotBlank":
                return "ValidationHelper.checkNotBlank(" + paramName + ", \"" + message + "\");";
                
            case "NotEmpty":
                return "ValidationHelper.checkNotEmpty(" + paramName + ", \"" + message + "\");";
                
            case "Size": {
                int min = getIntAttribute(constraint, "min", 0);
                int max = getIntAttribute(constraint, "max", Integer.MAX_VALUE);
                String sizeMessage = message.replace("{min}", String.valueOf(min)).replace("{max}", String.valueOf(max));
                return "ValidationHelper.checkSize(" + paramName + ", " + min + ", " + max + ", \"" + escapeJava(sizeMessage) + "\");";
            }
                
            case "Min": {
                long val = getLongAttribute(constraint, "value", Long.MIN_VALUE);
                String minMessage = message.replace("{value}", String.valueOf(val));
                return "ValidationHelper.checkMin(" + paramName + ", " + val + "L, \"" + escapeJava(minMessage) + "\");";
            }
                
            case "Max": {
                long val = getLongAttribute(constraint, "value", Long.MAX_VALUE);
                String maxMessage = message.replace("{value}", String.valueOf(val));
                return "ValidationHelper.checkMax(" + paramName + ", " + val + "L, \"" + escapeJava(maxMessage) + "\");";
            }
                
            case "Pattern": {
                String regex = getStringAttribute(constraint, "regexp", ".*");
                return "ValidationHelper.checkPattern(" + paramName + ", \"" + escapeJava(regex) + "\", \"" + message + "\");";
            }
                
            case "Email":
                return "ValidationHelper.checkEmail(" + paramName + ", \"" + message + "\");";
                
            case "Positive":
                return "ValidationHelper.checkPositive(" + paramName + ", \"" + message + "\");";
                
            case "PositiveOrZero":
                return "ValidationHelper.checkPositiveOrZero(" + paramName + ", \"" + message + "\");";
                
            case "Negative":
                return "ValidationHelper.checkNegative(" + paramName + ", \"" + message + "\");";
                
            case "NegativeOrZero":
                return "ValidationHelper.checkNegativeOrZero(" + paramName + ", \"" + message + "\");";
                
            case "AssertTrue":
                return "ValidationHelper.checkAssertTrue(" + paramName + ", \"" + message + "\");";
                
            case "AssertFalse":
                return "ValidationHelper.checkAssertFalse(" + paramName + ", \"" + message + "\");";
                
            default:
                return "";
        }
    }

    private String injectIntoConstructor(String content, String methodName, String calls) {
        // 匹配 public ClassName(...) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(public\\s+\\w+\\s*\\([^)]*\\)\\s*\\{)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String replacement = matcher.group(1) + "\n" + calls;
            return matcher.replaceFirst(replacement);
        }
        return content;
    }

    private String injectIntoMethod(String content, String methodName, String calls) {
        // 匹配 public [static] ReturnType methodName(...) {
        String patternStr = "(public\\s+(?:static\\s+)?(?:void|[\\w<>\\[\\],\\s]+)\\s+" 
            + java.util.regex.Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String replacement = matcher.group(1) + "\n" + calls;
            return matcher.replaceFirst(replacement);
        }
        return content;
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int getIntAttribute(FieldValidationModel.Constraint constraint, String name, int defaultValue) {
        Object value = constraint.getAttribute(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private long getLongAttribute(FieldValidationModel.Constraint constraint, String name, long defaultValue) {
        Object value = constraint.getAttribute(name);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private String getStringAttribute(FieldValidationModel.Constraint constraint, String name, String defaultValue) {
        Object value = constraint.getAttribute(name);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}
