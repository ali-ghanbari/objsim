/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.commons;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class MemberNameUtils {
    private MemberNameUtils() {

    }

    public static String sanitizeExtendedTestName(String name) {
        name = name.substring(1 + name.indexOf(' '));
        final int indexOfLP = name.indexOf('(');
        if (indexOfLP >= 0) {
            final String testCaseName = name.substring(0, indexOfLP);
            name = name.substring(1 + indexOfLP, name.length() - 1) + "." + testCaseName;
        }
        return name;
    }

    public static String sanitizeTestName(String name) {
        //SETLab style: test.class.name:test_name
        name = name.replace(':', '.');
        //Defects4J style: test.class.name::test_name
        name = name.replace("..", ".");
        final int indexOfLP = name.indexOf('(');
        if (indexOfLP >= 0) {
            name = name.substring(0, indexOfLP);
        }
        return name;
    }

    public static Pair<String, String> decomposeMethodName(final String qualifiedMethodName) {
        final int indexOfLastDot = qualifiedMethodName.lastIndexOf('.');
        final String className = qualifiedMethodName.substring(0, indexOfLastDot);
        final String methodName = qualifiedMethodName.substring(1 + indexOfLastDot);
        return new ImmutablePair<>(className, methodName);
    }

    public static String getClassName(final File classFile) {
        final class NameExtractor extends ClassVisitor {
            String className;

            public NameExtractor() {
                super(Opcodes.ASM7);
            }

            @Override
            public void visit(final int version,
                              final int access,
                              final String name,
                              final String signature,
                              final String superName,
                              final String[] interfaces) {
                this.className = name.replace('/', '.');
                super.visit(version, access, name, signature, superName, interfaces);
            }
        }

        try (final InputStream fis = new FileInputStream(classFile)) {
            final NameExtractor extractor = new NameExtractor();
            final ClassReader cr = new ClassReader(fis);
            cr.accept(extractor, 0);
            return extractor.className;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}