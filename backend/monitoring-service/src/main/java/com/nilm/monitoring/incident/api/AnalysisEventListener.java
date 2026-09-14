package com.nilm.monitoring.incident.api;

import com.nilm.monitoring.common.InvalidAnalysisEventException;
import com.nilm.monitoring.incident.dto.AnalysisEventMessage;
import com.nilm.monitoring.incident.service.AnalysisEventIngestService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// Kafka 메시지 수신 (event)
@Component
public class AnalysisEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEventListener.class);

    private final Validator validator;
    private final AnalysisEventIngestService ingestService;

    public AnalysisEventListener(Validator validator, AnalysisEventIngestService ingestService) {
        this.validator = validator;
        this.ingestService = ingestService;
    }

    @KafkaListener(
            topics = "${app.kafka.analysis-event-topic}",
            containerFactory = "analysisEventKafkaListenerContainerFactory",
            autoStartup = "${app.kafka.consumer.enabled:true}")
    public void consume(ConsumerRecord<String, AnalysisEventMessage> record, Acknowledgment acknowledgment) {
        AnalysisEventMessage message = record.value();
        if (message == null) {
            throw new InvalidAnalysisEventException("analysis event payload is null");
        }

        Set<ConstraintViolation<AnalysisEventMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new InvalidAnalysisEventException(violations.iterator().next().getMessage());
        }
        if (record.key() == null || !record.key().equals(message.householdId())) {
            throw new InvalidAnalysisEventException("Kafka key must equal household_id");
        }

        AnalysisEventIngestService.IngestResult result = ingestService.ingest(message);
        acknowledgment.acknowledge();
        log.debug("Processed analysis event {} with result {}", message.eventId(), result);
    }
}
