package com.k8s.dummy.operator.controller.handlers;

import java.util.Queue;

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

public abstract class EventHandler<T> implements ResourceEventHandler<T> {
    private final Queue<String> queue;

    public EventHandler(Queue<String> queue) {
        this.queue = queue;
    }

    public void addToQueue(String name) {
        queue.add(name);
    }

    public String getFQN(String namespace, String name) {
        return String.format("%s/%s", namespace, name);
    }
}
