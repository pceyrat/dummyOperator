package com.k8s.dummy.operator.utils;

import com.fasterxml.jackson.annotation.JsonValue;
import io.fabric8.kubernetes.api.model.MicroTime;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/*
    https://github.com/fabric8io/kubernetes-client/issues/3240
    https://github.com/fabric8io/kubernetes-client/pull/3299
*/
public class WorkaroundMicroTime extends MicroTime {

    private static final DateTimeFormatter k8sMicroTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.'SSSSSSXXX");
    private final String k8sFormattedMicroTime;

    public WorkaroundMicroTime(ZonedDateTime dateTime) {
        this.k8sFormattedMicroTime = k8sMicroTime.format(dateTime);
    }

    @JsonValue
    public String serialise() {
        return this.k8sFormattedMicroTime;
    }

}
