package com.k8s.dummy.operator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@Configuration
public class OperatorConfigs {
    @Bean
    public KubernetesClient getKubernetesClient() {
        return  new DefaultKubernetesClient();
    }
}
