// Punto de entrada del microservicio de reservas (puerto 8081).
// Gestiona el ciclo de vida de las reservas y guarda los datos en PostgreSQL (tabla AS_RESERVATIONS).
package cl.andesstay.reservations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication: configuración automática y escaneo de los componentes de este paquete.
@SpringBootApplication
public class ReservationsApplication {
    public static void main(String[] args) { SpringApplication.run(ReservationsApplication.class, args); }
}
