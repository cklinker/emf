package io.kelta.auth;

import io.kelta.auth.aot.AuthRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication(exclude = {
    // Exclude default OAuth2 client auto-config — we provide a custom
    // ClientRegistrationRepository bean (DynamicClientRegistrationRepository)
    // that loads registrations from the database, not application properties.
    OAuth2ClientAutoConfiguration.class
})
@ImportRuntimeHints(AuthRuntimeHints.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
