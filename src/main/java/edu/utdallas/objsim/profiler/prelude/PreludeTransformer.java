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

import edu.utdallas.objsim.commons.asm.ComputeClassWriter;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.commons.relational.MethodsDom;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.functional.predicate.Predicate;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.pitest.bytecode.FrameOptions.pickFlags;

/**
 * A class file transformer to instrument class files so that accessed fields are recorded.
 * This is intended to be used to minimize the overhead of object-utils library.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class PreludeTransformer implements ClassFileTransformer {
    private final ClassByteArraySource byteArraySource;

    private final Predicate<String> appClassFilter;

    private final Collection<String> patchedMethods;

    private final FieldsDom fieldsDom;

    private final MethodsDom methodsDom;

    private final Map<String, String> cache;

    public PreludeTransformer(final ClassByteArraySource byteArraySource,
                              final Predicate<String> appClassFilter,
                              final Collection<String> patchedMethods,
                              final FieldsDom fieldsDom,
                              final MethodsDom methodsDom) {
        this.byteArraySource = byteArraySource;
        this.appClassFilter = appClassFilter;
        this.patchedMethods = patchedMethods;
        this.fieldsDom = fieldsDom;
        this.methodsDom = methodsDom;
        this.cache = new HashMap<>();
    }

    private boolean isAppClass(String className) {
        className = className.replace('/', '.');
        return this.appClassFilter.apply(className);
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            if (className == null || !isAppClass(className)) {
                return null; // no transformation
            }
            final ClassReader classReader = new ClassReader(classfileBuffer);
            final ClassWriter classWriter = new ComputeClassWriter(this.byteArraySource,
                    this.cache,
                    pickFlags(classfileBuffer));
            final ClassVisitor classVisitor = new PreludeTransformerClassVisitor(classWriter,
                    this.fieldsDom,
                    this.methodsDom,
                    this.patchedMethods);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
            return classWriter.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
        return null;
    }
}
