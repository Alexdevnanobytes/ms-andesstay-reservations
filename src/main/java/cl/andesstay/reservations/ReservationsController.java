package cl.andesstay.reservations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationsController {
    private final ReservationService service;
    public ReservationsController(ReservationService service) { this.service = service; }
    public record CreateRequest(@NotBlank String guestId, @NotBlank String unitId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut) { }
    public record UpdateRequest(@NotBlank String unitId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut) { }
    public record StatusRequest(@NotNull ReservationStatus status) { }

    @GetMapping public List<Reservation> list(@RequestParam(required = false) String guestId) { return service.list(guestId); }
    @GetMapping("/{id}") public Reservation one(@PathVariable String id) { return service.one(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Reservation create(@Valid @RequestBody CreateRequest data) { return service.create(data); }
    @PutMapping("/{id}") public Reservation update(@PathVariable String id, @Valid @RequestBody UpdateRequest data) { return service.update(id, data); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id); }
    @PatchMapping("/{id}/status") public Reservation change(@PathVariable String id, @Valid @RequestBody StatusRequest data) {
        return service.transition(id, data.status());
    }
}
