package io.logisticsos.mdm;

import org.apache.avro.generic.GenericRecord;

/** Output port for golden-record changes (implemented by {@link MdmPublisher}; fake-able in tests). */
public interface ReferenceSink {

    void publishUpsert(MdmEntities entity, MdmRegistry.Entry entry, GenericRecord record, boolean active,
            String sourceSystem);
}