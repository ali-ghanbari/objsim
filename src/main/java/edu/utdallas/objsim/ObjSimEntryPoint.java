package edu.utdallas.objsim;

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
import edu.utdallas.objsim.commons.process.LoggerUtils;
import edu.utdallas.objsim.commons.relational.MethodsDom;
import edu.utdallas.objsim.profiler.prelude.PreludeProfiler;
import edu.utdallas.objsim.profiler.prelude.PreludeProfilerResults;
import edu.utdallas.objsim.profiler.primary.PrimaryProfiler;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassInfo;
import org.pitest.classpath.ClassFilter;
import org.pitest.classpath.ClassPath;
import org.pitest.classpath.CodeSource;
import org.pitest.classpath.PathFilter;
import org.pitest.classpath.ProjectClassPaths;
import org.pitest.functional.predicate.Predicate;
import org.pitest.functional.prelude.Prelude;
import org.pitest.mutationtest.config.DefaultCodePathPredicate;
import org.pitest.mutationtest.config.DefaultDependencyPathPredicate;
import org.pitest.mutationtest.tooling.JarCreatingJarFinder;
import org.pitest.mutationtest.tooling.KnownLocationJavaAgentFinder;
import org.pitest.process.JavaAgent;
import org.pitest.process.JavaExecutableLocator;
import org.pitest.process.KnownLocationJavaExecutableLocator;
import org.pitest.process.LaunchOptions;
import org.pitest.process.ProcessArgs;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry point for our patch prioritization system!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ObjSimEntryPoint {
    private static final CSVFormat CSV_FORMAT;

    static {
        CSV_FORMAT = CSVFormat.DEFAULT.withRecordSeparator(System.lineSeparator());
    }

    private final File classBuildDirectory;

    private final ClassPath classPath;

    private final ClassByteArraySource byteArraySource;

    private final Predicate<String> appClassFilter;

    private final Predicate<String> testClassFilter;

    private final File compatibleJREHome;

    private final List<String> childJVMArgs;

    private final File inputCSVFile;

    public ObjSimEntryPoint(final File classBuildDirectory,
                            final ClassPath classPath,
                            final ClassByteArraySource byteArraySource,
                            final Predicate<String> appClassFilter,
                            final Predicate<String> testClassFilter,
                            final File compatibleJREHome,
                            final Collection<String> childJVMArgs,
                            final File inputCSVFile) {
        this.classBuildDirectory = classBuildDirectory;
        this.classPath = classPath;
        this.byteArraySource = byteArraySource;
        this.appClassFilter = appClassFilter;
        this.testClassFilter = testClassFilter;
        this.compatibleJREHome = compatibleJREHome;
        this.childJVMArgs = new ArrayList<>(childJVMArgs);
        this.inputCSVFile = inputCSVFile;
    }

    /**
     * Entry point for the entire system!
     *
     * @throws Exception Any failure
     */
    public void run() throws Exception {
        final List<InputRecord> records = new LinkedList<>();
        final Set<String> targetMethods = new HashSet<>();
        final Map<Integer, String> groundTruthMap = new HashMap<>(); // patchId --> ground-truth label

        try (final Reader fr = new FileReader(this.inputCSVFile);
             final CSVParser parser = CSVParser.parse(fr, CSV_FORMAT)) {
            for (final CSVRecord record : parser.getRecords()) {
                final InputRecord inputRecord = InputRecord.fromCSVRecord(record);
                records.add(inputRecord);
                targetMethods.addAll(inputRecord.patchedMethods);
                groundTruthMap.put(inputRecord.patchId, inputRecord.groundTruthLabel);
            }
        }

        final ProcessArgs defaultProcessArgs = getDefaultProcessArgs();
        final Collection<String> testClassNames = retrieveTestClassNames();

        if (testClassNames.isEmpty()) {
            throw new ClassNotFoundException("no test classes found; perhaps testClassFilter is not set properly");
        }

        final PreludeProfilerResults preludeResults = PreludeProfiler.runPrelude(defaultProcessArgs,
                this.appClassFilter, testClassNames, targetMethods);

        final MethodsDom methodsDom = preludeResults.getMethodsDom();
        final Map<Integer, Score> patchScoreMap = new HashMap<>();
        for (final InputRecord record : records) {
            final Set<String> patchedMethods = record.patchedMethods;
            final Set<String> coveringPassingTests = new HashSet<>();
            final Set<String> coveringFailingTests = new HashSet<>();
            for (final String methodName : patchedMethods) {
                final int methodIndex = methodsDom.indexOf(methodName);
                if (methodIndex < 0) {
                    throw new IllegalStateException("Not found method '" + methodName + "' in methods dom.");
                }
                final CoveringTests coveringTests = getCoveringTests(preludeResults.getMethodCoverageMap(),
                        preludeResults.getFailingTests(), methodIndex);
                coveringPassingTests.addAll(coveringTests.passingTests);
                coveringFailingTests.addAll(coveringTests.failingTests);
            }
            // run covering passing tests on unpatched program
            Map<String, Wrapped[]> originalSnapshots = PrimaryProfiler.getSnapshots(defaultProcessArgs,
                    patchedMethods, coveringPassingTests, preludeResults);
            // run covering passing tests on patched program
            Map<String, Wrapped[]> patchedSnapshots = PrimaryProfiler.getSnapshots(defaultProcessArgs,
                    this.classBuildDirectory, record.classFiles, patchedMethods, coveringPassingTests, preludeResults);
            final Triple<Double /*min*/, Double /*avg*/, Double /*max*/> passingScore =
                    calculateDistance(originalSnapshots, patchedSnapshots);
            // run covering failing tests on unpatched program
            originalSnapshots = PrimaryProfiler.getSnapshots(defaultProcessArgs, patchedMethods,
                    coveringFailingTests, preludeResults);
            // run covering failing tests on patched program
            patchedSnapshots = PrimaryProfiler.getSnapshots(defaultProcessArgs, this.classBuildDirectory,
                    record.classFiles, patchedMethods, coveringFailingTests, preludeResults);
            final Triple<Double /*min*/, Double /*avg*/, Double /*max*/> failingScore =
                    calculateDistance(originalSnapshots, patchedSnapshots);
            patchScoreMap.put(record.patchId, new Score(passingScore, failingScore));
        }

        try (final PrintWriter pw = new PrintWriter("objsim-scores-complete.csv");
             final CSVPrinter printer = new CSVPrinter(pw, CSV_FORMAT)) {
            printer.printRecord("Patch Id",
                    "Min Score (Passing)",
                    "Avg. Score (Passing)",
                    "Max Score (Passing)",
                    "Min Score (Failing)",
                    "Avg. Score (Failing)",
                    "Max Score (Failing)",
                    "Ground-Truth Label");
            for (final Map.Entry<Integer, Score> entry : patchScoreMap.entrySet()) {
                final int patchId = entry.getKey();
                final Score score = entry.getValue();
                printer.printRecord(patchId,
                        score.passingScore.getLeft(),
                        score.passingScore.getMiddle(),
                        score.passingScore.getRight(),
                        score.failingScore.getLeft(),
                        score.failingScore.getMiddle(),
                        score.failingScore.getRight(),
                        groundTruthMap.get(patchId));
            }
        }
    }

    private static class Score {
        final Triple<Double, Double, Double> passingScore;

        final Triple<Double, Double, Double> failingScore;

        public Score(final Triple<Double, Double, Double> passingScore,
                     final Triple<Double, Double, Double> failingScore) {
            this.passingScore = passingScore;
            this.failingScore = failingScore;
        }
    }

    private Triple<Double /*min*/, Double /*avg*/, Double /*max*/> calculateDistance(final Map<String, Wrapped[]> originalSnapshots,
                                                                                     final Map<String, Wrapped[]> patchedSnapshots) {
        if (originalSnapshots.size() != patchedSnapshots.size()) {
            return ImmutableTriple.of(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        if (originalSnapshots.isEmpty()) {
            return ImmutableTriple.of(0D, 0D, 0D);
        }
        double minDist = Double.POSITIVE_INFINITY;
        double maxDist = Double.NEGATIVE_INFINITY;
        double distSum = 0D;
        int size = 0;
        for (final Map.Entry<String, Wrapped[]> entry : originalSnapshots.entrySet()) {
            final String testName = entry.getKey();
            final Wrapped[] os = entry.getValue();
            final Wrapped[] ps = patchedSnapshots.get(testName);
            if (ps == null) {
                return ImmutableTriple.of(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (os.length != ps.length) {
                return ImmutableTriple.of(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            final int n = os.length;
            if (n == 0) { // avoiding NaN's
                continue;
            }
            size += n;
            for (int i = 0; i < n; i++) {
                final double distance = os[i].distance(ps[i]);
                minDist = Math.min(minDist, distance);
                maxDist = Math.max(maxDist, distance);
                if (!Double.isInfinite(distance) && !Double.isInfinite(distSum)) {
                    distSum += distance;
                } else {
                    distSum = Double.POSITIVE_INFINITY;
                }
            }
        }
        double avgDist = 0;
        if (size > 0 && !Double.isInfinite(distSum)) {
            avgDist = distSum / (double) size;
        }
        return ImmutableTriple.of(minDist, avgDist, maxDist);
    }

    private CoveringTests getCoveringTests(final Map<String, Set<Integer>> map,
                                           final Set<String> failingTests,
                                           final int methodIndex) {
        final CoveringTests coveringTests = new CoveringTests();
        for (final Map.Entry<String, Set<Integer>> entry : map.entrySet()) {
            if (entry.getValue().contains(methodIndex)) {
                final String testName = entry.getKey();
                if (failingTests.contains(testName)) {
                    coveringTests.failingTests.add(testName);
                } else {
                    coveringTests.passingTests.add(testName);
                }
            }
        }
        return coveringTests;
    }

    private static class CoveringTests {
        final Set<String> passingTests;

        final Set<String> failingTests;

        public CoveringTests() {
            this.passingTests = new HashSet<>();
            this.failingTests = new HashSet<>();
        }
    }

    private Set<String> retrieveTestClassNames() {
        final ProjectClassPaths pcp = new ProjectClassPaths(this.classPath, defaultClassFilter(), defaultPathFilter());
        final CodeSource codeSource = new CodeSource(pcp);
        final Set<String> testClassNames = new HashSet<>();
        for (final ClassInfo classInfo : codeSource.getTests()) {
            testClassNames.add(classInfo.getName().asJavaName());
        }
        return testClassNames;
    }

    private static PathFilter defaultPathFilter() {
        return new PathFilter(new DefaultCodePathPredicate(),
                Prelude.not(new DefaultDependencyPathPredicate()));
    }

    private ClassFilter defaultClassFilter() {
        return new ClassFilter(this.testClassFilter, this.appClassFilter);
    }

    private ProcessArgs getDefaultProcessArgs() {
        final LaunchOptions defaultLaunchOptions = new LaunchOptions(getJavaAgent(),
                getDefaultJavaExecutableLocator(),
                this.childJVMArgs,
                Collections.<String, String>emptyMap());
        return ProcessArgs.withClassPath(this.classPath)
                .andLaunchOptions(defaultLaunchOptions)
                .andStderr(LoggerUtils.err())
                .andStdout(LoggerUtils.out());
    }

    private JavaExecutableLocator getDefaultJavaExecutableLocator() {
        final File javaFile = FileUtils.getFile(this.compatibleJREHome, "bin", "java");
        return new KnownLocationJavaExecutableLocator(javaFile.getAbsolutePath());
    }

    private JavaAgent getJavaAgent() {
        final String jarLocation = (new JarCreatingJarFinder(this.byteArraySource))
                .getJarLocation()
                .value();
        return new KnownLocationJavaAgentFinder(jarLocation);
    }
}
