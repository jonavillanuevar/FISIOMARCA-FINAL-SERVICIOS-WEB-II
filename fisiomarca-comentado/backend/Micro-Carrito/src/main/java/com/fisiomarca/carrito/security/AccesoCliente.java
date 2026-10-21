// ============================================================================
// MÓDULO: CARRITO - CONTROL DE PROPIEDAD DE DATOS
// RESPONSABILIDAD: impedir que un paciente opere sobre el carrito de otro.
// Regla: el PACIENTE solo accede a sus propios datos; el ADMINISTRADOR a todos.
// ============================================================================
package com.fisiomarca.carrito.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fisiomarca.carrito.utils.AccesoDenegadoException;

// Clase de utilidad con métodos estáticos. "final" y constructor privado evitan
// que alguien cree objetos de ella: solo se usa llamando sus métodos.
// SecurityContextHolder guarda la identidad de la petición actual (la lee del token).
public final class AccesoCliente {
    private AccesoCliente() {}

    // esAdmin(): ¿el usuario de esta petición tiene ROLE_ADMINISTRADOR?
    // Recorre las autoridades (permisos) del usuario autenticado.
    public static boolean esAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
    }

    // clienteIdDelToken(): lee el claim "clienteId" del JWT de la petición.
    // Devuelve null si el token no tiene clienteId (por ejemplo, un admin).
    public static Integer clienteIdDelToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim("clienteId") instanceof Number n)
            return n.intValue();
        return null;
    }

    // verificar(clienteId): ES LA REGLA DE PROPIEDAD.
    //   - Si es admin, pasa.
    //   - Si no, el clienteId del token debe ser igual al clienteId de la ruta/body.
    //   - Si no coincide, lanza AccesoDenegadoException (la petición se rechaza).
    // Cada endpoint del CarritoController llama a este método antes de hacer nada.
    public static void verificar(Integer clienteId) {
        if (esAdmin()) return;
        Integer propio = clienteIdDelToken();
        if (propio == null || !propio.equals(clienteId))
            throw new AccesoDenegadoException("No tienes permiso para acceder a datos de otro paciente");
    }
}
