// ============================================================================
// MÓDULO: CARRITO - LLAMADA INTERNA A AUTH (Feign)
// RESPONSABILIDAD: consultar a AUTH-SERVICE si un cliente existe.
// Feign genera la implementación automáticamente a partir de esta interfaz.
// ============================================================================
package com.fisiomarca.carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.fisiomarca.carrito.utils.ApiResponse;

// @FeignClient(name = "AUTH-SERVICE"): el NOMBRE del servicio, no una IP.
// Feign pregunta a Eureka dónde está AUTH-SERVICE y balancea la llamada.
// Esta llamada NO pasa por el Gateway y NO reenvía el token (limitación conocida).
@FeignClient(name = "AUTH-SERVICE")
public interface UsuariosFeign {
    // existeCliente(id): hace GET /usuarios/clientes/existe/{id} en AUTH.
    // Devuelve ApiResponse<Boolean> con true o false.
    @GetMapping("/usuarios/clientes/existe/{id}")
    ApiResponse<Boolean> existeCliente(@PathVariable("id") Integer id);
}
