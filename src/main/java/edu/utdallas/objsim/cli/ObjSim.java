package edu.utdallas.objsim.cli;

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
import edu.utdallas.objsim.maven.AbstractObjSimMojo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.functional.predicate.Predicate;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Not all Java projects are Maven-based! In such situations we can use commandline interface of the tool to
 * apply it on the projects.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ObjSim {
    public static void main(final String[] args) {
        /* ------------- Processing Commandline Options ------------- */
        final Options options = new Options();

        options.addOption("i", "inputCSVFile", false, "The name of the file (in relative or absolute form) of the CSV file containing required information about patches");
        options.addOption(varArgsOption("v", "childJVMArgs", false, "A list of JVM arguments used when creating a child JVM process, i.e. during profiling"));
        options.addOption(varArgsOption("n", "includeProductionClasses", true, "Whether or not include production classes"));
        options.addOption(varArgsOption("c", "targetClasses", true, "Target application classes to be transformed"));
        options.addOption(varArgsOption("s", "excludeTestClasses", true, "Whether or not test classes should be excluded"));
        options.addOption(varArgsOption("e", "excludedClasses", false, "Target application classes to be excluded from transformation"));
        options.addOption(varArgsOption("t", "targetTests", true, "Target test classes to be included"));
        options.addOption(varArgsOption("x", "excludedTests", false, "Target test classes to be excluded"));
        options.addOption("b", "buildFolder", true, "Build folder for application classes");
        options.addOption("u", "testBuildFolder", true, "Build folder for test classes; ignored if s = false");
        options.addOption("l", "classpath", true, "Classpath for the target program");
        options.addOption(noArgOption("h", "help", "Print usage"));

        final CommandLineParser clParser = new DefaultParser();
        final CommandLine cmd;
        try {
            cmd = clParser.parse(options, args);
        } catch (ParseException pe) {
            System.out.printf("Error %s: %s%n", pe.getClass().getName(), pe.getMessage());
            printUsage(options);
            return;
        }

        // help?
        if (cmd.hasOption('h')) {
            printUsage(options);
            return;
        }
        // input file
        File inputCSVFile = new File("input-file.csv");
        if (cmd.hasOption('i')) {
            inputCSVFile = new File(cmd.getOptionValue('i'));
        }
        if (!inputCSVFile.isFile()) {
            System.out.printf("Input CSV file '%s' non-existent%n", inputCSVFile.getAbsolutePath());
            printUsage(options);
            return;
        }
        // child process args
        Set<String> childJVMArgs = Collections.singleton("-Xmx128g");
        if (cmd.hasOption('v')) {
            childJVMArgs = new HashSet<>();
            Collections.addAll(childJVMArgs, cmd.getOptionValues('v'));
        }
        // build folder
        final File buildFolder = new File(cmd.getOptionValue('b'));
        if (!buildFolder.isDirectory()) {
            System.out.printf("Build folder '%s' non-existent%n", buildFolder.getAbsolutePath());
            printUsage(options);
            return;
        }
        // target & excluded tests
        final Set<String> targetTests0 = new HashSet<>();
        if (cmd.hasOption('t')) {
            Collections.addAll(targetTests0, cmd.getOptionValues('t'));
        } else {
            targetTests0.add("*Test");
            targetTests0.add("*Tests");
            targetTests0.add("*TestCase*");
        }
        Predicate<String> excludedTests = PredicateFactory.alwaysFalse();
        if (cmd.hasOption('x')) {
            final Set<String> excludedTests0 = new HashSet<>();
            Collections.addAll(excludedTests0, cmd.getOptionValues('x'));
            excludedTests = PredicateFactory.orGlobs(excludedTests0);
        }
        final Predicate<String> targetTests = PredicateFactory.and(PredicateFactory.orGlobs(targetTests0),
                PredicateFactory.not(excludedTests));
        // should we exclude test classes?
        boolean excludeTestClasses = true;
        if (cmd.hasOption('s')) {
            excludeTestClasses = Boolean.parseBoolean(cmd.getOptionValue('s'));
        }
        // should we include production classes?
        boolean includeProductionClasses = true;
        if (cmd.hasOption('n')) {
            includeProductionClasses = Boolean.parseBoolean(cmd.getOptionValue('n'));
        }
        // target & excluded classes
        Predicate<String> excludedClasses = PredicateFactory.alwaysFalse();
        if (cmd.hasOption('e')) {
            Set<String> excludedClasses0 = new HashSet<>();
            Collections.addAll(excludedClasses0, cmd.getOptionValues('e'));
            excludedClasses = PredicateFactory.orGlobs(excludedClasses0);
        }
        if (excludeTestClasses) {
            final File targetTestDirectory = new File(cmd.getOptionValue('u'));
            if (!targetTestDirectory.isDirectory()) {
                System.out.printf("Build folder '%s' non-existent%n", targetTestDirectory.getAbsolutePath());
                printUsage(options);
                return;
            }
            excludedClasses = PredicateFactory.or(excludedClasses, AbstractObjSimMojo.classFileFilter(targetTestDirectory));
        }
        Predicate<String> p;
        if (cmd.hasOption('c')) {
            final Set<String> targetClasses0 = new HashSet<>();
            Collections.addAll(targetClasses0, cmd.getOptionValues('c'));
            p = PredicateFactory.orGlobs(targetClasses0);
        } else if (includeProductionClasses) {
            p = AbstractObjSimMojo.classFileFilter(buildFolder);
        } else {
            System.out.println("No target classes are specified");
            printUsage(options);
            return;
        }
        final Predicate<String> targetClasses = PredicateFactory.and(p, PredicateFactory.not(excludedClasses));

        // classpath
        final Set<File> extraClasspathElements = new HashSet<>();
        for (final String element : cmd.getOptionValue('l').split(File.pathSeparator)) {
            extraClasspathElements.add(new File(element));
        }
        // locating JRE
        final String jreHome = System.getProperty("java.home");
        if (jreHome == null) {
            throw new IllegalStateException("JAVA_HOME is not set");
        }
        final File compatibleJREHome = new File(jreHome);
        if (!compatibleJREHome.isDirectory()) {
            throw new IllegalStateException("Invalid JAVA_HOME/JRE_HOME");
        }
        /* -------------------------- */

        final ClassPath classPath = createClassPath(extraClasspathElements);
        final ClassByteArraySource byteArraySource = ClassPathUtils.createClassByteArraySource(classPath);
        try {
            (new ObjSimEntryPoint(buildFolder,
                    classPath,
                    byteArraySource,
                    targetClasses,
                    targetTests,
                    compatibleJREHome,
                    childJVMArgs,
                    inputCSVFile)).run();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static void printUsage(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ObjSim", options);
    }

    private static Option noArgOption(final String opt, final String longOpt, final String description) {
        final Option option = new Option(opt, longOpt, false, description);
        option.setArgs(0);
        return option;
    }

    private static Option varArgsOption(final String opt, final String longOpt, final boolean hasArg, final String description) {
        final Option option = new Option(opt, longOpt, hasArg, description);
        option.setArgs(Option.UNLIMITED_VALUES);
        return option;
    }

    private static ClassPath createClassPath(final Collection<File> extraElements) {
        final Set<File> elements = new HashSet<>(ClassPathUtils.getClassPathElements());
        elements.addAll(extraElements);
        return new ClassPath(elements);
    }
}
