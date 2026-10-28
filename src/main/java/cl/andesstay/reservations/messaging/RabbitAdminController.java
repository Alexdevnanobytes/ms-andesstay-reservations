package cl.andesstay.reservations.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal broker administration API. Every request first passes InternalSecurity's
 * X-Internal-Token filter; the BFF additionally restricts these routes to role ADMIN.
 */
@RestController
@Validated
@RequestMapping("/api/rabbit")
public class RabbitAdminController {
    private static final String QUEUE_PREFIX = "q.admin.";
    private static final String EXCHANGE_PREFIX = "andesstay.admin.";
    private static final String NAME_REGEX = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    private final RabbitAdmin rabbitAdmin;
    private final RestClient management;

    public RabbitAdminController(RabbitAdmin rabbitAdmin, RestClient.Builder builder,
        @Value("${rabbitmq.management-url}") String managementUrl,
        @Value("${rabbitmq.username}") String username,
        @Value("${rabbitmq.password}") String password) {
        this.rabbitAdmin = rabbitAdmin;
        this.management = builder.requestFactory(new JdkClientHttpRequestFactory()).baseUrl(managementUrl)
            .defaultHeaders(headers -> headers.setBasicAuth(username, password)).build();
    }

    public record QueueRequest(@NotBlank @Pattern(regexp = NAME_REGEX) String name) { }
    public record ExchangeRequest(@NotBlank @Pattern(regexp = NAME_REGEX) String name,
                                  @NotBlank @Pattern(regexp = "direct|topic") String type) { }
    public record BindingRequest(@NotBlank @Pattern(regexp = NAME_REGEX) String queue,
                                 @NotBlank @Pattern(regexp = NAME_REGEX) String exchange,
                                 @NotBlank @Pattern(regexp = "[A-Za-z0-9._*-]{1,255}") String routingKey) { }

    @GetMapping("/queues") public List<Map<String, Object>> queues() { return list("/api/queues/%2F"); }
    @GetMapping("/exchanges") public List<Map<String, Object>> exchanges() { return list("/api/exchanges/%2F"); }
    @GetMapping("/bindings") public List<Map<String, Object>> bindings() { return list("/api/bindings/%2F"); }

    @PostMapping("/queues") @ResponseStatus(HttpStatus.CREATED)
    public void createQueue(@Valid @RequestBody QueueRequest request) {
        requirePrefix(request.name(), QUEUE_PREFIX, "cola");
        String dlqName = request.name() + ".dlq";
        Queue dlq = QueueBuilder.durable(dlqName).build();
        Queue queue = QueueBuilder.durable(request.name())
            .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(dlqName).build();
        rabbitAdmin.declareQueue(dlq);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(dlq)
            .to(new DirectExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)).with(dlqName));
    }

    @DeleteMapping("/queues/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable @Pattern(regexp = NAME_REGEX) String name) {
        requirePrefix(name, QUEUE_PREFIX, "cola");
        rabbitAdmin.deleteQueue(name);
        rabbitAdmin.deleteQueue(name + ".dlq");
    }

    @PostMapping("/exchanges") @ResponseStatus(HttpStatus.CREATED)
    public void createExchange(@Valid @RequestBody ExchangeRequest request) {
        requirePrefix(request.name(), EXCHANGE_PREFIX, "exchange");
        if ("direct".equals(request.type())) rabbitAdmin.declareExchange(new DirectExchange(request.name(), true, false));
        else rabbitAdmin.declareExchange(new TopicExchange(request.name(), true, false));
    }

    @DeleteMapping("/exchanges/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable @Pattern(regexp = NAME_REGEX) String name) {
        requirePrefix(name, EXCHANGE_PREFIX, "exchange");
        rabbitAdmin.deleteExchange(name);
    }

    @PostMapping("/bindings") @ResponseStatus(HttpStatus.CREATED)
    public void createBinding(@Valid @RequestBody BindingRequest request) {
        requirePrefix(request.queue(), QUEUE_PREFIX, "cola");
        requirePrefix(request.exchange(), EXCHANGE_PREFIX, "exchange");
        rabbitAdmin.declareBinding(new Binding(request.queue(), Binding.DestinationType.QUEUE,
            request.exchange(), request.routingKey(), null));
    }

    @DeleteMapping("/bindings") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@RequestParam @Pattern(regexp = NAME_REGEX) String queue,
        @RequestParam @Pattern(regexp = NAME_REGEX) String exchange,
        @RequestParam @Pattern(regexp = "[A-Za-z0-9._*-]{1,255}") String routingKey) {
        requirePrefix(queue, QUEUE_PREFIX, "cola");
        requirePrefix(exchange, EXCHANGE_PREFIX, "exchange");
        rabbitAdmin.removeBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null));
    }

    private List<Map<String, Object>> list(String path) {
        try {
            List<Map<String, Object>> response = management.get().uri(path).retrieve()
                .body(new ParameterizedTypeReference<>() { });
            return response == null ? List.of() : response;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RabbitMQ Management API no disponible");
        }
    }

    private void requirePrefix(String value, String prefix, String kind) {
        if (value == null || !value.startsWith(prefix))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El " + kind + " debe comenzar con " + prefix);
    }
}
