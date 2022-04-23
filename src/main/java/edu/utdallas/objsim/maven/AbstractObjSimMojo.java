package edu.utdallas.objsim.maven;

/*
 * #%L
 * objsim
 * %%
 * Copyright (C) 2020 The University of Texas at Dallas
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import edu.utdallas.objsim.ObjSimEntryPoint;
import edu.utdallas.objsim.commons.classpath.ClassPathUtils;
import edu.utdallas.objsim.commons.functional.PredicateFactory;
import edu.utdallas.objsim.commons.misc.NameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.functional.predicate.Predicate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The base class for our awesome Maven plugin Mojo!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public abstract class AbstractObjSimMojo extends AbstractMojo {
    protected File compatibleJREHome;

    protected Predicate<String> appClassFilter;

    protected Predicate<String> testClassFilter;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "plugin.artifactMap", readonly = true, required = true)
    protected Map<String, Artifact> pluginArtifactMap;

    // -----------------------
    // ---- plugin params ----
    // -----------------------

    /**
     * The name of the file (in relative or absolute form) of the CSV file
     * containing required information about patches.
     * By default, the file <code>input-file.csv</code>, in the project
     * base directory, shall be used.
     */
    @Parameter(property = "inputCSVFile", defaultValue = "input-file.csv")
    protected File inputCSVFile;

    @Parameter(property = "targetClasses")
    protected Set<String> targetClasses;

    @Parameter(property = "excludedClasses")
    protected Set<String> excludedClasses;

    @Parameter(property = "excludeTestClasses", defaultValue = "true")
    protected boolean excludeTestClasses;

    @Parameter(property = "targetTests")
    protected Set<String> targetTests;

    @Parameter(property = "excludedTests")
    protected Set<String> excludedTests;

    @Parameter(property = "inputFile", defaultValue = "./input-file.csv")
    protected File inputFile;

    @Parameter(property = "includeProductionClasses", defaultValue = "true")
    protected boolean includeProductionClasses;

    @Parameter(property = "childJVMArgs")
    protected Set<String> childJVMArgs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        validateAndSanitizeParameters();

        final ClassPath classPath = createClassPath();
        final ClassByteArraySource byteArraySource = ClassPathUtils.createClassByteArraySource(classPath);

        final File classBuildDirectory = new File(this.project.getBuild().getOutputDirectory());
        try {
            (new ObjSimEntryPoint(classBuildDirectory,
                    classPath,
                    byteArraySource,
                    this.appClassFilter,
                    this.testClassFilter,
                    this.compatibleJREHome,
                    this.childJVMArgs,
                    this.inputCSVFile)).run();
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
            throw new MojoFailureException("Invalid JAVA_HOME");
        }

        final String groupId = this.project.getGroupId();

        if (this.targetTests == null) {
            this.targetTests = new HashSet<>();
        }
        if (this.excludedTests == null) {
            this.excludedTests = new HashSet<>();
        }
        if (this.targetTests.isEmpty()) {
            this.targetTests.add(String.format("%s*Test", groupId));
            this.targetTests.add(String.format("%s*Tests", groupId));
            this.targetTests.add(String.format("%s*TestCase*", groupId));
        }
        this.testClassFilter = PredicateFactory.and(PredicateFactory.orGlobs(this.targetTests),
                PredicateFactory.not(PredicateFactory.orGlobs(this.excludedTests)));

        if (this.targetClasses == null) {
            this.targetClasses = new HashSet<>();
        }
        if (this.excludedClasses == null) {
            this.excludedClasses = new HashSet<>();
        }
        if (this.targetClasses.isEmpty()) {
            this.targetClasses.add(groupId + "*");
        }
        Predicate<String> excludedClassesFilter = PredicateFactory.orGlobs(this.excludedClasses);
        if (this.excludeTestClasses) {
            final File testClassesBaseDirectory = new File(this.project.getBuild().getTestOutputDirectory());
            excludedClassesFilter = PredicateFactory.or(excludedClassesFilter, classFileFilter(testClassesBaseDirectory));
        }
        Predicate<String> p = PredicateFactory.orGlobs(this.targetClasses);
        if (this.includeProductionClasses) {
            final File classesBaseDirectory = new File(this.project.getBuild().getOutputDirectory());
            p = PredicateFactory.or(p, classFileFilter(classesBaseDirectory));
        }
        this.appClassFilter = PredicateFactory.and(p, PredicateFactory.not(excludedClassesFilter));

        if (this.childJVMArgs == null) {
            this.childJVMArgs = new HashSet<>();
        }
        if (this.childJVMArgs.isEmpty()) {
            this.childJVMArgs.add("-Xmx128g");
        }
    }

    public static Predicate<String> classFileFilter(final File classesBaseDirectory) {
        final Collection<File> classFiles = FileUtils.listFiles(classesBaseDirectory, new String[] {"class"}, true);
        final Set<String> classes = new HashSet<>();
        for (final File classFile : classFiles) {
            classes.add(NameUtils.getClassName(classFile));
        }
        return PredicateFactory.fromCollection(classes);
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
}
