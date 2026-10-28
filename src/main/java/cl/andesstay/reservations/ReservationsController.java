package cl.andesstay.reservations;

// Controlador REST del microservicio de reservas. Solo lo llama el BFF, con el header X-Internal-Token
// (lo verifica el filtro InternalSecurity antes de llegar aquí). Las reglas de rol ya las aplicó el BFF.
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Todas las rutas empiezan con /api/reservations.
@RestController
@RequestMapping("/api/reservations")
public class ReservationsController {
    private final ReservationService service;
    public ReservationsController(ReservationService service) { this.service = service; }
    // Cuerpos de las peticiones. @NotBlank y @NotNull se revisan con @Valid; si fallan, 400 Bad Request.
    public record CreateRequest(@NotBlank String guestId, @NotBlank String unitId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut) { }
    public record UpdateRequest(@NotBlank String unitId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut) { }
    // status debe ser uno de los valores de ReservationStatus; un valor desconocido también da 400.
    public record StatusRequest(@NotNull ReservationStatus status) { }

    // GET /api/reservations[?guestId=...]: todas las reservas o solo las de un huésped.
    @GetMapping public List<Reservation> list(@RequestParam(required = false) String guestId) { return service.list(guestId); }
    // GET /api/reservations/{id}: una reserva (404 si no existe).
    @GetMapping("/{id}") public Reservation one(@PathVariable String id) { return service.one(id); }
    // POST /api/reservations: crea una reserva (201 Created).
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Reservation create(@Valid @RequestBody CreateRequest data) { return service.create(data); }
    // PUT /api/reservations/{id}: edita unidad y fechas (solo en estado CREADA).
    @PutMapping("/{id}") public Reservation update(@PathVariable String id, @Valid @RequestBody UpdateRequest data) { return service.update(id, data); }
    // DELETE /api/reservations/{id}: elimina (204 No Content).
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id); }
    // PATCH /api/reservations/{id}/status: cambia el estado según la máquina de estados.
    @PatchMapping("/{id}/status") public Reservation change(@PathVariable String id, @Valid @RequestBody StatusRequest data) {
        return service.transition(id, data.status());
    }
}
