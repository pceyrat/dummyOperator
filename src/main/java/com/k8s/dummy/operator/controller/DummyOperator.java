package com.k8s.dummy.operator.controller;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

import com.k8s.dummy.operator.controller.client.EnhancedClient;
import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyStatus;
import com.k8s.dummy.operator.utils.WorkaroundMicroTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.client.informers.cache.Lister;

@Component
public class DummyOperator implements Runnable, HealthIndicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyOperator.class);
    private static final String IMAGE = "busybox";

    private final EnhancedClient enhancedClient;
    private final String kindName;
    private final Map<String,String> operatorLabel;

    private final BlockingQueue<String> queue;

    private final Lister<Dummy> dummyLister;
    private final Lister<Deployment> deployLister;

    public DummyOperator(@Autowired EnhancedClient enhancedClient,
                         @Qualifier("operator.kindName") String kindName,
                         @Qualifier("operator.queue") BlockingQueue<String> queue,
                         @Autowired Lister<Dummy> dummyLister,
                         @Autowired Lister<Deployment> deployLister,
                         @Autowired AsyncTaskExecutor asyncTaskExecuter) {
        this.enhancedClient = enhancedClient;
        this.queue = queue;
        this.kindName = kindName;
        this.operatorLabel = Map.of("xgeeks", kindName);
        this.dummyLister = dummyLister;
        this.deployLister = deployLister;
        asyncTaskExecuter.execute(this);
    }

    @Override
    public void run() {
        LOGGER.info("Starting Dummy controller");
        while(true) {
            try {
                controlLoop();
            } catch (InterruptedException e) {
                LOGGER.error("Error while waiting {}", e.getMessage());
            }
        }
    }

    @Override
    public Health health() {
        String details = "Dummy Custom Resource Definition";
        if (enhancedClient.checkHealthiness(kindName)) {
            return Health.up().withDetail(details, "Available").build();
        }
        return Health.down().withDetail(details, "Not Available").build();
    }

    public void controlLoop() throws InterruptedException {
        String dummyKey = queue.take();
        Optional.ofNullable(dummyLister.get(dummyKey))
            .ifPresentOrElse(
                dummy -> reconcile(dummy),
                () -> LOGGER.info("Dummy resource not in cache")
            );
    }

    private void reconcile(Dummy dummy) {
        Optional<Deployment> deployment = deployLister.list().stream().filter(deploy -> deploy.getMetadata().getName().equals(dummy.getMetaName())).findAny();
        deployment.ifPresentOrElse(deploy -> {
            PodTemplateSpec desiredPodTemplateSpec = generatePodTemplateSpec(dummy);
            if (!isDesiredDeployment(deploy, desiredPodTemplateSpec, dummy.getSpec().getReplicas())) {
                enhancedClient.editDeployment(dummy, desiredPodTemplateSpec);
                enhancedClient.updateStatus(updateStatus(dummy));
                enhancedClient.addEvent(generateEvent(dummy, "editing"));
            }
        }, () -> {
            enhancedClient.addDeployment(generateDeployment(dummy));
            enhancedClient.updateStatus(updateStatus(dummy));
            enhancedClient.addEvent(generateEvent(dummy, "creating"));
        });
    }

    public Deployment generateDeployment(Dummy dummy) {
        return new DeploymentBuilder()
                    .withNewMetadata()
                        .withName(dummy.getMetaName())
                        .withNamespace(dummy.getMetaspace())
                        .addToLabels(kindName, dummy.getMetaName())
                        .addToOwnerReferences(dummy.getOwnerReference())
                        .endMetadata()
                    .withNewSpec()
                        .withReplicas(dummy.getSpec().getReplicas())
                        .withTemplate(generatePodTemplateSpec(dummy))
                        .withNewSelector()
                            .addToMatchLabels(kindName, dummy.getMetaName())
                            .endSelector()
                        .endSpec()
                    .build();
    }

    public PodTemplateSpec generatePodTemplateSpec(Dummy dummy) {
        String command = "/bin/sh";
        String[] args = {"-c", String.format("/bin/echo \"%s\n%s\"; /bin/sleep %d", dummy.getSpec().getQuote(), String.join(" ", dummy.getSpec().getExtra()), dummy.getSpec().getSleep())};
        return new PodTemplateSpecBuilder()
                        .withNewMetadata()
                            .addToLabels(kindName, dummy.getMetaName())
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
    
    public Dummy updateStatus(Dummy dummy) {
        Optional<DummyStatus> oldStatus = Optional.ofNullable(dummy.getStatus());
        oldStatus.ifPresentOrElse(st -> st.incrementTimesChanged(), () -> dummy.setStatus(new DummyStatus()));
        return dummy;
    }

    public Event generateEvent(Dummy dummy, String action) {
        String instance = this.getClass().getSimpleName().toLowerCase();
        return new EventBuilder()
                    .withNewMetadata()
                        .withGenerateName(dummy.getMetaName() + "-" + action + "-")
                        .withNamespace(dummy.getMetaspace())
                        .withLabels(operatorLabel)
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
    }

    public boolean isDesiredDeployment(Deployment deployment, PodTemplateSpec podTemplateSpec, int replicas) {
        return deployment.getSpec().getReplicas() == replicas && deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs().equals(podTemplateSpec.getSpec().getContainers().get(0).getArgs());
    }
}
