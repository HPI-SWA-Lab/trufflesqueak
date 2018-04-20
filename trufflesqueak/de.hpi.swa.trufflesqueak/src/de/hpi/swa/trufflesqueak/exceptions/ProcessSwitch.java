package de.hpi.swa.trufflesqueak.exceptions;

import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.trufflesqueak.model.ContextObject;

public class ProcessSwitch extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    private final ContextObject context;

    public ProcessSwitch(final ContextObject context) {
        this.context = context;
    }

    public ContextObject getNewContext() {
        return context;
    }

    @Override
    public String toString() {
        return "Process switch to " + context;
    }
}
