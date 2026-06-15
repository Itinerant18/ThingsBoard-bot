package com.seple.ThingsBoard_Bot.model.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured verdict returned by the LLM judge ({@code ResponseEvaluationService}).
 *
 * <p>Mirrors the JSON schema in {@code prompts/evaluation-prompt.txt}. Deserialized from the judge's
 * output; never built by hand in production code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationReport {

    @JsonProperty("problem_identified")
    private boolean problemIdentified;

    /** CRITICAL | MAJOR | MINOR | NONE. */
    private String severity;

    private List<EvaluationIssue> issues = new ArrayList<>();
}
