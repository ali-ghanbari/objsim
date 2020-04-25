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

import static edu.utdallas.objsim.commons.asm.MethodBodyUtils.pushInteger;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.CHECKCAST;

/**
 * A versatile method visitor that adds code to record system state at the exit
 * point(s) of a patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrimaryMethodTransformer extends FinallyBlockAdviceAdapter {
    private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");

    private static final String SNAPSHOT_TRACKER;

    static {
        SNAPSHOT_TRACKER = Type.getInternalName(SnapshotTracker.class);
    }

    private final boolean isStatic;

    private final Type[] paramTypes;

    private final Type retType;

    public PrimaryMethodTransformer(final MethodVisitor methodVisitor,
                                    final boolean isStatic,
                                    final  Type[] paramTypes,
                                    final Type retType,
                                    final int invokeSpecialSkips) {
        super(ASM7, methodVisitor, invokeSpecialSkips);
        this.isStatic = isStatic;
        this.paramTypes = paramTypes;
        this.retType = retType;
    }

    @Override
    protected void onMethodEnter() {
        // nothing
    }

    private void createArray(final Type type, final int size) {
        pushInteger(this.mv, size);
        super.visitTypeInsn(ANEWARRAY, type.getInternalName());
    }

    @Override
    protected void onMethodExit(boolean normal) {
        createArray(OBJECT_TYPE, calculateArraySize());
        int arrayIndex = 0;
        if (normal) {
            if (isNonVoidMethod()) {
                putReturnValue(arrayIndex);
            }
            arrayIndex++; // first entry will be left null in case of void return type
        } else { // saving throwable object
            super.visitInsn(DUP_X1);
            super.visitInsn(SWAP);
            pushInteger(this.mv, arrayIndex++);
            super.visitInsn(SWAP);
            super.visitInsn(AASTORE);
        }
        int paramIndex = 0;
        if (!this.isStatic) {
            super.visitInsn(DUP);
            pushInteger(this.mv, arrayIndex++);
            super.visitVarInsn(ALOAD, paramIndex++);
            super.visitInsn(AASTORE);
        }
        for (final Type type : this.paramTypes) {
            super.visitInsn(DUP);
            pushInteger(this.mv, arrayIndex++);
            pushWrappedParamValue(type, paramIndex);
            super.visitInsn(AASTORE);
            paramIndex += type.getSize();
        }
        super.visitInsn(DUP);
        super.visitMethodInsn(INVOKESTATIC, SNAPSHOT_TRACKER,
                "submitSystemState", "([Ljava/lang/Object;)V",
                false);
        // revive return value
        if (normal && isNonVoidMethod()) {
            super.visitInsn(ICONST_0);
            super.visitInsn(AALOAD);
            unbox(this.retType);
            switch (this.retType.getSort()) {
                case Type.ARRAY:
                case Type.OBJECT:
                case Type.METHOD:
                    super.visitTypeInsn(CHECKCAST, this.retType.getInternalName());
            }
        } else if (!normal) {
            super.visitInsn(ICONST_0);
            super.visitInsn(AALOAD);
            super.visitTypeInsn(CHECKCAST, "java/lang/Throwable");
        } else {
            super.visitInsn(POP); // array reference
        }
    }

    private void pushWrappedParamValue(final Type paramType, final int paramIndex) {
        switch (paramType.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.INT:
            case Type.SHORT:
                super.visitVarInsn(ILOAD, paramIndex);
                box(paramType);
                break;
            case Type.DOUBLE:
                super.visitVarInsn(DLOAD, paramIndex);
                box(paramType);
                break;
            case Type.FLOAT:
                super.visitVarInsn(FLOAD, paramIndex);
                box(paramType);
                break;
            case Type.LONG:
                super.visitVarInsn(LLOAD, paramIndex);
                box(paramType);
                break;
            default:
                super.visitVarInsn(ALOAD, paramIndex);
        }
    }

    private void putReturnValue(final int index) {
        if (this.retType.getSize() < 2) { // this includes null value in case of void methods
            super.visitInsn(DUP_X1);
            super.visitInsn(SWAP);
        } else {
            super.visitInsn(DUP_X2);
            super.visitInsn(DUP_X2);
            super.visitInsn(POP);
        }
        box(this.retType);
        pushInteger(this.mv, index);
        super.visitInsn(SWAP);
        super.visitInsn(AASTORE);
    }

    private void box(final Type type) {
        // if type represents a primitive type a primitive-typed value is expected
        // on top of the stack.
        switch (type.getSort()) {
            case Type.BOOLEAN:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean",
                        "valueOf", "(Z)Ljava/lang/Boolean;",
                        false);
                break;
            case Type.BYTE:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Byte",
                        "valueOf", "(B)Ljava/lang/Byte;",
                        false);
                break;
            case Type.CHAR:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Character",
                        "valueOf", "(C)Ljava/lang/Character;",
                        false);
                break;
            case Type.DOUBLE:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Double",
                        "valueOf", "(D)Ljava/lang/Double;",
                        false);
                break;
            case Type.FLOAT:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Float",
                        "valueOf", "(F)Ljava/lang/Float;",
                        false);
                break;
            case Type.INT:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Integer",
                        "valueOf", "(I)Ljava/lang/Integer;",
                        false);
                break;
            case Type.LONG:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Long",
                        "valueOf", "(J)Ljava/lang/Long;",
                        false);
                break;
            case Type.SHORT:
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Short",
                        "valueOf", "(S)Ljava/lang/Short;",
                        false);
        }
        // if type of the item on top of the stack is not primitive, there
        // is no need to take further action.
    }

    private void unbox(final Type type) {
        // in case the type represents a primitive type, a wrapped object is expected
        // on top of the stack.
        switch (type.getSort()) {
            case Type.BOOLEAN:
                super.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean",
                        "booleanValue", "()Z",
                        false);
                break;
            case Type.BYTE:
                super.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte",
                        "byteValue", "()B",
                        false);
                break;
            case Type.CHAR:
                super.visitTypeInsn(CHECKCAST, "java/lang/Character");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character",
                        "charValue", "()C",
                        false);
                break;
            case Type.DOUBLE:
                super.visitTypeInsn(CHECKCAST, "java/lang/Double");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double",
                        "doubleValue", "()D",
                        false);
                break;
            case Type.FLOAT:
                super.visitTypeInsn(CHECKCAST, "java/lang/Float");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float",
                        "floatValue", "()F",
                        false);
                break;
            case Type.INT:
                super.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer",
                        "intValue", "()I",
                        false);
                break;
            case Type.LONG:
                super.visitTypeInsn(CHECKCAST, "java/lang/Long");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long",
                        "longValue", "()J",
                        false);
                break;
            case Type.SHORT:
                super.visitTypeInsn(CHECKCAST, "java/lang/Short");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short",
                        "shortValue", "()S",
                        false);
        }
        // if the original type was not primitive, no wrapping has taken place,
        // so there is no need to take further action.
    }

    private boolean isNonVoidMethod() {
        return this.retType.getSort() != Type.VOID;
    }

    private int calculateArraySize() {
        int sz = this.isStatic ? 0 : 1;
        sz += this.paramTypes.length;
        sz++; // in normal termination we put return value (if there is any) in the first
              // slot of the array. in exceptional termination we put thrown object there.
        return sz;
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        int maxSz = Math.max(0, this.retType.getSize());
        for (final Type pt : this.paramTypes) {
            maxSz = Math.max(maxSz, pt.getSize());
        }
        final int topUp = 10 + maxSz; // just for good measure!
        super.visitMaxs(topUp + maxStack, maxLocals);
    }
}
