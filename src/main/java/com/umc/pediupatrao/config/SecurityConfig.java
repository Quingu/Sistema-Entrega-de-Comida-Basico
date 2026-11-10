package com.umc.pediupatrao.config;

import java.io.IOException;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
                .authenticationProvider(authenticationProvider())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**", "/error").permitAll()

                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/dashboard", "/api/dashboard/**").hasAnyRole("ADMIN", "GERENTE")

                        .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/produtos/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelar").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/status", "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole("GERENTE")

                        .requestMatchers(HttpMethod.POST, "/api/clientes/**").hasAnyRole("ATENDENTE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasAnyRole("ATENDENTE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/clientes/**").hasAnyRole("ATENDENTE", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole("ATENDENTE", "GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/pedidos/novo").hasAnyRole("ATENDENTE", "GERENTE", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(e -> e.accessDeniedHandler(this::tratarAcessoNegado));

        return http.build();
    }

    /**
     * Requisições de API recebem 403 em JSON; páginas continuam indo para a tela de erro.
     * Isso evita que o dashboard receba HTML no lugar do contrato esperado.
     */
    private void tratarAcessoNegado(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) throws IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"erro\":\"Acesso negado.\","
                    + "\"detalhe\":\"O perfil autenticado não tem permissão para acessar este recurso.\"}");
            return;
        }
        // Encaminha para a página de erro com status 403 (o template mostra "Acesso negado").
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> response.sendRedirect("/");
    }
}

