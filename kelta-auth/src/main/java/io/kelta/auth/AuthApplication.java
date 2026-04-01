package io.kelta.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;

@SpringBootApplication(exclude = {
    // Exclude default OAuth2 client auto-config — we provide a custom
    // ClientRegistrationRepository bean (DynamicClientRegistrationRepository)
    // that loads registrations from the database, not application properties.
    OAuth2ClientAutoConfiguration.class
})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
