package com.team26.freelance.common.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventFactoryTest {

    @Test
    void mongoEventInterfaceExposesMilestoneMethods() throws Exception {
        Method getId = MongoEvent.class.getMethod("getId");
        Method getTimestamp = MongoEvent.class.getMethod("getTimestamp");
        Method getAction = MongoEvent.class.getMethod("getAction");
        Method getDetails = MongoEvent.class.getMethod("getDetails");

        assertThat(getId.getReturnType()).isEqualTo(String.class);
        assertThat(getTimestamp.getReturnType()).isEqualTo(LocalDateTime.class);
        assertThat(getAction.getReturnType()).isEqualTo(String.class);
        assertThat(getDetails.getReturnType()).isEqualTo(Map.class);
    }

    @Test
    void factoryCreatesAllMongoEventTypes() {
        assertThat(EventFactory.createEvent(EventType.AUTH, authParams())).isInstanceOf(AuthEvent.class);
        assertThat(EventFactory.createEvent(EventType.JOB, jobParams())).isInstanceOf(JobEvent.class);
        assertThat(EventFactory.createEvent(EventType.PROPOSAL, proposalParams())).isInstanceOf(ProposalEvent.class);
        assertThat(EventFactory.createEvent(EventType.CONTRACT, contractParams())).isInstanceOf(ContractEvent.class);
        assertThat(EventFactory.createEvent(EventType.PAYOUT_AUDIT, payoutParams())).isInstanceOf(PayoutAuditEvent.class);
    }

    @Test
    void authEventFieldsMatchParams() {
        AuthEvent event = (AuthEvent) EventFactory.createEvent(EventType.AUTH, authParams());

        assertThat(event.getId()).isEqualTo("auth-1");
        assertThat(event.getUserId()).isEqualTo(56L);
        assertThat(event.getAction()).isEqualTo("REGISTERED");
        assertThat(event.getTimestamp()).isEqualTo(timestamp());
        assertThat(event.getDetails()).containsEntry("email", "joe@example.com");
    }

    @Test
    void payoutAuditEventExposesMethodAndAmountFields() {
        PayoutAuditEvent event = (PayoutAuditEvent) EventFactory.createEvent(EventType.PAYOUT_AUDIT, payoutParams());

        assertThat(event.getPayoutId()).isEqualTo(99L);
        assertThat(event.getAction()).isEqualTo("COMPLETED");
        assertThat(event.getMethod()).isEqualTo("PAYPAL");
        assertThat(event.getAmount()).isEqualTo(2500.75);
        assertThat(event.getDetails()).containsEntry("strategyApplied", "FullPayoutReversalStrategy");
    }

    @Test
    void mapConstructorDefaultsMissingTimestampAndDetails() {
        AuthEvent event = (AuthEvent) EventFactory.createEvent(EventType.AUTH, Map.of("action", "LOGGED_IN"));

        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getDetails()).isEmpty();
    }

    private static Map<String, Object> authParams() {
        Map<String, Object> params = baseParams("auth-1", "REGISTERED");
        params.put("userId", 56);
        params.put("details", Map.of("email", "joe@example.com"));
        return params;
    }

    private static Map<String, Object> jobParams() {
        Map<String, Object> params = baseParams("job-1", "INDEXED");
        params.put("jobId", "12");
        return params;
    }

    private static Map<String, Object> proposalParams() {
        Map<String, Object> params = baseParams("proposal-1", "ANALYTICS_VIEWED");
        params.put("proposalId", 22L);
        return params;
    }

    private static Map<String, Object> contractParams() {
        Map<String, Object> params = baseParams("contract-1", "MILESTONE_TRACKED");
        params.put("contractId", 33L);
        return params;
    }

    private static Map<String, Object> payoutParams() {
        Map<String, Object> params = baseParams("payout-1", "COMPLETED");
        params.put("payoutId", 99L);
        params.put("method", "PAYPAL");
        params.put("amount", "2500.75");
        params.put("details", Map.of("strategyApplied", "FullPayoutReversalStrategy"));
        return params;
    }

    private static Map<String, Object> baseParams(String id, String action) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("action", action);
        params.put("timestamp", timestamp());
        params.put("details", Map.of());
        return params;
    }

    private static LocalDateTime timestamp() {
        return LocalDateTime.of(2026, 5, 1, 12, 30);
    }
}
