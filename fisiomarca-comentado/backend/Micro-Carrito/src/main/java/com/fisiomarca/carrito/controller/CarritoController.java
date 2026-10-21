// ============================================================================
// MÓDULO: CARRITO - ENDPOINTS REST
// RESPONSABILIDAD: recibir la petición, verificar que el paciente sea dueño
// del carrito (AccesoCliente), delegar la lógica a CarritoServices y devolver
// el carrito completo ya calculado.
// Patrón de TODOS los endpoints:  1) verificar acceso  2) ejecutar  3) responder
// Ruta base: /carrito  (exige token; el Gateway ya validó la firma)
// ============================================================================
package com.fisiomarca.carrito.controller;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.fisiomarca.carrito.client.CatalogoFeign;
import com.fisiomarca.carrito.dto.*;
import com.fisiomarca.carrito.entity.*;
import com.fisiomarca.carrito.security.AccesoCliente;
import com.fisiomarca.carrito.services.CarritoServices;
import com.fisiomarca.carrito.utils.ApiResponse;
import jakarta.validation.Valid;

// @RestController: los métodos devuelven JSON.
// @RequestMapping("/carrito"): prefijo de todas las rutas de esta clase.
@RestController
@RequestMapping("/carrito")
public class CarritoController {
    // Dependencias inyectadas por el constructor.
    private final CarritoServices services;   // lógica y acceso a la base de datos
    private final CatalogoFeign catalogoFeign; // consulta de nombre/imagen/duración del servicio

    public CarritoController(CarritoServices services, CatalogoFeign catalogoFeign) {
        this.services = services;
        this.catalogoFeign = catalogoFeign;
    }

    // convertir(clienteId): arma la respuesta COMPLETA del carrito.
    // Pasos:
    //   1) busca (o crea) el carrito del cliente,
    //   2) por cada ítem, consulta a Catálogo el nombre, imagen y duración,
    //   3) calcula el total en dinero y la duración total en minutos,
    //   4) devuelve un CarritoDTO listo para el frontend.
    // Por eso cada respuesta trae datos "frescos" de Catálogo.
    private CarritoDTO convertir(Integer clienteId) throws Exception {
        Carrito carrito = services.obtenerPorCliente(clienteId);
        List<CarritoItemDTO> itemsDTO = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;   // acumulador de dinero (BigDecimal evita errores de decimales)
        int duracion = 0;                     // acumulador de minutos

        for (CarritoItem item : services.listarItems(clienteId)) {
            // Copiamos los datos del ítem guardado en la base del carrito.
            CarritoItemDTO dto = new CarritoItemDTO();
            dto.setId(item.getId());
            dto.setServicioId(item.getServicioId());
            dto.setCantidad(item.getCantidad());
            dto.setPrecioUnitario(item.getPrecioUnitario());
            dto.setSubtotal(item.getSubtotal());

            // Llamada Feign a MICRO-CATALOGO para enriquecer el ítem con su nombre,
            // imagen y duración. Si no hay respuesta, el ítem se muestra sin esos datos.
            ApiResponse<ServicioFeignDTO> servicio = catalogoFeign.buscarServicio(item.getServicioId());
            if (servicio != null && servicio.getData() != null) {
                dto.setServicioNombre(servicio.getData().getNombre());
                dto.setImagenUrl(servicio.getData().getImagenUrl());
                dto.setDuracionMinutos(servicio.getData().getDuracionMinutos());
                // Duración del ítem = minutos del servicio x cantidad.
                int minutos = servicio.getData().getDuracionMinutos() == null ? 0 : servicio.getData().getDuracionMinutos();
                duracion += minutos * item.getCantidad();
            }
            itemsDTO.add(dto);
            total = total.add(item.getSubtotal());
        }
        return new CarritoDTO(carrito.getId(), carrito.getClienteId(), carrito.getFechaCreacion(), itemsDTO, total, duracion);
    }

    // ver(clienteId): GET /carrito/{clienteId}
    // Devuelve el carrito del paciente indicado.
    @GetMapping("/{clienteId}")
    public ResponseEntity<ApiResponse<?>> ver(@PathVariable Integer clienteId) throws Exception {
        AccesoCliente.verificar(clienteId);   // 1) ¿es su carrito o es admin?
        return new ResponseEntity<>(new ApiResponse<>(true, "Carrito del cliente", convertir(clienteId)), HttpStatus.OK);
    }

    // agregar(dto): POST /carrito/items
    // Recibe en el body: clienteId, servicioId y cantidad.
    // @Valid valida el DTO antes de entrar al método.
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<?>> agregar(@Valid @RequestBody AgregarItemDTO dto) throws Exception {
        // El clienteId viene en el BODY, así que verificamos ese valor, no uno de la URL.
        AccesoCliente.verificar(dto.getClienteId());
        // CarritoServices valida el servicio con Catálogo, el tope de 4 sesiones y guarda el ítem.
        services.agregar(dto);
        return new ResponseEntity<>(new ApiResponse<>(true, "Servicio agregado al carrito", convertir(dto.getClienteId())), HttpStatus.CREATED);
    }

    // cantidad(): PUT /carrito/{clienteId}/items/{itemId}?cantidad=N
    // La nueva cantidad llega como parámetro de URL (@RequestParam).
    @PutMapping("/{clienteId}/items/{itemId}")
    public ResponseEntity<ApiResponse<?>> cantidad(@PathVariable Integer clienteId, @PathVariable Integer itemId,
            @RequestParam Integer cantidad) throws Exception {
        AccesoCliente.verificar(clienteId);
        services.cambiarCantidad(clienteId, itemId, cantidad);
        return new ResponseEntity<>(new ApiResponse<>(true, "Cantidad actualizada", convertir(clienteId)), HttpStatus.OK);
    }

    // eliminar(): DELETE /carrito/{clienteId}/items/{itemId}
    // Quita un ítem del carrito.
    @DeleteMapping("/{clienteId}/items/{itemId}")
    public ResponseEntity<ApiResponse<?>> eliminar(@PathVariable Integer clienteId, @PathVariable Integer itemId) throws Exception {
        AccesoCliente.verificar(clienteId);
        services.eliminarItem(clienteId, itemId);
        return new ResponseEntity<>(new ApiResponse<>(true, "Item eliminado", convertir(clienteId)), HttpStatus.OK);
    }

    // vaciar(): DELETE /carrito/{clienteId}/vaciar
    // Elimina todos los ítems del carrito.
    @DeleteMapping("/{clienteId}/vaciar")
    public ResponseEntity<ApiResponse<?>> vaciar(@PathVariable Integer clienteId) throws Exception {
        AccesoCliente.verificar(clienteId);
        services.vaciar(clienteId);
        return new ResponseEntity<>(new ApiResponse<>(true, "Carrito vaciado", convertir(clienteId)), HttpStatus.OK);
    }

}
