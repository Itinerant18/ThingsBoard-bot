package com.seple.ThingsBoard_Bot.service.query;

/**
 * Phase 2, Task 2.3 - maps the extractor's {@link ResponseFormat} onto a single generation
 * instruction appended to the LLM user message. Deterministic handlers currently treat the
 * format as a documented no-op (their templates are fixed); rendering hints there land with
 * the Phase 3 handler work.
 */
public final class ResponseFormatInstructions {

    private ResponseFormatInstructions() {
    }

    /** One instruction line for the requested format, or an empty string for null (handler default). */
    public static String forFormat(ResponseFormat format) {
        if (format == null) {
            return "";
        }
        return switch (format) {
            case TABLE -> "\nFORMAT: Render the data as a markdown table.";
            case SUMMARY -> "\nFORMAT: Provide a short summary paragraph (3 sentences maximum).";
            case BULLETS -> "\nFORMAT: Answer as a concise bulleted list.";
            case DETAILED -> "\nFORMAT: Provide a detailed answer covering all available data points.";
            case COMPARISON -> "\nFORMAT: Present the entities side by side (markdown table with one column per entity).";
            case SHORT -> "\nFORMAT: Answer in at most 2 short sentences.";
        };
    }
}
