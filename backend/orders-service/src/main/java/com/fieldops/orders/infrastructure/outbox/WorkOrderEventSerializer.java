package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.WorkOrderEvent;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class WorkOrderEventSerializer {

    public String toJson(WorkOrderEvent event) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(WorkOrderEvent.getClassSchema(), out);
            SpecificDatumWriter<WorkOrderEvent> writer = new SpecificDatumWriter<>(WorkOrderEvent.class);
            writer.write(event, encoder);
            encoder.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize WorkOrderEvent to JSON", e);
        }
    }

    public WorkOrderEvent fromJson(String json) {
        try {
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(WorkOrderEvent.getClassSchema(), json);
            SpecificDatumReader<WorkOrderEvent> reader = new SpecificDatumReader<>(WorkOrderEvent.class);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize WorkOrderEvent from JSON", e);
        }
    }
}
