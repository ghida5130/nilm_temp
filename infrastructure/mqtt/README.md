# MQTT Broker와 시뮬레이터

Mosquitto 서비스 실행은 [local Compose](../local/compose.yaml) 또는 [EC2-A Compose](../ec2-a/compose.yaml)에서 관리한다. 이 폴더에서는 설정 파일과 테스트 발행 코드를 관리한다.

## 파일

- config/mosquitto.local.conf: 로컬 1883 리스너와 계정 인증.
- config/mosquitto.production.conf: 운영 8883 TLS 리스너와 계정 인증.
- config/passwd: 로컬 계정 해시 파일. Git 제외.
- config/mosquitto.conf: 이전 컨테이너 바인드 경로 보존용. 새 Compose는 사용하지 않는다.
- simulator/simulator.py: 로컬 및 대규모 데이터 발행 도구 (10초 피크 시연 모드 내장).
- simulator/waveform_viewer.html: 브라우저 기반 실시간 인터랙티브 시각화 대시보드.
- simulator/README.md: 시뮬레이터 전용 상세 명세 및 실행 가이드.

## 최초 실행

[infrastructure 실행 안내](../README.md)를 따라 local/.env를 준비한다. 기존 passwd가 있으면 그 계정의 실제 비밀번호를 MQTT_USER/MQTT_PASS에 넣는다.

새 passwd가 필요한 경우 local 폴더에서:

```powershell
.\Setup-Local.ps1 -InitializeMqtt
docker compose up -d mosquitto
```

초기화 스크립트는 비어 있지 않은 기존 passwd를 덮어쓰지 않는다. passwd는 비밀번호 해시를 담고 있으므로 원래 비밀번호를 읽어낼 수 없다.

## 데이터 흐름

```text
시뮬레이터/센서 → Mosquitto → MQTT–Kafka Bridge → Kafka
```

시뮬레이터는 v1/power/sim/{house}/main으로 발행한다. Bridge는 v1/power/sim/+/main을 구독하고 Kafka power.raw.v1에 전달한다. MQTT 토픽과 Kafka 토픽은 서로 다른 이름 공간이다.

새 구성의 전달 검증은 local 폴더에서:

```powershell
docker compose exec -T mqtt-kafka-bridge python smoke.py
```

기존 simulator.py는 자체 개발용 접속 설정을 갖고 있으며 Compose의 env를 자동으로 읽지 않는다. 실행 전에 해당 도구의 접속 설정을 확인한다.

## 운영 인증

EC2-A에는 운영 passwd와 서버 인증서·개인키를 배포한다. EC2-B Bridge에는 실제 MQTT 계정과 CA 파일을 제공한다. Bridge의 MQTT_HOST가 서버 인증서 SAN과 일치해야 한다.


# 추후 외부 ESP32 단말기 접속을 위한 TLS 통신 포트 (현재는 주석 처리)
# listener 8883
# certfile /etc/mosquitto/certs/server.crt
# keyfile /etc/mosquitto/certs/server.key

# =================================================================
# 2. Security (보안 및 인증)
# =================================================================
# 익명 접속 원천 차단 (반드시 false로 설정)
allow_anonymous false

# 인증에 사용할 비밀번호 파일 경로 지정
password_file /mosquitto/config/passwd

# =================================================================
# 3. Performance & Resource Control (성능 및 리소스 제어)
# =================================================================
# 동시 접속 클라이언트 수 제한 (OOM 방지, 가구 시뮬레이터 고려)
max_connections 500

# 브로커 단의 디스크 영구 저장 비활성화 (Kafka가 데이터 파이프라인 역할 담당)
persistence false

# 과도한 크기의 단일 메시지 차단 (네트워크 병목 방지, 256KB 제한)
message_size_limit 262144
```

---

### (3) `config/passwd` (인증 계정)
`mosquitto_passwd` 유틸리티에 의해 해시 암호화되어 관리되는 사용자 목록입니다.

| 사용자명 (`Username`)   | 역할 / 용도                                                      |
| :---------------------- | :--------------------------------------------------------------- |
| **`simulator_user`**    | 스마트홈/IoT 센서 데이터 시뮬레이터 발행(Publish)용 계정         |
| **`kafka_bridge_user`** | MQTT 메시지를 구독(Subscribe)하여 Kafka로 전달하는 브릿지용 계정 |

---

## 3. 최초 구축 및 실행 순서

터미널 환경(**Git Bash** 또는 **Windows CMD / PowerShell**)에 맞는 명령어를 확인하여 실행하세요.

### Step 1. 비밀번호 파일 초기화 & 컨테이너 실행

- **Windows CMD (명령 프롬프트)**
  ```cmd
  type nul > config\passwd
  docker-compose up -d
  ```

- **Git Bash (MINGW64)**
  ```bash
  touch config/passwd
  docker-compose up -d
  ```

- **PowerShell**
  ```powershell
  New-Item -ItemType File -Force -Path config\passwd
  docker-compose up -d
  ```

---

### Step 2. 사용자 계정 생성

> [!NOTE]
> - **CMD / PowerShell**: 컨테이너 내부 경로(`/mosquitto/config/passwd`)를 그대로 사용합니다.
> - **Git Bash**: Git Bash의 자동 경로 변환을 피하기 위해 슬래시 2개(`//mosquitto/config/passwd`)를 사용해야 합니다.

