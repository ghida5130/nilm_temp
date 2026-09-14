package com.nilm.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.common.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.http.MediaType;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * app.security.enabled 값으로 보안을 켜고 끈다.
 * - false(기본, local): 모든 요청 허용 — Keycloak 없이 실행 가능
 * - true(prod 예정): JWT 검증 활성화 — JWT_ISSUER_URI 환경변수 필요
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    public SecurityFilterChain securedFilterChain(HttpSecurity http,
            AuthenticationEntryPoint apiAuthenticationEntryPoint,
            AccessDeniedHandler apiAccessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/monitoring/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                        .authenticationEntryPoint(apiAuthenticationEntryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler));
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, exception) -> writeSecurityError(mapper, response,
                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.", request.getRequestURI());
    }

    @Bean
    public AccessDeniedHandler apiAccessDeniedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> writeSecurityError(mapper, response,
                HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "요청을 수행할 권한이 없습니다.", request.getRequestURI());
    }

    private static void writeSecurityError(ObjectMapper mapper, HttpServletResponse response,
            int status, String code, String message, String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, code, message, path, List.of()));
    }

    /**
     * Keycloak 토큰의 realm_access.roles를 Spring의 ROLE_* 권한으로 변환.
     * hasRole("ADMIN")은 내부적으로 ROLE_ADMIN을 찾으므로 접두사를 붙여준다.
     */
    private JwtAuthenticationConverter keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(Object::toString)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}
