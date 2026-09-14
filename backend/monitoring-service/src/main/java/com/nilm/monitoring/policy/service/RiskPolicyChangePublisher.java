package com.nilm.monitoring.policy.service;

import com.nilm.monitoring.domain.RiskPolicyChange;
import com.nilm.monitoring.domain.RiskPolicyChangeStatus;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeCommand;
import com.nilm.monitoring.policy.repository.RiskPolicyChangeRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RiskPolicyChangePublisher {
    private final RiskPolicyChangeRepository changes;
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public RiskPolicyChangePublisher(RiskPolicyChangeRepository changes,
            KafkaTemplate<String, Object> kafka,
            @Value("${app.kafka.risk-policy-change-request-topic:risk-policy.change.request.v1}") String topic) {
        this.changes = changes;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.policy.change-publish-delay-ms:5000}")
    public void publishPending() {
        List<RiskPolicyChange> pending = changes.findByStatusOrderByCreatedAt(
                RiskPolicyChangeStatus.PENDING, PageRequest.of(0, 100));
        pending.forEach(change -> kafka.send(topic, change.getId().toString(),
                new RiskPolicyChangeCommand(change.getId(), change.getWarningThreshold(),
                        change.getDangerThreshold(), change.getMinDurationSeconds(),
                        change.getChangeReason())));
    }
}
