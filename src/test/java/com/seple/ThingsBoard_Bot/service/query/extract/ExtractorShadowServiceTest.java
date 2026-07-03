package com.seple.ThingsBoard_Bot.service.query.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ExtractorShadowServiceTest {

    private IntentExtractor intentExtractor;
    private ExtractorConfig config;
    private SimpleMeterRegistry meterRegistry;
    private ExtractorShadowService service;

    @BeforeEach
    void setUp() {
        intentExtractor = mock(IntentExtractor.class);
        config = new ExtractorConfig();
        meterRegistry = new SimpleMeterRegistry();
        service = new ExtractorShadowService(intentExtractor, config, meterRegistry, new BranchAliasIndex());
    }

    private ResolvedQuery resolved(QueryIntent intent, String technicalId, String branchName) {
        BranchSnapshot branch = null;
        if (technicalId != null) {
            branch = BranchSnapshot.builder()
                    .identity(BranchIdentity.builder().technicalId(technicalId).branchName(branchName).build())
                    .build();
        }
        return ResolvedQuery.builder().intent(intent).targetBranch(branch).originalQuestion("q").build();
    }

    private ExtractionResult extraction(QueryIntent intent, double confidence, String... entities) {
        return new ExtractionResult(List.of(new ExtractedIntent(intent, List.of(entities), null, confidence)));
    }

    private double count(String outcome) {
        var counter = meterRegistry.find("extractor.agreement").tag("outcome", outcome).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void offModeNeverInvokesExtractor() {
        config.setMode(ExtractorConfig.Mode.OFF);
        service.maybeShadow("battery voltage of Malda Town", List.of(),
                resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));
        verifyNoInteractions(intentExtractor);
    }

    @Test
    void activeModeDoesNotShadow() {
        config.setMode(ExtractorConfig.Mode.ACTIVE);
        service.maybeShadow("battery voltage of Malda Town", List.of(),
                resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));
        verifyNoInteractions(intentExtractor);
    }

    @Test
    void shadowModeRunsComparisonAsync() {
        config.setMode(ExtractorConfig.Mode.SHADOW);
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.BATTERY_VOLTAGE, 0.95, "Malda Town"));

        service.maybeShadow("battery voltage of Malda Town", List.of(),
                resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        verify(intentExtractor, timeout(2000)).extract(anyString(), anyList());
    }

    @Test
    void agreementCountsMatch() {
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.BATTERY_VOLTAGE, 0.95, "Malda Town"));

        service.compare("q", List.of(), resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        assertEquals(1.0, count("match"));
    }

    @Test
    void intentMismatchCounted() {
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.CCTV_STATUS, 0.9, "Malda Town"));

        service.compare("q", List.of(), resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        assertEquals(1.0, count("intent_mismatch"));
    }

    @Test
    void entityMismatchCounted() {
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.BATTERY_VOLTAGE, 0.9, "Bhubaneshwar"));

        service.compare("q", List.of(), resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        assertEquals(1.0, count("entity_mismatch"));
    }

    @Test
    void emptyExtractionCounted() {
        when(intentExtractor.extract(anyString(), anyList())).thenReturn(ExtractionResult.empty());

        service.compare("q", List.of(), resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        assertEquals(1.0, count("empty"));
    }

    @Test
    void branchlessResolverAgreesWithEntityFreeExtraction() {
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.GLOBAL_OVERVIEW, 0.95));

        service.compare("q", List.of(), resolved(QueryIntent.GLOBAL_OVERVIEW, null, null));

        assertEquals(1.0, count("match"));
    }

    @Test
    void typoEntityStillAgreesViaCompaction() {
        // Extractor copies entities verbatim; "MALDATOWN" compacts onto BOI-MALDATOWN.
        when(intentExtractor.extract(anyString(), anyList()))
                .thenReturn(extraction(QueryIntent.BATTERY_VOLTAGE, 0.9, "MALDATOWN"));

        service.compare("q", List.of(), resolved(QueryIntent.BATTERY_VOLTAGE, "BOI-MALDATOWN", "MALDA TOWN"));

        assertEquals(1.0, count("match"));
    }
}
