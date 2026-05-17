package com.firmae.validation.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maven Mojo - 扫描 @PreCompile 注解的方法/构造器，注入 Jakarta Validation 校验代码。
 * <p>
 * 在 generate-sources 阶段执行，使用 JavaParser 解析源码 AST，
 * 找到标注了 @PreCompile 的方法或构造器，解析参数上的 Jakarta Validation 注解，
 * 在方法体开头注入 ValidationHelper.checkXxx(...) 调用，
 * 将修改后的源码输出到 target/generated-sources/validation-injected 目录。
 * </p>
 */
@Mojo(name = "inject-validation", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ValidationCodegenMojo extends AbstractMojo {

    /**
     * 源码目录，默认为 ${project.basedir}/src/main/java
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project.basedir}/src/main/java", required = true)
    private File sourceDir;

    /**
     * 输出目录，默认为 ${project.build.directory}/generated-sources/validation-injected
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project.build.directory}/generated-sources/validation-injected", required = true)
    private File outputDir;

    /**
     * Jakarta Validation 注解名称集合
     */
    private static final Set<String> VALIDATION_ANNOTATIONS = Set.of(
            "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max",
            "Pattern", "Email", "Positive", "PositiveOrZero",
            "Negative", "NegativeOrZero", "AssertTrue", "AssertFalse",
            "MinString", "MaxString", "SizeString");

    /**
     * 待注入 compact constructor 的 Record（Record 名 -> 校验语句列表）
     * 用于没有显式 compact constructor 的 Record，在字符串后处理阶段插入
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private final Map<String, List<Statement>> pendingRecordInjections = new HashMap<>();
    private boolean springSupport = false;
    private boolean needsSpringConfigHolderImport = false;
    private static final Pattern SPRING_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{(.+)}$");

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDir.exists()) {
            getLog().warn("源码目录不存在: " + sourceDir.getAbsolutePath());
            return;
        }

        getLog().info("扫描源码目录: " + sourceDir.getAbsolutePath());
        getLog().info("输出目录: " + outputDir.getAbsolutePath());

        this.springSupport = detectSpringSupport();
        getLog().info("Spring support " + (springSupport ? "enabled" : "disabled"));
        generateSpringConfigHolder();

        List<File> javaFiles = findJavaFiles(sourceDir);
        getLog().info("找到 " + javaFiles.size() + " 个 Java 源文件");

        int processedFiles = 0;
        int injectedMethods = 0;

        for (File javaFile : javaFiles) {
            try {
                this.needsSpringConfigHolderImport = false;
                int count = processJavaFile(javaFile);
                if (count > 0) {
                    processedFiles++;
                    injectedMethods += count;
                }
            } catch (Exception e) {
                getLog().error("处理文件失败: " + javaFile.getAbsolutePath(), e);
            }
        }

        getLog().info("处理完成: " + processedFiles + " 个文件, " + injectedMethods + " 个方法被注入校验代码");
    }

    /**
     * 递归查找所有 .java 文件
     */
    private List<File> findJavaFiles(File dir) throws MojoExecutionException {
        List<File> files = new ArrayList<>();
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        files.add(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException("扫描源码目录失败", e);
        }
        return files;
    }

    /**
     * 处理单个 Java 文件
     *
     * @return 被注入校验代码的方法数量
     */
    private int processJavaFile(File javaFile) throws IOException {
        // 读取原始源码（用于 Record 的字符串后处理）
        String originalSource = Files.readString(javaFile.toPath(), StandardCharsets.UTF_8);

        JavaParser parser = new JavaParser(
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(javaFile)) {
            cu = parser.parse(in).getResult().orElse(null);
        }
        if (cu == null) {
            getLog().warn("无法解析文件: " + javaFile.getAbsolutePath());
            return 0;
        }

        int injectedCount = 0;

        // 处理普通类和接口中的方法
        injectedCount += processClassOrInterface(cu);

        // 处理 Record 中的 compact constructor
        injectedCount += processRecords(cu);

        if (injectedCount > 0) {
            // 添加 ValidationHelper 的 import
            addImportIfAbsent(cu, "com.firmae.validation.ValidationHelper");
        }

        // 移除所有 @PreCompile 注解（已处理完毕，不需要保留到生成的源码中）
        removePreCompileAnnotations(cu);

        // 所有文件都输出到 generated-sources 目录
        writeOutputFile(javaFile, cu, originalSource);

        return injectedCount;
    }

    /**
     * 处理普通类和接口中的方法和构造器
     */
    private int processClassOrInterface(CompilationUnit cu) {
        int count = 0;
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration cls : classes) {
            // 处理方法
            for (MethodDeclaration method : cls.getMethods()) {
                if (hasPreCompileAnnotation(method)) {
                    injectValidation(method);
                    count++;
                    getLog().info("已注入校验代码到方法: " + method.getName() + ", 参数数: " + method.getParameters().size());
                }
            }
            // 处理构造器
            for (ConstructorDeclaration constructor : cls.getConstructors()) {
                if (hasPreCompileAnnotation(constructor)) {
                    injectValidation(constructor);
                    count++;
                    getLog().debug("已注入校验代码到构造器: " + constructor.getName());
                }
            }
        }
        return count;
    }

    /**
     * 处理 Record：所有 Record 统一用字符串后处理插入 compact constructor
     * 因为 JavaParser 无法生成 compact constructor 语法（无括号），AST 操作会导致编译错误
     */
    private int processRecords(CompilationUnit cu) {
        int count = 0;
        List<RecordDeclaration> records = cu.findAll(RecordDeclaration.class);
        for (RecordDeclaration record : records) {
            boolean preCompileOnRecord = hasPreCompileAnnotation(record);
            boolean preCompileOnConstructor = false;

            for (ConstructorDeclaration constructor : record.getConstructors()) {
                if (hasPreCompileAnnotation(constructor)) {
                    preCompileOnConstructor = true;
                }
            }

            if (preCompileOnRecord || preCompileOnConstructor) {
                // 统一用字符串后处理，不通过 AST 操作 Record constructor
                List<Statement> validationStatements = new ArrayList<>();
                for (Parameter component : record.getParameters()) {
                    List<Statement> stmts = generateValidationStatementsForRecordComponent(component);
                    validationStatements.addAll(stmts);
                }
                if (!validationStatements.isEmpty()) {
                    pendingRecordInjections.put(
                            record.getNameAsString(),
                            new ArrayList<>(validationStatements));
                }
                count++;
                getLog().debug("已注入校验代码到 Record: " + record.getName());
            }
        }
        return count;
    }

    /**
     * 检查节点是否有 @PreCompile 注解
     */
    private boolean hasPreCompileAnnotation(NodeWithAnnotations<?> node) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = getAnnotationSimpleName(annotation);
            if ("PreCompile".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取注解的简单名称
     */
    private String getAnnotationSimpleName(AnnotationExpr annotation) {
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getName().getIdentifier();
        } else if (annotation.isMarkerAnnotationExpr()) {
            return annotation.asMarkerAnnotationExpr().getName().getIdentifier();
        } else if (annotation.isSingleMemberAnnotationExpr()) {
            return annotation.asSingleMemberAnnotationExpr().getName().getIdentifier();
        }
        return annotation.getNameAsString();
    }

    /**
     * 向方法/构造器注入校验代码
     */
    private void injectValidation(CallableDeclaration<?> callable) {
        List<Parameter> parameters = callable.getParameters();
        BlockStmt body;

        if (callable instanceof MethodDeclaration) {
            body = ((MethodDeclaration) callable).getBody().orElse(null);
        } else if (callable instanceof ConstructorDeclaration) {
            body = ((ConstructorDeclaration) callable).getBody();
        } else {
            return;
        }

        if (body == null || parameters.isEmpty()) {
            return;
        }

        // 收集所有需要注入的校验语句
        List<Statement> validationStatements = new ArrayList<>();

        for (Parameter parameter : parameters) {
            List<Statement> stmts = generateValidationStatements(parameter);
            validationStatements.addAll(stmts);
        }

        if (validationStatements.isEmpty()) {
            return;
        }

        // 在方法体的第一个语句前注入所有校验语句
        for (int i = 0; i < validationStatements.size(); i++) {
            body.addStatement(i, validationStatements.get(i));
        }
    }

    /**
     * 为单个参数生成所有校验语句
     */
    private List<Statement> generateValidationStatements(Parameter parameter) {
        List<Statement> statements = new ArrayList<>();
        String paramName = parameter.getNameAsString();

        for (AnnotationExpr annotation : parameter.getAnnotations()) {
            String annotationName = getAnnotationSimpleName(annotation);

            if ("Validated".equals(annotationName)) {
                // @Validated 注解：使用 Hibernate Validator 校验嵌套对象
                String message = paramName + "校验失败";
                List<String> groups = extractGroups(annotation);
                Statement stmt = createValidateCall(paramName, message, groups);
                statements.add(stmt);
                continue;
            }

            if (!VALIDATION_ANNOTATIONS.contains(annotationName)) {
                continue;
            }

            Statement stmt = generateValidationStatement(paramName, annotationName, annotation);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    /**
     * 为 Record component 生成所有校验语句
     */
    private List<Statement> generateValidationStatementsForRecordComponent(Parameter component) {
        List<Statement> statements = new ArrayList<>();
        String paramName = component.getNameAsString();

        for (AnnotationExpr annotation : component.getAnnotations()) {
            String annotationName = getAnnotationSimpleName(annotation);

            if (!VALIDATION_ANNOTATIONS.contains(annotationName)) {
                continue;
            }

            Statement stmt = generateValidationStatement(paramName, annotationName, annotation);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    /**
     * 根据注解类型生成对应的校验语句
     */
    private Statement generateValidationStatement(String paramName, String annotationName, AnnotationExpr annotation) {
        String message = extractMessage(annotation, paramName, annotationName);

        return switch (annotationName) {
            case "NotNull" -> createCheckCall("checkNotNull", paramName, message);
            case "NotBlank" -> createCheckCall("checkNotBlank", paramName, message);
            case "NotEmpty" -> createCheckCall("checkNotEmpty", paramName, message);
            case "Size" -> createSizeCheckCall(paramName, annotation, message);
            case "SizeString" -> createSizeStringCheckCall(paramName, annotation, message);
            case "Min" -> createMinCheckCall(paramName, annotation, message);
            case "MinString" -> createMinCheckCall(paramName, annotation, message);
            case "Max" -> createMaxCheckCall(paramName, annotation, message);
            case "MaxString" -> createMaxCheckCall(paramName, annotation, message);
            case "Pattern" -> createPatternCheckCall(paramName, annotation, message);
            case "Email" -> createCheckCall("checkEmail", paramName, message);
            case "Positive" -> createCheckCall("checkPositive", paramName, message);
            case "PositiveOrZero" -> createCheckCall("checkPositiveOrZero", paramName, message);
            case "Negative" -> createCheckCall("checkNegative", paramName, message);
            case "NegativeOrZero" -> createCheckCall("checkNegativeOrZero", paramName, message);
            case "AssertTrue" -> createCheckCall("checkAssertTrue", paramName, message);
            case "AssertFalse" -> createCheckCall("checkAssertFalse", paramName, message);
            default -> null;
        };
    }

    /**
     * 创建简单的 ValidationHelper.checkXxx(param, message) 调用
     */
    private Statement createCheckCall(String methodName, String paramName, String message) {
        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName(methodName),
                new NodeList<>(
                        new NameExpr(paramName),
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    /**
     * 创建 ValidationHelper.validate(param, message, groups...) 调用
     */
    private Statement createValidateCall(String paramName, String message, List<String> groups) {
        NodeList<Expression> args = new NodeList<>();
        args.add(new NameExpr(paramName));
        args.add(new StringLiteralExpr(message));

        if (!groups.isEmpty()) {
            // 有分组：ValidationHelper.validate(param, "msg", Group1.class, Group2.class)
            for (String group : groups) {
                args.add(new FieldAccessExpr(new NameExpr(group), "class"));
            }
        }

        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("validate"),
                args);
        return new ExpressionStmt(call);
    }

    /**
     * 从 @Validated 注解中提取 groups 属性
     */
    private List<String> extractGroups(AnnotationExpr annotation) {
        List<String> groups = new ArrayList<>();
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                if ("groups".equals(pair.getNameAsString())) {
                    // groups = {Group1.class, Group2.class}
                    Expression valueExpr = pair.getValue();
                    if (valueExpr.isArrayInitializerExpr()) {
                        for (Expression element : valueExpr.asArrayInitializerExpr().getValues()) {
                            groups.add(element.toString().replace(".class", ""));
                        }
                    } else if (valueExpr.isClassExpr()) {
                        groups.add(valueExpr.asClassExpr().getTypeAsString());
                    }
                    break;
                }
            }
        }
        return groups;
    }

    /**
     * 创建 ValidationHelper.checkSize(param, min, max, message) 调用
     */
    private Statement createSizeCheckCall(String paramName, AnnotationExpr annotation, String message) {
        int min = extractIntAttribute(annotation, "min", 0);
        int max = extractIntAttribute(annotation, "max", Integer.MAX_VALUE);

        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("checkSize"),
                new NodeList<>(
                        new NameExpr(paramName),
                        new IntegerLiteralExpr(String.valueOf(min)),
                        new IntegerLiteralExpr(String.valueOf(max)),
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    private Statement createSizeStringCheckCall(String paramName, AnnotationExpr annotation, String message) {
        String minRaw = extractStringAttribute(annotation, "min", "0");
        String maxRaw = extractStringAttribute(annotation, "max", String.valueOf(Integer.MAX_VALUE));
        Expression minExpr = createIntValueExpression(minRaw);
        Expression maxExpr = createIntValueExpression(maxRaw);

        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("checkSize"),
                new NodeList<>(
                        new NameExpr(paramName),
                        minExpr,
                        maxExpr,
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    /**
     * 创建 ValidationHelper.checkMin(param, value, message) 调用
     */
    private Statement createMinCheckCall(String paramName, AnnotationExpr annotation, String message) {
        String rawValue = extractStringAttribute(annotation, "value", null);
        if (springSupport && isSpringPlaceholder(rawValue)) {
            String key = extractSpringPropertyKey(rawValue);
            this.needsSpringConfigHolderImport = true;
            MethodCallExpr parseCall = new MethodCallExpr(
                    new NameExpr("Long"),
                    new SimpleName("parseLong"),
                    new NodeList<>(
                            new MethodCallExpr(
                                    new NameExpr("SpringConfigHolder"),
                                    new SimpleName("getProperty"),
                                    new NodeList<>(new StringLiteralExpr(key)))));
            MethodCallExpr call = new MethodCallExpr(
                    new NameExpr("ValidationHelper"),
                    new SimpleName("checkMin"),
                    new NodeList<>(
                            new NameExpr(paramName),
                            parseCall,
                            new StringLiteralExpr(message)));
            return new ExpressionStmt(call);
        }

        long value = parseLongValue(rawValue, extractLongAttribute(annotation, "value", Long.MIN_VALUE));
        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("checkMin"),
                new NodeList<>(
                        new NameExpr(paramName),
                        new LongLiteralExpr(String.valueOf(value) + "L"),
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    /**
     * 创建 ValidationHelper.checkMax(param, value, message) 调用
     */
    private Statement createMaxCheckCall(String paramName, AnnotationExpr annotation, String message) {
        String rawValue = extractStringAttribute(annotation, "value", null);
        if (springSupport && isSpringPlaceholder(rawValue)) {
            String key = extractSpringPropertyKey(rawValue);
            this.needsSpringConfigHolderImport = true;
            MethodCallExpr parseCall = new MethodCallExpr(
                    new NameExpr("Long"),
                    new SimpleName("parseLong"),
                    new NodeList<>(
                            new MethodCallExpr(
                                    new NameExpr("SpringConfigHolder"),
                                    new SimpleName("getProperty"),
                                    new NodeList<>(new StringLiteralExpr(key)))));
            MethodCallExpr call = new MethodCallExpr(
                    new NameExpr("ValidationHelper"),
                    new SimpleName("checkMax"),
                    new NodeList<>(
                            new NameExpr(paramName),
                            parseCall,
                            new StringLiteralExpr(message)));
            return new ExpressionStmt(call);
        }

        long value = parseLongValue(rawValue, extractLongAttribute(annotation, "value", Long.MAX_VALUE));
        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("checkMax"),
                new NodeList<>(
                        new NameExpr(paramName),
                        new LongLiteralExpr(String.valueOf(value) + "L"),
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    /**
     * 创建 ValidationHelper.checkPattern(param, regexp, message) 调用
     */
    private Statement createPatternCheckCall(String paramName, AnnotationExpr annotation, String message) {
        String regexp = extractStringAttribute(annotation, "regexp", "");
        if (springSupport && isSpringPlaceholder(regexp)) {
            String key = extractSpringPropertyKey(regexp);
            this.needsSpringConfigHolderImport = true;
            MethodCallExpr call = new MethodCallExpr(
                    new NameExpr("ValidationHelper"),
                    new SimpleName("checkPattern"),
                    new NodeList<>(
                            new NameExpr(paramName),
                            new MethodCallExpr(
                                    new NameExpr("SpringConfigHolder"),
                                    new SimpleName("getProperty"),
                                    new NodeList<>(new StringLiteralExpr(key))),
                            new StringLiteralExpr(message)));
            return new ExpressionStmt(call);
        }

        MethodCallExpr call = new MethodCallExpr(
                new NameExpr("ValidationHelper"),
                new SimpleName("checkPattern"),
                new NodeList<>(
                        new NameExpr(paramName),
                        new StringLiteralExpr(regexp),
                        new StringLiteralExpr(message)));
        return new ExpressionStmt(call);
    }

    /**
     * 从注解中提取 message 属性值
     */
    private String extractMessage(AnnotationExpr annotation, String paramName, String annotationName) {
        String message = extractStringAttribute(annotation, "message", null);

        if (message == null || message.isEmpty()) {
            if ("Size".equals(annotationName) || "SizeString".equals(annotationName)) {
                message = getSizeDefaultMessage(paramName, annotation);
            } else {
                message = getDefaultMessage(paramName, annotationName);
            }
        }

        // 替换消息中的占位符
        message = replacePlaceholders(message, annotation, annotationName);

        // 去除 jakarta.validation.constraints 包前缀
        if (message.startsWith("{jakarta.validation.constraints.")) {
            if ("Size".equals(annotationName) || "SizeString".equals(annotationName)) {
                message = getSizeDefaultMessage(paramName, annotation);
            } else {
                message = getDefaultMessage(paramName, annotationName);
            }
        }

        return message;
    }

    /**
     * 替换消息中的占位符
     */
    private String replacePlaceholders(String message, AnnotationExpr annotation, String annotationName) {
        switch (annotationName) {
            case "Size":
            case "SizeString": {
                String min = extractStringAttribute(annotation, "min", "0");
                String max = extractStringAttribute(annotation, "max", String.valueOf(Integer.MAX_VALUE));
                message = message.replace("{min}", min);
                message = message.replace("{max}", max);
                break;
            }
            case "Min":
            case "MinString": {
                String value = extractStringAttribute(annotation, "value", null);
                if (value == null) {
                    value = String.valueOf(extractLongAttribute(annotation, "value", Long.MIN_VALUE));
                }
                message = message.replace("{value}", value);
                break;
            }
            case "Max":
            case "MaxString": {
                String value = extractStringAttribute(annotation, "value", null);
                if (value == null) {
                    value = String.valueOf(extractLongAttribute(annotation, "value", Long.MAX_VALUE));
                }
                message = message.replace("{value}", value);
                break;
            }
            case "Pattern": {
                String regexp = extractStringAttribute(annotation, "regexp", "");
                message = message.replace("{regexp}", regexp);
                break;
            }
            default:
                break;
        }
        return message;
    }

    /**
     * 获取默认消息
     */
    private String getDefaultMessage(String paramName, String annotationName) {
        return switch (annotationName) {
            case "NotNull" -> paramName + "不能为空";
            case "NotBlank" -> paramName + "不能为空白";
            case "NotEmpty" -> paramName + "不能为空";
            case "Size", "SizeString" -> paramName + "大小不合法";
            case "Min", "MinString" -> paramName + "不能小于最小值";
            case "Max", "MaxString" -> paramName + "不能大于最大值";
            case "Pattern" -> paramName + "格式不正确";
            case "Email" -> paramName + "邮箱格式不正确";
            case "Positive" -> paramName + "必须为正数";
            case "PositiveOrZero" -> paramName + "不能为负数";
            case "Negative" -> paramName + "必须为负数";
            case "NegativeOrZero" -> paramName + "必须为负数或零";
            case "AssertTrue" -> paramName + "必须为 true";
            case "AssertFalse" -> paramName + "必须为 false";
            default -> paramName + "校验失败";
        };
    }

    /**
     * 获取 @Size 注解的默认消息（包含 min/max 信息）
     */
    private String getSizeDefaultMessage(String paramName, AnnotationExpr annotation) {
        String minRaw = extractStringAttribute(annotation, "min", "0");
        String maxRaw = extractStringAttribute(annotation, "max", String.valueOf(Integer.MAX_VALUE));
        int min = parseIntString(minRaw, 0);
        int max = parseIntString(maxRaw, Integer.MAX_VALUE);
        if (max == Integer.MAX_VALUE) {
            return paramName + "长度不能小于 " + min;
        }
        return paramName + "长度必须在 " + min + " 到 " + max + " 之间";
    }

    private int parseIntString(String rawValue, int defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if (normalized.endsWith("L") || normalized.endsWith("l")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLongValue(String rawValue, long fallback) {
        if (rawValue != null && !rawValue.isEmpty()) {
            String normalized = rawValue.trim();
            if (normalized.endsWith("L") || normalized.endsWith("l")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignore) {
                // 如果不是有效数字，则使用 fallback
            }
        }
        return fallback;
    }

    private Expression createIntValueExpression(String rawValue) {
        if (rawValue == null) {
            rawValue = "0";
        }
        if (isSpringPlaceholder(rawValue)) {
            String key = extractSpringPropertyKey(rawValue);
            this.needsSpringConfigHolderImport = true;
            return new MethodCallExpr(
                    new NameExpr("Integer"),
                    new SimpleName("parseInt"),
                    new NodeList<>(
                            new MethodCallExpr(
                                    new NameExpr("SpringConfigHolder"),
                                    new SimpleName("getProperty"),
                                    new NodeList<>(new StringLiteralExpr(key)))));
        }
        String normalized = rawValue.trim();
        if (normalized.endsWith("L") || normalized.endsWith("l")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return new IntegerLiteralExpr(normalized);
    }

    private Expression createLongValueExpression(String rawValue) {
        if (rawValue == null) {
            rawValue = "0";
        }
        if (isSpringPlaceholder(rawValue)) {
            String key = extractSpringPropertyKey(rawValue);
            this.needsSpringConfigHolderImport = true;
            return new MethodCallExpr(
                    new NameExpr("Long"),
                    new SimpleName("parseLong"),
                    new NodeList<>(
                            new MethodCallExpr(
                                    new NameExpr("SpringConfigHolder"),
                                    new SimpleName("getProperty"),
                                    new NodeList<>(new StringLiteralExpr(key)))));
        }
        String normalized = rawValue.trim();
        if (normalized.endsWith("L") || normalized.endsWith("l")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return new LongLiteralExpr(normalized + "L");
    }

    /**
     * 从注解中提取字符串属性值
     */
    private String extractStringAttribute(AnnotationExpr annotation, String attrName, String defaultValue) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(attrName)) {
                    Expression valueExpr = pair.getValue();
                    if (valueExpr instanceof StringLiteralExpr) {
                        return ((StringLiteralExpr) valueExpr).getValue();
                    } else if (valueExpr instanceof LiteralStringValueExpr) {
                        return ((LiteralStringValueExpr) valueExpr).getValue();
                    }
                    return valueExpr.toString();
                }
            }
        }
        return defaultValue;
    }

    /**
     * 从注解中提取 int 属性值
     */
    private int extractIntAttribute(AnnotationExpr annotation, String attrName, int defaultValue) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(attrName)) {
                    Expression valueExpr = pair.getValue();
                    if (valueExpr.isIntegerLiteralExpr()) {
                        try {
                            return Integer.parseInt(valueExpr.asIntegerLiteralExpr().getValue());
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                    if (valueExpr.isUnaryExpr()) {
                        try {
                            return Integer.parseInt(valueExpr.toString());
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                    try {
                        return Integer.parseInt(valueExpr.toString());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                }
            }
        }
        return defaultValue;
    }

    /**
     * 从注解中提取 long 属性值
     */
    private long extractLongAttribute(AnnotationExpr annotation, String attrName, long defaultValue) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(attrName)) {
                    Expression valueExpr = pair.getValue();
                    if (valueExpr.isIntegerLiteralExpr()) {
                        try {
                            return Long.parseLong(valueExpr.asIntegerLiteralExpr().getValue());
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                    if (valueExpr.isLongLiteralExpr()) {
                        try {
                            String val = valueExpr.asLongLiteralExpr().getValue();
                            if (val.endsWith("L") || val.endsWith("l")) {
                                val = val.substring(0, val.length() - 1);
                            }
                            return Long.parseLong(val);
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                    if (valueExpr.isUnaryExpr()) {
                        try {
                            return Long.parseLong(valueExpr.toString());
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                    try {
                        return Long.parseLong(valueExpr.toString());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                }
            }
        }
        return defaultValue;
    }

    /**
     * 添加 import 语句（如果不存在）
     */
    private void addImportIfAbsent(CompilationUnit cu, String importClass) {
        boolean exists = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importClass));
        if (!exists) {
            cu.addImport(importClass);
        }
    }

    /**
     * 移除所有 @PreCompile 注解（已处理完毕，不需要保留到生成的源码中）
     */
    private void removePreCompileAnnotations(CompilationUnit cu) {
        cu.findAll(AnnotationExpr.class).stream()
                .filter(a -> "PreCompile".equals(getAnnotationSimpleName(a)))
                .forEach(AnnotationExpr::remove);
    }

    /**
     * 将修改后的 CompilationUnit 写入输出文件
     * 对没有显式 compact constructor 的 Record，在原始源码上做字符串替换
     */
    private void writeOutputFile(File sourceFile, CompilationUnit cu, String originalSource) throws IOException {
        // 计算相对路径
        Path sourcePath = sourceDir.toPath();
        Path filePath = sourceFile.toPath();
        Path relativePath = sourcePath.relativize(filePath);

        Path outputPath = outputDir.toPath().resolve(relativePath);

        // 确保输出目录存在
        Files.createDirectories(outputPath.getParent());

        String sourceCode;

        if (!pendingRecordInjections.isEmpty()) {
            // 有需要插入 compact constructor 的 Record，在原始源码上做字符串替换
            sourceCode = injectCompactConstructors(originalSource);
            pendingRecordInjections.clear();

            // 在替换后的源码中添加 ValidationHelper 的 import（如果不存在）
            if (!sourceCode.contains("import com.firmae.validation.ValidationHelper;")) {
                // 在 package 声明后插入 import
                int packageEnd = sourceCode.indexOf(';');
                if (packageEnd >= 0) {
                    sourceCode = sourceCode.substring(0, packageEnd + 1)
                            + "\nimport com.firmae.validation.ValidationHelper;"
                            + sourceCode.substring(packageEnd + 1);
                }
            }
            if (needsSpringConfigHolderImport
                    && !sourceCode.contains("import com.firmae.validation.SpringConfigHolder;")) {
                int packageEnd = sourceCode.indexOf(';');
                if (packageEnd >= 0) {
                    sourceCode = sourceCode.substring(0, packageEnd + 1)
                            + "\nimport com.firmae.validation.SpringConfigHolder;"
                            + sourceCode.substring(packageEnd + 1);
                }
            }

            // 移除原始源码中的 @PreCompile 注解
            sourceCode = sourceCode.replaceAll("@PreCompile\\s*\n?", "");
        } else {
            sourceCode = cu.toString();
            if (needsSpringConfigHolderImport
                    && !sourceCode.contains("import com.firmae.validation.SpringConfigHolder;")) {
                int packageEnd = sourceCode.indexOf(';');
                if (packageEnd >= 0) {
                    sourceCode = sourceCode.substring(0, packageEnd + 1)
                            + "\nimport com.firmae.validation.SpringConfigHolder;"
                            + sourceCode.substring(packageEnd + 1);
                }
            }
        }

        // 写入文件
        Files.writeString(outputPath, sourceCode);

        getLog().debug("输出文件: " + outputPath);
    }

    private boolean detectSpringSupport() {
        if (project == null) {
            return false;
        }
        return project.getDependencies().stream()
                .anyMatch(dep -> (dep.getGroupId() != null && dep.getGroupId().startsWith("org.springframework"))
                        || (dep.getArtifactId() != null && dep.getArtifactId().startsWith("spring-")));
    }

    private void generateSpringConfigHolder() {
        try {
            Path holderPath = outputDir.toPath().resolve("com/firmae/validation/SpringConfigHolder.java");
            Files.createDirectories(holderPath.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("package com.firmae.validation;\n\n");
            if (springSupport) {
                sb.append("import org.springframework.context.ApplicationContext;\n");
                sb.append("import org.springframework.context.ApplicationListener;\n");
                sb.append("import org.springframework.context.event.ContextRefreshedEvent;\n");
                sb.append("import org.springframework.core.env.Environment;\n");
                sb.append("import org.springframework.stereotype.Component;\n\n");
                sb.append("@Component\n");
                sb.append("public class SpringConfigHolder implements ApplicationListener<ContextRefreshedEvent> {\n");
                sb.append("    private static volatile ApplicationContext applicationContext;\n");
                sb.append("    private static volatile Environment environment;\n\n");
                sb.append("    @Override\n");
                sb.append("    public void onApplicationEvent(ContextRefreshedEvent event) {\n");
                sb.append("        applicationContext = event.getApplicationContext();\n");
                sb.append("        environment = applicationContext.getEnvironment();\n");
                sb.append("    }\n\n");
                sb.append("    public static String getProperty(String key) {\n");
                sb.append("        return environment == null ? null : environment.getProperty(key);\n");
                sb.append("    }\n\n");
                sb.append("    public static String getProperty(String key, String defaultValue) {\n");
                sb.append(
                        "        return environment == null ? defaultValue : environment.getProperty(key, defaultValue);\n");
                sb.append("    }\n\n");
                sb.append("    public static <T> T getProperty(String key, Class<T> targetType) {\n");
                sb.append("        return environment == null ? null : environment.getProperty(key, targetType);\n");
                sb.append("    }\n\n");
                sb.append("    public static <T> T getProperty(String key, Class<T> targetType, T defaultValue) {\n");
                sb.append(
                        "        return environment == null ? defaultValue : environment.getProperty(key, targetType, defaultValue);\n");
                sb.append("    }\n\n");
                sb.append("    public static boolean isSpringSupport() {\n");
                sb.append("        return environment != null;\n");
                sb.append("    }\n");
                sb.append("}\n");
            } else {
                sb.append("public class SpringConfigHolder {\n");
                sb.append("    private static final boolean springSupport = false;\n\n");
                sb.append("    public static boolean isSpringSupport() {\n");
                sb.append("        return springSupport;\n");
                sb.append("    }\n");
                sb.append("}\n");
            }
            Files.writeString(holderPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLog().warn("无法生成 SpringConfigHolder: " + e.getMessage());
        }
    }

    private boolean isSpringPlaceholder(String value) {
        return value != null && SPRING_PLACEHOLDER_PATTERN.matcher(value).matches();
    }

    private String extractSpringPropertyKey(String placeholder) {
        var matcher = SPRING_PLACEHOLDER_PATTERN.matcher(placeholder);
        return matcher.matches() ? matcher.group(1) : placeholder;
    }

    /**
     * 在原始 Record 源码字符串中插入 compact constructor
     * 如果已有 compact constructor，在 body 开头插入校验语句
     * 如果没有，创建新的 compact constructor
     */
    private String injectCompactConstructors(String sourceCode) {
        for (Map.Entry<String, List<Statement>> entry : pendingRecordInjections.entrySet()) {
            String recordName = entry.getKey();
            List<Statement> statements = entry.getValue();

            // 查找 record 声明：record Xxx(...) {
            String searchPattern = "record " + recordName + "(";

            int recordStart = sourceCode.indexOf(searchPattern);
            if (recordStart < 0)
                continue;

            // 找到 record 参数列表的右括号（跳过注解中的括号）
            int parenEnd = findMatchingParen(sourceCode, recordStart + searchPattern.length() - 1);
            if (parenEnd < 0)
                continue;

            // 找到 record body 开始的 '{'
            int braceStart = sourceCode.indexOf('{', parenEnd);
            if (braceStart < 0)
                continue;

            // 找到匹配的结束 '}'
            int braceEnd = findMatchingBrace(sourceCode, braceStart);
            if (braceEnd < 0)
                continue;

            String body = sourceCode.substring(braceStart + 1, braceEnd).trim();

            if (body.isEmpty()) {
                // 没有 compact constructor，创建新的
                StringBuilder constructor = new StringBuilder();
                constructor.append("\n    public ").append(recordName).append(" {\n");
                for (Statement stmt : statements) {
                    constructor.append("        ").append(stmt.toString().trim()).append(";\n");
                }
                constructor.append("    }\n");
                sourceCode = sourceCode.substring(0, braceEnd) + constructor + sourceCode.substring(braceEnd);
            } else {
                // 已有 compact constructor，在第一个语句前插入校验代码
                // 找到 compact constructor body 的 '{'
                int ctorBraceStart = sourceCode.indexOf('{', braceStart + 1);
                if (ctorBraceStart < 0 || ctorBraceStart >= braceEnd)
                    continue;

                int ctorBraceEnd = findMatchingBrace(sourceCode, ctorBraceStart);
                if (ctorBraceEnd < 0)
                    continue;

                StringBuilder insert = new StringBuilder();
                for (Statement stmt : statements) {
                    insert.append("        ").append(stmt.toString().trim()).append(";\n");
                }

                // 在 compact constructor body 的 '{' 后插入
                sourceCode = sourceCode.substring(0, ctorBraceStart + 1) + "\n" + insert
                        + sourceCode.substring(ctorBraceStart + 1);
            }
        }
        return sourceCode;
    }

    /**
     * 找到匹配的右括号位置（跳过注解中的括号）
     */
    private int findMatchingParen(String source, int start) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);

            if (inLineComment) {
                if (c == '\n')
                    inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < source.length()) {
                    i++;
                    continue;
                }
                if (c == '"')
                    inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\' && i + 1 < source.length()) {
                    i++;
                    continue;
                }
                if (c == '\'')
                    inChar = false;
                continue;
            }

            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            if (c == '(')
                depth++;
            if (c == ')') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    /**
     * 找到匹配的右大括号位置（正确处理字符串、注释、转义字符）
     */
    private int findMatchingBrace(String source, int start) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);

            if (inLineComment) {
                if (c == '\n')
                    inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < source.length()) {
                    i++;
                    continue;
                }
                if (c == '"')
                    inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\' && i + 1 < source.length()) {
                    i++;
                    continue;
                }
                if (c == '\'')
                    inChar = false;
                continue;
            }

            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            if (c == '{')
                depth++;
            if (c == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }
}
