package com.k8s.dummy.operator.controller.client;

import com.k8s.dummy.operator.model.v1beta1.Dummy;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.events.v1.Event;

public interface EnhancedClient<T> {
  void addDeployment(Deployment deployment);

  void editDeployment(Dummy dummy, PodTemplateSpec desiredPodTemplateSpec);

  void updateStatus(Dummy dummy);

  void addEvent(Event event);

  boolean checkHealthiness(String kindName);

  T getClient();
}
