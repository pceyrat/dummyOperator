package com.k8s.dummy.operator.controller.client;

import com.k8s.dummy.operator.model.v1beta1.Dummy;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.events.v1.Event;

/**
 * A generic EnhancedClient that we will use in our operator it requires that it
 * can add and edit deployments, update status, add events and check the healthiness.
 */
public interface EnhancedClient<T> {
  void addDeployment(Deployment deployment);

  void editDeployment(Dummy dummy, PodTemplateSpec desiredPodTemplateSpec);

  void updateStatus(Dummy dummy);

  void addEvent(Event event);

  boolean checkHealthiness(String kindName);

  T getClient();
}
