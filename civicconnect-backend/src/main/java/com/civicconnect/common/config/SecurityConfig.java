package com.civicconnect.common.config;

import com.civicconnect.common.error.SecurityProblemHandler;
import com.civicconnect.common.security.JwtService;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import com.civicconnect.common.error.FilterErrorHandler;
import com.civicconnect.common.error.ProblemWriter;
import com.civicconnect.common.ratelimit.ApiRateLimitFilter;
import com.civicconnect.common.ratelimit.TokenBucketLimiter;
import com.civicconnect.common.security.AccountStatusLookup;
import com.civicconnect.common.security.ActiveAccountJwtConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems,
                                                   AccountStatusLookup accounts, ProblemWriter problemWriter,
                                                   @Qualifier("apiRequestLimiter") TokenBucketLimiter apiLimiter,
                                                   @Qualifier("ipRequestLimiter") TokenBucketLimiter ipLimiter,
                                                   @Qualifier("authIpLimiter") TokenBucketLimiter authIpLimiter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())              // no cookies are used, so CSRF does not apply
                .cors(Customizer.withDefaults())           // uses the CorsConfigurationSource in WebConfig
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // 401 and 403 from the filters use the same error body as the controllers (error model)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ActiveAccountJwtConverter(accounts)))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                // Errors thrown in the filters (e.g. the database is down during the account check) -> PED body
                .addFilterBefore(new FilterErrorHandler(problemWriter), BearerTokenAuthenticationFilter.class)
                // B4: per-IP and per-account rate limits, after the token is verified so the account is known
                .addFilterAfter(new ApiRateLimitFilter(apiLimiter, ipLimiter, authIpLimiter, problemWriter),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }


    @Bean
    public SecretKey jwtSigningKey(@Value("${civicconnect.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        // Checks expiry (the default) and that the token was issued by this application ("iss": "civicconnect")
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
