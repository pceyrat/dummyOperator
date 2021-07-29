package com.k8s.dummy.operator.controller.client;

import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyList;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An enhanced client that uses the KubernetesClient class from fabric8.
 */
public class EnhancedKubernetesClient implements EnhancedClient<KubernetesClient> {
  private static final Logger LOGGER = LoggerFactory.getLogger(EnhancedKubernetesClient.class);

  private final KubernetesClient client;

  public EnhancedKubernetesClient(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void addDeployment(Deployment deployment) {
    LOGGER.info("Creating a deployment");
    client.apps().deployments().create(deployment);
  }

  @Override
  public void editDeployment(Dummy dummy, PodTemplateSpec desiredPodTemplateSpec) {
    LOGGER.info("Editing deployment");
    client.apps().deployments().inNamespace(dummy.getMetaspace()).withName(dummy.getMetaName())
        .edit(d -> new DeploymentBuilder(d)
            .editSpec().withReplicas(dummy.getSpec().getReplicas())
            .withTemplate(desiredPodTemplateSpec).endSpec().build());
      
  }

  @Override
  public void updateStatus(Dummy dummy) {
    LOGGER.info("Update status");
    client.customResources(Dummy.class, DummyList.class)
          .inNamespace(dummy.getMetaspace())
          .withName(dummy.getMetaName())
          .patchStatus(dummy);
  }

  @Override
  public void addEvent(Event event) {
    LOGGER.info("Add event");
    client.events().v1().events().inNamespace(event.getMetadata().getNamespace()).create(event);
  }

  @Override
  public boolean checkHealthiness(String kindName) {
    return client.apiextensions().v1().customResourceDefinitions()
                 .list().getItems().stream()
                 .anyMatch(x -> x.getSpec().getNames().getKind().equals(kindName));
  }

  @Override
  public KubernetesClient getClient() {
    return client;
  }
}
