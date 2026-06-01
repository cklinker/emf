package io.kelta.worker.service;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric8-backed {@link CustomDomainProvisioner}. On {@link #onVerified} it
 * creates (or server-side-applies) a cert-manager {@code Certificate} and a
 * Traefik {@code IngressRoute} so the customer's domain immediately serves
 * TLS-terminated traffic through {@code emf-gateway}. On {@link #onRemoved}
 * the same objects are deleted.
 *
 * <p>Activated by {@code kelta.worker.k8s-provisioner.enabled=true}. Off by
 * default — local dev, tests, and any cluster where the worker has no RBAC
 * keep using {@link LoggingCustomDomainProvisioner}.
 *
 * <p>Both CRDs are managed via {@link GenericKubernetesResource} rather than
 * typed models so the worker doesn't need to vendor cert-manager / Traefik
 * client jars. The resource shapes mirror the manifests already shipped in
 * {@code homelab-argo/emf/ingressroute.yaml}.
 */
@Component
@ConditionalOnProperty(name = "kelta.worker.k8s-provisioner.enabled", havingValue = "true")
public class K8sCustomDomainProvisioner implements CustomDomainProvisioner {

    private static final Logger log = LoggerFactory.getLogger(K8sCustomDomainProvisioner.class);

    private static final ResourceDefinitionContext CERTIFICATE_CRD = new ResourceDefinitionContext.Builder()
            .withGroup("cert-manager.io")
            .withVersion("v1")
            .withKind("Certificate")
            .withPlural("certificates")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext INGRESSROUTE_CRD = new ResourceDefinitionContext.Builder()
            .withGroup("traefik.io")
            .withVersion("v1alpha1")
            .withKind("IngressRoute")
            .withPlural("ingressroutes")
            .withNamespaced(true)
            .build();

    private final KubernetesClient client;
    private final String namespace;
    private final String gatewayServiceName;
    private final int gatewayServicePort;
    private final String clusterIssuerName;

    public K8sCustomDomainProvisioner(
            KubernetesClient client,
            @Value("${kelta.worker.k8s-provisioner.namespace:emf}") String namespace,
            @Value("${kelta.worker.k8s-provisioner.gateway-service:emf-gateway}") String gatewayServiceName,
            @Value("${kelta.worker.k8s-provisioner.gateway-port:80}") int gatewayServicePort,
            @Value("${kelta.worker.k8s-provisioner.cluster-issuer:letsencrypt-http01}") String clusterIssuerName) {
        this.client = client;
        this.namespace = namespace;
        this.gatewayServiceName = gatewayServiceName;
        this.gatewayServicePort = gatewayServicePort;
        this.clusterIssuerName = clusterIssuerName;
        log.info("K8sCustomDomainProvisioner active: namespace={} gatewayService={} issuer={}",
                namespace, gatewayServiceName, clusterIssuerName);
    }

    @Override
    public void onVerified(String domainId, String domain, String tenantId) {
        try {
            applyCertificate(domainId, domain, tenantId);
            applyIngressRoute(domainId, domain, tenantId);
            log.info("Provisioned IngressRoute + Certificate for domain={} tenantId={} (id={})",
                    domain, tenantId, domainId);
        } catch (KubernetesClientException e) {
            // Don't rethrow — the verify endpoint already committed the DB row
            // and the cache has been invalidated. Operators can reconcile by
            // calling verify again once the K8s issue is resolved.
            log.error("Failed to provision K8s resources for domain={} (id={}): {}",
                    domain, domainId, e.getMessage(), e);
        }
    }

    @Override
    public void onRemoved(String domainId, String domain, String tenantId) {
        try {
            client.genericKubernetesResources(INGRESSROUTE_CRD)
                    .inNamespace(namespace)
                    .withName(resourceName(domainId))
                    .delete();
            client.genericKubernetesResources(CERTIFICATE_CRD)
                    .inNamespace(namespace)
                    .withName(resourceName(domainId))
                    .delete();
            log.info("Deleted IngressRoute + Certificate for domain={} (id={})", domain, domainId);
        } catch (KubernetesClientException e) {
            log.error("Failed to delete K8s resources for domain={} (id={}): {}",
                    domain, domainId, e.getMessage(), e);
        }
    }

    private void applyCertificate(String domainId, String domain, String tenantId) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("secretName", tlsSecretName(domainId));
        spec.put("dnsNames", List.of(domain));
        spec.put("issuerRef", Map.of(
                "name", clusterIssuerName,
                "kind", "ClusterIssuer"));

        GenericKubernetesResource cert = new GenericKubernetesResourceBuilder()
                .withApiVersion("cert-manager.io/v1")
                .withKind("Certificate")
                .withNewMetadata()
                .withName(resourceName(domainId))
                .withNamespace(namespace)
                .withLabels(commonLabels(tenantId, domainId))
                .endMetadata()
                .withAdditionalProperties(Map.of("spec", spec))
                .build();

        client.genericKubernetesResources(CERTIFICATE_CRD)
                .inNamespace(namespace)
                .resource(cert)
                .serverSideApply();
    }

    private void applyIngressRoute(String domainId, String domain, String tenantId) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("match", "Host(`" + domain + "`)");
        route.put("kind", "Rule");
        route.put("services", List.of(Map.of(
                "name", gatewayServiceName,
                "port", gatewayServicePort)));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("entryPoints", List.of("websecure"));
        spec.put("routes", List.of(route));
        spec.put("tls", Map.of("secretName", tlsSecretName(domainId)));

        GenericKubernetesResource ir = new GenericKubernetesResourceBuilder()
                .withApiVersion("traefik.io/v1alpha1")
                .withKind("IngressRoute")
                .withNewMetadata()
                .withName(resourceName(domainId))
                .withNamespace(namespace)
                .withLabels(commonLabels(tenantId, domainId))
                .endMetadata()
                .withAdditionalProperties(Map.of("spec", spec))
                .build();

        client.genericKubernetesResources(INGRESSROUTE_CRD)
                .inNamespace(namespace)
                .resource(ir)
                .serverSideApply();
    }

    private static String resourceName(String domainId) {
        // K8s names: lowercase alphanumeric + hyphens, ≤253 chars. UUIDs fit.
        return "tenant-domain-" + domainId;
    }

    private static String tlsSecretName(String domainId) {
        return "tenant-domain-" + domainId + "-tls";
    }

    private static Map<String, String> commonLabels(String tenantId, String domainId) {
        return Map.of(
                "app.kubernetes.io/managed-by", "kelta-worker",
                "kelta.io/tenant-id", tenantId,
                "kelta.io/domain-id", domainId);
    }
}
