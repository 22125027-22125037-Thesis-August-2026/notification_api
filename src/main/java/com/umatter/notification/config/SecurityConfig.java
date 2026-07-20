package com.umatter.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String publicKeyBase64;
    private final String jwksUri;
    private final String issuer;
    private final String audience;

    public SecurityConfig(
            @Value("${notification.jwt.public-key:}") String publicKeyBase64,
            @Value("${notification.jwt.jwks-uri:}") String jwksUri,
            @Value("${notification.jwt.issuer}") String issuer,
            @Value("${notification.jwt.audience}") String audience) {
        this.publicKeyBase64 = publicKeyBase64;
        this.jwksUri = jwksUri;
        this.issuer = issuer;
        this.audience = audience;
    }

    /**
     * Prefers Auth's published key set over a statically configured public key.
     *
     * <p>With a JWKS URI, Nimbus selects the verification key by the token's {@code kid} and
     * refetches when it sees an unknown one, so a key rotation at Auth needs no redeploy here.
     * The fetch is lazy — the first token triggers it — so Auth is not a startup dependency.
     * The static-key branch remains for a deployment that has no JWKS endpoint to point at.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;

        if (jwksUri != null && !jwksUri.isBlank()) {
            if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                log.warn("Both notification.jwt.jwks-uri and notification.jwt.public-key are set; "
                        + "the static key is ignored in favour of the published key set");
            }
            decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
            log.info("JWT verification keys will be resolved from JWKS at {}", jwksUri);
        } else if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
            decoder = NimbusJwtDecoder.withPublicKey(parseRsaPublicKey(publicKeyBase64)).build();
            log.info("JWT verification configured with a static RSA public key");
        } else {
            throw new IllegalStateException(
                    "No JWT verification key configured — set notification.jwt.jwks-uri (JWT_JWKS_URI) "
                            + "or notification.jwt.public-key (JWT_PUBLIC_KEY)");
        }

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> tokenAud = jwt.getAudience();
            if (tokenAud != null && tokenAud.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience not present", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter converter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    private static RSAPublicKey parseRsaPublicKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64.replaceAll("\\s+", ""));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse JWT_PUBLIC_KEY as a Base64-encoded RSA public key (X.509)", e);
        }
    }
}
