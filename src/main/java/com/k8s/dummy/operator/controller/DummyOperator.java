package com.k8s.dummy.operator.controller;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyList;
import com.k8s.dummy.operator.model.v1beta1.DummyStatus;
import com.k8s.dummy.operator.utils.WorkaroundMicroTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;

@Component
public class DummyOperator implements Runnable, HealthIndicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyOperator.class);
    private static final long RESYNC_PERIOD = 40 * 1_000L;
    private static final String CR_KIND_NAME = "Dummy";
    private static final Map<String,String> OPERATOR_LABEL = Map.of("xgeeks", CR_KIND_NAME);
    private static final String IMAGE = "busybox";

    private final KubernetesClient client;

    private final BlockingQueue<String> myQueue;

    private final Lister<Dummy> dummyLister;
    private final Lister<Deployment> deployLister;

    public DummyOperator(@Autowired KubernetesClient client, @Autowired AsyncTaskExecutor asyncTaskExecuter) {
        this.client = client;
        this.myQueue = new LinkedBlockingQueue<>();
        this.dummyLister = createDummyLister();
        this.deployLister = createDeploymentLister();
        asyncTaskExecuter.execute(this);
    }

    @Override
    public void run() {
        LOGGER.info("Starting Dummy controller");
        while(true) {
            try {
                String dummyKey = myQueue.take();
                Optional.ofNullable(dummyLister.get(dummyKey))
                    .ifPresentOrElse(
                        dummy -> reconcile(dummy),
                        () -> LOGGER.info("Dummy resource not in cache"));
            } catch (InterruptedException e) {
                LOGGER.error("Error while waiting {}", e.getMessage());
            }
        }
    }

    @Override
    public Health health() {
        String details = "Dummy Custom Resource Definition";
        boolean status = client.apiextensions().v1().customResourceDefinitions()
                                .list().getItems().stream()
                                .anyMatch(x -> x.getSpec().getNames().getKind().equals(CR_KIND_NAME));
        if (status) {
            return Health.up().withDetail(details, "Available").build();
        }
        return Health.down().withDetail(details, "Not Available").build();
    }

    private void reconcile(Dummy dummy) {
        LOGGER.info("Reconciling object {}", dummy);
        Optional<Deployment> deployment = deployLister.list().stream().filter(deploy -> deploy.getMetadata().getName().equals(dummy.getMetaName())).findAny();
        deployment.ifPresentOrElse(deploy -> {
            PodTemplateSpec podTemplateSpec = createPodTemplateSpec(dummy);
            int replicas = dummy.getSpec().getReplicas();
            if (checkDeployment(deploy, podTemplateSpec, replicas)) {
                LOGGER.info("Editing deployment");
                client.apps().deployments().inNamespace(dummy.getMetaspace()).withName(dummy.getMetaName())
                    .edit(d -> new DeploymentBuilder(d)
                        .editSpec().withReplicas(replicas)
                        .withTemplate(podTemplateSpec).endSpec().build());
                updateStatus(dummy);
                addEvent(dummy, "editing");
            }
        }, () -> {
            LOGGER.info("Creating deployment");
            createDeployment(dummy);
            updateStatus(dummy);
            addEvent(dummy, "creating");
        });
    }

    private void createDeployment(Dummy dummy) {
        client.apps().deployments().create(
            new DeploymentBuilder()
                    .withNewMetadata()
                        .withName(dummy.getMetaName())
                        .withNamespace(dummy.getMetaspace())
                        .addToLabels(CR_KIND_NAME, dummy.getMetaName())
                        .addToOwnerReferences(dummy.getOwnerReference())
                        .endMetadata()
                    .withNewSpec()
                        .withReplicas(dummy.getSpec().getReplicas())
                        .withTemplate(createPodTemplateSpec(dummy))
                        .withNewSelector()
                            .addToMatchLabels(CR_KIND_NAME, dummy.getMetaName())
                            .endSelector()
                        .endSpec()
                    .build()
        );
    }

    private PodTemplateSpec createPodTemplateSpec(Dummy dummy) {
        String command = "/bin/sh";
        String[] args = {"-c", String.format("/bin/echo \"%s\n%s\"; /bin/sleep %d", dummy.getSpec().getQuote(), String.join(" ", dummy.getSpec().getExtra()), dummy.getSpec().getSleep())};
        return new PodTemplateSpecBuilder()
                        .withNewMetadata()
                            .addToLabels(CR_KIND_NAME, dummy.getMetaName())
                            .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName(dummy.getMetaName() + "-container")
                                .withImage(IMAGE)
                                .withCommand(command)
                                .withArgs(args)
                                .endContainer()
                            .endSpec()
                        .build();
    }
    
    private void updateStatus(Dummy dummy) {
        LOGGER.info("Update status");
        Optional<DummyStatus> oldStatus = Optional.ofNullable(dummy.getStatus());
        oldStatus.ifPresentOrElse(st -> st.incrementTimesChanged(), () -> {
            DummyStatus newStatus = new DummyStatus();
            dummy.setStatus(newStatus);
        });
        client.customResources(Dummy.class, DummyList.class).inNamespace(dummy.getMetaspace()).withName(dummy.getMetaName()).patchStatus(dummy);
    }

    private void addEvent(Dummy dummy, String action) {
        LOGGER.info("Add event");
        String instance = this.getClass().getSimpleName().toLowerCase();
        Event event = new EventBuilder()
                                .withNewMetadata()
                                    .withGenerateName(dummy.getMetaName() + "-" + action + "-")
                                    .withNamespace(dummy.getMetaspace())
                                    .withLabels(OPERATOR_LABEL)
                                    .addToOwnerReferences(dummy.getOwnerReference())
                                    .endMetadata()
                                .withType("Normal")
                                .withAction(action)
                                .withReason(action)
                                .withNote(dummy.getSpec().toString())
                                .withEventTime(new WorkaroundMicroTime(ZonedDateTime.now()))
                                .withReportingController(instance)
                                .withReportingInstance(instance)
                                .withRegarding(dummy.getObjectReference())
                                .build();
        client.events().v1().events().inNamespace(dummy.getMetaspace()).create(event);
    }

    private boolean checkDeployment(Deployment deployment, PodTemplateSpec podTemplateSpec, int replicas) {
        return !deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs().equals(podTemplateSpec.getSpec().getContainers().get(0).getArgs()) || deployment.getSpec().getReplicas() != replicas;
    }

    private Lister<Dummy> createDummyLister() {
        SharedIndexInformer<Dummy> dummyInformer = client.customResources(Dummy.class, DummyList.class).inAnyNamespace().inform(new ResourceEventHandler<Dummy>() {
            @Override
            public void onAdd(Dummy dummy) {
                LOGGER.info("{} dummy added", dummy.getMetaName());
                myQueue.add(String.format("%s/%s", dummy.getMetaspace(), dummy.getMetaName()));
            }

            @Override
            public void onUpdate(Dummy oldDummy, Dummy newDummy) {
                if (!newDummy.getSpec().equals(oldDummy.getSpec())) {
                    LOGGER.info("{} dummy updated", oldDummy.getMetaName());
                    myQueue.add(String.format("%s/%s", newDummy.getMetaspace(), newDummy.getMetaName()));
                }
            }

            @Override
            public void onDelete(Dummy dummy, boolean deletedFinalStateUnknown) {
                LOGGER.info("{} dummy deleted", dummy.getMetaName());
            }
        }, RESYNC_PERIOD);

        while (!dummyInformer.hasSynced());
        LOGGER.info("Dummy informer initialized and synced");
        return new Lister<Dummy>(dummyInformer.getIndexer());
    }

    private Lister<Deployment> createDeploymentLister() {
        Function<Deployment, Optional<OwnerReference>> getDummyOwnerReference = (deployment) ->
            deployment.getMetadata().getOwnerReferences().stream()
                                                        .filter(x -> x.getKind().equals(CR_KIND_NAME))
                                                        .findFirst();

        SharedIndexInformer<Deployment> deploymentInformer = client.apps().deployments().inAnyNamespace().inform(new ResourceEventHandler<Deployment>() {

            @Override
            public void onAdd(Deployment deployment) {}

            @Override
            public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
                Optional<OwnerReference> ownerReference = getDummyOwnerReference.apply(newDeployment);

                ownerReference.ifPresent(owner -> {
                    if (checkDeployment(oldDeployment, newDeployment.getSpec().getTemplate(), newDeployment.getSpec().getReplicas())) {
                        String resourceName = String.format("%s/%s", newDeployment.getMetadata().getNamespace(), owner.getName());
                        myQueue.add(resourceName);
                        LOGGER.info("Deployment from dummy resource {} updated", resourceName);
                    }
                });
            }

            @Override
            public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
                Optional<OwnerReference> ownerReference = getDummyOwnerReference.apply(deployment);

                ownerReference.ifPresent(owner -> {
                    String resourceName = String.format("%s/%s", deployment.getMetadata().getNamespace(), owner.getName());
                    myQueue.add(resourceName);
                    LOGGER.info("Deployment from dummy resource {} deleted", resourceName);
                });
            }
        }, RESYNC_PERIOD);

        while (!deploymentInformer.hasSynced());
        LOGGER.info("Deployment informer initialized and synced");
        return new Lister<Deployment>(deploymentInformer.getIndexer());
    }
}
