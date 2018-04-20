package de.hpi.swa.trufflesqueak.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.trufflesqueak.SqueakImageContext;

@ReportPolymorphism
@TypeSystemReference(SqueakTypes.class)
public abstract class AbstractNodeWithImage extends Node {
    @CompilationFinal protected final SqueakImageContext image;

    protected AbstractNodeWithImage(final SqueakImageContext image) {
        this.image = image;
    }
}
