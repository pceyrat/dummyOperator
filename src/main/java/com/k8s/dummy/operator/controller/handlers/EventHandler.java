package com.k8s.dummy.operator.controller.handlers;

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import java.util.Queue;

/**
 * A generic event handler will have a queue to where it will add resource names
 * and it will be able to build a Fully Qualified Name from the namespace and name.
 */
public abstract class EventHandler<T> implements ResourceEventHandler<T> {
  private final Queue<String> queue;

  protected EventHandler(Queue<String> queue) {
    this.queue = queue;
  }

  public void addToQueue(String name) {
    queue.add(name);
  }

  public String getFqn(String namespace, String name) {
    return String.format("%s/%s", namespace, name);
  }
}
