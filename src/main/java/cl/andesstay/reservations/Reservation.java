package cl.andesstay.reservations;

// Entidad JPA: cada objeto Reservation es una fila de la tabla AS_RESERVATIONS en PostgreSQL.
// Las tablas las crea y modifica Flyway (src/main/resources/db/migration); Hibernate solo valida (ddl-auto: validate).
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// @Entity marca la clase como tabla; @Table indica el nombre exacto de la tabla.
@Entity
@Table(name = "AS_RESERVATIONS")
public class Reservation {
    // Clave primaria: un UUID como texto (36 caracteres), generado por la aplicación.
    @Id @Column(length = 36) private String id;
    // Identificador del huésped: oid de Entra ID o sub de Cognito.
    @Column(nullable = false, length = 64) private String guestId;
    // Unidad reservada (id de una unidad de ms-andesstay-catalog; está en otra tabla y otro servicio).
    @Column(nullable = false, length = 36) private String unitId;
    // Fechas de entrada y salida (LocalDate: sin hora).
    @Column(nullable = false) private LocalDate checkIn;
    @Column(nullable = false) private LocalDate checkOut;
    // Estado actual. EnumType.STRING guarda el nombre (por ejemplo "CONFIRMADA") y no un número.
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ReservationStatus status;
    // Momento de creación; se usa para ordenar la lista (más recientes primero).
    @Column(nullable = false) private Instant createdAt;
    // Constructor vacío protegido: lo exige JPA para reconstruir objetos desde la base de datos.
    protected Reservation() { }
    // Constructor para una reserva nueva: genera el UUID, parte en estado CREADA y registra la fecha de creación.
    public Reservation(String guestId, String unitId, LocalDate checkIn, LocalDate checkOut) {
        id = UUID.randomUUID().toString(); this.guestId = guestId; changeDates(unitId, checkIn, checkOut);
        status = ReservationStatus.CREADA; createdAt = Instant.now();
    }
    // Getters: Jackson los usa para convertir la reserva a JSON en las respuestas.
    public String getId() { return id; }
    public String getGuestId() { return guestId; }
    public String getUnitId() { return unitId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    // Cambia unidad y fechas (lo usa la edición, que solo se permite en estado CREADA).
    public void changeDates(String unitId, LocalDate checkIn, LocalDate checkOut) {
        this.unitId = unitId; this.checkIn = checkIn; this.checkOut = checkOut;
    }
    // Cambia el estado. Las reglas de qué transiciones son válidas están en ReservationService.
    public void setStatus(ReservationStatus status) { this.status = status; }
}
