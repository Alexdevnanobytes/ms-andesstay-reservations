package cl.andesstay.reservations.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

/** Central declaration of durable exchanges, work queues, dead-letter queues and bindings. */
@Configuration
public class RabbitTopology {
    @Bean Jackson2JsonMessageConverter rabbitJsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
    public static final String DIRECT_EXCHANGE = "andesstay.events.direct";
    public static final String TOPIC_EXCHANGE = "andesstay.events.topic";
    public static final String DEAD_LETTER_EXCHANGE = "andesstay.events.dlx";
    public static final String NOTIFICATIONS_QUEUE = "q.reservation.notifications";
    public static final String TICKETS_QUEUE = "q.reservation.tickets";
    public static final String AUDIT_QUEUE = "q.reservation.audit";
    public static final String NOTIFICATIONS_DLQ = NOTIFICATIONS_QUEUE + ".dlq";
    public static final String TICKETS_DLQ = TICKETS_QUEUE + ".dlq";
    public static final String AUDIT_DLQ = AUDIT_QUEUE + ".dlq";

    @Bean DirectExchange reservationDirectExchange() { return new DirectExchange(DIRECT_EXCHANGE, true, false); }
    @Bean TopicExchange reservationTopicExchange() { return new TopicExchange(TOPIC_EXCHANGE, true, false); }
    @Bean DirectExchange reservationDeadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean Queue notificationsQueue() { return workQueue(NOTIFICATIONS_QUEUE, NOTIFICATIONS_DLQ); }
    @Bean Queue ticketsQueue() { return workQueue(TICKETS_QUEUE, TICKETS_DLQ); }
    @Bean Queue auditQueue() { return workQueue(AUDIT_QUEUE, AUDIT_DLQ); }
    @Bean Queue notificationsDlq() { return QueueBuilder.durable(NOTIFICATIONS_DLQ).build(); }
    @Bean Queue ticketsDlq() { return QueueBuilder.durable(TICKETS_DLQ).build(); }
    @Bean Queue auditDlq() { return QueueBuilder.durable(AUDIT_DLQ).build(); }

    @Bean Binding notificationsBinding(Queue notificationsQueue, DirectExchange reservationDirectExchange) {
        return BindingBuilder.bind(notificationsQueue).to(reservationDirectExchange).with("reservation.created");
    }
    @Bean Binding ticketsBinding(Queue ticketsQueue, TopicExchange reservationTopicExchange) {
        return BindingBuilder.bind(ticketsQueue).to(reservationTopicExchange).with("reservation.status.*");
    }
    @Bean Binding auditBinding(Queue auditQueue, TopicExchange reservationTopicExchange) {
        return BindingBuilder.bind(auditQueue).to(reservationTopicExchange).with("reservation.#");
    }
    @Bean Binding notificationsDlqBinding(Queue notificationsDlq, DirectExchange reservationDeadLetterExchange) {
        return BindingBuilder.bind(notificationsDlq).to(reservationDeadLetterExchange).with(NOTIFICATIONS_DLQ);
    }
    @Bean Binding ticketsDlqBinding(Queue ticketsDlq, DirectExchange reservationDeadLetterExchange) {
        return BindingBuilder.bind(ticketsDlq).to(reservationDeadLetterExchange).with(TICKETS_DLQ);
    }
    @Bean Binding auditDlqBinding(Queue auditDlq, DirectExchange reservationDeadLetterExchange) {
        return BindingBuilder.bind(auditDlq).to(reservationDeadLetterExchange).with(AUDIT_DLQ);
    }

    private Queue workQueue(String name, String deadLetterQueue) {
        return QueueBuilder.durable(name)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(deadLetterQueue)
            .build();
    }
}
