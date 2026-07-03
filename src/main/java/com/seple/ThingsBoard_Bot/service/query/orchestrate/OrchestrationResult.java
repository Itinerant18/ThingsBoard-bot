package com.seple.ThingsBoard_Bot.service.query.orchestrate;

/**
 * Outcome of running extracted intents through the deterministic handlers (Phase 2, Task 2.2).
 *
 * @param status  what happened
 * @param message the combined answer ({@code ANSWERED}) or the clarification question to send
 *                back to the user ({@code CLARIFICATION}); null for {@code UNANSWERED}
 */
public record OrchestrationResult(Status status, String message) {

    public enum Status {
        /** At least one intent produced a deterministic answer; message holds the combined text. */
        ANSWERED,
        /** An entity fell into the confirmation/suggestion band; message asks the user which branch. */
        CLARIFICATION,
        /** Nothing could be answered deterministically - caller falls back to the LLM path. */
        UNANSWERED
    }

    public static OrchestrationResult answered(String message) {
        return new OrchestrationResult(Status.ANSWERED, message);
    }

    public static OrchestrationResult clarification(String message) {
        return new OrchestrationResult(Status.CLARIFICATION, message);
    }

    public static OrchestrationResult unanswered() {
        return new OrchestrationResult(Status.UNANSWERED, null);
    }
}
