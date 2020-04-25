package edu.utdallas.objsim.profiler;

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

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objsim.commons.misc.NameUtils;
import edu.utdallas.objsim.commons.process.AbstractChildProcessArguments;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.junit.runner.JUnitRunner;
import edu.utdallas.objsim.profiler.prelude.FieldAccessRecorder;
import edu.utdallas.objsim.profiler.prelude.PreludeTransformer;
import edu.utdallas.objsim.profiler.primary.PrimaryTransformer;
import edu.utdallas.objsim.profiler.primary.SnapshotTracker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.manipulation.Filter;
import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.functional.Option;
import org.pitest.junit.DescriptionFilter;
import org.pitest.junit.adapter.AdaptedJUnitTestUnit;
import org.pitest.process.ProcessArgs;
import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.ExitCode;
import org.pitest.util.IsolationUtils;
import org.pitest.util.SafeDataInputStream;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.runner.Description.createTestDescription;

import static edu.utdallas.objsim.commons.misc.NameUtils.decomposeMethodName;

/**
 * Entry point for Profiler.
 * Profiler is responsible for running test cases against original and patched versions
 * of the program and returning the system state at the exit point(s) of the specified
 * method.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class Profiler {
    private static final int CACHE_SIZE = 500;

    private Profiler() {

    }

    public static void main(String[] args) throws Exception {
        System.out.println("Profiler is HERE!");
        final int port = Integer.parseInt(args[0]);
        try (Socket socket = new Socket("localhost", port)) {
            final SafeDataInputStream dis = new SafeDataInputStream(socket.getInputStream());

            final AbstractChildProcessArguments arguments = dis.read(AbstractChildProcessArguments.class);

            final boolean isPreludeStage = arguments instanceof PreludeProfilerArguments;

            final ClassLoader contextClassLoader = IsolationUtils.getContextClassLoader();
            ClassByteArraySource byteArraySource = new ClassloaderByteArraySource(contextClassLoader);
            byteArraySource = new CachingByteArraySource(byteArraySource, CACHE_SIZE);

            final FieldsDom fieldsDom;
            final ClassFileTransformer transformer;
            if (isPreludeStage) {
                final String whiteListPrefix = ((PreludeProfilerArguments) arguments).getWhiteListPrefix();
                fieldsDom = new FieldsDom();
                transformer = new PreludeTransformer(byteArraySource, whiteListPrefix, arguments.getPatchedMethodName(), fieldsDom);
            } else {
                fieldsDom = ((PrimaryProfilerArguments) arguments).getFieldsDom();
                final Collection<Integer> accessedFields = ((PrimaryProfilerArguments) arguments).getAccessedFields();
                SnapshotTracker.setAccessedFields(fieldsDom, accessedFields);
                transformer = new PrimaryTransformer(arguments.getPatchedMethodName(), byteArraySource);
            }
            HotSwapAgent.addTransformer(transformer);

            final ProfilerReporter reporter = new ProfilerReporter(socket.getOutputStream());

            final JUnitRunner runner = new JUnitRunner(testNameToTestUnit(arguments.getCoveringTestNames()));
            runner.setTestUnits(decorateTestCases(runner.getTestUnits(), reporter));
            runner.run();

            if (isPreludeStage) {
                reporter.reportFieldsDom(fieldsDom);
                reporter.reportFieldAccesses(FieldAccessRecorder.getFieldAccesses());
            }

            System.out.println("Profiler is DONE!");
            reporter.done(ExitCode.OK);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static List<TestUnit> decorateTestCases(final List<TestUnit> testUnits,
                                                    final ProfilerReporter reporter) {
        final List<TestUnit> res = new LinkedList<>();
        for (final TestUnit testUnit : testUnits) {
            res.add(new TestUnit() {
                @Override
                public void execute(ResultCollector resultCollector) {
                    testUnit.execute(resultCollector);
                    final String testName =
                            NameUtils.sanitizeExtendedTestName(testUnit.getDescription().getName());
                    reporter.reportSnapshots(testName, SnapshotTracker.SNAPSHOTS.toArray(new Wrapped[0]));
                    SnapshotTracker.SNAPSHOTS.clear();
                }

                @Override
                public Description getDescription() {
                    return testUnit.getDescription();
                }
            });
        }
        return res;
    }


    private static List<TestUnit> testNameToTestUnit(final Collection<String> testCaseNames)
            throws Exception {
        final List<TestUnit> res = new LinkedList<>();
        for (final String testCaseName : testCaseNames) {
            final Pair<String, String> methodNameParts =
                    decomposeMethodName(NameUtils.sanitizeExtendedTestName(testCaseName));
            final Class<?> testSuite = Class.forName(methodNameParts.getLeft());
            Method testCase = null;
            for (final Method method : testSuite.getMethods()) {
                final int mod = method.getModifiers();
                if (Modifier.isAbstract(mod) || Modifier.isNative(mod) || !Modifier.isPublic(mod)) {
                    continue;
                }
                if (method.getName().equals(methodNameParts.getRight())) {
                    testCase = method;
                    break;
                }
            }
            if (testCase == null) {
                throw new IllegalStateException("not found test method " + methodNameParts.getRight());
            }
            final Filter filter = DescriptionFilter.matchMethodDescription(createTestDescription(testSuite,
                    testCase.getName(),
                    testCase.getDeclaredAnnotations()));
            res.add(new AdaptedJUnitTestUnit(testSuite, Option.some(filter)));
        }
        return res;
    }

    private static Pair<FieldsDom, ? extends List<Integer>> runPrelude(final ProcessArgs defaultProcessArgs,
                                                                       final String whiteListPrefix,
                                                                       final String patchedMethodFullName,
                                                                       final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        final PreludeProfilerArguments arguments = new PreludeProfilerArguments(whiteListPrefix, patchedMethodFullName, coveringTestNames);
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        return new ImmutablePair<>(process.getFieldsDom(), process.getFieldAccesses());
    }

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final File targetDirectory,
                                                      final File patchedClassFile,
                                                      final String whiteListPrefix,
                                                      final String patchedMethodFullName,
                                                      final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        Validate.isTrue(patchedClassFile.isFile());
        Validate.isTrue(targetDirectory.isDirectory());
        final Backup backup = backup(new File(targetDirectory, "classes"), patchedClassFile);
        final ProfilerProcess process = runProcess(defaultProcessArgs,
                whiteListPrefix,
                patchedMethodFullName,
                coveringTestNames);
        backup.restore();
        return process.getSnapshots();
    }

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final String whiteListPrefix,
                                                      final String patchedMethodFullName,
                                                      final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        return runProcess(defaultProcessArgs,
                whiteListPrefix,
                patchedMethodFullName,
                coveringTestNames).getSnapshots();
    }

    private static ProfilerProcess runProcess(final ProcessArgs defaultProcessArgs,
                                              final String whiteListPrefix,
                                              final String patchedMethodFullName,
                                              final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        final Pair<FieldsDom, ? extends List<Integer>> preludeResult = runPrelude(defaultProcessArgs,
                whiteListPrefix, patchedMethodFullName, coveringTestNames);
        final FieldsDom fieldsDom = preludeResult.getLeft();
        final List<Integer> accessedFields = preludeResult.getRight();
        final AbstractChildProcessArguments arguments = new PrimaryProfilerArguments(patchedMethodFullName,
                coveringTestNames, fieldsDom, accessedFields);
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        return process;
    }

    private static Backup backup(final File baseDirectory,
                                 final File patchedClassFile) throws IOException {
        final String classFullName = NameUtils.getClassName(patchedClassFile);
        final int indexOfLastDot = classFullName.lastIndexOf('.');
        final String packageName = classFullName.substring(0, indexOfLastDot);
        final String className = classFullName.substring(1 + indexOfLastDot);
        final File destDirectory = FileUtils.getFile(baseDirectory, packageName.split("\\."));
        final File backupFile = new File(className + ".class.bak");
        final File replacedFile = new File(destDirectory, className + ".class");
        FileUtils.copyFile(replacedFile, backupFile);
        FileUtils.copyFile(patchedClassFile, replacedFile);
        return new Backup(backupFile, replacedFile);
    }

    private static final class Backup {
        private final File backupFile;

        private final File replacedFile;

        Backup(final File backupFile, final File replacedFile) {
            this.backupFile = backupFile;
            this.replacedFile = replacedFile;
        }

        void restore() throws IOException {
            FileUtils.copyFile(this.backupFile, this.replacedFile);
            if (!this.backupFile.delete()) {
                throw new RuntimeException("Unable to delete " + this.backupFile);
            }
        }
    }
}
