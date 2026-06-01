package io.kelta.worker.service;

/**
 * Provisions / deprovisions the K8s objects required to serve traffic for a
 * verified tenant custom domain. Today the platform expects each customer
 * domain to have:
 * <ul>
 *   <li>a {@code traefik.io/v1alpha1 IngressRoute} routing
 *       {@code Host(`{domain}`)} to the {@code emf-gateway} Service, and</li>
 *   <li>a {@code cert-manager.io/v1 Certificate} (HTTP-01 ClusterIssuer
 *       {@code letsencrypt-http01}) so TLS is auto-issued / renewed.</li>
 * </ul>
 *
 * <p>This interface lets the verification flow stay declarative — call
 * {@link #onVerified} after the TXT record is confirmed, call {@link #onRemoved}
 * after the row is deleted — while keeping the actual K8s client behind an
 * impl. The default impl simply logs (safe for local dev / native image
 * builds without the K8s client on the classpath); a Fabric8-backed impl
 * is wired up in cluster-mode deployments.
 */
public interface CustomDomainProvisioner {

    /** Called after the domain row has been marked {@code verified=true}. */
    void onVerified(String domainId, String domain, String tenantId);

    /** Called after the domain row has been deleted or downgraded. */
    void onRemoved(String domainId, String domain, String tenantId);
}
