package com.k8s.dummy.operator.model.v1beta1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1beta1")
@Group("xgeeks.ki.com")
public class Dummy extends CustomResource<DummySpec, DummyStatus> implements Namespaced {

  /** Create ObjectReference using Dummy attributes.
   * @return ObjectReference
   */
  public ObjectReference getObjectReference() {
    return new ObjectReferenceBuilder()
                  .withApiVersion(getApiVersion())
                  .withKind(getKind())
                  .withName(getMetaName())
                  .withNamespace(getMetaspace())
                  .withUid(getMetadata().getUid())
                  .build();
  }

  /** Create OwnerReference using Dummy attributes.
   * @return OwnerReference
   */
  public OwnerReference getOwnerReference() {
    return new OwnerReferenceBuilder()
                  .withController(true)
                  .withApiVersion(getApiVersion())
                  .withKind(getKind())
                  .withName(getMetaName())
                  .withUid(getMetadata().getUid())
                  .build();
  }

  public String getMetaName() {
    return getMetadata().getName();
  }

  public String getMetaspace() {
    return getMetadata().getNamespace();
  }
}
