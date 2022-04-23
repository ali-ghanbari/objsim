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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.PUTFIELD;

/**
 * A method visitor for instrumenting the target program so as to keep track of
 * accessed fields.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class FieldAccessRecorderMethodVisitor extends GeneratorAdapter {
    private static final Type FIELD_ACCESS_RECORDER = Type.getType(FieldAccessRecorder.class);

    private final FieldsDom fieldsDom;

    public FieldAccessRecorderMethodVisitor(final MethodVisitor mv,
                                            final int access,
                                            final String name,
                                            final String desc,
                                            final FieldsDom fieldsDom) {
        super(ASM7, mv, access, name, desc);
        this.fieldsDom = fieldsDom;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == GETFIELD || opcode == PUTFIELD) {
            final int fieldIndex = this.fieldsDom.getOrAdd(getFieldFullName(owner, name));
            push(fieldIndex);
            invokeStatic(FIELD_ACCESS_RECORDER, Method.getMethod("void registerFieldAccess(int)"));
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    private static String getFieldFullName(final String owner, final String name) {
        return owner.replace('/', '.') + "." + name;
    }
}
