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

import edu.utdallas.objsim.commons.asm.MethodUtils;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static edu.utdallas.objsim.commons.misc.NameUtils.composeMethodFullName;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * A class transformer to instrument class files so that accessed fields are recorded.
 * This is intended to be used to minimize the overhead of obj-utils library.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class PreludeTransformerClassVisitor extends ClassVisitor {
    private final byte[] classFileBytes;

    private final String patchedMethodFullName;

    private final FieldsDom fieldsDom;

    private String owner;

    public PreludeTransformerClassVisitor(final ClassVisitor classVisitor,
                                          final byte[] classFileBytes,
                                          final String patchedMethodFullName,
                                          final FieldsDom fieldsDom) {
        super(ASM7, classVisitor);
        this.classFileBytes = classFileBytes;
        this.fieldsDom = fieldsDom;
        this.patchedMethodFullName = patchedMethodFullName;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.owner = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        final MethodVisitor defMethodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        int skips = 0;
        if (name.equals("<init>")) {
            skips = 1 + MethodUtils.getFirstSpecialInvoke(this.classFileBytes, descriptor);
//            System.out.printf("INFO: %d INVOKESPECIAL instruction(s) will be skipped.%n", skips);
        }
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        if (this.patchedMethodFullName.equals(methodFullName)) {
            final MethodVisitor decorator = new PatchedMethodDecorator(defMethodVisitor, skips);
            return new FieldAccessRecorderMethodVisitor(decorator, this.fieldsDom);
        }
        return new FieldAccessRecorderMethodVisitor(defMethodVisitor, this.fieldsDom);
    }
}
