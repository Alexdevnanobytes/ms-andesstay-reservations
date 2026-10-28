-- Línea base: deja la tabla tal como la crea Hibernate hoy.
-- IF NOT EXISTS: en una base nueva la crea; en el RDS actual (ya existe) no cambia nada.
CREATE TABLE IF NOT EXISTS as_reservations (
    id          VARCHAR(36)  NOT NULL,
    guest_id    VARCHAR(64)  NOT NULL,
    unit_id     VARCHAR(36)  NOT NULL,
    check_in    DATE         NOT NULL,
    check_out   DATE         NOT NULL,
    status      VARCHAR(24)  NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT as_reservations_pkey PRIMARY KEY (id)
);