#### [Windows CMD / PowerShell 기준]
```cmd
:: 1. simulator_user 계정 등록 (비밀번호 대화형 입력)
docker exec -it mosquitto-broker mosquitto_passwd /mosquitto/config/passwd simulator_user

:: 2. kafka_bridge_user 계정 등록
docker exec -it mosquitto-broker mosquitto_passwd /mosquitto/config/passwd kafka_bridge_user
```

*또는 비밀번호를 한 번에 인자로 전달하는 방식 (`-b` 옵션):*
```cmd
docker exec mosquitto-broker mosquitto_passwd -b /mosquitto/config/passwd simulator_user <비밀번호>
docker exec mosquitto-broker mosquitto_passwd -b /mosquitto/config/passwd kafka_bridge_user <비밀번호>
```

#### [Git Bash 기준]
```bash
# 1. simulator_user 계정 등록
docker exec -it mosquitto-broker mosquitto_passwd //mosquitto/config/passwd simulator_user

# 2. kafka_bridge_user 계정 등록
docker exec -it mosquitto-broker mosquitto_passwd //mosquitto/config/passwd kafka_bridge_user
```

---

### Step 3. 브로커 재시작 (비밀번호 반영)
비밀번호 파일이 갱신되었으므로 Mosquitto 프로세스가 이를 인식하도록 재시작합니다.

```cmd
docker restart mosquitto-broker
```

---

## 4. 동작 테스트 및 검증 방법

### 방법 A. Docker 컨테이너 내부 CLI 도구로 송수신 테스트

#### 1) 터미널 1 : 구독 (Subscribe) - 메시지 대기
```cmd
docker exec -it mosquitto-broker mosquitto_sub -t "home/sensor/#" -u "kafka_bridge_user" -P "<비밀번호>" -v
```

#### 2) 터미널 2 : 발행 (Publish) - 메시지 전송

- **CMD (명령 프롬프트)**
  ```cmd
  docker exec -it mosquitto-broker mosquitto_pub -t "home/sensor/livingroom" -u "simulator_user" -P "<비밀번호>" -m "{\"temp\": 24.5, \"humidity\": 50}"
  ```

- **Git Bash**
  ```bash
  docker exec -it mosquitto-broker mosquitto_pub -t "home/sensor/livingroom" -u "simulator_user" -P "<비밀번호>" -m '{"temp": 24.5, "humidity": 50}'
  ```

*정상 동작 시 터미널 1에 `home/sensor/livingroom {"temp": 24.5, "humidity": 50}`이 출력됩니다.*

---

### 방법 B. 익명 접속 차단 검증
비밀번호 없이 접속을 시도하여 인증 실패(`Connection Refused`)가 발생하는지 확인합니다.

```cmd
:: 인증 없이 구독 시도 -> Connection error: Connection Refused: not authorised 발생 확인
docker exec -it mosquitto-broker mosquitto_sub -t "home/#"
```

---

## 5. 컨테이너 관리 명령어 모음

| 동작                 | Windows CMD / PowerShell             | Git Bash                             |
| :------------------- | :----------------------------------- | :----------------------------------- |
| **상태 확인**        | `docker ps -f name=mosquitto-broker` | `docker ps -f name=mosquitto-broker` |
| **로그 실시간 확인** | `docker logs -f mosquitto-broker`    | `docker logs -f mosquitto-broker`    |
| **컨테이너 재시작**  | `docker restart mosquitto-broker`    | `docker restart mosquitto-broker`    |
| **컨테이너 정지**    | `docker-compose down`                | `docker-compose down`                |
| **컨테이너 시작**    | `docker-compose up -d`               | `docker-compose up -d`               |

---

## 6. 터미널별 차이점 요약

