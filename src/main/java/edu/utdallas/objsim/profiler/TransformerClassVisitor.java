package edu.utdallas.objsim.profiler;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

import static edu.utdallas.objsim.commons.MemberNameUtils.composeMethodFullName;
import static org.objectweb.asm.Opcodes.ASM7;

public class TransformerClassVisitor extends ClassVisitor {
    private final String patchedMethodFullName;

    private String owner;

    public TransformerClassVisitor(final ClassVisitor classVisitor,
                                   final String patchedMethodFullName) {
        super(ASM7, classVisitor);
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
        final String methodFullName = composeMethodFullName(this.owner, name, descriptor);
        if (this.patchedMethodFullName.equals(methodFullName)) {
            final boolean isStatic = Modifier.isStatic(access);
            final Type[] paramTypes = Type.getArgumentTypes(descriptor);
            final Type retType = Type.getReturnType(descriptor);
            return new TransformerMethodVisitor(defMethodVisitor, isStatic, paramTypes, retType);
        }
        return defMethodVisitor;
    }
}
