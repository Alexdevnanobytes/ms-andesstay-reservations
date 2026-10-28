package cl.andesstay.reservations.messaging;

import java.time.Instant;

/** Event contract published when a reservation is created or changes state. */
public record ReservationEvent(
    String eventId,
    int schemaVersion,
    String eventType,
    Instant occurredAt,
    String reservationId,
    String guestId,
    String unitId,
    String status,
    String checkIn,
    String checkOut
) { }
