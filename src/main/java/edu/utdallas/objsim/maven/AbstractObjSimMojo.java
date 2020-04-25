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
import edu.utdallas.objsim.commons.misc.NameUtils;
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The base class for our awesome Maven plugin Mojo!
 *
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

    /**
     * The name of the file (in relative or absolute form) of the CSV file
     * containing required information about patches.
     * By default, the file <code>input-file.csv</code>, in the project
     * base directory, shall be used.
     */
    @Parameter(property = "inputCSVFile", defaultValue = "input-file.csv")
    protected File inputCSVFile;

    /**
     * A list of originally failing test cases. You want to tell ObjSim which test
     * cases, among those that cover a patch location were originally failing.
     * The test cases should be fully qualified, and in either of the following
     * forms:
     * <code>c.t</code>
     * <code>c:t</code>
     * <code>c::t</code>
     * where c is the fully qualified Java name of the test cass and t is the name
     * of the test method (without parameter lists).
     */
    @Parameter(property = "failingTests", required = true)
    protected Set<String> failingTests;

    /**
     * A list of JVM arguments used when creating a child JVM process, i.e. during
     * profiling.
     *
     * If left unspecified, ObjSim will use the following arguments:
     * "-Xmx32g"
     */
    @Parameter(property = "childJVMArgs")
    protected List<String> childJVMArgs;

    @Parameter(property = "whiteListPrefix", defaultValue = "${project.groupId}")
    protected String whiteListPrefix;

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
                    .withWhiteListPrefix(this.whiteListPrefix)
                    .withClassPath(classPath)
                    .withCompatibleJREHome(this.compatibleJREHome)
                    .withChildJVMArgs(this.childJVMArgs)
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
            temp.add(NameUtils.sanitizeTestName(failingTest));
        }
        this.failingTests.clear();
        this.failingTests.addAll(temp);

        if (this.childJVMArgs == null || this.childJVMArgs.isEmpty()) {
            this.childJVMArgs = Collections.singletonList("-Xmx32g");
        }

        if (this.whiteListPrefix.isEmpty()) {
            this.whiteListPrefix = this.project.getGroupId();
        }
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

    // credit: this method is adopted from PIT's source code
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
