package cl.andesstay.reservations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock ReservationRepository repository;
    @Mock CatalogClient catalog;
    ReservationService service;
    Reservation reservation;
    @BeforeEach void init() {
        service = new ReservationService(repository, catalog);
        LocalDate start = LocalDate.now().plusDays(4);
        reservation = new Reservation("guest-1", "unit-1", start, start.plusDays(2));
        lenient().when(repository.lockById(reservation.getId())).thenReturn(Optional.of(reservation));
        lenient().when(repository.saveAndFlush(reservation)).thenAnswer(inv -> inv.getArgument(0));
    }
    @Test void cannotCheckInBeforeConfirmation() {
        assertThrows(ResponseStatusException.class, () -> service.transition(reservation.getId(), ReservationStatus.EN_ESTADIA));
        verifyNoInteractions(catalog);
    }
    @Test void confirmationAllocatesAndCancellationReleases() {
        service.transition(reservation.getId(), ReservationStatus.CONFIRMADA);
        verify(catalog).allocate(reservation.getId(), reservation.getUnitId(), reservation.getCheckIn(), reservation.getCheckOut());
        service.transition(reservation.getId(), ReservationStatus.CANCELADA);
        verify(catalog).release(reservation.getId());
    }
}
