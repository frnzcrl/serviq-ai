package com.serviq.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository contextRepository) throws Exception {
        return http
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .securityContext(context -> context.securityContextRepository(contextRepository))
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/auth/**").authenticated()
                // Until technician ownership checks exist, only office staff can access customer data.
                .requestMatchers("/api/v1/customers/**", "/api/v1/service-requests/**",
                    "/api/v1/technicians/**", "/api/v1/availability/**",
                    "/api/v1/appointments/**", "/api/v1/work-orders/**")
                    .hasAnyRole("ADMIN", "DISPATCHER")
                .requestMatchers(HttpMethod.GET, "/api/v1/operations/**")
                    .hasAnyRole("ADMIN", "DISPATCHER", "VIEWER")
                .requestMatchers("/api/v1/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value())))
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .build();
    }
}
