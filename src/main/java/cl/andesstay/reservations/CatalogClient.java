package cl.andesstay.reservations;

// Cliente HTTP de reservations hacia ms-andesstay-catalog.
// Se usa al confirmar, cancelar o hacer checkout de una reserva, para ocupar o liberar la unidad.
// Las rutas /internal/... de catalog no son para el frontend: solo las llama este servicio.
import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

// @Component: Spring crea una instancia y la inyecta en ReservationService.
@Component
public class CatalogClient {
    private final RestClient client;
    // URL de catalog (CATALOG_URL) y token interno compartido (INTERNAL_TOKEN), desde application.yml.
    public CatalogClient(RestClient.Builder builder, @Value("${catalog.base-url}") String base,
                         @Value("${internal.token}") String token) {
        // Todas las llamadas llevan el header X-Internal-Token; sin él, catalog respondería 401.
        this.client = builder.baseUrl(base).defaultHeader("X-Internal-Token", token).build();
    }
    // Asigna (ocupa) una unidad para una reserva entre dos fechas: POST /internal/allocations.
    // Si catalog responde error (por ejemplo 409 "unidad ocupada"), se propaga el mismo código.
    // Si catalog no responde, se lanza 503 Service Unavailable.
    public void allocate(String reservationId, String unitId, LocalDate from, LocalDate to) {
        try {
            client.post().uri("/internal/allocations").body(Map.of("reservationId", reservationId,
                "unitId", unitId, "from", from.toString(), "to", to.toString())).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), "Catálogo: " + ex.getStatusText()); }
        catch (ResourceAccessException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible"); }
    }
    // Libera la unidad de una reserva: DELETE /internal/allocations/{reservationId}.
    public void release(String reservationId) {
        try { client.delete().uri("/internal/allocations/{id}", reservationId).retrieve().toBodilessEntity(); }
        catch (RestClientResponseException ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), "No se pudo liberar la unidad"); }
        catch (ResourceAccessException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible"); }
    }
}
