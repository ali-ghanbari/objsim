/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.profiler;

import edu.utdallas.objsim.commons.asm.ComputeClassWriter;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.pitest.classinfo.ClassByteArraySource;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import static edu.utdallas.objsim.commons.misc.MemberNameUtils.decomposeMethodName;
import static org.pitest.bytecode.FrameOptions.pickFlags;

/**
 * A versatile class file transformer that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class ProfilerTransformer implements ClassFileTransformer {
    private final ClassByteArraySource byteArraySource;

    private final Map<String, String> cache;

    private final String patchedClassName;

    private final String patchedMethodFullName;

    public ProfilerTransformer(final String patchedMethodFullName,
                               final ClassByteArraySource byteArraySource) {
        final int indexOfLP = patchedMethodFullName.indexOf('(');
        final Pair<String, String> methodNameParts =
                decomposeMethodName(patchedMethodFullName.substring(0, indexOfLP));
        this.patchedClassName = methodNameParts.getLeft()
                .replace('.', '/');
        this.patchedMethodFullName = patchedMethodFullName;
        this.cache = new HashMap<>();
        this.byteArraySource = byteArraySource;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!this.patchedClassName.equals(className)) {
            return null; // no transformation
        }
        final ClassReader classReader = new ClassReader(classfileBuffer);
        final ClassWriter classWriter = new ComputeClassWriter(this.byteArraySource,
                this.cache, pickFlags(classfileBuffer));
        final ClassVisitor classVisitor = new TransformerClassVisitor(classfileBuffer,
                classWriter, this.patchedMethodFullName);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
}
