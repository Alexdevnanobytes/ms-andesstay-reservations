package cl.andesstay.reservations;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {
    private final RestClient client;
    public CatalogClient(RestClient.Builder builder, @Value("${catalog.base-url}") String base,
                         @Value("${internal.token}") String token) {
        this.client = builder.baseUrl(base).defaultHeader("X-Internal-Token", token).build();
    }
    public void allocate(String reservationId, String unitId, LocalDate from, LocalDate to) {
        try {
            client.post().uri("/internal/allocations").body(Map.of("reservationId", reservationId,
                "unitId", unitId, "from", from.toString(), "to", to.toString())).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), "Catálogo: " + ex.getStatusText()); }
        catch (ResourceAccessException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible"); }
    }
    public void release(String reservationId) {
        try { client.delete().uri("/internal/allocations/{id}", reservationId).retrieve().toBodilessEntity(); }
        catch (RestClientResponseException ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), "No se pudo liberar la unidad"); }
        catch (ResourceAccessException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible"); }
    }
}
