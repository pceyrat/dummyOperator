package com.k8s.dummy.operator.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.k8s.dummy.operator.controller.client.EnhancedClient;
import com.k8s.dummy.operator.model.v1beta1.Dummy;
import com.k8s.dummy.operator.model.v1beta1.DummySpec;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.core.task.AsyncTaskExecutor;

public class DummyOperatorTests {

  private static EnhancedClient enhancedClientMock;
  private static String kindName = "test";
  private static Lister<Dummy> dummyListerMock;
  private static Lister<Deployment> deployListerMock;
  private static AsyncTaskExecutor asyncTaskExecuterMock;
  private static DummyOperator dummyOperator;

  private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
  private final ObjectMeta defaultMetadata = new ObjectMetaBuilder().withName("testName")
                                                              .withNamespace("testNamespace")
                                                              .build();
  private Dummy dummy;

  /** Create DummyOperator object with mocks except for kindName and queue.
   * EnhancedClient mock does nothing when a method that would imply a change is called.
   */
  @BeforeAll
  public static void setup() {
    enhancedClientMock = mock(EnhancedClient.class);
    dummyListerMock = mock(Lister.class);
    deployListerMock = mock(Lister.class);
    asyncTaskExecuterMock = mock(AsyncTaskExecutor.class);

    Mockito.doNothing().when(asyncTaskExecuterMock).execute(any());
    Mockito.doNothing().when(enhancedClientMock).addDeployment(any());
    Mockito.doNothing().when(enhancedClientMock).editDeployment(any(), any());
    Mockito.doNothing().when(enhancedClientMock).updateStatus(any());
    Mockito.doNothing().when(enhancedClientMock).addEvent(any());

    dummyOperator = new DummyOperator(enhancedClientMock,
                                      kindName,
                                      queue,
                                      dummyListerMock,
                                      deployListerMock,
                                      asyncTaskExecuterMock);
  }

  /** Create a Dummy object with a defaultMetadata, some default attributes for the specification
   * and without a status.
   */
  @BeforeEach
  public void dummySetup() {
    dummy = new Dummy();

    dummy.setMetadata(defaultMetadata);

    DummySpec dummySpec = new DummySpec();
    dummySpec.setQuote("quote");
    dummySpec.setSleep(20);
    dummySpec.setExtra(new String[1]);
    dummySpec.setReplicas(1);

    dummy.setSpec(dummySpec);
  }

  @Test
  public void testPodTemplateCreation() {
    PodTemplateSpec podtemplateSpec = dummyOperator.generatePodTemplateSpec(dummy);

    assertEquals(1, podtemplateSpec.getMetadata().getLabels().size());
    assertEquals(dummy.getMetaName(), podtemplateSpec.getMetadata().getLabels().get(kindName));

    assertEquals(1, podtemplateSpec.getSpec().getContainers().size());
    assertEquals("busybox", podtemplateSpec.getSpec().getContainers().get(0).getImage());
    assertEquals(dummy.getMetaName() + "-container",
                 podtemplateSpec.getSpec().getContainers().get(0).getName());
  }

  @Test
  public void testPodTemplateCreationFailedIfFieldsNotDefined() {
    dummy.setSpec(new DummySpec());;
    assertThrows(NullPointerException.class, () -> dummyOperator.generatePodTemplateSpec(dummy));
  }

  @Test
  public void testDeploymentCreation() {
    Deployment deployment = dummyOperator.generateDeployment(dummy);

    assertEquals(dummy.getMetaName(), deployment.getMetadata().getName());
    assertEquals(dummy.getMetaspace(), deployment.getMetadata().getNamespace());

    assertEquals(1, deployment.getMetadata().getLabels().size());
    assertEquals(dummy.getMetaName(), deployment.getMetadata().getLabels().get(kindName));
    assertEquals(1, deployment.getMetadata().getOwnerReferences().size());
    assertEquals(dummy.getOwnerReference(), deployment.getMetadata().getOwnerReferences().get(0));

    assertEquals(Integer.valueOf(1), deployment.getSpec().getReplicas());
    assertEquals(dummy.getMetaName(),
                 deployment.getSpec().getSelector().getMatchLabels().get(kindName));
  }

  @Test
  public void testEventCreation() {
    String action = "action";
    Event event = dummyOperator.generateEvent(dummy, action);

    assertTrue(event.getMetadata().getGenerateName().startsWith(dummy.getMetaName()));
    assertEquals(dummy.getMetaspace(), event.getMetadata().getNamespace());

    assertEquals(1, event.getMetadata().getOwnerReferences().size());
    assertEquals(dummy.getOwnerReference(), event.getMetadata().getOwnerReferences().get(0));

    assertEquals(action, event.getAction());
    assertEquals(action, event.getReason());
    assertEquals(dummy.getSpec().toString(), event.getNote());
    assertEquals(dummy.getObjectReference(), event.getRegarding());
  }

  @Test
  public void testUpdateStatus() {
    assertNull(dummy.getStatus());

    dummyOperator.updateStatus(dummy);

    assertNotNull(dummy.getStatus());
    assertEquals(0, dummy.getStatus().getTimesChanged());

    dummyOperator.updateStatus(dummy);

    assertEquals(1, dummy.getStatus().getTimesChanged());
  }

