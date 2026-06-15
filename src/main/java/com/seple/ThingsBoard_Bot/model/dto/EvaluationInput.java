package com.seple.ThingsBoard_Bot.model.dto;

import lombok.Builder;
import lombok.Value;

/**
 * The inputs fed to the LLM-judge ({@code ResponseEvaluationService}) for a single answer.
 *
 * <p>These map 1:1 onto the placeholders in {@code prompts/evaluation-prompt.txt}. The structured
 * context JSON is the EXACT context the bot was given, so the judge audits the answer against the
 * same source of truth the bot saw.
 */
@Value
@Builder
public class EvaluationInput {

    /** Authorized customer tenant prefix (e.g. BOI, SEPL) -> [CUSTOMER_PREFIX]. */
    String customerPrefix;

    /** Active branch in session memory -> [ACTIVE_BRANCH_ID]. */
    String activeBranchId;

    /** The user's question -> [USER_QUESTION]. */
    String userQuestion;

    /** The source-of-truth context JSON the bot was given -> [STRUCTURED_CONTEXT_JSON]. */
    String structuredContextJson;

    /** Resolved intent name -> [METADATA_INTENT]. */
    String intent;

    /** Target branch name/technical id -> [METADATA_BRANCH]. */
    String targetBranch;

    /** Target subsystem (cctv, ias, ...) -> [METADATA_SYSTEM]. */
    String targetSystem;

    /** Whether this was a global (multi-branch) query -> [IS_GLOBAL]. */
    boolean global;

    /** DETERMINISTIC / LLM_FALLBACK -> [EXECUTION_PATH]. */
    String executionPath;

    /** The final answer sent to the user (including the suggestions block) -> [BOT_ANSWER]. */
    String botAnswer;
}
