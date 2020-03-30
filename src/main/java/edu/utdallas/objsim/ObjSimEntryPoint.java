/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim;

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objsim.commons.DistanceVisitor;
import edu.utdallas.objsim.commons.LoggerUtils;
import edu.utdallas.objsim.commons.MemberNameUtils;
import edu.utdallas.objsim.profiler.Profiler;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.functional.predicate.Predicate;
import org.pitest.mutationtest.tooling.JarCreatingJarFinder;
import org.pitest.mutationtest.tooling.KnownLocationJavaAgentFinder;
import org.pitest.process.JavaAgent;
import org.pitest.process.JavaExecutableLocator;
import org.pitest.process.KnownLocationJavaExecutableLocator;
import org.pitest.process.LaunchOptions;
import org.pitest.process.ProcessArgs;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ObjSimEntryPoint {
    private final File baseDirectory;

    private final ClassPath classPath;

    private final ClassByteArraySource byteArraySource;

    private final File compatibleJREHome;

    private final File inputCSVFile;

    private final Set<String> failingTests;

    private ObjSimEntryPoint(final File baseDirectory,
                             final ClassPath classPath,
                             final ClassByteArraySource byteArraySource,
                             final File compatibleJREHome,
                             final File inputCSVFile,
                             final Set<String> failingTests) {
        this.baseDirectory = baseDirectory;
        this.classPath = classPath;
        this.byteArraySource = byteArraySource;
        this.compatibleJREHome = compatibleJREHome;
        this.inputCSVFile = inputCSVFile;
        this.failingTests = failingTests;
    }

    public static ObjSimEntryPoint createEntryPoint() {
        return new ObjSimEntryPoint(null, null, null, null, null, null);
    }

    public ObjSimEntryPoint withBaseDirectory(final File baseDirectory) {
        return new ObjSimEntryPoint(baseDirectory, this.classPath, this.byteArraySource, this.compatibleJREHome, this.inputCSVFile, this.failingTests);
    }

    public ObjSimEntryPoint withClassPath(final ClassPath classPath) {
        return new ObjSimEntryPoint(this.baseDirectory, classPath, this.byteArraySource, this.compatibleJREHome, this.inputCSVFile, this.failingTests);
    }

    public ObjSimEntryPoint withClassByteArraySource(final ClassByteArraySource byteArraySource) {
        return new ObjSimEntryPoint(this.baseDirectory, this.classPath, byteArraySource, this.compatibleJREHome, this.inputCSVFile, this.failingTests);
    }

    public ObjSimEntryPoint withCompatibleJREHome(final File compatibleJREHome) {
        return new ObjSimEntryPoint(this.baseDirectory, this.classPath, this.byteArraySource, compatibleJREHome, this.inputCSVFile, this.failingTests);
    }

    public ObjSimEntryPoint withInputCSVFile(final File inputCSVFile) {
        return new ObjSimEntryPoint(this.baseDirectory, this.classPath, this.byteArraySource, this.compatibleJREHome, inputCSVFile, this.failingTests);
    }

    public ObjSimEntryPoint withFailingTests(final Set<String> failingTests) {
        return new ObjSimEntryPoint(this.baseDirectory, this.classPath, this.byteArraySource, this.compatibleJREHome, this.inputCSVFile, failingTests);
    }

    public void run() throws Exception {
        final List<InputRecord> records = new LinkedList<>();
        try (final Reader fr = new FileReader(this.inputCSVFile);
             final CSVParser parser = CSVParser.parse(fr, CSVFormat.DEFAULT)) {
            for (final CSVRecord record : parser.getRecords()) {
                records.add(InputRecord.fromCSVRecord(record));
            }
        }
        final File targetDirectory = new File(this.baseDirectory, "target");
        final ProcessArgs defaultProcessArgs = getDefaultProcessArgs();
        final Predicate<String> isFailingTest = getFailingTestsPredicate();

        final Map<Integer, List<Triple<String, Boolean, Double>>> distanceInfo =
                new HashMap<>();
        final Set<String> intersectionTests = new HashSet<>();
        for (final InputRecord record : records) {
            Collections.addAll(intersectionTests, record.coveringTests);
        }
        final File outputDir = new File(this.baseDirectory, "objsim-output");
        for (final InputRecord record : records) {
            final List<String> coveringTests = Arrays.asList(record.coveringTests);
            intersectionTests.retainAll(coveringTests);

            // run covering tests on unpatched program
            final Map<String, Wrapped[]> originalSnapshots = Profiler.getSnapshots(defaultProcessArgs,
                    record.patchedMethod,
                    coveringTests);
            // run covering tests on patched program
            final Map<String, Wrapped[]> patchedSnapshots = Profiler.getSnapshots(defaultProcessArgs,
                    targetDirectory,
                    record.classFile,
                    record.patchedMethod,
                    coveringTests);
            final File patchBaseDir = new File(outputDir, "patch-" + record.patchId);
            if (patchBaseDir.isDirectory()) {
                FileUtils.deleteDirectory(patchBaseDir);
            }
            saveSnapshots(patchBaseDir, coveringTests, isFailingTest, originalSnapshots, patchedSnapshots);
            try (final PrintWriter printWriter = new PrintWriter(new File(patchBaseDir, "raw-dist.csv"));
                 final CSVPrinter csvPrinter = new CSVPrinter(printWriter, CSVFormat.DEFAULT)) {
                final DistanceVisitor<Double> distanceListener = createDistanceListener(csvPrinter, record.patchId, distanceInfo);
                visitDistances(coveringTests, isFailingTest, originalSnapshots, patchedSnapshots, distanceListener);
            }
        }
        if (records.size() > 0) {
            final List<Integer> distanceBasedRankedList = rank(intersectionTests, isFailingTest, distanceInfo);

            final List<InputRecord> w = new LinkedList<>();

            L: for (final InputRecord record : records) {
                for (final int patchId : distanceBasedRankedList) {
                    if (record.patchId == patchId) {
                        continue L;
                    }
                }
                w.add(record);
            }

            final double maxSusp = maxSusp(records, distanceBasedRankedList);

            Collections.sort(w, new Comparator<InputRecord>() {
                @Override
                public int compare(final InputRecord r1, final InputRecord r2) {
                    return Double.compare(r2.suspVal, r1.suspVal);
                }
            });
            final Iterator<InputRecord> irIt = w.iterator();

            try (final PrintWriter ordering = new PrintWriter(new File(outputDir, "ranking.txt"))) {
                InputRecord record = irIt.hasNext() ? irIt.next() : null;
                while (record != null && record.suspVal >= maxSusp) {
                    ordering.println(record.patchId);
                    record = irIt.hasNext() ? irIt.next() : null;
                }
                for (final int patchId : distanceBasedRankedList) {
                    ordering.println(patchId);
                }
                while (record != null) {
                    ordering.println(record.patchId);
                    record = irIt.hasNext() ? irIt.next() : null;
                }
            }
        }
    }

    /**
     * filters out irrelevant data
     */
    private <T extends Number> Map<Integer, List<Pair<String, Double>>> filter(final Set<String> intersectionTests,
                                                                               final Map<Integer, List<Triple<String, Boolean, T>>> infoMap) {
        final  Map<Integer, List<Pair<String, Double>>> res = new HashMap<>();
        for (final Map.Entry<Integer, List<Triple<String, Boolean, T>>> entry : infoMap.entrySet()) {
            final int patchId = entry.getKey();
            final List<Pair<String, Double>> list = new LinkedList<>();
            for (final Triple<String, Boolean, T> triple : entry.getValue()) {
                if (intersectionTests.contains(triple.getLeft())) {
                    list.add(new ImmutablePair<>(triple.getLeft(), triple.getRight().doubleValue()));
                }
            }
            res.put(patchId, list);
        }
        return res;
    }

    private double maxSusp(final Collection<InputRecord> records,
                           final Collection<Integer> patchIds) {
        double max = Double.NEGATIVE_INFINITY;
        L: for (final int patchId : patchIds) {
            for (final InputRecord record : records) {
                if (record.patchId == patchId) {
                    max = Math.max(max, record.suspVal);
                    continue L;
                }
            }
        }
        return max;
    }

    private double minimumDistance(final String testName,
                                   final List<Pair<String, Double>> list) {
        double min = Double.POSITIVE_INFINITY;
        for (final Pair<String, Double> pair : list) {
            if (pair.getLeft().equals(testName)) {
                min = Math.min(min, pair.getRight());
            }
        }
        return min;
    }

    private double maximumDistance(final String testName,
                                   final List<Pair<String, Double>> list) {
        double max = Double.NEGATIVE_INFINITY;
        for (final Pair<String, Double> pair : list) {
            if (pair.getLeft().equals(testName)) {
                max = Math.max(max, pair.getRight());
            }
        }
        return max;
    }

    private static Comparator<Pair<Integer, Double>> ascendingOrderer() {
        return new Comparator<Pair<Integer, Double>>() {
            @Override
            public int compare(Pair<Integer, Double> o1, Pair<Integer, Double> o2) {
                return Double.compare(o1.getRight(), o2.getRight());
            }
        };
    }

    private static Comparator<Pair<Integer, Double>> descendingOrderer() {
        return new Comparator<Pair<Integer, Double>>() {
            @Override
            public int compare(Pair<Integer, Double> o1, Pair<Integer, Double> o2) {
                return Double.compare(o2.getRight(), o1.getRight());
            }
        };
    }

    private List<Pair<Integer, Double>> sortedVerticalStrip(final String testName,
                                                            final boolean isFailing,
                                                            final Map<Integer, List<Pair<String, Double>>> infoMap) {
        final List<Pair<Integer, Double>> vector = new LinkedList<>();
        for (final Map.Entry<Integer, List<Pair<String, Double>>> entry : infoMap.entrySet()) {
            final int patchId = entry.getKey();
            final double distance;
            if (isFailing) { // in this case bigger distance would be desirable so we pick the worst (minimum)
                distance = minimumDistance(testName, entry.getValue());
            } else { // in this case smaller distance would better so we pick worst (maximum)
                distance = maximumDistance(testName, entry.getValue());
            }
            vector.add(new ImmutablePair<>(patchId, distance));
        }
        if (isFailing) {
            Collections.sort(vector, ascendingOrderer());
        } else {
            Collections.sort(vector, descendingOrderer());
        }
        return vector;
    }

    private <T extends Number> List<Integer> rank(final Set<String> intersectionTests,
                                                  final Predicate<String> isFailingTest,
                                                  final Map<Integer, List<Triple<String, Boolean, T>>> infoMap) {
        final Map<Integer, Integer> scores = new HashMap<>();
        final Map<Integer, List<Pair<String, Double>>> filteredInfoMap =
                filter(intersectionTests, infoMap);
        for (final String testName : intersectionTests) {
            final boolean isFailing = isFailingTest.apply(testName);
            final List<Pair<Integer, Double>> sortedVerticalStrip =
                    sortedVerticalStrip(testName, isFailing, filteredInfoMap);
            double prevDist = Double.NaN;
            int score = 0;
            for (final Pair<Integer, Double> pair : sortedVerticalStrip) {
                final int patchId = pair.getLeft();
                if (prevDist != pair.getRight()) {
                    score++;
                    prevDist = pair.getRight();
                }
                Integer s = scores.get(patchId);
                if (s == null) {
                    s = 0;
                }
                scores.put(patchId, s + score);
            }
        }
        final List<Map.Entry<Integer, Integer>> temp = new ArrayList<>(scores.entrySet());
        Collections.sort(temp, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1,
                               Map.Entry<Integer, Integer> o2) {
                return Integer.compare(o2.getValue(), o1.getValue());
            }
        });
        final List<Integer> order = new LinkedList<>();
        for (final Map.Entry<Integer, Integer> e : temp) {
            order.add(e.getKey());
        }
        return order;
    }

    private static <T extends Number> DistanceVisitor<T> createDistanceListener(final CSVPrinter csvPrinter,
                                                                                final int patchId,
                                                                                final Map<Integer, List<Triple<String, Boolean, T>>> infoMap) {
        return new DistanceVisitor<T>() {
            @Override
            public void visitDistance(String testName, boolean wasFailing, T distance) {
                try {
                    csvPrinter.printRecord(testName, wasFailing, distance);
                    List<Triple<String, Boolean, T>> list = infoMap.get(patchId);
                    if (list == null) {
                        list = new LinkedList<>();
                        infoMap.put(patchId, list);
                    }
                    list.add(new ImmutableTriple<>(testName, wasFailing, distance));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private boolean visitDistances(final List<String> coveringTests,
                                   final Predicate<String> isFailingTest,
                                   final Map<String, Wrapped[]> originalSnapshots,
                                   final Map<String, Wrapped[]> patchedSnapshots,
                                   final DistanceVisitor<Double> distanceVisitor) {
        final List<Pair<Integer, Double>> w = new LinkedList<>();
        for (final String testName : coveringTests) {
            final Wrapped[] os = originalSnapshots.get(testName);
            final Wrapped[] ps = patchedSnapshots.get(testName);
            final boolean wasFailing = isFailingTest.apply(testName);

            if (os.length != ps.length) {
                return false;
            } else {
                for (int i = 0; i < os.length; i++) {
                    final double distance;
                    final Wrapped original = os[i];
                    final Wrapped patched = ps[i];

                    if (original.equals(patched)) {
                        distance = 0;
                    } else {
                        distance = original.distance(patched);
                    }
                    distanceVisitor.visitDistance(testName, wasFailing, distance);
                }
            }
        }
        return true;
    }

    private void saveSnapshots(final File patchBaseDir,
                               final Collection<String> coveringTests,
                               final Predicate<String> isFailingTest,
                               final Map<String, Wrapped[]> originalSnapshots,
                               final Map<String, Wrapped[]> patchedSnapshots)
            throws Exception {
        if (!patchBaseDir.mkdirs()) {
            throw new RuntimeException("Unable to create folder " + patchBaseDir.getAbsolutePath());
        }
        final File original = new File(patchBaseDir, "original.gz");
        final File patched = new File(patchBaseDir, "patched.gz");
        try (final OutputStream oriOS = new FileOutputStream(original);
             final OutputStream oriBOS = new BufferedOutputStream(oriOS);
             final OutputStream oriGZOS = new GZIPOutputStream(oriBOS);
             final ObjectOutputStream oriOOS = new ObjectOutputStream(oriGZOS);
             final OutputStream patchedOS = new FileOutputStream(patched);
             final OutputStream patchedBOS = new BufferedOutputStream(patchedOS);
             final OutputStream patchedGZOS = new GZIPOutputStream(patchedBOS);
             final ObjectOutputStream patchedOOS = new ObjectOutputStream(patchedGZOS)) {
            for (final String testName : coveringTests) {
                final boolean wasFailing = isFailingTest.apply(testName);
                oriOOS.writeObject(testName);
                oriOOS.writeBoolean(wasFailing);
                oriOOS.writeObject(originalSnapshots.get(testName));
                patchedOOS.writeObject(testName);
                patchedOOS.writeBoolean(wasFailing);
                patchedOOS.writeObject(patchedSnapshots.get(testName));
            }
        }
    }

    private Predicate<String> getFailingTestsPredicate() {
        return new Predicate<String>() {
            @Override
            public Boolean apply(String testName) {
                return ObjSimEntryPoint.this.failingTests.contains(testName);
            }
        };
    }

    private ProcessArgs getDefaultProcessArgs() {
        final LaunchOptions defaultLaunchOptions = new LaunchOptions(getJavaAgent(),
                getDefaultJavaExecutableLocator(),
                Collections.<String>emptyList(),
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

    private static final class InputRecord {
        final int patchId;

        final double suspVal;

        final String patchedMethod; // full name

        final File classFile;

        final String[] coveringTests;

        private InputRecord(final int patchId,
                            final double suspVal,
                            final String patchedMethod,
                            final File classFile,
                            final String[] coveringTests) {
            this.patchId = patchId;
            this.suspVal = suspVal;
            this.patchedMethod = patchedMethod;
            this.classFile = classFile;
            this.coveringTests = coveringTests;
        }

        static InputRecord fromCSVRecord(final CSVRecord record) {
            Validate.isTrue(record.size() == 5);
            final int patchId = Integer.parseInt(record.get(0));
            final double suspVal = Double.parseDouble(record.get(1));
            final String patchedMethod = record.get(2);
            final File classFile = new File(record.get(3));
            final String[] coveringTests = record.get(4).split("\\s");
            for  (int i = 0; i < coveringTests.length; i++) {
                coveringTests[i] = MemberNameUtils.sanitizeTestName(coveringTests[i]);
            }
            return new InputRecord(patchId, suspVal, patchedMethod, classFile, coveringTests);
        }
    }
}
