// ============================================================================
// MÓDULO: AUTH - ENDPOINTS PÚBLICOS DE AUTENTICACIÓN
// RESPONSABILIDAD: recibir credenciales o datos de registro y responder.
// Rutas base: /auth  (ambas son públicas; el Gateway tampoco pide token).
// ============================================================================
package com.fisiomarca.usuarios.controller;

import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.fisiomarca.usuarios.dto.*;
import com.fisiomarca.usuarios.entity.Usuario;
import com.fisiomarca.usuarios.security.JwtService;
import com.fisiomarca.usuarios.services.UsuarioServices;
import com.fisiomarca.usuarios.utils.ApiResponse;

import jakarta.validation.Valid;

/**
 * Endpoints de autenticación (públicos).
 *  - POST /auth/register -> crea un usuario CLIENTE (paciente)
 *  - POST /auth/login    -> valida credenciales y emite el JWT
 */
// @RestController: cada método devuelve datos JSON, no vistas HTML.
// @RequestMapping("/auth"): prefijo de todas las rutas de esta clase.
@RestController
@RequestMapping("/auth")
public class AuthController {

    // Dependencias inyectadas por el constructor.
    private final AuthenticationManager authenticationManager; // valida usuario y contraseña
    private final JwtService jwtService;                       // firma el token
    private final UsuarioServices usuarioServices;             // lógica de usuarios
    private final ModelMapper mapper;                          // convierte entidad -> DTO

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
            UsuarioServices usuarioServices, ModelMapper mapper) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioServices = usuarioServices;
        this.mapper = mapper;
    }

    // registrar(): POST /auth/register
    // Recibe: RegistroUsuarioDTO en JSON. @Valid aplica las validaciones del DTO
    //   (por ejemplo, correo con formato correcto).
    // Devuelve: 201 CREATED con los datos del usuario (sin contraseña).
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> registrar(@Valid @RequestBody RegistroUsuarioDTO dto) throws Exception {
        // La lógica de crear el usuario (y el cliente) está en UsuarioServices.
        Usuario usuario = usuarioServices.registrarCliente(dto);
        // Convertimos la entidad a DTO para no exponer datos internos como el hash.
        UsuarioDTO data = mapper.map(usuario, UsuarioDTO.class);
        if (usuario.getCliente() != null) data.setClienteId(usuario.getCliente().getId());
        return new ResponseEntity<>(new ApiResponse<>(true, "Usuario registrado", data), HttpStatus.CREATED);
    }

    // login(): POST /auth/login
    // Recibe: LoginRequest con email y password.
    // Devuelve: 200 OK con el token JWT y datos básicos del usuario.
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request) {
        // Normalizamos el correo: sin espacios y en minúsculas, para evitar duplicados.
        String email = request.getEmail().trim().toLowerCase();

        // PASO CLAVE: el AuthenticationManager busca al usuario por correo y compara
        // la contraseña con su hash BCrypt. Si no coincide, lanza una excepción
        // (y la respuesta es de error, no se emite token).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        // Si llegamos aquí, las credenciales son correctas. Cargamos el usuario completo.
        Usuario usuario = usuarioServices.buscarPorEmail(authentication.getName());
        // Generamos el JWT con los datos del usuario (roles, id, cliente, etc.).
        String token = jwtService.generateToken(usuario);

        // LoginResponse agrupa lo que el frontend necesita: token, tipo ("Bearer"),
        // duración en ms, nombre, rol y el id de cliente (si aplica).
        LoginResponse data = new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiration(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                usuario.getCliente() != null ? usuario.getCliente().getId() : null);

        return new ResponseEntity<>(new ApiResponse<>(true, "Inicio de sesión correcto", data), HttpStatus.OK);
    }
}
