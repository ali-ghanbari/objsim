package edu.utdallas.objsim.commons.misc;

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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Utility functions for manipulating identifiers of class members, i.e. methods and fields.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class NameUtils {
    private NameUtils() {

    }

    /**
     * A method for sanitizing test names.
     * !For internal use only!
     *
     * @param name Test name
     * @return Sanitized test name
     */
    public static String sanitizeExtendedTestName(String name) {
        final int indexOfSp = name.indexOf(' ');
        if (indexOfSp < 0) {
            sanitizeTestName(name);
        }
        name = name.substring(1 + indexOfSp);
        final int indexOfLP = name.indexOf('(');
        if (indexOfLP >= 0) {
            final String testCaseName = name.substring(0, indexOfLP);
            name = name.substring(1 + indexOfLP, name.length() - 1) + "." + testCaseName;
        }
        return name;
    }

    /**
     * A method for sanitizing test names.
     * !For internal use only!
     *
     * @param name Test name
     * @return Sanitized test name
     */
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

    /**
     * Decomposes a fully qualified method name into class name and method name.
     *
     * @param qualifiedMethodName Fully qualified method name
     * @return (c, n) where c is class name and n is the method name
     */
    public static Pair<String, String> decomposeMethodName(final String qualifiedMethodName) {
        final int indexOfLastDot = qualifiedMethodName.lastIndexOf('.');
        final String className = qualifiedMethodName.substring(0, indexOfLastDot);
        final String methodName = qualifiedMethodName.substring(1 + indexOfLastDot);
        return new ImmutablePair<>(className, methodName);
    }

    public static String composeMethodFullName(final String owner, final String name, final String descriptor) {
        return owner.replace('/', '.') +
                '.' +
                name +
                '(' +
                joinTypeClassNames(Type.getArgumentTypes(descriptor), ",") +
                ')';
    }

    /**
     * Joins class names an array of types using a separator.
     * Empty string shall be returned for empty array.
     *
     * @param types Array of types
     * @param separator Separator string
     * @return Resulting string
     */
    public static String joinTypeClassNames(final Type[] types, final String separator) {
        if (types == null || separator == null) {
            throw new IllegalArgumentException("Input array or separator cannot be null");
        }
        StringBuilder pt = new StringBuilder();
        final int iMax = types.length - 1;
        for (int i = 0; iMax >= 0; i++) {
            pt.append(types[i].getClassName());
            if (i == iMax) {
                return pt.toString();
            }
            pt.append(separator);
        }
        return "";
    }

    /**
     * Given a class file, returns Java name of the class.
     *
     * @param classFile Class file on the file system
     * @return Java name of the class
     */
    public static String getClassName(final File classFile) {
        try (final InputStream fis = new FileInputStream(classFile)) {
            final NameExtractor extractor = new NameExtractor();
            final ClassReader cr = new ClassReader(fis);
            cr.accept(extractor, 0);
            return extractor.className;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class NameExtractor extends ClassVisitor {
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
}