  @Test
  public void testIsDesiredDeploymentWhenArgsDontMatch() {
    Container container = new ContainerBuilder()
                                .withArgs(new String[0])
                                .build();
    Deployment deployment = new DeploymentBuilder().withNewSpec()
                                        .withReplicas(dummy.getSpec().getReplicas())
                                        .withNewTemplate()
                                            .withNewSpec()
                                                .withContainers(container)
                                                .endSpec()
                                            .endTemplate()
                                        .endSpec()
                                    .build();

    assertFalse(dummyOperator.isDesiredDeployment(deployment,
                dummyOperator.generatePodTemplateSpec(dummy), dummy.getSpec().getReplicas()));
  }

  @Test
  public void testIsDesiredDeploymentWhenReplicasDontMatch() {
    PodTemplateSpec desiredPodTemplateSpec = dummyOperator.generatePodTemplateSpec(dummy);
    Container container = new ContainerBuilder()
                        .withArgs(desiredPodTemplateSpec.getSpec().getContainers().get(0).getArgs())
                        .build();
    Deployment deployment = new DeploymentBuilder()
                                  .withNewSpec()
                                  .withReplicas(dummy.getSpec().getReplicas() + 1)
                                  .withNewTemplate()
                                      .withNewSpec()
                                          .withContainers(container)
                                          .endSpec()
                                      .endTemplate()
                                  .endSpec()
                                .build();

    assertFalse(dummyOperator.isDesiredDeployment(deployment,
                desiredPodTemplateSpec, dummy.getSpec().getReplicas()));
  }

  @Test
  public void testIsDesiredDeploymentSuccess() {
    PodTemplateSpec desiredPodTemplateSpec = dummyOperator.generatePodTemplateSpec(dummy);
    Container container = new ContainerBuilder()
                        .withArgs(desiredPodTemplateSpec.getSpec().getContainers().get(0).getArgs())
                        .build();
    Deployment deployment = new DeploymentBuilder().withNewSpec()
                                  .withReplicas(dummy.getSpec().getReplicas())
                                  .withNewTemplate()
                                      .withNewSpec()
                                          .withContainers(container)
                                          .endSpec()
                                      .endTemplate()
                                  .endSpec()
                                .build();

    assertTrue(dummyOperator.isDesiredDeployment(deployment, desiredPodTemplateSpec,
               dummy.getSpec().getReplicas()));
  }

  @Test
  public void testHealth() {
    Mockito.doReturn(true).when(enhancedClientMock).checkHealthiness(kindName);
    Health health = dummyOperator.health();

    assertEquals("UP", health.getStatus().getCode());

    Mockito.doReturn(false).when(enhancedClientMock).checkHealthiness(kindName);
    health = dummyOperator.health();

    assertEquals("DOWN", health.getStatus().getCode());
  }

  @Test
  public void testCreateWhenDummyExistsAndDeploymentDoesNot() throws InterruptedException {
    String fqn = String.format("%s/%s", dummy.getMetaspace(), dummy.getMetaName());
    queue.add(fqn);

    Mockito.doReturn(dummy).when(dummyListerMock).get(fqn);
    Mockito.doReturn(List.of()).when(deployListerMock).list();

    dummyOperator.controlLoop();

    Mockito.verify(enhancedClientMock).addDeployment(dummyOperator.generateDeployment(dummy));
  }

  @Test
  public void testEditWhenDummyExistsAndDeploymentDoesAndIsDesired() throws InterruptedException {
    String fqn = String.format("%s/%s", dummy.getMetaspace(), dummy.getMetaName());
    queue.add(fqn);

    PodTemplateSpec desiredPodTemplateSpec = dummyOperator.generatePodTemplateSpec(dummy);
    Container container = new ContainerBuilder()
                        .withArgs(desiredPodTemplateSpec.getSpec().getContainers().get(0).getArgs())
                        .build();
    Deployment deployment = new DeploymentBuilder().withMetadata(defaultMetadata).withNewSpec()
                                  .withReplicas(dummy.getSpec().getReplicas())
                                  .withNewTemplate()
                                      .withNewSpec()
                                          .withContainers(container)
                                          .endSpec()
                                      .endTemplate()
                                  .endSpec()
                                .build();

    Mockito.doReturn(dummy).when(dummyListerMock).get(fqn);
    Mockito.doReturn(List.of(deployment)).when(deployListerMock).list();

    dummyOperator.controlLoop();

    Mockito.verify(enhancedClientMock, times(0)).updateStatus(dummy);
  }

  @Test
  public void testEditWhenDummyExistsAndDeploymentDoesAndDiffers() throws InterruptedException {
    String fqn = String.format("%s/%s", dummy.getMetaspace(), dummy.getMetaName());
    queue.add(fqn);

    Container container = new ContainerBuilder()
                                .withArgs(new String[0])
                                .build();
    Deployment deployment = new DeploymentBuilder().withMetadata(defaultMetadata).withNewSpec()
                                  .withReplicas(2)
                                  .withNewTemplate()
                                      .withNewSpec()
                                          .withContainers(container)
                                          .endSpec()
                                      .endTemplate()
                                  .endSpec()
                                .build();

    Mockito.doReturn(dummy).when(dummyListerMock).get(fqn);
    Mockito.doReturn(List.of(deployment)).when(deployListerMock).list();

    dummyOperator.controlLoop();

    Mockito.verify(enhancedClientMock).editDeployment(dummy,
                   dummyOperator.generatePodTemplateSpec(dummy));
  }
}
