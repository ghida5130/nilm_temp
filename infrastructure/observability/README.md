### 간편 실행 (local)

1. 기존 서비스 실행하여 nilm-net 네트워크 활성화
2. observability 폴더 내의 .env.example 파일을 복사하여 .env로 생성
3. 아래 명령어를 observability 폴더에서 실행

```
docker compose --env-file .env up -d prometheus blackbox-exporter grafana
```

4. 링크를 통해 웹 대시보드 접속

- Prometheus: http://localhost:19090
- Targets: http://localhost:19090/classic/targets
- Grafana: http://localhost:13001

---

# Observability

EC2-A에서 Prometheus·Grafana·Blackbox Exporter를 실행하는 관측 스택.
EC2-A 서비스는 Docker 네트워크, EC2-B의 AI/Kafka/PostgreSQL은 사설 IP를 통해 수집.
기존 Compose 파일 수정 없이 독립 실행하거나 추후 기존 Compose에 병합 가능.

## 수집 범위

| 구성               | 수집 내용                                                                         |
| ------------------ | --------------------------------------------------------------------------------- |
| Prometheus         | 15초 주기 수집, 15일 또는 5GB 중 먼저 도달하는 보존 한도                          |
| AI `/metrics`      | 단계별 지연, E2E, 처리 상태, DLQ, 오류, Consumer Lag, 모델 정보, 런타임 기본 지표 |
| Blackbox Exporter  | 기존 HTTP health/readiness의 성공 여부·시간·상태 코드, TCP 연결 여부·시간         |
| Grafana            | 데이터소스와 대시보드 3개 자동 등록                                               |
| cAdvisor 선택 구성 | 같은 Linux Docker 호스트의 컨테이너 CPU·메모리·네트워크                           |

기본 ec2-a 대상: EC2-B의 AI health/ready 및 metrics, Kafka 9092/PostgreSQL 5432 TCP. EC2-A의 Spring 3개 서비스 readiness, Keycloak readiness, frontend healthz, Redis 6379/Mosquitto 8883 TCP.
Mosquitto probe는 TCP 연결까지만 확인하며 TLS 인증서나 MQTT 인증은 검증하지 않음.
선택 cAdvisor는 EC2-A 컨테이너만 관측. EC2-B의 mqtt-kafka-bridge 등 컨테이너 자원은 별도 원격 Exporter 구성이 필요하며 이번 구성에 포함하지 않음.
DB 쿼리 성능, Kafka 전체 Consumer Group의 committed lag, JVM 업무 지표, MQTT 메시지 통계는 이번 범위에 포함하지 않음.

## EC2-A에서 실행

기존 `infrastructure/ec2-a` 스택이 실행되어 `nilm-a_default` 네트워크가 있어야 함.
기존 Compose와 합치지 않고 이 폴더에서 별도 프로젝트로 실행.

```sh
cd infrastructure/observability
cp .env.ec2-a.example .env
```

`.env`의 `GRAFANA_ADMIN_PASSWORD`와 `EC2_B_PRIVATE_IP` 입력. 비어 있으면 Compose 실행 거부.
`EC2_B_PRIVATE_IP`는 실제 EC2-B 사설 IPv4 주소. Prometheus와 Blackbox 컨테이너에 `ec2-b.internal` 호스트명으로 매핑.
Prometheus 대상 JSON 자체는 환경변수를 치환하지 않으므로 고정 호스트 별칭 사용.

```sh
docker compose up -d
docker compose ps
```

- Grafana: http://localhost:13001 (`admin` / 지정 비밀번호)
- Prometheus: http://localhost:19090
- 수집 상태: http://localhost:19090/classic/targets
- Grafana의 `NILM Observability` 폴더에서 대시보드 확인

원격 접근: `ssh -L 13001:127.0.0.1:13001 -L 19090:127.0.0.1:19090 user@ec2-a` 후 로컬 브라우저에서 위 주소 사용.

포트는 루프백에만 바인딩. Prometheus 19090과 Grafana 13001은 프로젝트의 기존 포트와 충돌을 피하기 위한 호스트 포트.
Exporter는 호스트 포트를 노출하지 않음. Prometheus/Grafana 데이터는 named volume에 보존.
Grafana 초기 관리자 비밀번호 환경변수는 기존 DB에 생성된 계정 비밀번호를 자동 변경하지 않음.

