package cl.andesstay.reservations;

import java.time.LocalDate;
import java.util.List;
import cl.andesstay.reservations.messaging.ReservationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {
    private final ReservationRepository repository;
    private final CatalogClient catalog;
    private final ReservationEventPublisher events;
    @Autowired
    public ReservationService(ReservationRepository repository, CatalogClient catalog, ReservationEventPublisher events) {
        this.repository = repository; this.catalog = catalog; this.events = events;
    }
    ReservationService(ReservationRepository repository, CatalogClient catalog) {
        this.repository = repository; this.catalog = catalog; this.events = null;
    }
    private void validatePeriod(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La salida debe ser posterior a la entrada");
    }
    @Transactional(readOnly = true)
    public List<Reservation> list(String guestId) {
        return guestId == null ? repository.findAllByOrderByCreatedAtDesc() : repository.findByGuestIdOrderByCreatedAtDesc(guestId);
    }
    @Transactional(readOnly = true)
    public Reservation one(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva inexistente"));
    }
    @Transactional
    public Reservation create(ReservationsController.CreateRequest data) {
        validatePeriod(data.checkIn(), data.checkOut());
        Reservation saved = repository.save(new Reservation(data.guestId(), data.unitId(), data.checkIn(), data.checkOut()));
        if (events != null) events.publishAfterCommit(saved, "reservation.created");
        return saved;
    }
    @Transactional
    public Reservation update(String id, ReservationsController.UpdateRequest data) {
        validatePeriod(data.checkIn(), data.checkOut());
        Reservation reservation = locked(id);
        if (reservation.getStatus() != ReservationStatus.CREADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar una reserva creada");
        reservation.changeDates(data.unitId(), data.checkIn(), data.checkOut());
        return repository.save(reservation);
    }
    @Transactional
    public void delete(String id) {
        Reservation reservation = locked(id);
        if (reservation.getStatus() != ReservationStatus.CREADA && reservation.getStatus() != ReservationStatus.CANCELADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancele la reserva antes de eliminarla");
        repository.delete(reservation);
    }
    @Transactional
    public Reservation transition(String id, ReservationStatus next) {
        Reservation reservation = locked(id);
        ReservationStatus current = reservation.getStatus();
        boolean confirm = current == ReservationStatus.CREADA && next == ReservationStatus.CONFIRMADA;
        boolean arrival = current == ReservationStatus.CONFIRMADA && next == ReservationStatus.CHECKIN_PENDIENTE;
        boolean checkIn = current == ReservationStatus.CHECKIN_PENDIENTE && next == ReservationStatus.EN_ESTADIA;
        boolean checkOut = current == ReservationStatus.EN_ESTADIA && next == ReservationStatus.CHECKOUT;
        boolean cancel = next == ReservationStatus.CANCELADA &&
            (current == ReservationStatus.CREADA || current == ReservationStatus.CONFIRMADA || current == ReservationStatus.CHECKIN_PENDIENTE);
        if (!(confirm || arrival || checkIn || checkOut || cancel))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transición de estado inválida: " + current + " → " + next);
        boolean release = checkOut || (cancel && current != ReservationStatus.CREADA);
        if (confirm) catalog.allocate(id, reservation.getUnitId(), reservation.getCheckIn(), reservation.getCheckOut());
        if (release) catalog.release(id);
        try {
            reservation.setStatus(next);
            Reservation saved = repository.saveAndFlush(reservation);
            if (events != null) events.publishAfterCommit(saved, "reservation.status." + next.name().toLowerCase());
            return saved;
        } catch (RuntimeException ex) {
            // Compensación de errores de escritura local; no reemplaza una transacción distribuida.
            if (confirm) catalog.release(id);
            if (release) catalog.allocate(id, reservation.getUnitId(), reservation.getCheckIn(), reservation.getCheckOut());
            throw ex;
        }
    }
    private Reservation locked(String id) {
        return repository.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva inexistente"));
    }
}
