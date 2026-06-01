package io.kelta.worker.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a Fabric8 {@link KubernetesClient} only when the K8s provisioner is
 * enabled. The default in-cluster auto-config is used: the client picks up the
 * pod's mounted ServiceAccount token from
 * {@code /var/run/secrets/kubernetes.io/serviceaccount}. Local-dev / test
 * builds never instantiate this bean, so no kubeconfig is needed.
 */
@Configuration
@ConditionalOnProperty(name = "kelta.worker.k8s-provisioner.enabled", havingValue = "true")
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
