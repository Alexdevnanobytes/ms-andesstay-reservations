-- Estados válidos. Hibernate ya puede haber creado esta restricción con este mismo nombre;
-- el bloque solo la agrega si no existe.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'as_reservations_status_check'
          AND conrelid = 'as_reservations'::regclass
    ) THEN
        ALTER TABLE as_reservations
            ADD CONSTRAINT as_reservations_status_check
            CHECK (status IN ('CREADA', 'CONFIRMADA', 'CHECKIN_PENDIENTE', 'EN_ESTADIA', 'CHECKOUT', 'CANCELADA'));
    END IF;
END $$;

-- La salida debe ser posterior a la entrada (el servicio ya lo valida; esto lo asegura en la base).
ALTER TABLE as_reservations
    ADD CONSTRAINT as_reservations_period_check CHECK (check_out > check_in);

-- findByGuestIdOrderByCreatedAtDesc: reservas de un huésped, de la más nueva a la más antigua.
CREATE INDEX IF NOT EXISTS idx_as_reservations_guest_created
    ON as_reservations (guest_id, created_at DESC);

-- findAllByOrderByCreatedAtDesc: listado general para el personal.
CREATE INDEX IF NOT EXISTS idx_as_reservations_created
    ON as_reservations (created_at DESC);