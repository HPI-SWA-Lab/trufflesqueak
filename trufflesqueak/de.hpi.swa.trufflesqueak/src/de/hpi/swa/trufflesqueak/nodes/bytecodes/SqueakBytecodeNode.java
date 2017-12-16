package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.SqueakNodeWithCode;

@Instrumentable(factory = SqueakBytecodeNodeWrapper.class)
public abstract class SqueakBytecodeNode extends SqueakNodeWithCode {
    @CompilationFinal protected final int numBytecodes;
    @CompilationFinal protected final int index;
    @CompilationFinal(dimensions = 1) protected final int[] successors;
    @CompilationFinal private SourceSection sourceSection;
    public int lineNumber = 1;

    protected SqueakBytecodeNode(SqueakBytecodeNode original) {
        super(original.code);
        index = original.index;
        numBytecodes = original.numBytecodes;
        successors = original.successors;
        setSourceSection(original.getSourceSection());
    }

    public SqueakBytecodeNode(CompiledCodeObject code, int index, int numBytecodes) {
        super(code);
        this.index = index;
        this.numBytecodes = numBytecodes;
        this.successors = new int[]{index + numBytecodes, -1};
    }

    public SqueakBytecodeNode(CompiledCodeObject code, int index) {
        this(code, index, 1);
    }

    public int executeInt(VirtualFrame frame) {
// if (successorIndex < 0) {
// throw new RuntimeException("Inner nodes are not allowed to be executed here");
// }
        executeVoid(frame);
        return 0;
    }

    public void executeVoid(VirtualFrame frame) {
        executeGeneric(frame);
    }

    public int getNumBytecodes() {
        return numBytecodes;
    }

    public int getIndex() {
        return index;
    }

    @Override
    protected boolean isTaggedWith(Class<?> tag) {
        return tag == StandardTags.StatementTag.class;
    }

    public int[] getSuccessors() {
        return successors;
    }
}