| 구분                   | Windows CMD / PowerShell                       | Git Bash (MINGW64)                          |
| :--------------------- | :--------------------------------------------- | :------------------------------------------ |
| **빈 파일 생성**       | `type nul > config\passwd`                     | `touch config/passwd`                       |
| **컨테이너 내부 경로** | `/mosquitto/config/passwd` (그대로 사용)       | `//mosquitto/config/passwd` (`//` 2개 필수) |
| **JSON 인자 따옴표**   | `"{\"key\": \"value\"}"` (큰따옴표 이스케이프) | `'{"key": "value"}'` (작은따옴표)           |

---

## 7. 스마트홈 전력 데이터 시뮬레이터 (`simulator.py`)

스마트홈 내 각 가구 및 가전기기의 실시간 소비 전력(W)을 모사하여 Mosquitto MQTT 브로커로 실시간 발행(Publish)하는 Python 프로그램입니다.

### (1) 시뮬레이션 사양
- **대상 가구**: `H001` ~ `H010` (총 10개 가구)
- **계측 지점**: 가구별 스마트 미터 / **메인 분전반 (`main`)**
- **내부 모델링 (NILM AI 모델 학습 대상 6종 수동 가전 + 기저/미계측 부하)**:
  - **수동 활동 가전 6종**: 전기포트 (`kettle`), 인덕션 (`induction`), 전기다리미 (`iron`), 전자레인지 (`microwave`), 헤어드라이기 (`hair_dryer`), 진공 청소기 (`vacuum_cleaner`)
  - **기저 및 미계측 부하**: AI Hub 실측 분전반 미계측 부하(~65%) 및 냉장고 자동 컴프레서 주기(5~20분 가동/정지 반복, 70~100W) 모사
  - **가전 파형 합성 엔진 (AI 모델 추론 최적화)**:
    - **기동 돌입전류(Inrush Overshoot)**: 켜짐 직후 1~2초간 1.05~1.50배 피크 전력 및 순간 역률 저하 모사
    - **단속 운전(Duty Cycle)**: 인덕션/전기다리미의 서모스탯 온도 제어(가열 ON ↔ 휴지 OFF) 반복 모사
    - **시계열 완만 드리프트(AR-1)**: 전압 및 기저 대기전력이 독립 난수가 아닌 부드러운 시계열 곡선을 갖도록 1차 자기회귀 프로세스 적용
- **특징**:
  - 가구 내 각 가전들의 상태 전이 및 소비 전력/역률을 합산하여 **가구 전체 메인 분전반 총 교류 전력 특성(4특징 및 전압/전류)**을 산출 및 발행
  - NILM AI 모델 요구 스펙(다변량 4특징) 및 기존 데이터 파이프라인(Kafka Bridge, HDFS Loader) 호환성 유지
- **발행 토픽 규칙**: `v1/power/sim/{house}/main` (예: `v1/power/sim/H001/main`)
- **전송 메시지 스키마 (JSON, 신규 AI 4특징 및 레거시 이중 호환)**:
  ```json
  {
    "message_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "household_id": "H001",
    "device_id": "main",
    "measured_at": "2026-09-08T07:42:47.034Z",
    "active_power": 3590.2,
    "reactive_power": 412.5,
    "power_factor": 0.993,
    "current": 16.32,

    "house": "H001",
    "device": "main",
    "ts": "2026-09-08T07:42:47.034Z",
    "power_w": 3590.2,
    "voltage": 220.1,
    "apparent_power": 3613.8
  }
  ```

### (2) 시뮬레이터 환경 구축 및 실행 방법

1. **필수 라이브러리(`aiomqtt`) 설치**
   ```bash
   pip install -r simulator/requirements.txt
   ```

