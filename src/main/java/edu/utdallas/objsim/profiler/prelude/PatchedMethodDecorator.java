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

import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.getInternalName;

/**
 * A method visitor to configure {@link FieldAccessRecorder} properly upon entering
 * and exiting the patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PatchedMethodDecorator extends FinallyBlockAdviceAdapter {
    private static final String FIELD_ACCESS_RECORDER = getInternalName(FieldAccessRecorder.class);

    public PatchedMethodDecorator(final MethodVisitor methodVisitor,
                                  final int invokeSpecialSkips) {
        super(ASM7, methodVisitor, invokeSpecialSkips);
    }

    @Override
    protected void onMethodEnter() {
        incrementCounter();
    }

    private void incrementCounter() {
        super.visitMethodInsn(INVOKESTATIC,
                FIELD_ACCESS_RECORDER,
                "inc",
                "()V",
                false);
    }

    @Override
    protected void onMethodExit(boolean normalExit) {
        decrementCounter();
    }

    private void decrementCounter() {
        super.visitMethodInsn(INVOKESTATIC,
                FIELD_ACCESS_RECORDER,
                "dec",
                "()V",
                false);
    }
}
