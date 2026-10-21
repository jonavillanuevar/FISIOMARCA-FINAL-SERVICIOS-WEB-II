// ============================================================================
// MÓDULO: RESERVAS - RESOURCE SERVER + REGLAS POR ROL
// RESPONSABILIDAD: validar el JWT y decidir qué ROL puede usar cada ruta.
// Aquí SÍ se revisan roles (a diferencia del Gateway, que solo valida firma).
// ============================================================================
package com.fisiomarca.reservas.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource Server del MICRO-RESERVAS.
 * Valida el JWT con el secreto compartido (HS256) que emite AUTH-SERVICE.
 */
@Configuration
public class SecurityConfig {

    // jwtDecoder(): verifica la firma del token con la clave compartida con AUTH.
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret debe tener al menos 32 bytes para HS256");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // jwtAuthenticationConverter(): convierte el claim "roles" en ROLE_... para
    // que hasRole("ADMINISTRADOR") funcione.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // securityFilterChain(): reglas por ruta. Se evalúan EN ORDEN; la primera que coincide manda.
    //   hasRole("ADMINISTRADOR")  -> token válido Y rol administrador, si no: 403
    //   authenticated()           -> cualquier usuario con token válido
    //   anyRequest().authenticated() -> lo demás requiere token
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // SOLO ADMIN: listar todas las reservas y ver el dashboard.
                .requestMatchers(HttpMethod.GET, "/reservas/lista", "/reservas/dashboard").hasRole("ADMINISTRADOR")
                // SOLO ADMIN: reserva directa sin pago (cita presencial en recepción).
                // El paciente NO usa esta ruta: usa /checkout y luego paga.
                .requestMatchers(HttpMethod.POST, "/reservas/registrar").hasRole("ADMINISTRADOR")
                // SOLO ADMIN: marcar atención, registrar llegada y reasignar fisio/sala.
                .requestMatchers(HttpMethod.PUT, "/reservas/atender/**", "/reservas/checkin/**", "/reservas/reasignar/**").hasRole("ADMINISTRADOR")
                // Lectura para cualquier usuario autenticado (el historial valida que sea el propio paciente).
                .requestMatchers(HttpMethod.GET, "/reservas/fisioterapeutas/**", "/reservas/salas/**", "/reservas/historial/**").authenticated()
                // Gestión de fisioterapeutas y salas: solo ADMIN (las reglas GET de arriba ya dieron acceso
                // de lectura a cualquier autenticado; estas cubren POST/PUT).
                .requestMatchers("/reservas/fisioterapeutas/**", "/reservas/salas/**", "/reservas/historial/**").hasRole("ADMINISTRADOR")
                // Todo lo demás: solo con token válido (la propiedad del dato se revisa en cada método).
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
