package io.logisticsos.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void traceparentFormatIsW3cCompliant() {
        String traceId = Trace.newTraceId();
        String spanId = Trace.newSpanId();

        String parent = Trace.traceparent(traceId, spanId);

        assertThat(parent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(Trace.isValidTraceId(traceId)).isTrue();
        assertThat(Trace.isValidSpanId(spanId)).isTrue();
        assertThat(Trace.traceparent(Trace.newTraceId(), Trace.newSpanId())).isNotEqualTo(parent);
    }

    @Test
    void idsAreUniquePerCall() {
        assertThat(Trace.newTraceId()).isNotEqualTo(Trace.newTraceId());
        assertThat(Trace.newSpanId()).isNotEqualTo(Trace.newSpanId());
        assertThat(Trace.newCorrelationId()).isNotEqualTo(Trace.newCorrelationId());
    }

    @Test
    void mdcScopeSetsAndRestores() {
        MDC.put("existing", "kept");
        try (Mdc.CloseableScope ignored = Mdc.scope(Mdc.TENANT_ID, "acme")) {
            assertThat(MDC.get(Mdc.TENANT_ID)).isEqualTo("acme");
            try (Mdc.CloseableScope nested = Mdc.scope(Mdc.TENANT_ID, "other")) {
                assertThat(MDC.get(Mdc.TENANT_ID)).isEqualTo("other");
            }
            assertThat(MDC.get(Mdc.TENANT_ID)).isEqualTo("acme");
        }
        assertThat(MDC.get(Mdc.TENANT_ID)).isNull();
        assertThat(MDC.get("existing")).isEqualTo("kept");
    }

    @Test
    void nullValueClearsKey() {
        MDC.put(Mdc.TRACE_ID, "abc");
        try (Mdc.CloseableScope ignored = Mdc.scope(Mdc.TRACE_ID, null)) {
            assertThat(MDC.get(Mdc.TRACE_ID)).isNull();
        }
        assertThat(MDC.get(Mdc.TRACE_ID)).isEqualTo("abc");
    }
}