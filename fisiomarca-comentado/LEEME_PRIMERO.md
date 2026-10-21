# Código comentado: lectura guiada

Lee primero `FisioMarca_Guion_Exposicion.pdf` y después estos archivos en este orden. Sigue el mismo flujo del PDF.

## Orden de lectura

1. **Eureka (directorio de servicios)**
   - `backend/Servidor-Eureka/.../ServidorEurekaApplication.java`
   - `backend/Servidor-Eureka/src/main/resources/application.yml`

2. **Auth (identidad y token)**
   - `backend/AUTH-SERVICE/.../security/JwtService.java`: cómo se crea el token.
   - `backend/AUTH-SERVICE/.../security/SecurityConfig.java`: qué rutas son públicas y qué rol se exige.
   - `backend/AUTH-SERVICE/.../controller/AuthController.java`: login y registro.

3. **API Gateway (puerta de entrada)**
   - `backend/Gateway/.../JwtAuthenticationFilter.java`: valida la firma y el vencimiento, no los roles.
   - `backend/Gateway/src/main/resources/application.yml`: rutas y conexión con Eureka.

4. **Carrito (datos del paciente)**
   - `backend/Micro-Carrito/.../security/AccesoCliente.java`: regla de propiedad (solo el dueño o el admin).
   - `backend/Micro-Carrito/.../security/SecurityConfig.java`: validación del token.
   - `backend/Micro-Carrito/.../client/UsuariosFeign.java` y `CatalogoFeign.java`: llamadas internas.
   - `backend/Micro-Carrito/.../controller/CarritoController.java`: cada endpoint verifica acceso, ejecuta y responde.

5. **Reservas (citas)**
   - `backend/Micro-Reservas/.../security/SecurityConfig.java`: qué rutas son solo para ADMINISTRADOR.

## Qué garantiza este código

- Los archivos marcados tienen comentarios explicativos. El código es idéntico al original: se verificó línea por línea, sin contar comentarios.
- Los demás archivos son copias originales sin comentar. Están incluidos para que la estructura sea completa.
- Estos archivos sirven para leer. Para ejecutar el proyecto usa la carpeta original.

## Recordatorio

Las limitaciones que ves en el código (llamadas Feign sin token, secreto con valor por defecto, Gateway sin revisión de roles) están explicadas en la sección 4 del PDF.
