package cl.andesstay.reservations.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RabbitTopologyTest {
    private final RabbitTopology topology = new RabbitTopology();

    @Test void declaresThreeDurableWorkQueuesWithDeadLetterRoutes() {
        assertQueue(topology.notificationsQueue(), RabbitTopology.NOTIFICATIONS_QUEUE, RabbitTopology.NOTIFICATIONS_DLQ);
        assertQueue(topology.ticketsQueue(), RabbitTopology.TICKETS_QUEUE, RabbitTopology.TICKETS_DLQ);
        assertQueue(topology.auditQueue(), RabbitTopology.AUDIT_QUEUE, RabbitTopology.AUDIT_DLQ);
    }

    @Test void usesDirectAndTopicExchangesForReservationEvents() {
        assertEquals(RabbitTopology.DIRECT_EXCHANGE, topology.reservationDirectExchange().getName());
        assertEquals(RabbitTopology.TOPIC_EXCHANGE, topology.reservationTopicExchange().getName());
        assertEquals(RabbitTopology.DEAD_LETTER_EXCHANGE, topology.reservationDeadLetterExchange().getName());
    }

    private void assertQueue(org.springframework.amqp.core.Queue queue, String name, String dlq) {
        assertEquals(name, queue.getName());
        assertEquals(RabbitTopology.DEAD_LETTER_EXCHANGE, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(dlq, queue.getArguments().get("x-dead-letter-routing-key"));
    }
}
