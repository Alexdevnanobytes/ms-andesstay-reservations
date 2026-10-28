package cl.andesstay.reservations.messaging;

import cl.andesstay.reservations.Reservation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes reservation events only after the database transaction commits. */
@Component
public class ReservationEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public ReservationEventPublisher(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

    public void publishAfterCommit(Reservation reservation, String eventType) {
        ReservationEvent event = new ReservationEvent(UUID.randomUUID().toString(), 1, eventType,
            Instant.now(), reservation.getId(), reservation.getGuestId(), reservation.getUnitId(),
            reservation.getStatus().name(), reservation.getCheckIn().toString(), reservation.getCheckOut().toString());
        Runnable publish = () -> {
            String exchange = "reservation.created".equals(eventType)
                ? RabbitTopology.DIRECT_EXCHANGE : RabbitTopology.TOPIC_EXCHANGE;
            rabbitTemplate.convertAndSend(exchange, eventType, event);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }
}
