package io.github.adalberto.bedrockmcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defence in depth:
 * <ol>
 *   <li>AWS WAF + API Gateway Cognito authorizer reject anonymous/abusive traffic at the edge.</li>
 *   <li>This app validates the same JWT again ({@code app.security.enabled=true} in QA/PROD), because the ALB
 *       is reachable from inside the VPC and the MCP endpoint must never be anonymous.</li>
 * </ol>
 * Local development runs with security disabled for convenience.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props) throws Exception {
        boolean enabled = props.security().enabled();
        http
            .csrf(csrf -> csrf.disable()) // stateless JSON API, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                if (enabled) {
                    auth.anyRequest().authenticated();
                }
                else {
                    auth.anyRequest().permitAll();
                }
            });
        if (enabled) {
            http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
