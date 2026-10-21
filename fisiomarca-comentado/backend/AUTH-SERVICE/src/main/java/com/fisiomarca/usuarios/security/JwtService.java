// ============================================================================
// MÓDULO: AUTH - GENERACIÓN DEL TOKEN JWT
// RESPONSABILIDAD: crear el "carnet" firmado que el frontend usará en cada
// petición. Solo AUTH firma tokens, porque solo AUTH conoce la clave secreta.
// ============================================================================
package com.fisiomarca.usuarios.security;

import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.fisiomarca.usuarios.entity.Usuario;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Genera el JWT que el frontend usará como "Bearer <token>".
 * Claims (datos dentro del token) que los microservicios leen:
 *   - roles      -> lista de roles, se convierte en ROLE_... (permisos)
 *   - usuarioId  -> id del usuario
 *   - nombre     -> nombre completo
 *   - clienteId  -> id del cliente asociado (solo si el usuario es paciente)
 */
// @Service: Spring crea una instancia y la inyecta donde se necesite.
@Service
public class JwtService {

    // Contiene el secreto y la expiración leídos de application.yml (prefijo jwt).
    private final JwtProperties properties;

    // Constructor: Spring inyecta JwtProperties automáticamente.
    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    // getSigningKey(): convierte el secreto (texto en Base64) en una clave
    // criptográfica HMAC-SHA256 (HS256). Esta clave firma y verifica tokens.
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // generateToken(): recibe un Usuario ya autenticado y devuelve el JWT como texto.
    public String generateToken(Usuario usuario) {
        // Momento actual en milisegundos. Sirve para fechas de emisión y vencimiento.
        long now = System.currentTimeMillis();

        var builder = Jwts.builder()
                // subject: el "dueño" del token. Usamos el correo como identificador.
                .subject(usuario.getEmail())
                // roles: lista con el rol del usuario, p. ej. ["ADMINISTRADOR"].
                .claim("roles", List.of(usuario.getRol()))
                .claim("usuarioId", usuario.getId())
                .claim("nombre", usuario.getNombreCompleto())
                // iat (issued at): cuándo se emitió.
                .issuedAt(new Date(now))
                // exp (expiration): cuándo vence. Por defecto, 1 hora después.
                .expiration(new Date(now + properties.getExpiration()));

        // Solo los pacientes tienen cliente asociado; si existe, lo agregamos.
        if (usuario.getCliente() != null) {
            builder.claim("clienteId", usuario.getCliente().getId());
        }

        // signWith: firma el token con la clave secreta. Cualquier cambio en
        // el contenido invalidará la firma. compact(): arma el texto final
        // con el formato header.payload.firma.
        return builder.signWith(getSigningKey()).compact();
    }

    // getExpiration(): devuelve la duración del token en milisegundos.
    // AuthController la envía al frontend como "expiraEn".
    public long getExpiration() {
        return properties.getExpiration();
    }
}
