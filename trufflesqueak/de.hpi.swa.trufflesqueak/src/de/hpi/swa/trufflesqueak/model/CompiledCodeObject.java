package de.hpi.swa.trufflesqueak.model;

import java.util.Vector;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.CyclicAssumption;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.instrumentation.CompiledCodeObjectPrinter;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.trufflesqueak.nodes.MethodContextNode;
import de.hpi.swa.trufflesqueak.util.BitSplitter;
import de.hpi.swa.trufflesqueak.util.SqueakImageChunk;

public abstract class CompiledCodeObject extends SqueakObject {
    public static enum SLOT_IDENTIFIER {
        CLOSURE,
        THIS_CONTEXT,
        STACK_POINTER,
        MARKER,
        METHOD
    }

    private Source source;
    // frame info
    @CompilationFinal private FrameDescriptor frameDescriptor;
    @CompilationFinal public FrameSlot thisContextSlot;
    @CompilationFinal public FrameSlot closureSlot;
    @CompilationFinal(dimensions = 1) public FrameSlot[] stackSlots;
    @CompilationFinal public FrameSlot markerSlot;
    @CompilationFinal public FrameSlot methodSlot;
    @CompilationFinal public FrameSlot stackPointerSlot;
    private RootCallTarget callTarget;
    private final CyclicAssumption callTargetStable = new CyclicAssumption("Compiled method assumption");
    // header info and data
    @CompilationFinal(dimensions = 1) protected Object[] literals;
    @CompilationFinal(dimensions = 1) protected byte[] bytes;
    @CompilationFinal private int numArgs;
    protected int numLiterals;
    @SuppressWarnings("unused") private boolean isOptimized;
    @CompilationFinal private boolean hasPrimitive;
    @CompilationFinal private boolean needsLargeFrame;
    private @CompilationFinal int numTemps;
    @SuppressWarnings("unused") private int accessModifier;
    @SuppressWarnings("unused") private boolean altInstructionSet;

    abstract public NativeObject getCompiledInSelector();

    abstract public ClassObject getCompiledInClass();

    public CompiledCodeObject(SqueakImageContext img, ClassObject klass) {
        super(img, klass);
    }

    public CompiledCodeObject(SqueakImageContext img) {
        this(img, img.compiledMethodClass);
    }

    protected CompiledCodeObject(CompiledCodeObject original) {
        this(original.image, original.getSqClass());
        setLiteralsAndBytes(original.literals, original.bytes);
    }

    private void setLiteralsAndBytes(Object[] literals, byte[] bytes) {
        this.literals = literals;
        decodeHeader();
        this.bytes = bytes;
        updateAndInvalidateCallTargets();
    }

    public Source getSource() {
        if (source == null) {
            source = Source.newBuilder(CompiledCodeObjectPrinter.getString(this)).mimeType(SqueakLanguage.MIME_TYPE).name(toString()).build();
        }
        return source;
    }

    public int frameSize() {
        return needsLargeFrame ? CONTEXT.LARGE_FRAMESIZE : CONTEXT.SMALL_FRAMESIZE;
    }

