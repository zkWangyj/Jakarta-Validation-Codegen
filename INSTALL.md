# 安装说明

## 首次使用步骤

由于 `validation-codegen-plugin` 是本地开发的 Maven 插件，需要先安装到本地仓库。

### 1. 安装到本地仓库

在根目录执行：

```bash
mvn clean install -DskipTests
```

这会按顺序编译并安装所有模块到本地 Maven 仓库。

### 2. IDEA 刷新 Maven 项目

安装完成后，在 IDEA 中点击 Maven 面板的 **Reload All Maven Projects** 按钮。

### 3. 运行测试

```bash
mvn test
```

## 常见问题

### Q: IDEA 中插件显示红色错误
**A:** 需要先执行 `mvn clean install -DskipTests` 将插件安装到本地仓库。

### Q: 修改插件代码后测试不生效
**A:** 修改插件代码后需要重新安装：
```bash
mvn clean install -DskipTests
```

### Q: 如何单独编译插件
**A:**
```bash
cd validation-codegen-plugin
mvn clean install
```

### Q: JDK 版本要求
**A:** 需要 JDK 17+，支持 Record 语法和 `--release 17` 编译选项。
