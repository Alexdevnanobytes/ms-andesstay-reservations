package cl.andesstay.reservations.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Demo consumers acknowledge only successfully decoded messages; invalid messages go to the DLQ. */
@Component
public class ReservationEventConsumers {
    private static final Logger log = LoggerFactory.getLogger(ReservationEventConsumers.class);

    @RabbitListener(queues = RabbitTopology.NOTIFICATIONS_QUEUE, ackMode = "MANUAL")
    public void notifyGuest(ReservationEvent event, Message message, Channel channel) throws IOException {
        consume("notifications", event, message, channel);
    }

    @RabbitListener(queues = RabbitTopology.TICKETS_QUEUE, ackMode = "MANUAL")
    public void createTicket(ReservationEvent event, Message message, Channel channel) throws IOException {
        consume("tickets", event, message, channel);
    }

    @RabbitListener(queues = RabbitTopology.AUDIT_QUEUE, ackMode = "MANUAL")
    public void audit(ReservationEvent event, Message message, Channel channel) throws IOException {
        consume("audit", event, message, channel);
    }

    private void consume(String destination, ReservationEvent event, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (event == null || event.eventId() == null || event.reservationId() == null || event.eventType() == null)
                throw new IllegalArgumentException("Invalid reservation event");
            log.info("RabbitMQ {} consumer processed event={} reservation={} type={}",
                destination, event.eventId(), event.reservationId(), event.eventType());
            channel.basicAck(tag, false);
        } catch (RuntimeException ex) {
            log.error("Rejecting invalid event from {} queue; routing to DLQ", destination, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
