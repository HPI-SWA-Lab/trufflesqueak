package de.hpi.swa.trufflesqueak.nodes.context;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.nodes.SqueakNode;

@NodeChild(value = "objectNode", type = SqueakNode.class)
@NodeChild(value = "valueNode", type = SqueakNode.class)
public abstract class ObjectAtPutNode extends AbstractObjectAtNode {
    @CompilationFinal private final ValueProfile classProfile = ValueProfile.createClassProfile();
    @CompilationFinal private final int index;

    public static ObjectAtPutNode create(int index, SqueakNode object, SqueakNode value) {
        return ObjectAtPutNodeGen.create(index, object, value);
    }

    protected ObjectAtPutNode(int variableIndex) {
        index = variableIndex;
    }

    public abstract void executeWrite(VirtualFrame frame);

    protected static boolean isBigInteger(Object object) {
        return object instanceof BigInteger;
    }

    @Specialization
    protected void write(NativeObject object, int value) {
        classProfile.profile(object).setNativeAt0(index, value);
    }

    @Specialization(guards = "!isNativeObject(object)")
    protected void write(BaseSqueakObject object, int value) {
        classProfile.profile(object).atput0(index, value);
    }

    @Specialization
    protected void write(BaseSqueakObject object, BigInteger value) {
        classProfile.profile(object).atput0(index, object.image.wrap(value));
    }

    @Specialization(guards = "!isBigInteger(object)")
    protected void write(BaseSqueakObject object, Object value) {
        classProfile.profile(object).atput0(index, value);
    }

}
