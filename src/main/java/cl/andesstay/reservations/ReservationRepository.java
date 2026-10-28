package cl.andesstay.reservations;

// Repositorio de Spring Data JPA: acceso a la tabla AS_RESERVATIONS sin escribir SQL a mano.
// Al extender JpaRepository ya incluye save, findById, delete, etc.
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    // Spring genera la consulta a partir del nombre del método:
    // todas las reservas ordenadas por fecha de creación, de la más nueva a la más antigua.
    List<Reservation> findAllByOrderByCreatedAtDesc();
    // Solo las reservas de un huésped, también de la más nueva a la más antigua.
    List<Reservation> findByGuestIdOrderByCreatedAtDesc(String guestId);
    // Lee una reserva BLOQUEANDO su fila (SELECT ... FOR UPDATE) hasta que termine la transacción.
    // Evita que dos peticiones cambien la misma reserva al mismo tiempo (por ejemplo, dos confirmaciones).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> lockById(@Param("id") String id);
}
