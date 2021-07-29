package com.k8s.dummy.operator;

import com.k8s.dummy.operator.controller.client.EnhancedClient;
import com.k8s.dummy.operator.controller.client.EnhancedKubernetesClient;
import com.k8s.dummy.operator.controller.handlers.DeploymentEventHandler;
import com.k8s.dummy.operator.controller.handlers.DummyEventHandler;
import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummyList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
  public String getKingName(@Value("${custom.resource.name}") String kindName) {
    return kindName;
  }

  @Bean
  public DummyEventHandler getDummyEventHandler(@Qualifier("operator.queue") Queue<String> queue) {
    return new DummyEventHandler(queue);
  }

  @Bean
  public Lister<Dummy> createDummyLister(@Autowired KubernetesClient client,
                                         @Autowired DummyEventHandler dummyEventHandler,
                                         @Value("#{${operator.resync.period}}") long resync) {
    SharedIndexInformer<Dummy> dummyInformer = client.customResources(Dummy.class, DummyList.class)
                                                .inAnyNamespace()
                                                .inform(dummyEventHandler, resync);

    while (!dummyInformer.hasSynced()) {};
    return new Lister<Dummy>(dummyInformer.getIndexer());
  }

  @Bean
  public DeploymentEventHandler geDeploymentEventHandler(@Qualifier("operator.queue") Queue<String> queue,
                                                         @Qualifier("operator.kindName") String kindName) {
    return new DeploymentEventHandler(queue, kindName);
  }

  @Bean
  public Lister<Deployment> createDeploymentLister(@Autowired KubernetesClient client,
                                                   @Autowired DeploymentEventHandler deploymentEventHandler,
                                                   @Value("#{${operator.resync.period}}") long resync) {
    SharedIndexInformer<Deployment> deploymentInformer = client.apps().deployments()
                                                        .inAnyNamespace()
                                                        .inform(deploymentEventHandler, 2 * resync);

    while (!deploymentInformer.hasSynced()) {};
    return new Lister<Deployment>(deploymentInformer.getIndexer());
  }
}
