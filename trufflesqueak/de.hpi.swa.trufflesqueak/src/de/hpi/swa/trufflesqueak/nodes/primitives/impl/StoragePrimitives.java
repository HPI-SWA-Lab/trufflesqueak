package de.hpi.swa.trufflesqueak.nodes.primitives.impl;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.exceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.model.AbstractPointersObject;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.BlockClosureObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.EmptyObject;
import de.hpi.swa.trufflesqueak.model.LargeIntegerObject;
import de.hpi.swa.trufflesqueak.model.ListObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.SPECIAL_OBJECT_INDEX;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.nodes.process.GetActiveProcessNode;

public class StoragePrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return StoragePrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 18, numArguments = 2)
    protected static abstract class PrimMakePointNode extends AbstractPrimitiveNode {
        protected PrimMakePointNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object makePoint(int xPos, int yPos) {
            return code.image.newPoint(xPos, yPos);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 68, numArguments = 2)
    protected static abstract class PrimCompiledMethodObjectAtNode extends AbstractPrimitiveNode {
        protected PrimCompiledMethodObjectAtNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object literalAt(CompiledCodeObject receiver, int index) {
            // Use getLiterals() instead of getLiteral(i), the latter skips the header.
            return receiver.getLiterals()[index - 1];
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 69, numArguments = 3)
    protected static abstract class PrimCompiledMethodObjectAtPutNode extends AbstractPrimitiveNode {
        protected PrimCompiledMethodObjectAtPutNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object setLiteral(CompiledCodeObject code, int index, Object value) {
            code.setLiteral(index - 1, value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 70)
    protected static abstract class PrimNewNode extends AbstractPrimitiveNode {
        final static int NEW_CACHE_SIZE = 3;

        protected PrimNewNode(CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "NEW_CACHE_SIZE", guards = {"receiver == cachedReceiver"}, assumptions = {"classFormatStable"})
        protected BaseSqueakObject newDirect(ClassObject receiver,
                        @Cached("receiver") ClassObject cachedReceiver,
                        @Cached("cachedReceiver.getClassFormatStable()") Assumption classFormatStable) {
            return cachedReceiver.newInstance();
        }

        @Specialization(replaces = "newDirect")
        protected BaseSqueakObject newIndirect(ClassObject receiver) {
            return receiver.newInstance();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 71, numArguments = 2)
    protected static abstract class PrimNewArgNode extends AbstractPrimitiveNode {
        final static int NEW_CACHE_SIZE = 3;

        protected PrimNewArgNode(CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "NEW_CACHE_SIZE", guards = {"receiver == cachedReceiver"}, assumptions = {"classFormatStable"})
        protected BaseSqueakObject newWithArgDirect(ClassObject receiver, int size,
                        @Cached("receiver") ClassObject cachedReceiver,
                        @Cached("cachedReceiver.getClassFormatStable()") Assumption classFormatStable) {
            if (!cachedReceiver.isVariable() && size != 0) {
                throw new PrimitiveFailed();
            }
            if (size < 0) {
                throw new PrimitiveFailed();
            }
            return cachedReceiver.newInstance(size);
        }

        @Specialization(replaces = "newWithArgDirect")
        protected BaseSqueakObject newWithArg(ClassObject receiver, int size) {
            if (!receiver.isVariable() && size != 0) {
                throw new PrimitiveFailed();
            }
            if (size < 0) {
                throw new PrimitiveFailed();
            }
            return receiver.newInstance(size);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 72, numArguments = 2)
    protected static abstract class PrimArrayBecome extends AbstractPrimitiveNode {
        @Child private GetActiveProcessNode getActiveProcessNode;

        protected PrimArrayBecome(CompiledMethodObject method) {
            // TODO: this primitive does not correctly perform a one way become yet, FIXME!
            super(method);
            getActiveProcessNode = GetActiveProcessNode.create(method.image);
        }

        @Specialization
        protected BaseSqueakObject arrayBecome(ListObject receiver, ListObject argument) {
            if (receiver.size() != argument.size()) {
                throw new PrimitiveFailed("bad argument");
            }
            List<BaseSqueakObject> instances = getInstancesArray();
            for (Iterator<BaseSqueakObject> iterator = instances.iterator(); iterator.hasNext();) {
                BaseSqueakObject instance = iterator.next();
                if (instance != null && instance.getSqClass() != null) {
                    instance.pointersBecomeOneWay(receiver.getPointers(), argument.getPointers());
                }
            }
            return receiver;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected BaseSqueakObject arrayBecome(Object receiver, ListObject argument) {
            throw new PrimitiveFailed("bad receiver");
        }

        @SuppressWarnings("unused")
        @Specialization
        protected BaseSqueakObject arrayBecome(ListObject receiver, Object argument) {
            throw new PrimitiveFailed("bad argument");
        }

        private List<BaseSqueakObject> getInstancesArray() {
            PointersObject activeProcess = getActiveProcessNode.executeGet();
            // TODO: activeProcess.storeSuspendedContext(frame)
            try {
                return code.image.objects.allInstances();
            } finally {
                // TODO: activeProcess.storeSuspendedContext(code.image.nil)
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 73, numArguments = 2)
    protected static abstract class PrimAtNode extends AbstractPrimitiveNode {
        protected PrimAtNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeGeneric(VirtualFrame frame) {
            try {
                return executePrimitive(frame);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executePrimitive(VirtualFrame frame);

        @Specialization
        protected int at(char receiver, int idx) {
            if (idx == 1) {
                return receiver;
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization
        protected Object at(LargeIntegerObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }

        @Specialization
        protected long intAt(BigInteger receiver, int idx) {
            return LargeIntegerObject.byteAt0(receiver, idx - 1);
        }

        @Specialization
        protected long at(double receiver, int idx) {
            long doubleBits = Double.doubleToLongBits(receiver);
            if (idx == 1) {
                return 0xFFFFFFFF & (doubleBits >> 32);
            } else if (idx == 2) {
                return 0xFFFFFFFF & doubleBits;
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected int intAt(NativeObject receiver, int idx) throws ArithmeticException {
            return Math.toIntExact(receiver.getNativeAt0(idx - 1));
        }

        @Specialization
        protected long longAt(NativeObject receiver, int idx) {
            return receiver.getNativeAt0(idx - 1);
        }

        @Specialization
        protected Object at(BlockClosureObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }

        @Specialization
        protected Object at(CompiledCodeObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }

        @Specialization
        protected Object at(EmptyObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }

        @Specialization
        protected Object at(AbstractPointersObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }

        @Specialization
        protected Object at(BaseSqueakObject receiver, int idx) {
            return receiver.at0(idx - 1);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 74, numArguments = 3)
    protected static abstract class PrimAtPutNode extends AbstractPrimitiveNode {
        protected PrimAtPutNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeGeneric(VirtualFrame frame) {
            try {
                return executePrimitive(frame);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executePrimitive(VirtualFrame frame);

        @Specialization
        protected char atput(LargeIntegerObject receiver, int idx, char value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @Specialization
        protected int atput(LargeIntegerObject receiver, int idx, int value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @Specialization
        protected char atput(NativeObject receiver, int idx, char value) {
            receiver.setNativeAt0(idx - 1, value);
            return value;
        }

        @Specialization
        protected int atput(NativeObject receiver, int idx, int value) {
            receiver.setNativeAt0(idx - 1, value);
            return value;
        }

        @Specialization
        protected long atput(NativeObject receiver, int idx, long value) {
            receiver.setNativeAt0(idx - 1, value);
            return value;
        }

        @Specialization
        protected Object atput(BlockClosureObject receiver, int idx, Object value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @Specialization
        protected Object atput(ClassObject receiver, int idx, Object value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @Specialization
        protected Object atput(CompiledCodeObject receiver, int idx, Object value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object atput(EmptyObject receiver, int idx, Object value) {
            throw new PrimitiveFailed();
        }

        @Specialization
        protected Object atput(AbstractPointersObject receiver, int idx, Object value) {
            receiver.atput0(idx - 1, value);
            return value;
        }

        @Specialization
        protected Object atput(BaseSqueakObject receiver, int idx, Object value) {
            receiver.atput0(idx - 1, value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = {75, 171, 175})
    protected static abstract class PrimIdentityHashNode extends AbstractPrimitiveNode {
        protected PrimIdentityHashNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected int hash(char obj) {
            return obj;
        }

        @Specialization
        protected int hash(int obj) {
            return obj;
        }

        @Specialization
        protected int hash(long obj) {
            return (int) obj;
        }

        @Specialization
        protected int hash(BigInteger obj) {
            return obj.hashCode();
        }

        @Specialization
        protected int hash(BaseSqueakObject obj) {
            return obj.squeakHash();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 76, numArguments = 2)
    protected static abstract class PrimStoreStackPointerNode extends AbstractPrimitiveNode {
        protected PrimStoreStackPointerNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected BaseSqueakObject store(ContextObject receiver, int value) {
            receiver.atput0(CONTEXT.STACKPOINTER, value);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 78)
    protected static abstract class PrimNextInstanceNode extends AbstractPrimitiveNode {

        protected PrimNextInstanceNode(CompiledMethodObject method) {
            super(method);
        }

        protected boolean hasNoInstances(BaseSqueakObject sqObject) {
            return code.image.objects.getClassesWithNoInstances().contains(sqObject.getSqClass());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "hasNoInstances(sqObject)")
        protected BaseSqueakObject noInstances(BaseSqueakObject sqObject) {
            return code.image.nil;
        }

        @Specialization
        protected BaseSqueakObject someInstance(BaseSqueakObject sqObject) {
            List<BaseSqueakObject> instances = code.image.objects.allInstances(sqObject.getSqClass());
            int index;
            try {
                index = instances.indexOf(sqObject);
            } catch (NullPointerException e) {
                index = -1;
            }
            try {
                return instances.get(index + 1);
            } catch (IndexOutOfBoundsException e) {
                return code.image.nil;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 79, numArguments = 3)
    protected static abstract class PrimNewMethodNode extends AbstractPrimitiveNode {

        protected PrimNewMethodNode(CompiledMethodObject method) {
            super(method);
        }

        protected boolean isCompiledMethodClass(ClassObject receiver) {
            return receiver.isSpecialClassAt(SPECIAL_OBJECT_INDEX.ClassCompiledMethod);
        }

        @Specialization(guards = "isCompiledMethodClass(receiver)")
        protected BaseSqueakObject newMethod(ClassObject receiver, int bytecodeCount, int header) {
            CompiledMethodObject newMethod = (CompiledMethodObject) receiver.newInstance(bytecodeCount);
            newMethod.setHeader(header);
            return newMethod;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 129)
    protected static abstract class PrimSpecialObjectsArrayNode extends AbstractPrimitiveNode {

        protected PrimSpecialObjectsArrayNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected BaseSqueakObject get(@SuppressWarnings("unused") BaseSqueakObject receiver) {
            return code.image.specialObjectsArray;
        }

    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 170, numArguments = 2)
    protected static abstract class PrimCharacterValueNode extends AbstractPrimitiveNode {

        protected PrimCharacterValueNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected char value(@SuppressWarnings("unused") BaseSqueakObject receiver, char value) {
            return value;
        }

        @Specialization
        protected char value(@SuppressWarnings("unused") BaseSqueakObject ignored, int value) {
            return (char) value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 173, numArguments = 2)
    protected static abstract class PrimSlotAtNode extends AbstractPrimitiveNode {

        protected PrimSlotAtNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(BaseSqueakObject receiver, int index) {
            try {
                return receiver.at0(index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 174, numArguments = 3)
    protected static abstract class PrimSlotAtPutNode extends AbstractPrimitiveNode {

        protected PrimSlotAtPutNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(BaseSqueakObject receiver, int index, Object value) {
            try {
                receiver.atput0(index, value);
                return value;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }
    }
}
