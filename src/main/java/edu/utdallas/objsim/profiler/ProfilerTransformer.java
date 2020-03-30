/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Modifier;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import static edu.utdallas.objsim.commons.MemberNameUtils.decomposeMethodName;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ProfilerTransformer implements ClassFileTransformer {
    private static final String RELOCATED_ARRAY_UTILS =
            "edu.utdallas.objsim.reloc.apache.commons.lang3.ArrayUtils";

    private static final String SNAPSHOT_TRACKER =
            "edu.utdallas.objsim.profiler.SnapshotTracker";

    private final ClassPool classPool;

    private final String patchedClassName;

    private final String patchedMethodFullName;

    public ProfilerTransformer(final String patchedMethodFullName) {
        final int indexOfLP = patchedMethodFullName.indexOf('(');
        final Pair<String, String> methodNameParts =
                decomposeMethodName(patchedMethodFullName.substring(0, indexOfLP));
        this.patchedClassName = methodNameParts.getLeft().replace('.', '/');
        this.patchedMethodFullName = patchedMethodFullName;
        this.classPool = ClassPool.getDefault();
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!this.patchedClassName.equals(className)) {
            return classfileBuffer;
        }
        try {
            CtClass clazz = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
            if (clazz.isInterface()) {
                return classfileBuffer;
            }
            CtBehavior[] methods = clazz.getDeclaredBehaviors();
            for (final CtBehavior method : methods) {
                if (method.isEmpty() || Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                final String methodName = method.getLongName();
                if (!methodName.equals(this.patchedMethodFullName)) {
                    continue;
                }
                final boolean isStatic = Modifier.isStatic(method.getModifiers());
                final Type returnType = Type.getReturnType(method.getMethodInfo().getDescriptor());
                transformMethod(method, isStatic, returnType);
            }
            return clazz.toBytecode();
        } catch (IOException | CannotCompileException e) {
            e.printStackTrace();
        }
        throw new IllegalStateException("method " + this.patchedMethodFullName + " not found!");
    }

    private void transformMethod(final CtBehavior method,
                                 final boolean isStatic,
                                 final Type returnType) throws CannotCompileException {
        String inputArray;
        if (isStatic) {
            inputArray = "$args";
        } else {
            inputArray = RELOCATED_ARRAY_UTILS + ".add($args, $0)";
        }
        if (returnType.getSort() != Type.VOID) {
            inputArray = String.format("%s.add(%s, ($w) $_)", RELOCATED_ARRAY_UTILS, inputArray);
        }
        method.insertAfter(String.format("%s.submitSystemState(%s);", SNAPSHOT_TRACKER, inputArray), true);
    }
}
