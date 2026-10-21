// ============================================================================
// MÓDULO: EUREKA (service discovery)
// RESPONSABILIDAD: ser el "directorio" donde cada microservicio se registra
// con su nombre y dirección. Los demás servicios consultan aquí para
// encontrarse entre sí, sin usar direcciones fijas.
// Analogía: el directorio de un centro comercial.
// ============================================================================
package com.fisiomarca.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

// @SpringBootApplication: marca la clase principal. Activa la configuración
//   automática de Spring Boot y el escaneo de componentes.
// @EnableEurekaServer: convierte esta aplicación en un servidor Eureka.
//   Permite que otros servicios se registren y consulten la lista de servicios.
@EnableEurekaServer
@SpringBootApplication
public class ServidorEurekaApplication {

    // main(): punto de entrada de Java. Arranca Spring Boot, que levanta el
    // servidor en el puerto 8761 (configurado en application.yml).
    public static void main(String[] args) {
        SpringApplication.run(ServidorEurekaApplication.class, args);
    }
}
