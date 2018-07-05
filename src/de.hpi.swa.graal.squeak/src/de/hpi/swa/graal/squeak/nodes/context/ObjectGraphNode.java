package de.hpi.swa.graal.squeak.nodes.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.WeakPointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.context.ObjectGraphNodeGen.GetTraceablePointersNodeGen;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotReadNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public abstract class ObjectGraphNode extends AbstractNodeWithImage {
    private static final int PENDING_INITIAL_SIZE = 256;

    private static final int SEEN_INITIAL_CAPACITY = 1000000;

    @CompilationFinal private static HashSet<AbstractSqueakObject> classesWithNoInstances;

    @Child private GetTraceablePointersNode getPointersNode = GetTraceablePointersNode.create();

    @Child private FrameStackReadNode stackReadNode = FrameStackReadNode.create();
    @Child private FrameSlotReadNode stackPointerReadNode = FrameSlotReadNode.createForStackPointer();

    public static ObjectGraphNode create(final SqueakImageContext image) {
        return ObjectGraphNodeGen.create(image);
    }

    protected ObjectGraphNode(final SqueakImageContext image) {
        super(image);
    }

    public HashSet<AbstractSqueakObject> getClassesWithNoInstances() {
        if (classesWithNoInstances == null) {
            // TODO: BlockContext missing.
            final AbstractSqueakObject[] classes = new AbstractSqueakObject[]{image.smallIntegerClass, image.characterClass, image.floatClass};
            classesWithNoInstances = new HashSet<>(Arrays.asList(classes));
        }
        return classesWithNoInstances;
    }

    public List<AbstractSqueakObject> allInstances() {
        return executeTrace(null, false);
    }

    public List<AbstractSqueakObject> allInstances(final ClassObject classObj) {
        return executeTrace(classObj, false);
    }

    @TruffleBoundary
    public AbstractSqueakObject someInstance(final ClassObject classObj) {
        return executeTrace(classObj, true).get(0);
    }

    public abstract List<AbstractSqueakObject> executeTrace(ClassObject classObj, boolean isSomeInstance);

    @SuppressWarnings("unused")
    @Specialization(guards = {"classObj == null", "!isSomeInstance"})
    @TruffleBoundary
    protected final List<AbstractSqueakObject> doAllInstances(final ClassObject classObj, final boolean isSomeInstance) {
        final List<AbstractSqueakObject> result = new ArrayList<>();
        final Set<AbstractSqueakObject> seen = new HashSet<>(SEEN_INITIAL_CAPACITY);
        final Deque<AbstractSqueakObject> pending = new ArrayDeque<>(PENDING_INITIAL_SIZE);
        pending.add(image.specialObjectsArray);
        addObjectsFromTruffleFrames(pending);
        while (!pending.isEmpty()) {
            final AbstractSqueakObject currentObject = pending.pop();
            if (!seen.contains(currentObject)) {
                seen.add(currentObject);
                result.add(currentObject);
                pending.addAll(tracePointers(currentObject));
            }
        }
        return result;
    }

    @Specialization(guards = {"classObj != null", "!isSomeInstance"})
    @TruffleBoundary
    protected final List<AbstractSqueakObject> doAllInstancesOf(final ClassObject classObj, @SuppressWarnings("unused") final boolean isSomeInstance) {
        final List<AbstractSqueakObject> result = new ArrayList<>();
        final Set<AbstractSqueakObject> seen = new HashSet<>(SEEN_INITIAL_CAPACITY);
        final Deque<AbstractSqueakObject> pending = new ArrayDeque<>(PENDING_INITIAL_SIZE);
        pending.add(image.specialObjectsArray);
        addObjectsFromTruffleFrames(pending);
        while (!pending.isEmpty()) {
            final AbstractSqueakObject currentObject = pending.pop();
            if (!seen.contains(currentObject)) {
                seen.add(currentObject);
                final ClassObject sqClass = currentObject.getSqClass();
                if (classObj == sqClass) {
                    result.add(currentObject);
                }
                pending.addAll(tracePointers(currentObject));
            }
        }
        return result;
    }

    @Specialization(guards = {"classObj != null", "isSomeInstance"})
    @TruffleBoundary
    protected final List<AbstractSqueakObject> doSomeInstance(final ClassObject classObj, @SuppressWarnings("unused") final boolean isSomeInstance) {
        final List<AbstractSqueakObject> result = new ArrayList<>();
        final Set<AbstractSqueakObject> seen = new HashSet<>(SEEN_INITIAL_CAPACITY);
        final Deque<AbstractSqueakObject> pending = new ArrayDeque<>(PENDING_INITIAL_SIZE);
        pending.add(image.specialObjectsArray);
        addObjectsFromTruffleFrames(pending);
        while (!pending.isEmpty()) {
            final AbstractSqueakObject currentObject = pending.pop();
            if (!seen.contains(currentObject)) {
                seen.add(currentObject);
                final ClassObject sqClass = currentObject.getSqClass();
                if (classObj == sqClass) {
                    result.add(currentObject);
                    break;
                }
                pending.addAll(tracePointers(currentObject));
            }
        }
        return result;
    }

    @TruffleBoundary
    private void addObjectsFromTruffleFrames(final Deque<AbstractSqueakObject> pending) {
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Frame>() {
            @Override
            public Frame visitFrame(final FrameInstance frameInstance) {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                final Object stackPointerObject = stackPointerReadNode.executeRead(current);
                if (!(stackPointerObject instanceof Integer) || current.getFrameDescriptor().getSize() <= FrameAccess.RECEIVER) {
                    return null;
                }
                final int stackPointer = ((Integer) stackPointerObject);
                final Object[] arguments = current.getArguments();

                for (int i = FrameAccess.RECEIVER; i < arguments.length; i++) {
                    final Object argument = arguments[i];
                    if (argument instanceof AbstractSqueakObject) {
                        pending.add((AbstractSqueakObject) argument);
                    }
                }
                for (int i = 0; i < stackPointer; i++) {
                    final Object stackObject = stackReadNode.execute(current, i);
                    if (stackObject == null) {
                        return null; // this slot and all following have not been used
                    }
                    if (stackObject instanceof AbstractSqueakObject) {
                        pending.add((AbstractSqueakObject) stackObject);
                    }
                }
                return null;
            }
        });
    }

    private List<AbstractSqueakObject> tracePointers(final AbstractSqueakObject currentObject) {
        final List<AbstractSqueakObject> result = new ArrayList<>(32);
        final ClassObject sqClass = currentObject.getSqClass();
        if (sqClass != null) {
            result.add(sqClass);
        }
        for (Object object : getPointersNode.executeGet(currentObject)) {
            if (object instanceof AbstractSqueakObject && !(object instanceof NativeObject)) {
                result.add((AbstractSqueakObject) object);
            }
        }
        return result;
    }

    protected abstract static class GetTraceablePointersNode extends Node {
        private static final Object[] emptyResult = new Object[0];

        protected static GetTraceablePointersNode create() {
            return GetTraceablePointersNodeGen.create();
        }

        protected abstract Object[] executeGet(AbstractSqueakObject object);

        @Specialization
        protected static final Object[] doClass(final ClassObject object) {
            return object.getPointers();
        }

        @Specialization
        protected static final Object[] doClosure(final BlockClosureObject object) {
            return object.getTraceableObjects();
        }

        @Specialization
        protected static final Object[] doContext(final ContextObject object) {
            return object.getPointers();
        }

        @Specialization
        protected static final Object[] doMethod(final CompiledMethodObject object) {
            return object.getLiterals();
        }

        @Specialization
        protected static final Object[] doPointers(final PointersObject object) {
            return object.getPointers();
        }

        @Specialization
        protected static final Object[] doWeakPointers(final WeakPointersObject object) {
            return object.getPointers();
        }

        @Fallback
        protected static final Object[] doFallback(@SuppressWarnings("unused") final AbstractSqueakObject object) {
            return emptyResult;
        }
    }
}