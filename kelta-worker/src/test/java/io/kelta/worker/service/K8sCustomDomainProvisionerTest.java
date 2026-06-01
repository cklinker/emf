package io.kelta.worker.service;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("K8sCustomDomainProvisioner")
class K8sCustomDomainProvisionerTest {

    @Mock private KubernetesClient client;
    @SuppressWarnings("rawtypes")
    @Mock private MixedOperation mixedOp;
    @SuppressWarnings("rawtypes")
    @Mock private io.fabric8.kubernetes.client.dsl.NonNamespaceOperation nsOp;
    @SuppressWarnings("rawtypes")
    @Mock private Resource resource;

    private K8sCustomDomainProvisioner provisioner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.genericKubernetesResources(any())).thenReturn(mixedOp);
        when(mixedOp.inNamespace(anyString())).thenReturn(nsOp);
        when(nsOp.resource(any(GenericKubernetesResource.class))).thenReturn(resource);
        when(nsOp.withName(anyString())).thenReturn(resource);
        provisioner = new K8sCustomDomainProvisioner(client, "emf", "emf-gateway", 80, "letsencrypt-http01");
    }

    @Test
    @DisplayName("onVerified applies a Certificate + IngressRoute with the right shape")
    @SuppressWarnings("unchecked")
    void appliesCertificateAndIngressRoute() {
        provisioner.onVerified("d1", "acme.com", "tenant-1");

        ArgumentCaptor<GenericKubernetesResource> captor = ArgumentCaptor.forClass(GenericKubernetesResource.class);
        verify(nsOp, times(2)).resource(captor.capture());
        verify(resource, times(2)).serverSideApply();

        List<GenericKubernetesResource> applied = captor.getAllValues();
        GenericKubernetesResource cert = applied.stream()
                .filter(r -> "Certificate".equals(r.getKind())).findFirst().orElseThrow();
        GenericKubernetesResource route = applied.stream()
                .filter(r -> "IngressRoute".equals(r.getKind())).findFirst().orElseThrow();

        assertThat(cert.getMetadata().getName()).isEqualTo("tenant-domain-d1");
        assertThat(cert.getMetadata().getLabels()).containsEntry("kelta.io/tenant-id", "tenant-1");
        Map<String, Object> certSpec = (Map<String, Object>) cert.getAdditionalProperties().get("spec");
        assertThat(certSpec).containsEntry("dnsNames", List.of("acme.com"));
        assertThat(certSpec).containsEntry("secretName", "tenant-domain-d1-tls");
        assertThat(((Map<String, Object>) certSpec.get("issuerRef")).get("name")).isEqualTo("letsencrypt-http01");

        assertThat(route.getMetadata().getName()).isEqualTo("tenant-domain-d1");
        Map<String, Object> routeSpec = (Map<String, Object>) route.getAdditionalProperties().get("spec");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) routeSpec.get("routes");
        assertThat(routes.get(0).get("match")).isEqualTo("Host(`acme.com`)");
        assertThat(((Map<String, Object>) routeSpec.get("tls")).get("secretName"))
                .isEqualTo("tenant-domain-d1-tls");
    }

    @Test
    @DisplayName("onRemoved deletes both resources by name")
    void deletesByName() {
        provisioner.onRemoved("d1", "acme.com", "tenant-1");
        verify(nsOp, times(2)).withName("tenant-domain-d1");
        verify(resource, times(2)).delete();
    }

    @Test
    @DisplayName("K8s failures are swallowed so the verify endpoint still returns 200")
    void swallowsK8sErrors() {
        when(resource.serverSideApply()).thenThrow(new KubernetesClientException("forbidden"));
        // No exception bubbles out — caller (verify endpoint) keeps working.
        provisioner.onVerified("d1", "acme.com", "tenant-1");
    }
}
