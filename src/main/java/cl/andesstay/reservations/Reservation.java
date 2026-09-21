package cl.andesstay.reservations;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "AS_RESERVATIONS")
public class Reservation {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 64) private String guestId;
    @Column(nullable = false, length = 36) private String unitId;
    @Column(nullable = false) private LocalDate checkIn;
    @Column(nullable = false) private LocalDate checkOut;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ReservationStatus status;
    @Column(nullable = false) private Instant createdAt;
    protected Reservation() { }
    public Reservation(String guestId, String unitId, LocalDate checkIn, LocalDate checkOut) {
        id = UUID.randomUUID().toString(); this.guestId = guestId; changeDates(unitId, checkIn, checkOut);
        status = ReservationStatus.CREADA; createdAt = Instant.now();
    }
    public String getId() { return id; }
    public String getGuestId() { return guestId; }
    public String getUnitId() { return unitId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void changeDates(String unitId, LocalDate checkIn, LocalDate checkOut) {
        this.unitId = unitId; this.checkIn = checkIn; this.checkOut = checkOut;
    }
    public void setStatus(ReservationStatus status) { this.status = status; }
}