## Linux 컨테이너 자원 수집

Linux의 일반적인 rootful Docker와 `/var/lib/docker` 경로 기준.
cAdvisor는 호스트 파일시스템 및 Docker 메타데이터 읽기와 privileged 권한이 필요하므로 별도 파일로 분리.
기본 스택에는 이러한 호스트 마운트가 없음.

```sh
docker compose -f compose.yaml -f compose.resources.yaml up -d
```

Prometheus의 resources 디렉터리 마운트가 활성 대상 파일로 교체되어 cAdvisor 수집 시작.
기본 실행에서는 빈 대상 목록을 사용하므로 cAdvisor가 불필요하게 DOWN으로 표시되지 않음.
Docker Desktop/WSL은 Linux VM 내부만 관측하며 호스트 경로·장치가 다를 수 있음. 기본 스택부터 실행하고 Linux 서버에서 자원 수집 구성 사용 권장.
rootless Docker나 다른 data-root에서는 이 폴더의 `compose.resources.yaml` 마운트를 실제 경로에 맞춰 조정.
자원 대시보드에는 Compose project/service 라벨이 있는 컨테이너만 표시.

선택 구성을 사용했다면 종료할 때도 같은 파일 목록 사용.

```sh
docker compose -f compose.yaml -f compose.resources.yaml down
```

## EC2-B AI 수집 포트 연결

현재 EC2-B의 AI 서비스는 컨테이너 내부 8000만 사용하므로 EC2-A에서 직접 접근할 수 없음.
`compose.ec2-b.override.yaml`은 기존 AI 서비스의 8000을 EC2-B 사설 IP에 바인딩하는 추가 파일.
이 파일만 단독 실행하지 말고 기존 EC2-B Compose와 병합. 기존 파일 자체는 변경하지 않음.
EC2-B에서 기존 배포용 환경변수와 CONFIG_ROOT를 사용하여 실행. 아래 명령의 작업 디렉터리는 기존 `infrastructure/ec2-b`.

```sh
docker compose -p nilm-b --env-file .env -f compose.yaml -f ../observability/compose.ec2-b.override.yaml up -d --no-deps realtime-analysis-service
```

적용 시 AI 컨테이너가 재생성될 수 있음. 이후 배포에도 override를 포함해야 포트 설정 유지.
EC2-B 보안 그룹에서 EC2-A 보안 그룹 또는 사설 IP에 대해서만 TCP 8000 허용 필요.
TCP probe를 위해 5432/9092도 EC2-A에서 접근 가능해야 함. 이 두 포트는 기존 EC2-B Compose에 사설 IP 바인딩이 있음.
8000에서는 metrics뿐 아니라 health/ready도 제공하므로 인터넷에 공개하지 않음.
이번 변경은 파일 구성만 제공하며 서버 배포와 보안 그룹 변경은 수행하지 않음.

## 추후 EC2-A Compose에 병합

상대 경로 기준이 첫 Compose 파일로 바뀌므로 `OBSERVABILITY_ROOT`를 EC2-A 서버의 이 폴더 절대 경로로 설정.
기존 프로젝트 이름 `nilm-a`를 유지하고 기존 배포 환경변수에 관측용 환경변수를 함께 제공.
다음은 기존 `infrastructure/ec2-a`에서 실행하는 예시. 실제 배포의 환경 파일 경로와 CONFIG_ROOT 유지.

```sh
export OBSERVABILITY_ROOT=/absolute/path/to/infrastructure/observability
docker compose -p nilm-a --env-file .env --env-file ../observability/.env -f compose.yaml -f ../observability/compose.yaml config --quiet
docker compose -p nilm-a --env-file .env --env-file ../observability/.env -f compose.yaml -f ../observability/compose.yaml up -d prometheus grafana blackbox-exporter
```

셸의 OBSERVABILITY_ROOT가 환경 파일의 `.`보다 우선. 관측용 네트워크는 `observability`라는 별도 키를 사용하여 기존 default 네트워크와 충돌 방지.
`application`은 이미 존재하는 `nilm-a_default`에 연결하므로 기존 EC2-A 스택을 먼저 실행.
독립 실행 중인 관측 스택이 있다면 먼저 그 프로젝트를 중지해 19090/13001 포트 충돌 방지.
프로젝트가 `nilm-observability`에서 `nilm-a`로 바뀌면 named volume 이름도 변경됨. 기존 이력을 유지하려면 볼륨 이전 또는 기존 볼륨 명시가 필요하며 자동 이전하지 않음.
자원 수집 포함 시 `-f ../observability/compose.resources.yaml`도 추가하고 cadvisor 서비스를 함께 실행.

