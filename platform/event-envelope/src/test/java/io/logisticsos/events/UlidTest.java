package io.logisticsos.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UlidTest {

    static Stream<String> validUlids() {
        return Stream.of(
                "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "01ARZ3NDEKTSV4RRFFQ69G5FAU",
                Ulid.generate(1_800_000_000_000L));
    }

    @ParameterizedTest
    @MethodSource("validUlids")
    void acceptsValidUlids(String value) {
        assertThat(Ulid.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("validUlids")
    void rejectsInvalidUlids(String ignored) {
        assertThat(Ulid.isValid(null)).isFalse();
        assertThat(Ulid.isValid("")).isFalse();
        assertThat(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5F")).isFalse(); // too short
        assertThat(Ulid.isValid("01ARZ3NDEKTSV4ORFFQ69G5FAV")).isFalse(); // O excluded from Crockford
        assertThat(Ulid.isValid("01ARZ3NDEKTSV4IRFFQ69G5FAV")).isFalse(); // I excluded
        assertThat(Ulid.isValid("01arZ3NDEKTSV4RRFFQ69G5FAV")).isFalse(); // lowercase
    }

    @Test
    void timestampsRoundTrip() {
        long[] times = {0L, 1L, 1_700_000_000_000L, 1_800_000_000_000L};
        for (long t : times) {
            long parsed = Ulid.timestampOf(Ulid.generate(t));
            assertThat(parsed).as("ts=%d", t).isEqualTo(t);
        }
    }

    @Test
    void chronologicallyOrdered() {
        long t0 = 1_700_000_000_000L;
        List<String> ids = List.of(Ulid.generate(t0), Ulid.generate(t0 + 1), Ulid.generate(t0 + 2));
        assertThat(ids).isSorted();
    }

    @Test
    void randomWithinSameMillisecond() {
        long t = 1_700_000_000_000L;
        String a = Ulid.generate(t);
        String b = Ulid.generate(t);
        assertThat(a).isNotEqualTo(b);
        assertThat(Ulid.timestampOf(a)).isEqualTo(t);
        assertThat(Ulid.timestampOf(b)).isEqualTo(t);
    }
}