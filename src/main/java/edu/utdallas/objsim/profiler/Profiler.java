/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objsim.commons.MemberNameUtils;
import edu.utdallas.objsim.junit.runner.JUnitRunner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.manipulation.Filter;
import org.pitest.boot.HotSwapAgent;
import org.pitest.functional.Option;
import org.pitest.junit.DescriptionFilter;
import org.pitest.junit.adapter.AdaptedJUnitTestUnit;
import org.pitest.process.ProcessArgs;
import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.ExitCode;
import org.pitest.util.SafeDataInputStream;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.runner.Description.createTestDescription;

import static edu.utdallas.objsim.commons.MemberNameUtils.decomposeMethodName;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class Profiler {
    private Profiler() {

    }

    public static void main(String[] args) throws Exception {
        System.out.println("Profiler is HERE!");
        final int port = Integer.parseInt(args[0]);
        try (Socket socket = new Socket("localhost", port)) {
            final SafeDataInputStream dis = new SafeDataInputStream(socket.getInputStream());

            final ProfilerArguments arguments = dis.read(ProfilerArguments.class);

            HotSwapAgent.addTransformer(new ProfilerTransformer(arguments.getPatchedMethodName()));

            final ProfilerReporter reporter = new ProfilerReporter(socket.getOutputStream());

            final JUnitRunner runner = new JUnitRunner(testNameToTestUnit(arguments.getCoveringTestNames()));
            runner.setTestUnits(decorateTestCases(runner.getTestUnits(), reporter));
            runner.run();

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
                            MemberNameUtils.sanitizeExtendedTestName(testUnit.getDescription().getName());
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
                    decomposeMethodName(MemberNameUtils.sanitizeExtendedTestName(testCaseName));
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

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final File targetDirectory,
                                                      final File patchedClassFile,
                                                      final String patchedMethodFullName,
                                                      final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        Validate.isTrue(patchedClassFile.isFile());
        Validate.isTrue(targetDirectory.isDirectory());
        final Backup backup = backup(new File(targetDirectory, "classes"), patchedClassFile);
        final ProfilerArguments arguments = new ProfilerArguments(patchedMethodFullName, coveringTestNames);
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        backup.restore();
        return process.getSnapshots();
    }

    public static Map<String, Wrapped[]> getSnapshots(final ProcessArgs defaultProcessArgs,
                                                      final String patchedMethodFullName,
                                                      final Collection<String> coveringTestNames)
            throws IOException, InterruptedException {
        final ProfilerArguments arguments = new ProfilerArguments(patchedMethodFullName, coveringTestNames);
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        return process.getSnapshots();
    }

    private static Backup backup(final File baseDirectory,
                                 final File patchedClassFile) throws IOException {
        final String classFullName = MemberNameUtils.getClassName(patchedClassFile);
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
