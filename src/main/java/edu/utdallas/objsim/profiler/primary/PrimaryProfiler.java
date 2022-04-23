package edu.utdallas.objsim.profiler.primary;

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
import edu.utdallas.objsim.commons.process.ResourceUtils;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.junit.runner.CloseableTestUnit;
import edu.utdallas.objsim.junit.runner.JUnitRunner;
import edu.utdallas.objsim.junit.runner.WrappingTestUnit;
import edu.utdallas.objsim.profiler.prelude.PreludeProfilerResults;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.manipulation.Filter;
import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.functional.Option;
import org.pitest.junit.DescriptionFilter;
import org.pitest.junit.adapter.AdaptedJUnitTestUnit;
import org.pitest.mutationtest.execute.MemoryWatchdog;
import org.pitest.process.ProcessArgs;
import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.ExitCode;
import org.pitest.util.IsolationUtils;
import org.pitest.util.SafeDataInputStream;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.management.MemoryNotificationInfo;
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
public final class PrimaryProfiler {
    private static final int CACHE_SIZE = 500;

    private PrimaryProfiler() { }

    public static void main(String[] args) {
        System.out.println("Primary Profiler is HERE!");
        final int port = Integer.parseInt(args[0]);
        Socket socket = null;
        try {
            socket = new Socket("localhost", port);

            final SafeDataInputStream dis = new SafeDataInputStream(socket.getInputStream());

            final PrimaryProfilerArguments arguments = dis.read(PrimaryProfilerArguments.class);

            final ClassLoader contextClassLoader = IsolationUtils.getContextClassLoader();
            ClassByteArraySource byteArraySource = new ClassloaderByteArraySource(contextClassLoader);
            byteArraySource = new CachingByteArraySource(byteArraySource, CACHE_SIZE);

            final ClassFileTransformer transformer = new PrimaryTransformer(arguments.getPatchedMethods(),
                    byteArraySource);
            HotSwapAgent.addTransformer(transformer);

            final ProfilerReporter reporter = new ProfilerReporter(socket.getOutputStream());

            addMemoryWatchDog(reporter);

            final FieldsDom fieldsDom = arguments.getFieldsDom();
            SnapshotTracker.clearAccessFields();
            for (final Map.Entry<Integer, int[]> entry : arguments.accessedFieldsMap.entrySet()) {
                SnapshotTracker.setAccessedFields(fieldsDom, entry.getValue());
            }

            final JUnitRunner runner = new JUnitRunner(testNameToTestUnit(arguments.coveringTests));
            runner.setTestUnits(decorateTestCases(runner.getTestUnits(), reporter));
            runner.run();

            System.out.println("Primary Profiler is DONE!");
            reporter.done(ExitCode.OK);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
            System.out.println("WARNING: Error during profiling!");
        } finally {
            ResourceUtils.safelyCloseSocket(socket);
        }
    }

    private static List<CloseableTestUnit> decorateTestCases(final List<CloseableTestUnit> testUnits,
                                                    final ProfilerReporter reporter) {
        final List<CloseableTestUnit> res = new LinkedList<>();
        for (final TestUnit testUnit : testUnits) {
            res.add(new CloseableTestUnit() {
                @Override
                public void execute(ResultCollector resultCollector) {
                    testUnit.execute(resultCollector);
                    close();
                }

                @Override
                public void close() {
                    final String testName = NameUtils.sanitizeExtendedTestName(testUnit.getDescription().getName());
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


    private static List<CloseableTestUnit> testNameToTestUnit(final Collection<String> testCaseNames) throws Exception {
        final List<CloseableTestUnit> res = new LinkedList<>();
        for (final String testCaseName : testCaseNames) {
            final Pair<String, String> methodNameParts = decomposeMethodName(NameUtils.sanitizeExtendedTestName(testCaseName));
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
            res.add(new WrappingTestUnit(new AdaptedJUnitTestUnit(testSuite, Option.some(filter))));
        }
        return res;
    }

    // credit: this method is copied from PITest source code
    private static void addMemoryWatchDog(final ProfilerReporter reporter) {
        final NotificationListener listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                final String type = notification.getType();
                if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                    final CompositeData cd = (CompositeData) notification.getUserData();
                    final MemoryNotificationInfo memInfo = MemoryNotificationInfo.from(cd);
                    reporter.done(ExitCode.OUT_OF_MEMORY);
                } else {
                    System.out.println("Unknown notification: " + notification);
                }
            }
        };
        MemoryWatchdog.addWatchDogToAllPools(90, listener);
    }

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final File classBuildDirectory,
                                                      final Collection<File> patchedClassFiles,
                                                      final Collection<String> patchedMethods,
                                                      final Collection<String> coveringTests,
                                                      final PreludeProfilerResults preludeResults)
            throws IOException, InterruptedException {
        for (final File patchedClassFile : patchedClassFiles) {
            if (!patchedClassFile.isFile()) {
                throw new IllegalArgumentException("Invalid patch file " + patchedClassFile.getAbsolutePath());
            }
        }
        Validate.isTrue(classBuildDirectory.isDirectory());
        final List<Backup> backupList = backup(classBuildDirectory, patchedClassFiles);
        final Map<String, Wrapped[]> snapshots = getSnapshots(defaultProcessArgs,
                patchedMethods,
                coveringTests,
                preludeResults);
        for (final Backup backup : backupList) {
            backup.restore();
        }
        return snapshots;
    }

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final Collection<String> patchedMethods,
                                                      final Collection<String> coveringTests,
                                                      final PreludeProfilerResults preludeResults)
            throws IOException, InterruptedException {
        final PrimaryProfilerArguments arguments = new PrimaryProfilerArguments(patchedMethods,
                coveringTests, preludeResults.accessedFieldsMap());
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        return process.getSnapshots();
    }

    private static List<Backup> backup(final File baseDirectory,
                                       final Collection<File> patchedClassFiles) throws IOException {
        final List<Backup> list = new LinkedList<>();
        for (final File patchedClassFile : patchedClassFiles) {
            final String classFullName = NameUtils.getClassName(patchedClassFile);
            final int indexOfLastDot = classFullName.lastIndexOf('.');
            final String packageName = classFullName.substring(0, indexOfLastDot);
            final String className = classFullName.substring(1 + indexOfLastDot);
            final File destDirectory = FileUtils.getFile(baseDirectory, packageName.split("\\."));
            final File backupFile = new File(className + ".class.bak");
            final File replacedFile = new File(destDirectory, className + ".class");
            FileUtils.copyFile(replacedFile, backupFile);
            FileUtils.copyFile(patchedClassFile, replacedFile);
            list.add(new Backup(backupFile, replacedFile));
        }
        return list;
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
