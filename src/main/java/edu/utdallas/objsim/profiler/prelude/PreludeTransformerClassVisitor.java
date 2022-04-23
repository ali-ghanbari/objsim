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

import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.commons.relational.MethodsDom;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Collection;

import static edu.utdallas.objsim.commons.misc.NameUtils.composeMethodFullName;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * A class transformer to instrument class files so that accessed fields are recorded.
 * This is intended to be used to minimize the overhead of obj-utils library.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PreludeTransformerClassVisitor extends ClassVisitor {
    private final Collection<String> patchedMethods;

    private final FieldsDom fieldsDom;

    private final MethodsDom methodsDom;

    private String owner;

    public PreludeTransformerClassVisitor(final ClassVisitor classVisitor,
                                          final FieldsDom fieldsDom,
                                          final MethodsDom methodsDom,
                                          final Collection<String> patchedMethods) {
        super(ASM7, classVisitor);
        this.fieldsDom = fieldsDom;
        this.methodsDom = methodsDom;
        this.patchedMethods = patchedMethods;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.owner = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        final int methodIndex = this.methodsDom.getOrAdd(methodFullName);
        if (this.patchedMethods.contains(methodFullName)) {
            methodVisitor = new PatchedMethodDecorator(methodVisitor, access, name, descriptor, methodIndex);
        }
        methodVisitor = new MethodCoverageTransformer(methodVisitor, access, name, descriptor, methodIndex);
        return new FieldAccessRecorderMethodVisitor(methodVisitor, access, name, descriptor, this.fieldsDom);
    }
}
