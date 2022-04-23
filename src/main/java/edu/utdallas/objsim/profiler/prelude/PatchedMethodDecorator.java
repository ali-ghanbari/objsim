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

import edu.utdallas.objsim.commons.asm.FinallyBlockAdviceAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * A method visitor to configure {@link FieldAccessRecorder} properly upon entering
 * and exiting the patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PatchedMethodDecorator extends FinallyBlockAdviceAdapter {
    private static final Type FIELD_ACCESS_RECORDER = Type.getType(FieldAccessRecorder.class);

    private final int patchedMethodIndex;

    public PatchedMethodDecorator(final MethodVisitor methodVisitor,
                                  final int access,
                                  final String name,
                                  final String descriptor,
                                  final int patchedMethodIndex) {
        super(ASM7, methodVisitor, access, name, descriptor);
        this.patchedMethodIndex = patchedMethodIndex;
    }

    @Override
    protected void insertPrelude() {
        incrementCounter();
    }

    @Override
    protected void insertSequel(boolean normalExit) {
        decrementCounter();
    }

    private void incrementCounter() {
        push(this.patchedMethodIndex);
        invokeStatic(FIELD_ACCESS_RECORDER, Method.getMethod("void inc(int)"));
    }

    private void decrementCounter() {
        push(this.patchedMethodIndex);
        invokeStatic(FIELD_ACCESS_RECORDER, Method.getMethod("void dec(int)"));
    }
}
