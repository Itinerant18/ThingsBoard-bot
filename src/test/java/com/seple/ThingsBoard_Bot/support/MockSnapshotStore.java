package com.seple.ThingsBoard_Bot.support;

import java.io.IOException;
import java.util.List;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.FullDataPayloadParser;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;

/**
 * Deterministic, offline branch snapshot source for tests. Backed by the fixture in
 * {@code fixtures/full_data_fixture.json} - runs in milliseconds, never touches live
 * ThingsBoard or Redis.
 */
public final class MockSnapshotStore {

    private static final String DEFAULT_FIXTURE = "fixtures/full_data_fixture.json";

    private MockSnapshotStore() {
    }

    public static List<BranchSnapshot> loadDefault() throws IOException {
        return load(DEFAULT_FIXTURE);
    }

    public static List<BranchSnapshot> load(String fixturePath) throws IOException {
        FullDataPayloadParser parser = new FullDataPayloadParser();
        BranchSnapshotMapper mapper = new BranchSnapshotMapper(
                new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());
        return parser.parse(FixtureLoader.load(fixturePath)).branches().values().stream()
                .map(mapper::map)
                .toList();
    }
}
