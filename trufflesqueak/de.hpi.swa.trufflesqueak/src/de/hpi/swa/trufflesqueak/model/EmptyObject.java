package de.hpi.swa.trufflesqueak.model;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions;

public class EmptyObject extends SqueakObject {
    public EmptyObject(final SqueakImageContext img) {
        super(img);
    }

    public EmptyObject(final SqueakImageContext image, final ClassObject classObject) {
        super(image, classObject);
    }

    @Override
    public boolean become(final BaseSqueakObject other) {
        if (!(other instanceof EmptyObject)) {
            throw new PrimitiveExceptions.PrimitiveFailed();
        }
        return super.become(other);
    }

    @Override
    public final int size() {
        return 0;
    }

    @Override
    public final int instsize() {
        return 0;
    }

    @Override
    public Object at0(final long idx) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public void atput0(final long idx, final Object obj) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public BaseSqueakObject shallowCopy() {
        return new EmptyObject(image, getSqClass());
    }
}
