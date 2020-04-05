/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.profiler;

import edu.utdallas.objsim.commons.asm.MethodUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

import static edu.utdallas.objsim.commons.misc.MemberNameUtils.composeMethodFullName;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * A versatile class visitor that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class TransformerClassVisitor extends ClassVisitor {
    private final byte[] classFileByteArray;

    private final String patchedMethodFullName;

    private String owner;

    public TransformerClassVisitor(final byte[] classFileByteArray,
                                   final ClassVisitor classVisitor,
                                   final String patchedMethodFullName) {
        super(ASM7, classVisitor);
        this.classFileByteArray = classFileByteArray;
        this.patchedMethodFullName = patchedMethodFullName;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.owner = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        final MethodVisitor defMethodVisitor = super.visitMethod(access, name,
                descriptor, signature, exceptions);
        int skip = -1;
        if (name.equals("<init>")) {
            skip = MethodUtils.getFirstSpecialInvoke(this.classFileByteArray, descriptor);
            System.out.printf("INFO: %d SPECIALINVOKE instructions will be skipped.%n", skip + 1);
        }
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        if (this.patchedMethodFullName.equals(methodFullName)) {
            final boolean isStatic = Modifier.isStatic(access);
            final Type[] paramTypes = Type.getArgumentTypes(descriptor);
            final Type retType = Type.getReturnType(descriptor);
            return new TransformerMethodVisitor(defMethodVisitor, isStatic, paramTypes, retType, skip);
        }
        return defMethodVisitor;
    }
}
