package edu.utdallas.objsim.profiler;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.CHECKCAST;

public class TransformerMethodVisitor extends MethodVisitor {
    private final boolean isStatic;

    private final Type[] paramTypes;

    private final Type retType;

    private final Label startFinally;

    public TransformerMethodVisitor(final MethodVisitor methodVisitor,
                                    final boolean isStatic,
                                    final  Type[] paramTypes,
                                    final Type retType) {
        super(ASM7, methodVisitor);
        this.isStatic = isStatic;
        this.paramTypes = paramTypes;
        this.retType = retType;
        this.startFinally = new Label();
    }

    @Override
    public void visitCode() {
        super.visitCode();
        onMethodEnter();
        super.visitLabel(this.startFinally);
    }

    private void onMethodEnter() {
        // nothing
    }

    private void onMethodExit(boolean normal) {
        final int arraySize = calculateArraySize();
        pushInteger(arraySize);
        super.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int arrayIndex = 0;
        if (normal && isNonVoidMethod()) {
            putReturnValue(arrayIndex++);
        } else if (!normal) {
            // saving throwable object
            super.visitInsn(DUP_X1);
            super.visitInsn(SWAP);
            pushInteger(arrayIndex++);
            super.visitInsn(SWAP);
            super.visitInsn(AASTORE);
        }
        int paramIndex = 0;
        if (!this.isStatic) {
            super.visitInsn(DUP);
            pushInteger(arrayIndex++);
            super.visitVarInsn(ALOAD, paramIndex++);
            super.visitInsn(AASTORE);
        }
        for (final Type type : this.paramTypes) {
            super.visitInsn(DUP);
            pushInteger(arrayIndex++);
            pushWrappedParamValue(type, paramIndex++);
            super.visitInsn(AASTORE);
        }
        super.visitInsn(DUP);
        super.visitMethodInsn(INVOKESTATIC, "edu/utdallas/objsim/profiler/SnapshotTracker",
                "submitSystemState", "([Ljava/lang/Object;)V",
                false);
        // revive return value
        if (normal && isNonVoidMethod()) {
            super.visitInsn(ICONST_0);
            super.visitInsn(AALOAD);
            unWrapTopOfStack(this.retType);
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
                wrapTopOfStack(paramType);
                break;
            case Type.DOUBLE:
                super.visitVarInsn(DLOAD, paramIndex);
                wrapTopOfStack(paramType);
                break;
            case Type.FLOAT:
                super.visitVarInsn(FLOAD, paramIndex);
                wrapTopOfStack(paramType);
                break;
            case Type.LONG:
                super.visitVarInsn(LLOAD, paramIndex);
                wrapTopOfStack(paramType);
                break;
            default:
                super.visitVarInsn(ALOAD, paramIndex);
        }
    }

    private void putReturnValue(final int index) {
        if (this.retType.getSize() == 1) {
            super.visitInsn(DUP_X1);
            super.visitInsn(SWAP);
        } else {
            super.visitInsn(DUP_X2);
            super.visitInsn(DUP_X2);
            super.visitInsn(POP);
        }
        wrapTopOfStack(this.retType);
        pushInteger(index);
        super.visitInsn(SWAP);
        super.visitInsn(AASTORE);
    }

    private void wrapTopOfStack(final Type type) {
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

    private void unWrapTopOfStack(final Type type) {
        // in case the type represents a primitive type, a wrapped object is expected
        // on top of the stack.
        switch (type.getSort()) {
            case Type.BOOLEAN:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean",
                        "booleanValue", "()Z",
                        false);
                break;
            case Type.BYTE:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte",
                        "byteValue", "()B",
                        false);
                break;
            case Type.CHAR:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character",
                        "charValue", "()C",
                        false);
                break;
            case Type.DOUBLE:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double",
                        "doubleValue", "()D",
                        false);
                break;
            case Type.FLOAT:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float",
                        "floatValue", "()F",
                        false);
                break;
            case Type.INT:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer",
                        "intValue", "()I",
                        false);
                break;
            case Type.LONG:
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long",
                        "longValue", "()J",
                        false);
                break;
            case Type.SHORT:
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
        if (isNonVoidMethod()) {
            sz++;
        }
        return sz;
    }

    private void pushInteger(final int intValue) {
        if (intValue <= 5) {
            super.visitInsn(ICONST_0 + intValue);
        } else if (intValue <= Byte.MAX_VALUE) {
            super.visitIntInsn(BIPUSH, intValue);
        } else if (intValue <= Short.MAX_VALUE) {
            super.visitIntInsn(SIPUSH, intValue);
        } else {
            super.visitLdcInsn(intValue);
        }
    }

    private boolean isReturnInst(int opcode) {
        switch (opcode) {
            case RETURN:
            case IRETURN:
            case ARETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
                return true;
        }
        return false;
    }

    @Override
    public void visitInsn(int opcode) {
        if (isReturnInst(opcode)) {
            onMethodExit(true);
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        final Label endFinally = new Label();
        super.visitTryCatchBlock(this.startFinally, endFinally, endFinally, null);
        super.visitLabel(endFinally);
        onMethodExit(false);
        super.visitInsn(ATHROW);
        int maxSz = Math.max(0, this.retType.getSize());
        for (final Type pt : this.paramTypes) {
            maxSz = Math.max(maxSz, pt.getSize());
        }
        final int topUp = 10 + maxSz;
        super.visitMaxs(topUp + maxStack, maxLocals);
    }
}
