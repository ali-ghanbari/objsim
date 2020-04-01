/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import static edu.utdallas.objsim.commons.MemberNameUtils.decomposeMethodName;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ProfilerTransformer implements ClassFileTransformer {
    private final String patchedClassName;

    private final String patchedMethodFullName;

    public ProfilerTransformer(final String patchedMethodFullName) {
        final int indexOfLP = patchedMethodFullName.indexOf('(');
        final Pair<String, String> methodNameParts =
                decomposeMethodName(patchedMethodFullName.substring(0, indexOfLP));
        this.patchedClassName = methodNameParts.getLeft()
                .replace('.', '/');
        this.patchedMethodFullName = patchedMethodFullName;
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
        final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        final ClassVisitor classVisitor =
                new TransformerClassVisitor(classWriter, this.patchedMethodFullName);
        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }
}
