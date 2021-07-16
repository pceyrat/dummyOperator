package com.k8s.dummy.operator.controller.handlers;

import java.util.Queue;

import com.k8s.dummy.operator.model.v1beta1.Dummy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DummyEventHandler extends EventHandler<Dummy> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyEventHandler.class);

    public DummyEventHandler(Queue<String> queue) {
        super(queue);
    }

    @Override
    public void onAdd(Dummy dummy) {
        LOGGER.info("{} dummy added", dummy.getMetaName());
        addToQueue(getFQN(dummy.getMetaspace(), dummy.getMetaName()));
    }

    @Override
    public void onUpdate(Dummy oldDummy, Dummy newDummy) {
        if (!newDummy.getSpec().equals(oldDummy.getSpec())) {
            LOGGER.info("{} dummy updated", oldDummy.getMetaName());
            addToQueue(getFQN(newDummy.getMetaspace(), newDummy.getMetaName()));
        }
    }

    @Override
    public void onDelete(Dummy dummy, boolean deletedFinalStateUnknown) {
        LOGGER.info("{} dummy deleted", dummy.getMetaName());
    }
    
}
