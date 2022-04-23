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

import edu.utdallas.objsim.commons.asm.FinallyBlockAdviceAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Modifier;

/**
 * A versatile method visitor that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrimaryMethodTransformer extends FinallyBlockAdviceAdapter {
    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    private static final Type THROWABLE_TYPE = Type.getType(Throwable.class);

    private static final Type SNAPSHOT_TRACKER = Type.getType(SnapshotTracker.class);

    private final Type retType;

    public PrimaryMethodTransformer(final MethodVisitor methodVisitor,
                                    final int access,
                                    final String name,
                                    final String descriptor) {
        super(ASM7, methodVisitor, access, name, descriptor);
        this.retType = Type.getReturnType(descriptor);
    }

    @Override
    protected void insertPrelude() {
        // nothing
    }

    @Override
    protected void insertSequel(boolean normalExit) {
        int arrayIndex = 0;
        if (Modifier.isStatic(this.methodAccess)) {
            push(2); // 1 for parameters array if any, and the other for return/exceptional value if any
            newArray(OBJECT_TYPE);
        } else {
            push(3); // first one for "this" and the rest as above
            newArray(OBJECT_TYPE);
            dup();
            push(arrayIndex++);
            loadThis();
            arrayStore(OBJECT_TYPE);
        }
        dup();
        // store parameters, if any
        push(arrayIndex++);
        loadArgArray();
        arrayStore(OBJECT_TYPE);
        // store return/throwable object
        if (normalExit) {
            if (isNonVoidMethod()) {
                putReturnValue(arrayIndex);
            }
        } else { // saving throwable object
            dupX1();
            swap();
            push(arrayIndex);
            swap();
            arrayStore(OBJECT_TYPE);
        }
        dup();
        invokeStatic(SNAPSHOT_TRACKER, Method.getMethod("void submitSystemState(java.lang.Object[])"));
        // revive return value
        if (normalExit && isNonVoidMethod()) {
            push(arrayIndex);
            arrayLoad(OBJECT_TYPE);
            unbox(this.retType);
            switch (this.retType.getSort()) {
                case Type.ARRAY:
                case Type.OBJECT:
                case Type.METHOD:
                    checkCast(this.retType);
            }
        } else if (!normalExit) {
            push(arrayIndex);
            arrayLoad(OBJECT_TYPE);
            checkCast(THROWABLE_TYPE);
        } else {
            pop(); // array reference
        }
    }

    private void putReturnValue(final int index) {
        if (this.retType.getSize() < 2) { // this includes null value in case of void methods
            dupX1();
            swap();
        } else {
            dupX2();
            dupX2();
            pop();
        }
        box(this.retType);
        push(index);
        swap();
        arrayStore(OBJECT_TYPE);
    }

    private boolean isNonVoidMethod() {
        return this.retType.getSort() != Type.VOID;
    }
}