## 로컬 개발용 대상

로컬에서는 `.env.example`, EC2-A에서는 `.env.ec2-a.example`을 `.env`로 복사.
로컬 예시는 `APPLICATION_NETWORK=nilm-net`, `TARGET_ENV=local`, `EC2_B_PRIVATE_IP=127.0.0.1` 사용.
로컬 대상 JSON은 EC2-B 별칭을 사용하지 않으며 `TARGET_ENV`로 대상 묶음 선택.

## AI 대시보드 해석

- 지연은 초 단위 Histogram의 bucket/sum/count 사용. p50/p95/p99 및 평균은 관측된 샘플 기준.
- 단계 지연은 단계 호출당 관측. 메시지 하나에서 이벤트를 여러 번 발행하면 여러 관측 발생.
- `processed`는 추론 없이 버퍼 적재 후 커밋한 입력도 포함.
- E2E는 `snapshot.published_at - snapshot.observed_at`. published_at은 Snapshot 생성 시각이므로 Kafka ACK 시간은 제외. ACK는 `snapshot_publish_ack` 단계로 확인.
- Consumer Lag은 캐시 high watermark와 현재 position의 차이. committed offset 기준 Lag과 다름. 서로 다른 consumer group을 추가할 경우 그룹별 대상을 분리해야 합계가 혼합되지 않음.
- 모델 정보는 Manifest 이름과 버전이며 현재 FakePredictor를 실제 모델로 바꾸거나 모델 준비 상태를 검증하지 않음.
- 최초 DLQ/오류 발생 전, 버퍼 준비 전, 관측 없는 단계는 `No data`가 정상일 수 있음. 0으로 강제 보정해 수집 장애를 숨기지 않음.
- rate는 최소 두 수집 샘플 필요. 시작 직후 약 30~60초 후 확인. Histogram 관측이 없으면 분위수는 비어 있을 수 있음.
- AI instance 선택기로 여러 인스턴스 중 조회 대상 선택.

## 대상 추가 및 설정 반영

`prometheus/targets/<환경>/ai.json`, `http.json`, `tcp.json`에 target과 service/environment 라벨 정의.
같은 job 안에서 대상이 중복되지 않게 유지. 대상 JSON 변경은 최대 30초 후 자동 반영.
Prometheus 설정 변경은 `docker compose restart prometheus`, Blackbox 설정 변경은 `docker compose restart blackbox-exporter`.
Grafana dashboard JSON은 약 30초 후 자동 반영, provisioning 설정 변경은 `docker compose restart grafana`.
대시보드는 파일에서 관리하며 UI 저장은 비활성화.

## 확인 방법 및 제한

1. Prometheus Targets에서 `ai-analysis`가 UP인지 확인.
2. `nilm_analysis_model_info`와 `nilm_analysis_messages_total` 조회.
3. 기존 입력 메시지 처리 후 AI 지연 및 처리량 패널 확인.
4. `probe_success`로 각 HTTP/TCP 대상의 상태 확인. `up`은 Exporter 호출 성공이지 대상 서비스 정상 여부가 아님.
5. 401/403이면 인증 설정, 503이면 readiness 의존성, DNS 오류면 네트워크/대상 주소 확인.

현재 구현에는 외부 폴더 수정이 필요하지 않음. 후속 JVM/업무 지표 수집에는 서비스 의존성·endpoint 노출 변경이 필요할 수 있으며 이번 작업에서는 적용하지 않음.
AI E2E를 Kafka ACK 완료까지로 변경하거나 Consumer Lag을 committed 기준으로 바꾸려면 AI 코드 변경이 필요하며 현재 의미 그대로 표시.
알림 규칙과 외부 알림 전송은 미구성.
빌드 및 실행 테스트는 수행하지 않음. 환경별 실제 수집 성공은 실행 후 위 절차로 확인 필요.

## 참고

- [Prometheus 다중 대상 Exporter 구성](https://prometheus.io/docs/guides/multi-target-exporter/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [cAdvisor 실행 구성](https://github.com/google/cadvisor/blob/master/README.md)
