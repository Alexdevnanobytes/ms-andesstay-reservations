package cl.andesstay.reservations;

// Estados posibles de una reserva, en el orden normal del ciclo de vida:
// CREADA (recién creada) -> CONFIRMADA (unidad ocupada) -> CHECKIN_PENDIENTE (por llegar)
// -> EN_ESTADIA (huésped alojado) -> CHECKOUT (terminada). CANCELADA es el estado de anulación.
// En JSON se envían sin tildes; el frontend los muestra con tildes.
public enum ReservationStatus { CREADA, CONFIRMADA, CHECKIN_PENDIENTE, EN_ESTADIA, CHECKOUT, CANCELADA }
