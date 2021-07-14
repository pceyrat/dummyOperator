package com.k8s.dummy.operator.model.v1beta1;

public class DummyStatus {
    private int timesChanged = 0;

    public int getTimesChanged() {
        return timesChanged;
    }

    public void incrementTimesChanged() {
        this.timesChanged += 1;
    }

    @Override
    public String toString() {
        return "DummyStatus{ timesChanged=" + timesChanged + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        DummyStatus other = (DummyStatus) obj;
        if (timesChanged != other.getTimesChanged()) return false;
        return true;
    }
}