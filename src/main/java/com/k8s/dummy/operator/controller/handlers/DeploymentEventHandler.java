package com.k8s.dummy.operator.controller.handlers;


import java.util.Optional;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;

public class DeploymentEventHandler extends EventHandler<Deployment> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentEventHandler.class);

    private final String kindName;

    public DeploymentEventHandler(Queue<String> queue, String kindName) {
        super(queue);
        this.kindName = kindName;
    }

    private void tryToAdd(Deployment deployment, String logMessage) {
        Optional<OwnerReference> ownerReference = deployment.getMetadata().getOwnerReferences().stream()
                                                                .filter(x -> x.getKind().equals(kindName))
                                                                .findFirst();

        ownerReference.ifPresent(owner -> {
            String resourceName = getFQN(deployment.getMetadata().getNamespace(), owner.getName());
            addToQueue(resourceName);
            LOGGER.info(logMessage, resourceName);
        });
    }

    @Override
    public void onAdd(Deployment deployment) {}

    @Override
    public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
        tryToAdd(newDeployment, "Deployment from dummy resource {} updated");
    }

    @Override
    public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
        tryToAdd(deployment, "Deployment from dummy resource {} deleted");
    }
    
}
