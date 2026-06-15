package com.seple.ThingsBoard_Bot.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single problem found by the LLM judge.
 *
 * <p>{@code category} and {@code severity} are kept as free-form strings (rather than enums) so an
 * unexpected value from the judge never breaks deserialization of the whole report.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationIssue {

    /** DATA_GROUNDING | SECURITY | HEADER_COMPLIANCE | INTENT_ALIGNMENT | FORMATTING. */
    private String category;

    private String description;

    @JsonProperty("factual_contradiction")
    private FactualContradiction factualContradiction;
}
