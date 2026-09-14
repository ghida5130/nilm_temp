package com.nilm.monitoring.policy.api;

import com.nilm.monitoring.policy.dto.RiskPolicyChangeResult;
import com.nilm.monitoring.policy.service.RiskPolicyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class RiskPolicyChangeResultListener {
    private final RiskPolicyService service;

    public RiskPolicyChangeResultListener(RiskPolicyService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.risk-policy-change-result-topic:risk-policy.change.result.v1}",
            containerFactory = "riskPolicyResultKafkaListenerContainerFactory",
            autoStartup = "${app.kafka.consumer.enabled:true}")
    public void consume(ConsumerRecord<String, RiskPolicyChangeResult> record, Acknowledgment acknowledgment) {
        RiskPolicyChangeResult result = record.value();
        if (result == null || result.changeId() == null
                || record.key() == null || !record.key().equals(result.changeId().toString())) {
            throw new IllegalArgumentException("Risk policy result key/payload is invalid");
        }
        service.completeChange(result);
        acknowledgment.acknowledge();
    }
}
