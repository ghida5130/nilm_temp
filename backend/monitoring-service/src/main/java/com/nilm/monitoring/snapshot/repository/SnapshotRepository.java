package com.nilm.monitoring.snapshot.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.domain.RiskLevel;
import com.nilm.monitoring.snapshot.dto.ApplianceSnapshot;
import com.nilm.monitoring.snapshot.dto.MonitoringSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(SnapshotRepository.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SnapshotRepository(StringRedisTemplate redis, ObjectMapper mapper, Clock clock) {
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Optional<MonitoringSnapshot> find(String householdId) {
        String key = "monitoring:snapshot:" + householdId;
        try {
            Map<Object, Object> hash = redis.opsForHash().entries(key);
            if (hash.isEmpty()) return Optional.empty();
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) return Optional.empty();
            long revision = Long.parseLong(String.valueOf(hash.get("revision")));
            Instant observedAt = Instant.parse(String.valueOf(hash.get("observed_at")));
            JsonNode payload = mapper.readTree(String.valueOf(hash.get("payload")));
            Instant now = Instant.now(clock);
            Instant expiresAt = now.plusSeconds(ttl);
            if (!expiresAt.isAfter(now) || observedAt.isAfter(now.plusSeconds(30))) return Optional.empty();
            String dataStatus = text(payload, "dataStatus", "data_status");
            if (dataStatus == null || dataStatus.isBlank()) return Optional.empty();
            return Optional.of(new MonitoringSnapshot(householdId, revision, observedAt, expiresAt,
                    decimal(payload, "activePowerW", "active_power_w"),
                    integer(payload, "riskScore", "risk_score"),
                    risk(payload), instant(payload, "lastActivityAt", "last_activity_at"),
                    dataStatus, appliances(payload)));
        } catch (Exception e) {
            log.warn("Snapshot unavailable for household {}", householdId, e);
            return Optional.empty();
        }
    }

    private static List<ApplianceSnapshot> appliances(JsonNode payload) {
        JsonNode values = payload.path("appliances");
        if (!values.isArray()) return List.of();
        List<ApplianceSnapshot> result = new ArrayList<>();
        values.forEach(node -> result.add(new ApplianceSnapshot(
                text(node, "applianceType", "appliance_type"),
                node.path("isOn").asBoolean(node.path("is_on").asBoolean(false)),
                decimal(node, "probability", "probability"),
                decimal(node, "threshold", "threshold"),
                instant(node, "confirmedAt", "confirmed_at"))));
        return List.copyOf(result);
    }

    private static RiskLevel risk(JsonNode node) {
        String value = text(node, "riskLevel", "risk_level");
        return value == null || value.isBlank() ? null : RiskLevel.valueOf(value);
    }
    private static String text(JsonNode n, String camel, String snake) {
        JsonNode value = n.has(camel) ? n.get(camel) : n.get(snake);
        return value == null || value.isNull() ? null : value.asText();
    }
    private static BigDecimal decimal(JsonNode n, String camel, String snake) {
        JsonNode value = n.has(camel) ? n.get(camel) : n.get(snake);
        return value == null || !value.isNumber() ? null : value.decimalValue();
    }
    private static Integer integer(JsonNode n, String camel, String snake) {
        JsonNode value = n.has(camel) ? n.get(camel) : n.get(snake);
        return value == null || !value.isInt() ? null : value.intValue();
    }
    private static Instant instant(JsonNode n, String camel, String snake) {
        String value = text(n, camel, snake);
        return value == null ? null : Instant.parse(value);
    }
}
