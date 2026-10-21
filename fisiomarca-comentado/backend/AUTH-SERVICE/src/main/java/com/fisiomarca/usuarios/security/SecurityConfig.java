// ============================================================================
// MÓDULO: AUTH - SEGURIDAD DE LAS PROPIAS RUTAS
// RESPONSABILIDAD: decidir qué endpoints de AUTH son públicos y cuáles exigen
// token con un rol concreto. También configura cómo se leen los tokens.
// ============================================================================
package com.fisiomarca.usuarios.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del AUTH-SERVICE.
 *  - Autentica con DaoAuthenticationProvider (usuario + contraseña BCrypt).
 *  - Valida sus propios JWT con el secreto compartido (HS256) para proteger /usuarios/**.
 *  - /auth/login y /auth/register son públicos.
 */
// @Configuration: clase de configuración. Sus métodos @Bean crean objetos que Spring gestiona.
@Configuration
public class SecurityConfig {

    // Dependencias inyectadas: quién carga usuarios y cómo se comparan contraseñas.
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    // authenticationProvider(): le dice a Spring CÓMO verificar un login:
    // 1) busca al usuario con userDetailsService (por correo),
    // 2) compara la contraseña enviada con el hash guardado usando passwordEncoder.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // jwtDecoder(): crea el validador de tokens que usa el "resource server"
    // de este mismo servicio (protege /usuarios/**). Usa la misma clave que JwtService.
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret debe tener al menos 32 bytes para HS256");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        // macAlgorithm HS256: solo acepta tokens firmados con HMAC-SHA256.
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // jwtAuthenticationConverter(): traduce los claims del token a permisos de Spring.
    // Toma el claim "roles" y le agrega el prefijo ROLE_:
    //   ["ADMINISTRADOR"] -> ROLE_ADMINISTRADOR
    // Así, hasRole("ADMINISTRADOR") funciona en las reglas de abajo.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // securityFilterChain(): define las reglas de acceso de cada ruta.
    // Se evalúan de arriba hacia abajo; la primera que coincide manda.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            // CSRF se desactiva porque la API no usa cookies de sesión; el token va en el header.
            .csrf(csrf -> csrf.disable())
            // Sin formulario HTML ni login básico: la API solo habla JSON con tokens.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // STATELESS: el servidor NO guarda sesiones. Cada petición se valida sola.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Públicos: registro, login y errores.
                .requestMatchers("/auth/register", "/auth/login", "/error").permitAll()
                // Registro público de usuarios.
                .requestMatchers(HttpMethod.POST, "/usuarios/registrar").permitAll()
                // Pública: otros servicios la usan para verificar si un cliente existe.
                .requestMatchers(HttpMethod.GET, "/usuarios/clientes/existe/**").permitAll()
                // Solo ADMINISTRADOR puede listar usuarios o buscar clientes.
                .requestMatchers(HttpMethod.GET, "/usuarios/lista", "/usuarios/clientes/buscar/**").hasRole("ADMINISTRADOR")
                // Cualquier otra ruta exige estar autenticado (token válido).
                .anyRequest().authenticated())
            // Activa la validación de JWT en las rutas protegidas.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
