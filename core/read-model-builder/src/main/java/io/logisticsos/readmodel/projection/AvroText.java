package io.logisticsos.readmodel.projection;

/** Small text extraction helper for Avro generic values. */
final class AvroText {

    private AvroText() {
    }

    static String of(Object value) {
        return value == null ? null : value.toString();
    }
}