package com.firmae.validation.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 生成的代码有与与目标代码类名是相同的限定名，在idea中开能认为异常，使用后需要删除
 * 清理生成的源代码目录, 默认目录为 ${project.build.directory}/generated-sources/validation-injected
 */
@Mojo(name = "clean-generated-sources", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ValidationCodeCleanSourceMojo extends AbstractMojo {
    /**
     * 输出目录，默认为 ${project.build.directory}/generated-sources/validation-injected
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project.build.directory}/generated-sources/validation-injected", required = true)
    private File outputDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Cleaning generated sources...");
        if (outputDir.exists()) {
            try {
                Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to delete directory: " + outputDir, e);
            }
        }
    }
}