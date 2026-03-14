package io.kelta.gateway.authz.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CerbosConfig {

    private static final Logger log = LoggerFactory.getLogger(CerbosConfig.class);

    @Value("${kelta.gateway.cerbos.host:cerbos.emf.svc.cluster.local}")
    private String host;

    @Value("${kelta.gateway.cerbos.grpc-port:3593}")
    private int grpcPort;

    @Bean
    public CerbosBlockingClient cerbosBlockingClient() throws CerbosClientBuilder.InvalidClientConfigurationException {
        String target = host + ":" + grpcPort;
        log.info("Connecting to Cerbos at {} (gRPC, plaintext)", target);
        return new CerbosClientBuilder(target)
                .withPlaintext()
                .buildBlockingClient();
    }
}