    @TruffleBoundary
    protected void prepareFrameDescriptor() {
        frameDescriptor = new FrameDescriptor(image.nil);
        int numStackSlots = frameSize() + getSqClass().getBasicInstanceSize();
        stackSlots = new FrameSlot[numStackSlots];
        for (int i = 0; i < stackSlots.length; i++) {
            stackSlots[i] = frameDescriptor.addFrameSlot(i, FrameSlotKind.Illegal);
        }
        thisContextSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.THIS_CONTEXT, FrameSlotKind.Object);
        closureSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.CLOSURE, FrameSlotKind.Object);
        markerSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.MARKER, FrameSlotKind.Object);
        methodSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.METHOD, FrameSlotKind.Object);
        stackPointerSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.STACK_POINTER, FrameSlotKind.Int);
    }

    public RootCallTarget getCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreter();
            updateAndInvalidateCallTargets();
        }
        return callTarget;
    }

    @TruffleBoundary
    private void updateAndInvalidateCallTargets() {
        Frame frame = Truffle.getRuntime().getCurrentFrame().getFrame(FrameInstance.FrameAccess.MATERIALIZE);
        MethodContextObject activeContext = MethodContextObject.createReadOnlyContextObject(image, frame);
        MethodContextObject newContext = MethodContextObject.createWriteableContextObject(image, frameSize());
        newContext.atput0(CONTEXT.METHOD, this);
        newContext.atput0(CONTEXT.SENDER, activeContext);
        newContext.atput0(CONTEXT.INSTRUCTION_POINTER, newContext.getCodeObject().getBytecodeOffset() + 1);
        newContext.atput0(CONTEXT.RECEIVER, frame.getArguments()[0]);
        callTarget = Truffle.getRuntime().createCallTarget(new MethodContextNode(image.getLanguage(), newContext, this));
        callTargetStable.invalidate();
    }

    public Assumption getCallTargetStable() {
        return callTargetStable.getAssumption();
    }

    @Override
    public String toString() {
        String className = "UnknownClass";
        String selector = "unknownSelector";
        ClassObject classObject = getCompiledInClass();
        if (classObject != null) {
            className = classObject.nameAsClass();
        }
        NativeObject selectorObj = getCompiledInSelector();
        if (selectorObj != null) {
            selector = selectorObj.toString();
        }
        return className + ">>" + selector;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public int getNumTemps() {
        return numTemps;
    }

    public final int getNumArgs() {
        return numArgs;
    }

    public int getNumCopiedValues() {
        return 0;
    }

    public int getNumArgsAndCopiedValues() {
        return numArgs + getNumCopiedValues();
    }

    public FrameSlot getStackSlot(int i) {
        if (i >= stackSlots.length) { // This is fine, ignore for decoder
            return null;
        }
        return stackSlots[i];
    }

    public int convertTempIndexToStackIndex(int tempIndex) {
        return tempIndex - getNumArgsAndCopiedValues();
    }

    public int getNumStackSlots() {
        return stackSlots.length;
    }

    @Override
    public void fillin(SqueakImageChunk chunk) {
        super.fillin(chunk);
        Vector<Integer> data = chunk.data();
        int header = data.get(0) >> 1; // header is a tagged small integer
        int literalsize = header & 0x7fff;
        Object[] ptrs = chunk.getPointers(literalsize + 1);
        assert literals == null;
        literals = ptrs;
        decodeHeader();
        assert bytes == null;
        bytes = chunk.getBytes(ptrs.length);
    }

    void decodeHeader() {
        int hdr = getHeader();
        int[] splitHeader = BitSplitter.splitter(hdr, new int[]{15, 1, 1, 1, 6, 4, 2, 1});
        numLiterals = splitHeader[0];
        isOptimized = splitHeader[1] == 1;
        hasPrimitive = splitHeader[2] == 1;
        needsLargeFrame = splitHeader[3] == 1;
        numTemps = splitHeader[4];
        numArgs = splitHeader[5];
        accessModifier = splitHeader[6];
        altInstructionSet = splitHeader[7] == 1;
        prepareFrameDescriptor();
    }

    public int getHeader() {
        Object object = literals[0];
        assert object instanceof Integer;
        int hdr = (int) object;
        return hdr;
    }

    @Override
    public boolean become(BaseSqueakObject other) {
        if (other instanceof CompiledMethodObject) {
            if (super.become(other)) {
                Object[] literals2 = ((CompiledCodeObject) other).literals;
                byte[] bytes2 = ((CompiledCodeObject) other).bytes;
                ((CompiledCodeObject) other).setLiteralsAndBytes(literals, bytes);
                this.setLiteralsAndBytes(literals2, bytes2);
                return true;
            }
        }
        return false;
    }

    public int getBytecodeOffset() {
        return literals.length * 4;
    }

    @Override
    public int size() {
        return literals.length * 4 + bytes.length;
    }

    @Override
    public Object at0(int idx) {
        if (idx < literals.length * 4) {
            return literals[idx / 4];
        } else {
            return Byte.toUnsignedInt(bytes[idx - literals.length * 4]);
        }
    }

    @Override
    public void atput0(int idx, Object obj) {
        if (idx < literals.length) {
            setLiteral(idx / 4, obj);
        } else {
            bytes[idx] = (byte) obj;
        }
    }

    public Object getLiteral(int index) {
        int literalIndex = 1 + index; // skip header
        if (literalIndex < literals.length) {
            return literals[literalIndex];
        } else {
            return literals[0]; // for decoder
        }
    }

    public void setLiteral(int i, Object obj) {
        assert i > 0; // first lit is header
        literals[i] = obj;
    }

    @Override
    public int instsize() {
        return 0;
    }

    public boolean hasPrimitive() {
        return hasPrimitive;
    }

    public int primitiveIndex() {
        if (hasPrimitive && bytes.length >= 3) {
            return Byte.toUnsignedInt(bytes[1]) + (Byte.toUnsignedInt(bytes[2]) << 8);
        } else {
            return 0;
        }
    }

    public Object[] getLiterals() {
        return literals;
    }

    public byte[] getBytes() {
        return bytes;
    }

    abstract public CompiledMethodObject getMethod();
}