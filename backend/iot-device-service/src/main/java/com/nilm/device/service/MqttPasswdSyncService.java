package com.nilm.device.service;

import com.nilm.device.domain.DeviceCredential;
import com.nilm.device.domain.DeviceStatus;
import com.nilm.device.repository.DeviceCredentialRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB(원본) → Mosquitto passwd 파일(파생물) 동기화.
 *
 * 규칙:
 * - 이 서비스가 관리하는 계정은 "dev_" 접두사 네임스페이스뿐이다.
 *   기존 파일의 다른 계정(simulator_user, 브릿지 등)은 그대로 보존한다.
 * - 동기화 대상: 폐기되지 않은 계정 중 기기 상태가 REGISTERED/ACTIVE인 것.
 *   (SUSPENDED/RETIRED는 파일에서 빠짐 = 브로커 접속 차단)
 * - 원자적 쓰기: 임시 파일에 쓴 뒤 원본 위로 이동.
 */
@Service
public class MqttPasswdSyncService {

    private static final Logger log = LoggerFactory.getLogger(MqttPasswdSyncService.class);
    private static final String MANAGED_PREFIX = "dev_";
    private static final Set<DeviceStatus> SYNC_STATUSES =
            Set.of(DeviceStatus.REGISTERED, DeviceStatus.ACTIVE);

    public record SyncResult(int managedAccounts, int preservedAccounts, String passwdPath,
                             boolean reloaded, String note) {
    }

    private final DeviceCredentialRepository credentialRepository;
    private final Path passwdPath;
    private final boolean reloadEnabled;
    private final List<String> reloadCommand;

    public MqttPasswdSyncService(
            DeviceCredentialRepository credentialRepository,
            @Value("${app.mqtt.passwd-path:../../infrastructure/mqtt/config/passwd}") String passwdPath,
            @Value("${app.mqtt.reload-enabled:false}") boolean reloadEnabled,
            @Value("${app.mqtt.reload-command:docker kill --signal=HUP mosquitto-broker}") String reloadCommand) {
        this.credentialRepository = credentialRepository;
        this.passwdPath = Path.of(passwdPath).toAbsolutePath().normalize();
        this.reloadEnabled = reloadEnabled;
        this.reloadCommand = List.of(reloadCommand.split("\\s+"));
    }

    @Transactional(readOnly = true)
    public SyncResult sync() throws IOException {
        // 1. 기존 파일에서 우리 관리 밖 계정 라인 보존
        List<String> preserved = new ArrayList<>();
        if (Files.exists(passwdPath)) {
            for (String line : Files.readAllLines(passwdPath, StandardCharsets.UTF_8)) {
                if (!line.isBlank() && !line.startsWith(MANAGED_PREFIX)) {
                    preserved.add(line);
                }
            }
        }

        // 2. DB에서 동기화 대상 계정 라인 생성
        List<DeviceCredential> targets = credentialRepository.findSyncTargets(SYNC_STATUSES);
        List<String> lines = new ArrayList<>(preserved);
        int managed = 0;
        for (DeviceCredential c : targets) {
            if (c.getMosquittoHash() == null) {
                log.warn("mosquitto_hash 없음 (V3 이전 발급 계정) — 재발급 필요: {}", c.getMqttUsername());
                continue;
            }
            lines.add(c.getMqttUsername() + ":" + c.getMosquittoHash());
            managed++;
        }

        // 3. 원자적 쓰기 (임시 파일 → 이동)
        Path tmp = passwdPath.resolveSibling(passwdPath.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        Files.move(tmp, passwdPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("passwd 동기화 완료: 관리 계정 {}건, 보존 계정 {}건 -> {}", managed, preserved.size(), passwdPath);

        // 4. 브로커 리로드 (옵션)
        boolean reloaded = false;
        String note = "reload-disabled: 수동 리로드 필요 -> " + String.join(" ", reloadCommand);
        if (reloadEnabled) {
            note = runReload();
            reloaded = note == null;
            if (reloaded) {
                note = "브로커 SIGHUP 리로드 완료";
            }
        }
        return new SyncResult(managed, preserved.size(), passwdPath.toString(), reloaded, note);
    }

    /** 성공 시 null, 실패 시 사유 반환. */
    private String runReload() {
        try {
            Process p = new ProcessBuilder(reloadCommand).redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "리로드 명령 타임아웃";
            }
            if (p.exitValue() != 0) {
                return "리로드 명령 실패(exit " + p.exitValue() + "): "
                        + new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "리로드 중단됨";
        } catch (IOException e) {
            return "리로드 명령 실행 불가: " + e.getMessage();
        }
    }
}
