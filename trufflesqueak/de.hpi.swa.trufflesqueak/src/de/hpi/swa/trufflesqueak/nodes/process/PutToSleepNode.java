package de.hpi.swa.trufflesqueak.nodes.process;

import com.oracle.truffle.api.CompilerDirectives;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.ListObject;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.AbstractNodeWithImage;

public class PutToSleepNode extends AbstractNodeWithImage {
    @Child private LinkProcessToListNode linkProcessToList;
    @Child private GetSchedulerNode getSchedulerNode;

    public static PutToSleepNode create(final SqueakImageContext image) {
        return new PutToSleepNode(image);
    }

    protected PutToSleepNode(final SqueakImageContext image) {
        super(image);
        linkProcessToList = LinkProcessToListNode.create(image);
        getSchedulerNode = GetSchedulerNode.create(image);
    }

    protected void executePutToSleep(final BaseSqueakObject process) {
        CompilerDirectives.transferToInterpreter();
        // Save the given process on the scheduler process list for its priority.
        final long priority = (long) process.at0(PROCESS.PRIORITY);
        final PointersObject scheduler = getSchedulerNode.executeGet();
        final ListObject processLists = (ListObject) scheduler.at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        final PointersObject processList = (PointersObject) processLists.at0(priority - 1);
        linkProcessToList.executeLink(process, processList);
    }
}
