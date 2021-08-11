package com.k8s.dummy.operator.controller;

import com.k8s.dummy.operator.controller.client.EnhancedClient;
import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyStatus;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Class where our control loop is implemented.
 */
@Component
public class DummyController implements Runnable, HealthIndicator {
  private static final Logger LOGGER = LoggerFactory.getLogger(DummyController.class);
  private static final DateTimeFormatter k8sMicroTime =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.'SSSSSSXXX");

  private final EnhancedClient enhancedClient;
  private final String kindName;
  private final Map<String, String> operatorLabels;
  private final BlockingQueue<String> queue;

  private final Lister<Dummy> dummyLister;
  private final Lister<Deployment> deployLister;

  /**
   * Create a DummyOperator object and launch a thread executing the run method.
   *
   * @param enhancedClient client that follows an EnhancedClient interface
   * @param kindName custom resource kind name
   * @param operatorLabels labels to add for events
   * @param queue a blocking queue from which will receive the resource names to reconcile
   * @param dummyLister a lister with Dummy objects
   * @param deployLister a lister with Deployment objects
   * @param asyncTaskExecuter a task executer to start a thread executing this object
   */
  public DummyController(@Autowired EnhancedClient enhancedClient,
                       @Qualifier("operator.kindName") String kindName,
                       @Value("#{${operator.labels}}") Map<String, String> operatorLabels,
                       @Qualifier("operator.queue") BlockingQueue<String> queue,
                       @Autowired Lister<Dummy> dummyLister,
                       @Autowired Lister<Deployment> deployLister,
                       @Autowired AsyncTaskExecutor asyncTaskExecuter) {
    this.enhancedClient = enhancedClient;
    this.kindName = kindName;
    this.operatorLabels = operatorLabels;
    this.queue = queue;
    this.dummyLister = dummyLister;
    this.deployLister = deployLister;
    asyncTaskExecuter.execute(this);
  }

  @Override
  public void run() {
    LOGGER.info("Starting Dummy controller");
    while (true) {
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


  /** 
   * Control loop which takes the next resource from queue and if a Dummy
   * object exists in cache it calls the reconcile method. Other wise does nothing.
   *
   * @throws InterruptedException when current thread is interrupted
   */
  public void controlLoop() throws InterruptedException {
    String dummyKey = queue.take();
    Optional.ofNullable(dummyLister.get(dummyKey))
        .ifPresentOrElse(
            this::reconcile,
            () -> LOGGER.info("Dummy resource not in cache")
        );
  }


  /**
   * Retrieve the deployment associated with the Dummy object and if it does not exist create it,
   * if it exists, compare with the desired state and edit the deployment if they do not match.
   *
   * @param dummy Dummy object
   */
  private void reconcile(Dummy dummy) {
    Optional<Deployment> deployment = deployLister.list().stream()
                    .filter(deploy -> deploy.getMetadata().getName().equals(dummy.getMetaName()))
                    .findAny();
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


  /**
   * Generate a Deployment using the Dummy object and the generated pod
   * template (@see #method generatePodTemplateSpec).
   * Uses the kindName and the dummy name to label and as the selector.
   *
   * @param dummy Dummy object
   * @return Deployment
   */
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


  /**
   * Generate a pod specification template according with the Dummy attributes.
   * Uses the IMAGE attribute to define the container image.
   * Uses the kindName and the dummy name to label.
   *
   * @param dummy Dummy object
   * @return PodTemplateSpec
   */
  public PodTemplateSpec generatePodTemplateSpec(Dummy dummy) {
    String[] args = {"-c", String.format(
                        "/bin/echo \"%s\n%s\"; /bin/sleep %d",
                        dummy.getSpec().getQuote(),
                        String.join(" ", dummy.getSpec().getExtra()), dummy.getSpec().getSleep())};
    return new PodTemplateSpecBuilder()
                .withNewMetadata()
                  .addToLabels(kindName, dummy.getMetaName())
                  .endMetadata()
                .withNewSpec()
                  .addNewContainer()
                    .withName(dummy.getMetaName() + "-container")
                    .withImage("busybox")
                    .withCommand("/bin/sh")
                    .withArgs(args)
                    .endContainer()
                  .endSpec()
                .build();
  }


  /**
   * Create status if @param dummy does not have it or increment the timesChanged attribute
   * in the DummyStatus object.
   *
   * @param dummy Dummy object
   * @return Dummy
   */
  public Dummy updateStatus(Dummy dummy) {
    Optional<DummyStatus> oldStatus = Optional.ofNullable(dummy.getStatus());
    oldStatus.ifPresentOrElse(
        DummyStatus::incrementTimesChanged,
        () -> dummy.setStatus(new DummyStatus()));
    return dummy;
  }


  /**
   * Generate an event using the operatorLabel and Dummy attributes.
   *
   * @param dummy Dummy object
   * @param action String object indicating the action
   * @return Event
   */
  public Event generateEvent(Dummy dummy, String action) {
    String instance = this.getClass().getSimpleName().toLowerCase();
    return new EventBuilder()
                .withNewMetadata()
                  .withGenerateName(dummy.getMetaName() + "-" + action + "-")
                  .withNamespace(dummy.getMetaspace())
                  .withLabels(operatorLabels)
                  .addToOwnerReferences(dummy.getOwnerReference())
                  .endMetadata()
                .withType("Normal")
                .withAction(action)
                .withReason(action)
                .withNote(dummy.getSpec().toString())
                .withNewEventTime(k8sMicroTime.format(ZonedDateTime.now()))
                .withReportingController(instance)
                .withReportingInstance(instance)
                .withRegarding(dummy.getObjectReference())
                .build();
  }


  /**
   * Compare if the number of replicas is the same and if the first container args
   * is equal between desired podTemplate and current deployment state.
   *
   * @param deployment current state of the deployment
   * @param podTemplateSpec desired pod template specification
   * @param replicas desired number of replicas
   * @return boolean
   */
  public boolean isDesiredDeployment(Deployment deployment,
                                     PodTemplateSpec podTemplateSpec,
                                     int replicas) {
    return deployment.getSpec().getReplicas() == replicas
          && deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs()
                        .equals(podTemplateSpec.getSpec().getContainers().get(0).getArgs());
  }
}
