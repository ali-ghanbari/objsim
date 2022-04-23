package edu.utdallas.objsim.profiler.prelude;

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

import edu.utdallas.objsim.commons.misc.NameUtils;
import edu.utdallas.objsim.commons.process.ResourceUtils;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.commons.relational.MethodsDom;
import edu.utdallas.objsim.junit.runner.CloseableTestUnit;
import edu.utdallas.objsim.junit.runner.JUnitRunner;
import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.functional.predicate.Predicate;
import org.pitest.process.ProcessArgs;
import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.ExitCode;
import org.pitest.util.IsolationUtils;
import org.pitest.util.SafeDataInputStream;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry point for Profiler.
 * Profiler is responsible for running test cases against original and patched versions
 * of the program and returning the system state at the exit point(s) of the specified
 * method.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class PreludeProfiler {
    private static final int CACHE_SIZE = 500;

    private PreludeProfiler() { }

    public static void main(String[] args) {
        System.out.println("Prelude Profiler is HERE!");
        final int port = Integer.parseInt(args[0]);
        Socket socket = null;
        try {
            socket = new Socket("localhost", port);

            final SafeDataInputStream dis = new SafeDataInputStream(socket.getInputStream());

            final PreludeProfilerArguments arguments = dis.read(PreludeProfilerArguments.class);

            final ClassLoader contextClassLoader = IsolationUtils.getContextClassLoader();
            ClassByteArraySource byteArraySource = new ClassloaderByteArraySource(contextClassLoader);
            byteArraySource = new CachingByteArraySource(byteArraySource, CACHE_SIZE);

            final FieldsDom fieldsDom = new FieldsDom();
            final MethodsDom methodsDom = new MethodsDom();
            final ClassFileTransformer transformer = new PreludeTransformer(byteArraySource,
                    arguments.appClassFilter,
                    arguments.patchedMethods,
                    fieldsDom,
                    methodsDom);
            HotSwapAgent.addTransformer(transformer);

            final ProfilerReporter reporter = new ProfilerReporter(socket.getOutputStream());

            final JUnitRunner runner = new JUnitRunner(arguments.testClassNames);
            runner.setTestUnits(decorateTestCases(runner.getTestUnits()));
            runner.run();

            fieldsDom.save(".", true);
            methodsDom.save(".", true);
            reporter.reportMethodCoverageMap(MethodCoverageRecorder.getCoverageMap());
            reporter.reportFieldAccessesMap(FieldAccessRecorder.getFieldAccesses());
            reporter.reportFailingTests(runner.getFailingTests());

            System.out.println("Prelude Profiler is DONE!");
            reporter.done(ExitCode.OK);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
            System.out.println("WARNING: Error during profiling!");
        } finally {
            ResourceUtils.safelyCloseSocket(socket);
        }
    }

    private static List<CloseableTestUnit> decorateTestCases(final List<CloseableTestUnit> testUnits) {
        final List<CloseableTestUnit> res = new LinkedList<>();
        for (final TestUnit testUnit : testUnits) {
            res.add(new CloseableTestUnit() {
                @Override
                public void execute(ResultCollector resultCollector) {
                    final String testName = NameUtils.sanitizeExtendedTestName(testUnit.getDescription().getName());
                    MethodCoverageRecorder.addTestUnit(testName);
                    testUnit.execute(resultCollector);
                }

                @Override
                public void close() { }

                @Override
                public Description getDescription() {
                    return testUnit.getDescription();
                }
            });
        }
        return res;
    }

    public static PreludeProfilerResults runPrelude(final ProcessArgs defaultProcessArgs,
                                                    final Predicate<String> appClassFilter,
                                                    final Collection<String> testClassNames,
                                                    final Collection<String> patchedMethods) throws IOException, InterruptedException {
        final PreludeProfilerArguments arguments = new PreludeProfilerArguments(appClassFilter,
                testClassNames,
                patchedMethods);
        final ProfilerProcess process = new ProfilerProcess(defaultProcessArgs, arguments);
        process.start();
        process.waitToDie();
        final FieldsDom fieldsDom = new FieldsDom(".");
        final MethodsDom methodsDom = new MethodsDom(".");
        return new PreludeProfilerResults() {
            @Override
            public FieldsDom getFieldsDom() {
                return fieldsDom;
            }

            @Override
            public MethodsDom getMethodsDom() {
                return methodsDom;
            }

            @Override
            public Map<Integer, int[]> accessedFieldsMap() {
                return process.getAccessedFieldsMap();
            }

            @Override
            public Map<String, Set<Integer>> getMethodCoverageMap() {
                return process.getMethodCoverageMap();
            }

            @Override
            public Set<String> getFailingTests() {
                return process.getFailingTests();
            }
        };
    }
}
