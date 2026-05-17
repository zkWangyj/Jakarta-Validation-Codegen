package com.firmae.validation.processor;

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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Jakarta Validation 注解处理器
 * 处理流程:
 * 1. 扫描所有带 @PreCompile 注解的方法/构造器
 * 2. 解析方法参数上的校验注解
 * 3. 直接修改源码，在方法体开头注入 ValidationHelper.validateParameter(...) 调用
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({ "validation.debug", "validation.sourceDir" })
public class ValidationProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Messager messager;
    private Filer filer;
    private ValidationAnnotationParser annotationParser;

    private final Set<String> processedClasses = new HashSet<>();
    private boolean springSupport = false;
    private boolean springHolderGenerated = false;
    private static final java.util.regex.Pattern SPRING_PLACEHOLDER_PATTERN = java.util.regex.Pattern
            .compile("^\\$\\{(.+)}$");

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
        this.springSupport = elementUtils.getTypeElement("org.springframework.context.ApplicationContext") != null
                && elementUtils.getTypeElement("org.springframework.core.env.Environment") != null;

        messager.printMessage(Diagnostic.Kind.NOTE, "ValidationProcessor initialized");
        messager.printMessage(Diagnostic.Kind.NOTE, "Spring support " + (springSupport ? "enabled" : "disabled"));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        if (!springHolderGenerated) {
            generateSpringConfigHolder();
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
            if (modified.contains("SpringConfigHolder")
                    && !modified.contains("import com.firmae.validation.SpringConfigHolder;")) {
                modified = modified.replaceFirst(
                        "(package\\s+[^;]+;)",
                        "$1\n\nimport com.firmae.validation.SpringConfigHolder;");
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

    private void generateSpringConfigHolder() {
        springHolderGenerated = true;
        try {
            JavaFileObject sourceFile = filer.createSourceFile("com.firmae.validation.SpringConfigHolder");
            try (Writer writer = sourceFile.openWriter()) {
                if (springSupport) {
                    writer.write("package com.firmae.validation;\n\n");
                    writer.write("import org.springframework.context.ApplicationContext;\n");
                    writer.write("import org.springframework.core.env.Environment;\n\n");
                    writer.write("public class SpringConfigHolder {\n");
                    writer.write("    private static boolean springSupport = false;\n");
                    writer.write("    private static ApplicationContext applicationContext;\n");
                    writer.write("    private static Environment environment;\n\n");
                    writer.write("    public SpringConfigHolder(ApplicationContext context) {\n");
                    writer.write("        applicationContext = context;\n");
                    writer.write("        environment = context.getEnvironment();\n");
                    writer.write("        springSupport = true;\n");
                    writer.write("    }\n\n");
                    writer.write("    public static String getProperty(String key) {\n");
                    writer.write("        return environment.getProperty(key);\n");
                    writer.write("    }\n\n");
                    writer.write("    public static String getProperty(String key, String defaultValue) {\n");
                    writer.write("        return environment.getProperty(key, defaultValue);\n");
                    writer.write("    }\n\n");
                    writer.write("    public static <T> T getProperty(String key, Class<T> targetType) {\n");
                    writer.write("        return environment.getProperty(key, targetType);\n");
                    writer.write("    }\n\n");
                    writer.write(
                            "    public static <T> T getProperty(String key, Class<T> targetType, T defaultValue) {\n");
                    writer.write("        return environment.getProperty(key, targetType, defaultValue);\n");
                    writer.write("    }\n\n");
                    writer.write("    public static boolean isSpringSupport() {\n");
                    writer.write("        return springSupport;\n");
                    writer.write("    }\n");
                    writer.write("}\n");
                } else {
                    writer.write("package com.firmae.validation;\n\n");
                    writer.write("public class SpringConfigHolder {\n");
                    writer.write("    private static final boolean springSupport = false;\n\n");
                    writer.write("    public static boolean isSpringSupport() {\n");
                    writer.write("        return springSupport;\n");
                    writer.write("    }\n");
                    writer.write("}\n");
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to generate SpringConfigHolder: " + e.getMessage());
        }
    }

    private boolean isSpringPlaceholder(String value) {
        return value != null && SPRING_PLACEHOLDER_PATTERN.matcher(value).matches();
    }

    private String extractSpringPropertyKey(String placeholder) {
        java.util.regex.Matcher matcher = SPRING_PLACEHOLDER_PATTERN.matcher(placeholder);
        return matcher.matches() ? matcher.group(1) : placeholder;
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
                String sizeMessage = message.replace("{min}", String.valueOf(min)).replace("{max}",
                        String.valueOf(max));
                return "ValidationHelper.checkSize(" + paramName + ", " + min + ", " + max + ", \""
                        + escapeJava(sizeMessage) + "\");";
            }

            case "SizeString": {
                String minRaw = getStringAttribute(constraint, "min", "0");
                String maxRaw = getStringAttribute(constraint, "max", String.valueOf(Integer.MAX_VALUE));
                String sizeMessage = message.replace("{min}", minRaw).replace("{max}", maxRaw);
                String minExpr = isSpringPlaceholder(minRaw) && springSupport
                        ? "Integer.parseInt(SpringConfigHolder.getProperty(\""
                                + escapeJava(extractSpringPropertyKey(minRaw))
                                + "\", \"" + escapeJava(minRaw) + "\"))"
                        : "Integer.parseInt(\"" + escapeJava(minRaw) + "\")";
                String maxExpr = isSpringPlaceholder(maxRaw) && springSupport
                        ? "Integer.parseInt(SpringConfigHolder.getProperty(\""
                                + escapeJava(extractSpringPropertyKey(maxRaw))
                                + "\", \"" + escapeJava(maxRaw) + "\"))"
                        : "Integer.parseInt(\"" + escapeJava(maxRaw) + "\")";
                return "ValidationHelper.checkSize(" + paramName + ", " + minExpr + ", " + maxExpr + ", \""
                        + escapeJava(sizeMessage) + "\");";
            }

            case "Min":
            case "MinString": {
                String raw = getStringAttribute(constraint, "value", null);
                if (raw == null) {
                    long val = getLongAttribute(constraint, "value", Long.MIN_VALUE);
                    String minMessage = message.replace("{value}", String.valueOf(val));
                    return "ValidationHelper.checkMin(" + paramName + ", " + val + "L, \"" + escapeJava(minMessage)
                            + "\");";
                }
                String minMessage = message.replace("{value}", raw);
                if (isSpringPlaceholder(raw) && springSupport) {
                    String key = extractSpringPropertyKey(raw);
                    return "ValidationHelper.checkMin(" + paramName
                            + ", Long.parseLong(SpringConfigHolder.getProperty(\""
                            + escapeJava(key) + "\", \"" + escapeJava(raw) + "\")), \"" + escapeJava(minMessage)
                            + "\");";
                }
                String normalized = raw.trim();
                if (normalized.endsWith("L") || normalized.endsWith("l")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                return "ValidationHelper.checkMin(" + paramName + ", Long.parseLong(\"" + escapeJava(normalized)
                        + "\"), \"" + escapeJava(minMessage) + "\");";
            }

            case "Max":
            case "MaxString": {
                String raw = getStringAttribute(constraint, "value", null);
                if (raw == null) {
                    long val = getLongAttribute(constraint, "value", Long.MAX_VALUE);
                    String maxMessage = message.replace("{value}", String.valueOf(val));
                    return "ValidationHelper.checkMax(" + paramName + ", " + val + "L, \"" + escapeJava(maxMessage)
                            + "\");";
                }
                String maxMessage = message.replace("{value}", raw);
                if (isSpringPlaceholder(raw) && springSupport) {
                    String key = extractSpringPropertyKey(raw);
                    return "ValidationHelper.checkMax(" + paramName
                            + ", Long.parseLong(SpringConfigHolder.getProperty(\""
                            + escapeJava(key) + "\", \"" + escapeJava(raw) + "\")), \"" + escapeJava(maxMessage)
                            + "\");";
                }
                String normalized = raw.trim();
                if (normalized.endsWith("L") || normalized.endsWith("l")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                return "ValidationHelper.checkMax(" + paramName + ", Long.parseLong(\"" + escapeJava(normalized)
                        + "\"), \"" + escapeJava(maxMessage) + "\");";
            }

            case "DecimalMin": {
                String raw = getStringAttribute(constraint, "value", "0");
                String minMessage = message.replace("{value}", raw);
                if (isSpringPlaceholder(raw) && springSupport) {
                    String key = extractSpringPropertyKey(raw);
                    return "ValidationHelper.checkDecimalMin(" + paramName
                            + ", new java.math.BigDecimal(SpringConfigHolder.getProperty(\"" + escapeJava(key)
                            + "\", \"" + escapeJava(raw) + "\")), \"" + escapeJava(minMessage) + "\");";
                }
                return "ValidationHelper.checkDecimalMin(" + paramName + ", new java.math.BigDecimal(\""
                        + escapeJava(raw) + "\"), \"" + escapeJava(minMessage) + "\");";
            }

            case "DecimalMax": {
                String raw = getStringAttribute(constraint, "value", "0");
                String maxMessage = message.replace("{value}", raw);
                if (isSpringPlaceholder(raw) && springSupport) {
                    String key = extractSpringPropertyKey(raw);
                    return "ValidationHelper.checkDecimalMax(" + paramName
                            + ", new java.math.BigDecimal(SpringConfigHolder.getProperty(\"" + escapeJava(key)
                            + "\", \"" + escapeJava(raw) + "\")), \"" + escapeJava(maxMessage) + "\");";
                }
                return "ValidationHelper.checkDecimalMax(" + paramName + ", new java.math.BigDecimal(\""
                        + escapeJava(raw) + "\"), \"" + escapeJava(maxMessage) + "\");";
            }

            case "Pattern": {
                String regex = getStringAttribute(constraint, "regexp", ".*");
                if (isSpringPlaceholder(regex) && springSupport) {
                    String key = extractSpringPropertyKey(regex);
                    return "ValidationHelper.checkPattern(" + paramName + ", SpringConfigHolder.getProperty(\""
                            + escapeJava(key) + "\", \"" + escapeJava(regex) + "\"), \"" + escapeJava(message) + "\");";
                }
                return "ValidationHelper.checkPattern(" + paramName + ", \"" + escapeJava(regex) + "\", \""
                        + escapeJava(message) + "\");";
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
