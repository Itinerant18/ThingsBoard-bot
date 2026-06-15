package com.seple.ThingsBoard_Bot.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A specific context-vs-answer mismatch reported by the LLM judge. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactualContradiction {

    @JsonProperty("context_value")
    private String contextValue;

    @JsonProperty("bot_value")
    private String botValue;
}
