// ============================================================================
// MÓDULO: CARRITO - RESOURCE SERVER (validación del JWT)
// RESPONSABILIDAD: validar el token que llega en cada petición del carrito.
// El Carrito NO emite tokens; solo los VALIDA con la misma clave que AUTH.
// ============================================================================
package com.fisiomarca.carrito.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource Server del MICRO-CARRITO.
 * Valida el JWT con el secreto compartido (HS256) que emite AUTH-SERVICE.
 */
@Configuration
public class SecurityConfig {

    // jwtDecoder(): construye el validador de firmas. Recibe el secreto desde
    // jwt.secret (application.yml o variable JWT_SECRET) y lo usa para verificar
    // que cada token fue firmado por AUTH con esa misma clave.
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret debe tener al menos 32 bytes para HS256");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // jwtAuthenticationConverter(): convierte el claim "roles" en permisos ROLE_...
    // Así AccesoCliente.esAdmin() puede reconocer a un ADMINISTRADOR.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // securityFilterChain(): TODAS las rutas del carrito exigen un token válido.
    // anyRequest().authenticated() = "cualquier petición sin token válido se rechaza con 401".
    // La regla de "solo el dueño" NO está aquí: está en AccesoCliente.verificar(),
    // dentro de cada endpoint, porque necesita comparar el clienteId concreto.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