2. **시뮬레이터 구동 (CLI 옵션 지원)**
   - **커맨드라인 옵션 상세 (`python simulator/simulator.py --help`)**:
     | 옵션 | 단축키 | 기본값 | 설명 |
     | :--- | :--- | :--- | :--- |
     | `--scenario` | `-s` | `random` | 시나리오 모드 (`random`: 연속 확률 시뮬레이션, `peak`: 10초 3,000W+ 피크 시연 모드) |
     | `--houses` | `-n` | `10` | 시뮬레이션 대상 가구 수 (`H001` ~ `H{n:03d}`) |
     | `--interval` | `-i` | `1.0` | 데이터 발행 주기 (초 단위) |
     | `--hz` | | `None` | 가구당 초당 측정 횟수 (지정 시 `1/hz` 초로 자동 환산) |
     | `--count` | `-c` | `0` | 전송 사이클 횟수 (`0`: 무한, `N > 0`: N회 발행 후 종료, peak 모드 기본: 60) |
     | `--host` | | `localhost` | MQTT 브로커 호스트 주소 |
     | `--port` | `-p` | `1883` | MQTT 브로커 포트 번호 |
     | `--user` | `-u` | `simulator_user` | MQTT 인증 계정명 |
     | `--password` | | `test1234` | MQTT 인증 비밀번호 |
     | `--qos` | | `1` | 발행 QoS 레벨 (`0` 또는 `1`) |
     | `--quiet` | `-q` | `False` | 요약 모드 (매초 상세 로그 생략, 5초 주기 누적 TPS 통계만 출력) |

   - **주요 실행 예시**:
     ```bash
     # 1) [시연용] 10초 피크 시연 모드 (H001 대상 10초에 3,500W 도달 후 60초 완주)
     python simulator/simulator.py --scenario peak

     # 2) 1,000가구 대규모 스트리밍 부하 테스트 (1,000 msg/s 연속 발행)
     python simulator/simulator.py -n 1000 -q

     # 3) 테스트용 10가구 5회 전송 후 자동 종료
     python simulator/simulator.py -n 10 -c 5
     ```

### (3) 실시간 웹 파형 시각화 대시보드 (`waveform_viewer.html`)
브라우저에서 직접 원클릭으로 시연 시나리오를 시작하고 실시간 차트와 가전 상태를 관찰할 수 있는 인터랙티브 뷰어입니다.
* **실행**: 파일 탐색기에서 `simulator/waveform_viewer.html`을 더블 클릭하거나 웹 브라우저로 오픈
* **주요 기능**:
  * **[10초 피크 시연 시작] 버튼**: 클릭 즉시 실시간 애니메이션 차트 가동, 10초에 3,400~3,500W 피크 경보 점멸
  * **[연속 실시간 시뮬레이션] 버튼**: 6대 가전 무한 연속 동작
  * **일시정지 / 리셋 / 배속 조절(1x, 2x, 5x) / CSV 내보내기 지원**

### (4) 시뮬레이터 데이터 실시간 수신 검증 (구독 테스트)
별도의 터미널 창에서 아래 명령어를 실행하여 시뮬레이터가 보낸 데이터가 실시간으로 들어오는지 확인합니다:
```cmd
docker exec -it mosquitto-broker mosquitto_sub -t "v1/power/sim/#" -u "kafka_bridge_user" -P "test1234" -v
```

---

## 8. 현재 구축된 데이터 흐름 (Data Flow)

현재 단계에서 구현 및 검증이 완료된 IoT 데이터 수집 흐름입니다.

```text
+-------------------------------------------------------------+
|  1. 데이터 생성 (Edge / Simulator)                          |
|  - Python 시뮬레이터 (simulator.py)                         |
|  - 가구(H001~H003) 6종 가전 전력 데이터 JSON 생성           |
+-------------------------------------------------------------+
                              │
                              │ MQTT Publish (QoS 1, Port 1883)
                              │ Auth: simulator_user
                              ▼
+-------------------------------------------------------------+
|  2. 메시지 수집 및 중계 (Broker Ingestion)                  |
|  - Mosquitto MQTT v2 Broker (Docker)                        |
|  - Topic: v1/power/sim/{house}/{device}                     |
|  - 익명 차단 및 계정 기반 인증 라우팅                       |
+-------------------------------------------------------------+
                              │
                              │ MQTT Subscribe (QoS 1)
                              │ Auth: kafka_bridge_user
                              ▼
+-------------------------------------------------------------+
|  3. 데이터 수신 및 검증 (Consumer / Pipeline Endpoint)      |
|  - 토픽(v1/power/sim/#) 구독자                              |
|  - 실시간 전력 데이터 수신 확인 완료                        |
+-------------------------------------------------------------+
```

### 단계별 상세 명세

| 단계       | 구분                 | 구성 요소                   | 상세 내용                                                        |
| :--------- | :------------------- | :-------------------------- | :--------------------------------------------------------------- |
| **Step 1** | **발행 (Publish)**   | `simulator.py` (Python 3)   | 가구별 6종 가전의 전력값(W)을 1초 주기로 JSON 직렬화하여 발행    |
| **Step 2** | **수집 (Broker)**    | `mosquitto-broker` (Docker) | `v1/power/sim/{house}/{device}` 토픽으로 수신 및 인메모리 라우팅 |
| **Step 3** | **구독 (Subscribe)** | CLI / 파이프라인 수신단     | `v1/power/sim/#` 토픽을 구독하여 실시간 데이터 수신 및 검증      |



