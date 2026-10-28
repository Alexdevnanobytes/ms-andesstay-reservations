package cl.andesstay.reservations;

// Lógica de negocio de las reservas: validaciones, reglas de edición y borrado,
// y la máquina de estados, que coordina con catalog la ocupación de las unidades.
import java.time.LocalDate;
import java.util.List;
import cl.andesstay.reservations.messaging.ReservationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// @Service: componente de negocio. Recibe por constructor el repositorio y el cliente de catalog.
@Service
public class ReservationService {
    private final ReservationRepository repository;
    private final CatalogClient catalog;
    private final ReservationEventPublisher events;
    @Autowired
    public ReservationService(ReservationRepository repository, CatalogClient catalog, ReservationEventPublisher events) {
        this.repository = repository; this.catalog = catalog; this.events = events;
    }
    /** Retained for isolated unit tests that do not exercise event publication. */
    ReservationService(ReservationRepository repository, CatalogClient catalog) {
        this.repository = repository; this.catalog = catalog;
        this.events = null;
    }
    // La salida debe ser posterior a la entrada; si no, 400 Bad Request.
    private void validatePeriod(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La salida debe ser posterior a la entrada");
    }
    // @Transactional(readOnly = true): consulta dentro de una transacción de solo lectura.
    // Sin guestId devuelve todas las reservas (personal); con guestId, solo las de ese huésped.
    @Transactional(readOnly = true)
    public List<Reservation> list(String guestId) {
        return guestId == null ? repository.findAllByOrderByCreatedAtDesc() : repository.findByGuestIdOrderByCreatedAtDesc(guestId);
    }
    // Una reserva por id; 404 si no existe.
    @Transactional(readOnly = true)
    public Reservation one(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva inexistente"));
    }
    // Crea una reserva en estado CREADA. Crear NO ocupa la unidad: eso ocurre al confirmar.
    @Transactional
    public Reservation create(ReservationsController.CreateRequest data) {
        validatePeriod(data.checkIn(), data.checkOut());
        Reservation saved = repository.save(new Reservation(data.guestId(), data.unitId(), data.checkIn(), data.checkOut()));
        if (events != null) events.publishAfterCommit(saved, "reservation.created");
        return saved;
    }
    // Edita unidad y fechas. Bloquea la fila y solo permite editar reservas CREADA (si no, 409).
    @Transactional
    public Reservation update(String id, ReservationsController.UpdateRequest data) {
        validatePeriod(data.checkIn(), data.checkOut());
        Reservation reservation = locked(id);
        if (reservation.getStatus() != ReservationStatus.CREADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar una reserva creada");
        reservation.changeDates(data.unitId(), data.checkIn(), data.checkOut());
        return repository.save(reservation);
    }
    // Elimina una reserva. Solo se puede si está CREADA o CANCELADA (si no, 409):
    // una reserva confirmada ocupa una unidad y primero hay que cancelarla para liberarla.
    @Transactional
    public void delete(String id) {
        Reservation reservation = locked(id);
        if (reservation.getStatus() != ReservationStatus.CREADA && reservation.getStatus() != ReservationStatus.CANCELADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancele la reserva antes de eliminarla");
        repository.delete(reservation);
    }
    // Máquina de estados: cambia el estado de una reserva si la transición es válida.
    // @Transactional: todo ocurre en una transacción; si algo falla, los cambios en la base se deshacen.
    @Transactional
    public Reservation transition(String id, ReservationStatus next) {
        // Bloquea la fila de la reserva para que nadie la cambie mientras tanto.
        Reservation reservation = locked(id);
        ReservationStatus current = reservation.getStatus();
        // Transiciones permitidas:
        //   CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA -> CHECKOUT
        //   y a CANCELADA desde CREADA, CONFIRMADA o CHECKIN_PENDIENTE.
        boolean confirm = current == ReservationStatus.CREADA && next == ReservationStatus.CONFIRMADA;
        boolean arrival = current == ReservationStatus.CONFIRMADA && next == ReservationStatus.CHECKIN_PENDIENTE;
        boolean checkIn = current == ReservationStatus.CHECKIN_PENDIENTE && next == ReservationStatus.EN_ESTADIA;
        boolean checkOut = current == ReservationStatus.EN_ESTADIA && next == ReservationStatus.CHECKOUT;
        boolean cancel = next == ReservationStatus.CANCELADA &&
            (current == ReservationStatus.CREADA || current == ReservationStatus.CONFIRMADA || current == ReservationStatus.CHECKIN_PENDIENTE);
        // Cualquier otra transición (por ejemplo CREADA -> EN_ESTADIA) responde 409 Conflict.
        // Es la regla "no hay check-in sin confirmar".
        if (!(confirm || arrival || checkIn || checkOut || cancel))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transición de estado inválida: " + current + " → " + next);
        // ¿Hay que liberar la unidad? Al hacer checkout, o al cancelar una reserva que ya la tenía ocupada
        // (cancelar desde CREADA no libera nada porque nunca se ocupó).
        boolean release = checkOut || (cancel && current != ReservationStatus.CREADA);
        // Confirmar ocupa la unidad en catalog (si está ocupada en esas fechas, catalog responde 409).
        if (confirm) catalog.allocate(id, reservation.getUnitId(), reservation.getCheckIn(), reservation.getCheckOut());
        // Checkout o cancelación liberan la unidad en catalog.
        if (release) catalog.release(id);
        // Guarda el nuevo estado. saveAndFlush fuerza la escritura inmediata para detectar errores aquí mismo.
        try {
            reservation.setStatus(next);
            Reservation saved = repository.saveAndFlush(reservation);
            if (events != null) events.publishAfterCommit(saved, "reservation.status." + next.name().toLowerCase());
            return saved;
        // Si el guardado falla después de haber llamado a catalog, se deshace lo hecho en catalog.
        } catch (RuntimeException ex) {
            // Compensación de errores de escritura local; no reemplaza una transacción distribuida.
            if (confirm) catalog.release(id);
            if (release) catalog.allocate(id, reservation.getUnitId(), reservation.getCheckIn(), reservation.getCheckOut());
            throw ex;
        }
    }
    // Lee la reserva con bloqueo de fila; 404 si no existe.
    private Reservation locked(String id) {
        return repository.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva inexistente"));
    }
}
