package io.logisticsos.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void rejectsInvalidTenantIds() {
        assertThatThrownBy(() -> TenantId.from("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantId.from("AcMe_01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantId.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizesCase() {
        assertThat(TenantId.from("AcME-01").value()).isEqualTo("acme-01");
    }

    @Test
    void scopeBindsAndRestores() {
        TenantContext.runAs(TenantId.from("acme"), () -> {
            assertThat(TenantContext.require().value()).isEqualTo("acme");
        });
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void nestedScopeRestoresOuterTenant() {
        TenantContext.runAs(TenantId.from("outer"), () -> {
            try (TenantContext.TenantScope ignored = TenantContext.scope(TenantId.from("inner"))) {
                assertThat(TenantContext.require().value()).isEqualTo("inner");
            }
            assertThat(TenantContext.require().value()).isEqualTo("outer");
        });
    }

    @Test
    void requiresTenantWhenUnbound() {
        TenantContext.clear();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staysThreadLocalAcrossParallelWork() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            String tenant = "tenant-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    TenantContext.runAs(TenantId.from(tenant), () -> {
                        assertThat(TenantContext.require().value()).isEqualTo(tenant);
                    });
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(TenantContext.current()).isEmpty(); // main thread never bound
    }
}