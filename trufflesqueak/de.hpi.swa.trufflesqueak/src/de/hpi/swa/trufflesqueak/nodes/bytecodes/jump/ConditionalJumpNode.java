package de.hpi.swa.trufflesqueak.nodes.bytecodes.jump;

import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;

public class ConditionalJumpNode extends AbstractJump {
    private final int alternativeSuccessorIndex;
    protected final int offset;
    public final Boolean isIfTrue;

    private ConditionalJumpNode(CompiledCodeObject code, int index, int off, boolean condition) {
        super(code, index);
        alternativeSuccessorIndex = index + off;
        offset = off;
        isIfTrue = condition;
    }

    public ConditionalJumpNode(CompiledCodeObject code, int index, int bytecode) {
        this(code, index, shortJumpOffset(bytecode), false);
    }

    public ConditionalJumpNode(CompiledCodeObject code, int index, int bytecode, int parameter, boolean condition) {
        this(code, index + 1, longJumpOffset(bytecode, parameter), condition);
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        if (pop(frame) == isIfTrue) {
            return alternativeSuccessorIndex;
        } else {
            return successorIndex;
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RuntimeException("ConditionalJumps cannot step");
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        throw new RuntimeException("ConditionalJumps cannot be executed");
    }
}
