// ============================================================================
// MÓDULO: CARRITO - LLAMADA INTERNA A CATÁLOGO (Feign)
// RESPONSABILIDAD: obtener el servicio (nombre, precio, duración) desde Catálogo.
// Así el precio lo define el Catálogo y NO el cliente que envía la petición.
// ============================================================================
package com.fisiomarca.carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import com.fisiomarca.carrito.dto.ServicioFeignDTO;
import com.fisiomarca.carrito.utils.ApiResponse;

// name = "MICRO-CATALOGO": nombre registrado en Eureka.
@FeignClient(name = "MICRO-CATALOGO")
public interface CatalogoFeign {
    // buscarServicio(id): GET /catalogo/servicios/buscar/{id} en Catálogo.
    // Devuelve el servicio con su precio actual y si está activo.
    @GetMapping("/catalogo/servicios/buscar/{id}")
    ApiResponse<ServicioFeignDTO> buscarServicio(@PathVariable("id") Integer id);
}
