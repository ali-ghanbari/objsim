package edu.utdallas.objsim.profiler;

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

public class ProfilerTransformer implements ClassFileTransformer {
    private static final String RELOCATED_ARRAY_UTILS =
            "edu.utdallas.validator.reloc.apache.commons.lang3.ArrayUtils";

    private static final String SNAPSHOT_TRACKER =
            "edu.utdallas.validator.profiler.SnapshotTracker";

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
