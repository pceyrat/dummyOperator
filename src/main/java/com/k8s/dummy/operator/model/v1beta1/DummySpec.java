package com.k8s.dummy.operator.model.v1beta1;

import java.util.Arrays;

public class DummySpec {
  private String quote;

  private int sleep;

  private int replicas;

  private String[] extra;

  public void setQuote(String quote) {
    this.quote = quote;
  }

  public String getQuote() {
    return quote;
  }

  public void setSleep(int sleep) {
    this.sleep = sleep;
  }

  public int getSleep() {
    return sleep;
  }

  public void setReplicas(int replicas) {
    this.replicas = replicas;
  }

  public int getReplicas() {
    return replicas;
  }

  public void setExtra(String[] extra) {
    this.extra = extra;
  }

  public String[] getExtra() {
    return extra;
  }

  @Override
  public String toString() {
    return "DummySpec{" + "quote=" + quote + ","
                        + "sleep=" + sleep + ","
                        + "replicas=" + replicas + ","
                        + "extra=" + String.join(" ", extra)
                        + "}";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DummySpec other = (DummySpec) obj;
    if (!quote.equals(other.getQuote())) {
      return false;
    }
    if (sleep != other.getSleep()) {
      return false;
    }
    if (replicas != other.getReplicas()) {
      return false;
    }
    if (!Arrays.equals(extra, other.getExtra())) {
      return false;
    }
    return true;
  }
}
