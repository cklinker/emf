package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import io.kelta.auth.controller.ForcePasswordChangeController;
import io.kelta.auth.controller.MfaController;
import io.kelta.auth.federation.DynamicClientRegistrationRepository;
import io.kelta.auth.federation.FederatedLoginSuccessHandler;
import io.kelta.auth.federation.FederatedUserMapper;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.OidcDiscoveryService;
import io.kelta.auth.service.PasswordPolicyService;
import io.kelta.auth.service.TotpService;
import io.kelta.auth.service.WorkerClient;
import io.kelta.crypto.EncryptionService;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
public class AuthorizationServerConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Configure OIDC on the configurer BEFORE capturing endpoint matchers,
        // so that OIDC discovery endpoints (/.well-known/openid-configuration)
        // are included in the security matcher for this chain.
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        authorizationServerConfigurer
                .oidc(Customizer.withDefaults())
                .authorizationEndpoint(endpoint -> endpoint
                        .consentPage("/oauth2/consent")
                        .authenticationProviders(configureRedirectUriValidator()));

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
            .securityMatcher(endpointsMatcher)
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
            .cors(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                ))
            .with(authorizationServerConfigurer, Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            FederatedUserMapper federatedUserMapper,
            WorkerClient workerClient,
            ClientRegistrationRepository clientRegistrationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TotpService totpService,
            PasswordPolicyService passwordPolicyService) throws Exception {
        org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
                savedRequestHandler = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();

        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/change-password",
                                "/mfa-challenge",
                                "/mfa-challenge/recovery",
                                "/mfa-setup",
                                "/mfa-setup/complete",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/auth/session",
                                "/auth/direct-login",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            // Check MFA requirements after successful password authentication
                            if (authentication.getPrincipal() instanceof KeltaUserDetails userDetails
                                    && totpService != null) {
                                String userId = userDetails.getId();
                                String tenantId = userDetails.getTenantId();
                                String email = userDetails.getEmail();

                                boolean mfaEnrolled = totpService.isEnrolled(userId);
                                boolean mfaRequired = isMfaRequiredForTenant(passwordPolicyService, tenantId);

                                if (mfaEnrolled) {
                                    // User has MFA — redirect to challenge
                                    HttpSession session = request.getSession();
                                    session.setAttribute(MfaController.SESSION_MFA_PENDING, true);
                                    session.setAttribute(MfaController.SESSION_MFA_USER_ID, userId);
                                    session.setAttribute(MfaController.SESSION_MFA_TENANT_ID, tenantId);
                                    session.setAttribute(MfaController.SESSION_MFA_EMAIL, email);
                                    SecurityContextHolder.clearContext();
                                    response.sendRedirect("/mfa-challenge");
                                    return;
                                }

                                if (mfaRequired && !mfaEnrolled) {
                                    // Tenant requires MFA but user not enrolled — redirect to setup
                                    HttpSession session = request.getSession();
                                    session.setAttribute(MfaController.SESSION_MFA_PENDING, true);
                                    session.setAttribute(MfaController.SESSION_MFA_SETUP_REQUIRED, true);
                                    session.setAttribute(MfaController.SESSION_MFA_USER_ID, userId);
                                    session.setAttribute(MfaController.SESSION_MFA_TENANT_ID, tenantId);
                                    session.setAttribute(MfaController.SESSION_MFA_EMAIL, email);
                                    SecurityContextHolder.clearContext();
                                    response.sendRedirect("/mfa-setup");
                                    return;
                                }
                            }
                            // No MFA required — replay the original saved request (e.g. /oauth2/authorize?...)
                            // so the authorization code flow completes and redirects back to the UI.
                            savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
                        })
                        .failureHandler((request, response, exception) -> {
                            if (exception.getCause() instanceof CredentialsExpiredException
                                    || exception instanceof CredentialsExpiredException) {
                                String email = request.getParameter("username");
                                request.getSession().setAttribute(
                                        ForcePasswordChangeController.SESSION_ATTR_EMAIL, email);
                                response.sendRedirect("/change-password");
                            } else {
                                response.sendRedirect("/login?error");
                            }
                        })
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/auth/session", "/auth/direct-login")
                );

        // Enable federated OAuth2 login only when federation is configured
        if (federatedUserMapper != null
                && clientRegistrationRepository instanceof DynamicClientRegistrationRepository) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .successHandler(new FederatedLoginSuccessHandler(
                            federatedUserMapper, workerClient))
            );
        }

        return http.build();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(EncryptionService.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            WorkerClient workerClient,
            OidcDiscoveryService discoveryService,
            EncryptionService encryptionService) {
        return new DynamicClientRegistrationRepository(workerClient, discoveryService, encryptionService);
    }

    /**
     * Fallback client registration repository when encryption is not configured.
     * Federation is disabled — any attempt to use an OIDC provider will fail fast
     * with a clear error message rather than silently operating without encryption.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository noOpClientRegistrationRepository() {
        log.warn("EncryptionService not configured (KELTA_ENCRYPTION_KEY not set). "
                + "OAuth2 federation is DISABLED. Set KELTA_ENCRYPTION_KEY to enable OIDC provider federation.");
        return registrationId -> {
            throw new IllegalStateException(
                    "OAuth2 federation is disabled because KELTA_ENCRYPTION_KEY is not configured. "
                    + "Set the KELTA_ENCRYPTION_KEY environment variable to enable OIDC provider federation.");
        };
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService service =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);

        // Register KeltaUserDetails in the Jackson allowlist so the JDBC service
        // can serialize/deserialize the principal stored in authorization records.
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();
        objectMapper.registerModules(org.springframework.security.jackson2.SecurityJackson2Modules.getModules(classLoader));
        objectMapper.registerModule(new org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(io.kelta.auth.model.KeltaUserDetails.class, io.kelta.auth.model.KeltaUserDetailsMixin.class);
        rowMapper.setObjectMapper(objectMapper);
        service.setAuthorizationRowMapper(rowMapper);

        return service;
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuerUri())
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(AuthProperties properties) throws ParseException {
        String jwkSetJson = properties.getJwkSet();
        if (jwkSetJson != null && !jwkSetJson.isBlank()) {
            JWKSet jwkSet = JWKSet.parse(jwkSetJson);
            return new ImmutableJWKSet<>(jwkSet);
        }
        // Generate a key pair for development/testing
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Replaces the default redirect_uri validator on the authorization code request
     * authentication provider with our multi-tenant-aware validator.
     */
    private Consumer<List<AuthenticationProvider>> configureRedirectUriValidator() {
        return authenticationProviders -> {
            for (AuthenticationProvider provider : authenticationProviders) {
                if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider authCodeProvider) {
                    authCodeProvider.setAuthenticationValidator(new PlatformRedirectUriValidator());
                }
            }
        };
    }

    private boolean isMfaRequiredForTenant(PasswordPolicyService policyService, String tenantId) {
        return policyService.isMfaRequired(tenantId);
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
