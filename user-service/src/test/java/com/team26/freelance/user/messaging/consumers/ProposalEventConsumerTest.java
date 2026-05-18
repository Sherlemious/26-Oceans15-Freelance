package com.team26.freelance.user.messaging.consumers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.user.service.UserProposalEventService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ProposalEventConsumerTest {

    private static final String CORRELATION_ID_KEY = "correlationId";

    private ProposalEventConsumer consumer;
    private UserProposalEventService userProposalEventService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        userProposalEventService = Mockito.mock(UserProposalEventService.class);
        consumer = new ProposalEventConsumer(new ObjectMapper(), userProposalEventService);

        Logger logger = (Logger) LoggerFactory.getLogger(ProposalEventConsumer.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProposalEventConsumer.class);
        logger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    void shouldUseHeaderCorrelationIdAndClearMdcAfterSuccess() throws Exception {
        Message message = message(
                SagaTopics.PROPOSAL_COMPLETED,
                "{\"proposalId\":11,\"jobId\":22,\"freelancerId\":33,\"contractId\":44,\"agreedAmount\":100.50}",
                "acl192-corr-success");

        consumer.onProposalEvent(message);

        assertNull(MDC.get(CORRELATION_ID_KEY));

        ILoggingEvent consumedLog = findLog("Consuming proposal event");
        ILoggingEvent processedLog = findLog("Processed proposal event");

        assertNotNull(consumedLog);
        assertNotNull(processedLog);
        assertEquals("acl192-corr-success", consumedLog.getMDCPropertyMap().get(CORRELATION_ID_KEY));
        assertEquals("acl192-corr-success", processedLog.getMDCPropertyMap().get(CORRELATION_ID_KEY));
    }

    @Test
    void shouldHandleMissingCorrelationIdWithoutCrashing() throws Exception {
        Message message = message(
                SagaTopics.PROPOSAL_CANCELLED,
                "{\"proposalId\":12,\"jobId\":23,\"freelancerId\":34,\"reason\":\"manual\"}",
                null);

        consumer.onProposalEvent(message);

        ILoggingEvent consumedLog = findLog("Consuming proposal event");
        assertNotNull(consumedLog);
        String correlationId = consumedLog.getMDCPropertyMap().get(CORRELATION_ID_KEY);
        assertNotNull(correlationId);
        assertTrue(!correlationId.isBlank());
        assertNull(MDC.get(CORRELATION_ID_KEY));
    }

    @Test
    void shouldClearMdcBetweenMessages() throws Exception {
        Message first = message(
                SagaTopics.PROPOSAL_COMPLETED,
                "{\"proposalId\":21,\"jobId\":31,\"freelancerId\":41,\"contractId\":51,\"agreedAmount\":200.00}",
                "acl192-corr-first");
        Message second = message(
                SagaTopics.PROPOSAL_CANCELLED,
                "{\"proposalId\":22,\"jobId\":32,\"freelancerId\":42,\"reason\":\"timeout\"}",
                null);

        consumer.onProposalEvent(first);
        consumer.onProposalEvent(second);

        List<ILoggingEvent> consumedLogs = logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("Consuming proposal event"))
                .toList();

        assertEquals(2, consumedLogs.size());
        String firstCorrelationId = consumedLogs.get(0).getMDCPropertyMap().get(CORRELATION_ID_KEY);
        String secondCorrelationId = consumedLogs.get(1).getMDCPropertyMap().get(CORRELATION_ID_KEY);

        assertEquals("acl192-corr-first", firstCorrelationId);
        assertNotNull(secondCorrelationId);
        assertNotEquals(firstCorrelationId, secondCorrelationId);
        assertNull(MDC.get(CORRELATION_ID_KEY));
    }

    @Test
    void shouldLogFailureWithCorrelationIdAndClearMdc() {
        Message message = message(
                "proposal.unknown",
                "{\"proposalId\":91}",
                "acl192-corr-failure");

        assertThrows(IllegalArgumentException.class, () -> consumer.onProposalEvent(message));

        ILoggingEvent failureLog = findLog("Failed to process proposal event");
        assertNotNull(failureLog);
        Map<String, String> mdcMap = failureLog.getMDCPropertyMap();
        assertEquals("acl192-corr-failure", mdcMap.get(CORRELATION_ID_KEY));
        assertNull(MDC.get(CORRELATION_ID_KEY));
    }

    private Message message(String routingKey, String body, String correlationId) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setContentType("application/json");
        if (correlationId != null) {
            properties.setHeader(CORRELATION_ID_KEY, correlationId);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private ILoggingEvent findLog(String snippet) {
        return logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(snippet))
                .findFirst()
                .orElse(null);
    }
}
