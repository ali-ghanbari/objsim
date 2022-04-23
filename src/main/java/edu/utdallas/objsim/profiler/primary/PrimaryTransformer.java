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

import edu.utdallas.objsim.commons.asm.ComputeClassWriter;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.pitest.classinfo.ClassByteArraySource;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static edu.utdallas.objsim.commons.misc.NameUtils.decomposeMethodName;
import static org.pitest.bytecode.FrameOptions.pickFlags;

/**
 * A versatile class file transformer that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class PrimaryTransformer implements ClassFileTransformer {
    private final ClassByteArraySource byteArraySource;

    private final Map<String, String> cache;

    private final Set<String> patchedClasses; // internal names

    private final Set<String> patchedMethods;

    public PrimaryTransformer(final Set<String> patchedMethods,
                              final ClassByteArraySource byteArraySource) {
        final Set<String> patchedClasses = new HashSet<>();
        for (final String methodName : patchedMethods) {
            final int indexOfLP = methodName.indexOf('(');
            final Pair<String, String> methodNameParts = decomposeMethodName(methodName.substring(0, indexOfLP));
            patchedClasses.add(methodNameParts.getLeft().replace('.', '/'));
        }
        this.patchedClasses = patchedClasses;
        this.patchedMethods = patchedMethods;
        this.byteArraySource = byteArraySource;
        this.cache = new HashMap<>();
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null || !this.patchedClasses.contains(className)) {
            return null; // no transformation
        }
        final ClassReader classReader = new ClassReader(classfileBuffer);
        final ClassWriter classWriter = new ComputeClassWriter(this.byteArraySource,
                this.cache, pickFlags(classfileBuffer));
        final ClassVisitor classVisitor = new PrimaryTransformerClassVisitor(classWriter, this.patchedMethods);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
}
