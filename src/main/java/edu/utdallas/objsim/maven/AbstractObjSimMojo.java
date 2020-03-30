/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.maven;

import edu.utdallas.objsim.ObjSimEntryPoint;
import edu.utdallas.objsim.commons.MemberNameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.classpath.ClassPathByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.functional.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public abstract class AbstractObjSimMojo extends AbstractMojo {
    private static final int CACHE_SIZE = 200;

    protected File compatibleJREHome;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "plugin.artifactMap", readonly = true, required = true)
    protected Map<String, Artifact> pluginArtifactMap;

    // -----------------------
    // ---- plugin params ----
    // -----------------------

    @Parameter(property = "inputCSVFile", defaultValue = "input-file.csv")
    protected File inputCSVFile;

    @Parameter(property = "failingTests")
    protected Set<String> failingTests;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        validateAndSanitizeParameters();

        final ClassPath classPath = createClassPath();
        final ClassByteArraySource byteArraySource = createClassByteArraySource(classPath);

        final File baseDirectory = this.project.getBasedir();

        try {
            ObjSimEntryPoint.createEntryPoint()
                    .withBaseDirectory(baseDirectory)
                    .withClassByteArraySource(byteArraySource)
                    .withClassPath(classPath)
                    .withCompatibleJREHome(this.compatibleJREHome)
                    .withInputCSVFile(this.inputCSVFile)
                    .withFailingTests(this.failingTests)
                    .run();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void validateAndSanitizeParameters() throws MojoFailureException {
        final String jreHome = System.getProperty("java.home");
        if (jreHome == null) {
            throw new MojoFailureException("JAVA_HOME is not set");
        }
        this.compatibleJREHome = new File(jreHome);
        if (!this.compatibleJREHome.isDirectory()) {
            throw new MojoFailureException("Invalid JAVA_HOME/JRE_HOME");
        }
        if (!this.inputCSVFile.isFile()) {
            throw new MojoFailureException("Missing and/or invalid input CSV file");
        }
        final List<String> temp = new LinkedList<>();
        for (final String failingTest : this.failingTests) {
            temp.add(MemberNameUtils.sanitizeTestName(failingTest));
        }
        this.failingTests.clear();
        this.failingTests.addAll(temp);
    }

    private ClassPath createClassPath() {
        final List<File> classPathElements = new ArrayList<>();
        classPathElements.addAll(getProjectClassPath());
        classPathElements.addAll(getPluginClassPath());
        return new ClassPath(classPathElements);
    }

    private List<File> getProjectClassPath() {
        final List<File> classPath = new ArrayList<>();
        try {
            for (final Object cpElement : this.project.getTestClasspathElements()) {
                classPath.add(new File((String) cpElement));
            }
        } catch (DependencyResolutionRequiredException e) {
            getLog().warn(e);
        }
        return classPath;
    }

    private List<File> getPluginClassPath() {
        final List<File> classPath = new ArrayList<>();
        for (Object artifact : this.pluginArtifactMap.values()) {
            final Artifact dependency = (Artifact) artifact;
            if (isRelevantDep(dependency)) {
                classPath.add(dependency.getFile());
            }
        }
        return classPath;
    }

    private boolean isRelevantDep(final Artifact dependency) {
        return dependency.getGroupId().equals("edu.utdallas")
                && dependency.getArtifactId().equals("objsim");
    }

    private ClassByteArraySource createClassByteArraySource(final ClassPath classPath) {
        final ClassPathByteArraySource cpbas = new ClassPathByteArraySource(classPath);
        final ClassByteArraySource cbas = fallbackToClassLoader(cpbas);
        return new CachingByteArraySource(cbas, CACHE_SIZE);
    }

    // this method is adopted from PIT's source code
    private ClassByteArraySource fallbackToClassLoader(final ClassByteArraySource bas) {
        final ClassByteArraySource clSource = ClassloaderByteArraySource.fromContext();
        return new ClassByteArraySource() {
            @Override
            public Option<byte[]> getBytes(String clazz) {
                final Option<byte[]> maybeBytes = bas.getBytes(clazz);
                if (maybeBytes.hasSome()) {
                    return maybeBytes;
                }
                return clSource.getBytes(clazz);
            }
        };
    }
}