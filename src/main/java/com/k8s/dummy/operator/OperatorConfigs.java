package com.k8s.dummy.operator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.k8s.dummy.operator.controller.client.EnhancedClient;
import com.k8s.dummy.operator.controller.client.EnhancedKubernetesClient;
import com.k8s.dummy.operator.controller.handlers.DeploymentEventHandler;
import com.k8s.dummy.operator.controller.handlers.DummyEventHandler;
import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;

@Configuration
public class OperatorConfigs {
    @Bean
    public KubernetesClient getKubernetesClient() {
        return  new DefaultKubernetesClient();
    }

    @Bean
    public EnhancedClient getEnhancedKubernetesClient(@Autowired KubernetesClient client) {
        return  new EnhancedKubernetesClient(client);
    }

    @Bean("operator.queue")
    public BlockingQueue<String> getQueue() {
        return new LinkedBlockingQueue<>();
    }

    @Bean("operator.kindName")
    public String getKingName(@Value("${custom.resource.name:Dummy}") String kindName) {
        return kindName;
    }

    @Bean("operator.resync.period")
    public long getResyncPeriod(@Value("${operator.resync.period:40000}") String resync) {
        return Long.valueOf(resync);
    }

    @Bean
    public DummyEventHandler getDummyEventHandler(@Qualifier("operator.queue") BlockingQueue<String> queue) {
        return new DummyEventHandler(queue);
    }

    @Bean
    public Lister<Dummy> createDummyLister(@Autowired KubernetesClient client, @Autowired DummyEventHandler dummyEventHandler,  @Qualifier("operator.resync.period") long resync) {
        SharedIndexInformer<Dummy> dummyInformer = client.customResources(Dummy.class, DummyList.class).inAnyNamespace().inform(dummyEventHandler, resync);

        while (!dummyInformer.hasSynced());
        return new Lister<Dummy>(dummyInformer.getIndexer());
    }

    @Bean
    public DeploymentEventHandler geDeploymentEventHandler(@Qualifier("operator.queue") BlockingQueue<String> queue, @Qualifier("operator.kindName") String kindName) {
        return new DeploymentEventHandler(queue, kindName);
    }

    @Bean
    public Lister<Deployment> createDeploymentLister(@Autowired KubernetesClient client, @Autowired DeploymentEventHandler deploymentEventHandler, @Qualifier("operator.resync.period") long resync) {
        SharedIndexInformer<Deployment> deploymentInformer = client.apps().deployments().inAnyNamespace().inform(deploymentEventHandler, 2 * resync);

        while (!deploymentInformer.hasSynced());
        return new Lister<Deployment>(deploymentInformer.getIndexer());
    }
}
