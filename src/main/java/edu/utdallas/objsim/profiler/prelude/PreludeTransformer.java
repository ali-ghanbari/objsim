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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.pitest.classinfo.ClassByteArraySource;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
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

    private final String whiteListPrefix;

    private final String patchedMethodFullName;

    private final FieldsDom fieldsDom;

    private final Map<String, String> cache;

    public PreludeTransformer(final ClassByteArraySource byteArraySource,
                              final String whiteListPrefix,
                              final String patchedMethodFullName,
                              final FieldsDom fieldsDom) {
        this.byteArraySource = byteArraySource;
        this.whiteListPrefix = whiteListPrefix.replace('.', '/');
        this.patchedMethodFullName = patchedMethodFullName;
        this.fieldsDom = fieldsDom;
        this.cache = new HashMap<>();
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!className.startsWith(this.whiteListPrefix)) {
            return null; // no transformation
        }
        final ClassReader classReader = new ClassReader(classfileBuffer);
        final ClassWriter classWriter = new ComputeClassWriter(this.byteArraySource, this.cache, pickFlags(classfileBuffer));
        final ClassVisitor classVisitor = new PreludeTransformerClassVisitor(classWriter,
                classfileBuffer, this.patchedMethodFullName, this.fieldsDom);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
}
