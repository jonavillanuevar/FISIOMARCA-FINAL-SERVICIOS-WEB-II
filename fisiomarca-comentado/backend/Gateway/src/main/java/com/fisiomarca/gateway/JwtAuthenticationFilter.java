// ============================================================================
// MÓDULO: API GATEWAY - FILTRO DE SEGURIDAD
// RESPONSABILIDAD: ser la ÚNICA puerta de entrada. Revisa el token JWT de
// cada petición ANTES de reenviarla al microservicio correspondiente.
// OJO: este filtro verifica IDENTIDAD (firma y vencimiento), NO permisos.
// Los roles los revisa cada microservicio.
// ============================================================================
package com.fisiomarca.gateway;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filtro GLOBAL: se aplica a TODAS las rutas que pasan por el Gateway.
 * Deja pasar sin token:
 *   - /auth/login y /auth/register  (para obtener el token)
 *   - /usuarios/registrar           (registro público)
 *   - GET /catalogo/**              (catálogo público de solo lectura)
 *   - /ws/**                        (servicio SOAP)
 *   - peticiones OPTIONS            (preflight de CORS, que hace el navegador)
 */
// @Component: Spring crea UNA instancia de esta clase y la registra como filtro.
// GlobalFilter: indica que el filtro aplica a todas las rutas.
// Ordered: permite definir el orden de ejecución (ver getOrder()).
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Rutas exactas que no exigen token. Se compara la ruta completa.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login", "/auth/register", "/usuarios/registrar");

    // Clave con la que se verifica la firma. Se crea una sola vez al arrancar.
    private final SecretKey signingKey;

    // Constructor: Spring inyecta el valor de jwt.secret (de application.yml o
    // de la variable JWT_SECRET). Es el MISMO secreto que usa AUTH-SERVICE.
    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        // El secreto viene en Base64; lo decodificamos a bytes.
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        // HS256 exige una clave de al menos 256 bits (32 bytes).
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret debe tener al menos 32 bytes para HS256");
        }
        // Creamos la clave criptográfica HMAC-SHA para verificar firmas.
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // filter(): se ejecuta en CADA petición. Decide si la deja pasar o la rechaza.
    // Recibe: exchange (la petición y la respuesta) y chain (el resto del camino).
    // Devuelve: Mono<Void>, un valor reactivo que indica cuándo termina el proceso.
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1) Preflight CORS: el navegador envía OPTIONS antes de la petición real,
        //    sin token. Se deja pasar.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        // 2) Rutas públicas de autenticación: no exigen token.
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }
        // 3) Catálogo público: solo lectura (GET). POST/PUT/DELETE sí exigen token.
        if (HttpMethod.GET.equals(request.getMethod()) && path.startsWith("/catalogo/")) {
            return chain.filter(exchange);
        }
        // 4) Servicio SOAP: se expone sin token.
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        // 5) Desde aquí, la ruta es protegida. Buscamos el encabezado:
        //    Authorization: Bearer <token>
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "Falta el encabezado Authorization: Bearer <token>");
        }

        // 6) Quitamos el prefijo "Bearer " (7 caracteres) y los espacios sobrantes.
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange, "Token vacío");
        }

        try {
            // 7) VALIDACIÓN: parseSignedClaims verifica:
            //    a) que la firma coincida con nuestra clave (no fue alterado),
            //    b) que el token tenga formato correcto,
            //    c) que no haya vencido (campo exp).
            //    Si algo falla, lanza JwtException.
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);

            // 8) Token válido: se deja continuar la petición. El header Authorization
            //    NO se borra, así el microservicio puede volver a validarlo.
            return chain.filter(exchange);
        } catch (JwtException | IllegalArgumentException ex) {
            // 9) Firma inválida, token vencido o mal formado: 401.
            return unauthorized(exchange, "Token inválido o expirado");
        }
    }

    // unauthorized(): arma una respuesta HTTP 401 con cuerpo JSON y la envía.
    // Se usa cuando no sabemos quién es el usuario (falta token o es inválido).
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"success\":false,\"mensaje\":\"" + message + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    // getOrder(): número de orden del filtro. -100 es un valor bajo, así que
    // este filtro se ejecuta ANTES que los demás filtros del Gateway.
    @Override
    public int getOrder() {
        return -100;
    }
}
