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

import edu.utdallas.objsim.commons.asm.MethodUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

import static edu.utdallas.objsim.commons.misc.NameUtils.composeMethodFullName;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * A versatile class visitor that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrimaryTransformerClassVisitor extends ClassVisitor {
    private final byte[] classFileByteArray;

    private final String patchedMethodFullName;

    private String owner;

    public PrimaryTransformerClassVisitor(final byte[] classFileByteArray,
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
        int skip = 0;
        if (name.equals("<init>")) {
            skip = 1 + MethodUtils.getFirstSpecialInvoke(this.classFileByteArray, descriptor);
//            System.out.printf("INFO: %d INVOKESPECIAL instruction(s) will be skipped.%n", skip);
        }
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        if (this.patchedMethodFullName.equals(methodFullName)) {
            final boolean isStatic = Modifier.isStatic(access);
            final Type[] paramTypes = Type.getArgumentTypes(descriptor);
            final Type retType = Type.getReturnType(descriptor);
            return new PrimaryMethodTransformer(defMethodVisitor, isStatic, paramTypes, retType, skip);
        }
        return defMethodVisitor;
    }
}
