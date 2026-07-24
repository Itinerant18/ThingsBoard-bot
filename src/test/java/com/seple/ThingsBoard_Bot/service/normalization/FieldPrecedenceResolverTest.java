package com.seple.ThingsBoard_Bot.service.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;

/**
 * Gateway-state resolution must key off the fields devices actually emit. Real fleet payloads carry
 * {@code status_device_gateway_status} ("Online"/"Fault") and {@code gatewayStatus_SYSTEM ON}
 * ("true"/"false") — never the legacy {@code gateway_sts}. The bare {@code gatewayStatus} is a JSON
 * blob that cannot be classified. Regression guard for branches (e.g. BOI-LILUAH) landing in the
 * Unknown bucket while fully online.
 */
class FieldPrecedenceResolverTest {

    private final FieldPrecedenceResolver resolver =
            new FieldPrecedenceResolver(new ValueNormalizer());

    @Test
    void resolvesOnlineFromStatusDeviceGatewayStatus() {
        FieldPrecedenceResolver.ResolvedField r = resolver.resolveGatewayState(Map.of(
                "status_device_gateway_status", "Online"));
        assertEquals(NormalizedState.ONLINE, r.state());
        assertEquals("status_device_gateway_status", r.sourceField());
    }

    @Test
    void resolvesOnlineFromSystemOnFlagWhenCleanStatusAbsent() {
        FieldPrecedenceResolver.ResolvedField r = resolver.resolveGatewayState(Map.of(
                "gatewayStatus_SYSTEM ON", "true"));
        assertEquals(NormalizedState.ONLINE, r.state());
        assertEquals("gatewayStatus_SYSTEM ON", r.sourceField());
    }

    @Test
    void jsonBlobGatewayStatusAloneDoesNotClassify() {
        // The unparseable blob must not shadow the real keys — with no other signal it stays Unknown.
        FieldPrecedenceResolver.ResolvedField r = resolver.resolveGatewayState(Map.of(
                "gatewayStatus", "{\"SYSTEM ON\":\"true\",\"MAINS ON\":\"false\"}"));
        assertEquals(NormalizedState.UNKNOWN, r.state());
    }

    @Test
    void liluahShapePrefersCleanStatusOverBlob() {
        // Exact BOI-LILUAH shape: blob present AND clean field present -> must resolve ONLINE.
        FieldPrecedenceResolver.ResolvedField r = resolver.resolveGatewayState(Map.of(
                "gatewayStatus", "{\"SYSTEM ON\":\"true\",\"MAINS ON\":\"true\"}",
                "gatewayStatus_SYSTEM ON", "true",
                "status_device_gateway_status", "Online"));
        assertEquals(NormalizedState.ONLINE, r.state());
        assertEquals("status_device_gateway_status", r.sourceField());
    }

    @Test
    void faultStatusResolvesToFaultNotOffline() {
        FieldPrecedenceResolver.ResolvedField r = resolver.resolveGatewayState(Map.of(
                "status_device_gateway_status", "Fault"));
        assertEquals(NormalizedState.FAULT, r.state());
    }
}
