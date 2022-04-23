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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

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
    private final Set<String> patchedMethods;

    private String owner;

    public PrimaryTransformerClassVisitor(final ClassVisitor classVisitor, final Set<String> patchedMethods) {
        super(ASM7, classVisitor);
        this.patchedMethods = patchedMethods;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.owner = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        final MethodVisitor defMethodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        if (this.patchedMethods.contains(methodFullName)) {
            return new PrimaryMethodTransformer(defMethodVisitor, access, name, descriptor);
        }
        return defMethodVisitor;
    }
}
