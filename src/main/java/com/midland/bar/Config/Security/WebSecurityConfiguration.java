package com.midland.bar.Config.Security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class WebSecurityConfiguration {
    @Autowired
    private JwtAuthenticationFilter authenticationFilter;

    /** See app.cors.allowed-origin - the saloon and the bar serve on
     *  different ports, so this cannot be a constant. A comma-separated
     *  list is accepted: the own domain and the Railway address can both
     *  be served while people move over. */
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origin:http://localhost:4300}")
    private String allowedOrigin;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setExposedHeaders(List.of("Authorization"));
        corsConfiguration.setAllowedOrigins(java.util.Arrays.stream(allowedOrigin.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        return httpSecurity
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource())
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/authentication/**").permitAll()
                        // The closing dispatch of a live stream already authorised when it opened.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        // Snippe posts these server-to-server with no JWT - trust is
                        // established via HMAC signature verification instead (see
                        // SnippeWebhookController / SnippeClient.verifyWebhookSignature).
                        .requestMatchers("/setting/webhooks/**").permitAll()
                        // The container forwards an unhandled exception here with no
                        // JWT (the filter skips error dispatches). Locked, every 500
                        // reached the browser as a 401 "session expired" and logged
                        // the user out.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )

                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                )

                .exceptionHandling(ex -> ex

                        // 403 - User is authenticated but has no permission
                        .accessDeniedHandler((request, response, accessDeniedException) -> {

                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            response.getWriter().write("""
                                        {
                                            "status": 403,
                                            "message": "Access denied. You do not have permission to access this resource."
                                        }
                                    """);
                        })

                        // 401 - User is not authenticated / token expired
                        .authenticationEntryPoint((request, response, authException) -> {

                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            response.getWriter().write("""
                                        {
                                            "status": 401,
                                            "message": "Your session has expired. Please log in again."
                                        }
                                    """);
                        })
                )

                .addFilterBefore(
                        authenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }
}