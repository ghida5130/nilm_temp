# NILM IoT 스마트홈 전력 시뮬레이터 (Simulator)

스마트홈 메인 분전반(Smart Meter)의 초 단위 전력 계측 환경을 모사하여 Mosquitto MQTT 브로커로 실시간 스트리밍하는 고성능 시뮬레이션 패키지입니다.

---

## 1. 개요 및 목적

* **실시간 AI 추론 데이터 공급**: 비침습 가전 분리(NILM) 딥러닝 모델(TCN / Seq2Point)의 299초 슬라이딩 윈도우 추론에 필요한 다변량 교류 전력 데이터(유효전력, 무효전력, 역률, 전류, 전압)를 매초 실시간으로 생성하여 공급합니다.
* **대규모 인프라 스트레스 테스트**: 최대 1,000가구 동시 발행(1,000 msg/s)을 지원하여 MQTT 브로커, 카프카 브릿지, Flink 스트림 파이프라인의 처리 한계를 검증합니다.
* **원클릭 피크 시연(Demo) 지원**: 발표 및 대시보드 시연을 위해, 시작 10초 시점에 3,000W 이상의 피크 전력을 정확하게 발생시키는 결정론적 시연 모드와 실시간 웹 대시보드를 제공합니다.

---

## 2. 파일 구성

| 파일명                      | 역할 및 설명                                                                                                     |
| :-------------------------- | :--------------------------------------------------------------------------------------------------------------- |
| **`simulator.py`**          | **전력 시뮬레이터 호환 Facade & CLI 진입점** (1,000가구 벤치마크, 10초 피크/루틴누락 시연 모드 내장)             |
| **`engine/`**               | **시뮬레이터 핵심 엔진 패키지** (`config`, `profiles`, `state`, `power_model`, `publisher` 책임 분리)            |
| **`web_server.py`**         | **인터랙티브 웹 서버 진입점** (CLI 인자 처리, 서버 초기화 및 실행)                                             |
| **`server/`**               | **웹 서버 모듈 패키지** (`config.py`, `manager.py`, `request_handler.py` 책임 분리)                              |
| **`waveform_viewer.html`**  | 브라우저 기반 실시간 인터랙티브 시각화 대시보드 (원클릭 피크 시연 버튼, 실시간 차트, 가전 상태 칩)               |
| **`visualize_waveform.py`** | 파형 시뮬레이션 데이터 생성 및 CSV/HTML 리포트 정적 출력 도구                                                    |
| **`requirements.txt`**      | 시뮬레이터 실행에 필요한 최소 의존성 목록 (`aiomqtt`)                                                            |

---

## 3. 물리 엔진 및 전력 모델링

단순 난수가 아닌, 교류(AC) 전력 공학 공식과 AI Hub 실측 데이터셋(EDA 중앙값)을 기반으로 물리적 상호 연동 계산을 수행합니다.

### (1) 다변량 교류 전력 계산식
* **피상전력(Apparent Power)**: $S = \sqrt{P^2 + Q^2}$ (단위: VA)
* **역률(Power Factor)**: $PF = \frac{P}{S}$ (범위: 0.1 ~ 1.0)
* **무효전력(Reactive Power)**: $Q = P \times \frac{\sqrt{1 - PF^2}}{PF}$ (단위: var)
* **부하전류(Current)**: $I = \frac{S}{V}$ (단위: A)

### (2) 환경 노이즈 및 기저 부하
* **전압($V$)**: 220V 기준 1차 자기회귀 모델(AR-1)을 적용하여 212V ~ 228V 사이를 완만하게 변동
* **상시 기저부하**: 가구별 40~65W 상시 대기전력 (Random Walk 드리프트)
* **냉장고 컴프레서 주기**: 15~25분 가동(약 63W, PF 0.78), 20~35분 정지 주기를 갖는 자동 주기 가전 모사

### (3) 6대 타겟 수동 가전 상태 머신 (FSM)
* **전기포트 (`kettle`)**: 순수 저항 히터, 돌입 없음, 단일 구형파 (1,500 ~ 1,800W, PF 0.98~1.00)
* **인덕션 (`induction`)**: 인버터 유도 가열, 18초 가열 ↔ 10초 휴지 서모스탯 듀티 사이클 반복 (1,300 ~ 1,750W, PF 0.91~0.95)
* **전기다리미 (`iron`)**: 전열선 바이메탈 온도 제어 듀티 사이클 (1,200 ~ 1,550W, PF 0.98~1.00)
* **전자레인지 (`microwave`)**: 마그네트론/변압기 자화 돌입전류 1.35배 기동 피크 (850 ~ 1,150W, PF 0.88~0.94)
* **헤어드라이기 (`hair_dryer`)**: 열선 및 소형 팬모터 기동 피크 1.20배 (800 ~ 1,200W, PF 0.94~0.98)
* **진공청소기 (`vacuum_cleaner`)**: 고속 직권 모터 강한 기동 돌입 1.50배 및 강한 지상 무효전력 (700 ~ 950W, PF 0.75~0.85)

---

## 4. 발행 토픽 및 전송 페이로드 규격

* **MQTT 발행 토픽**: `v1/power/sim/{household_id}/main` (기본 QoS 1)
* **전송 페이로드 (JSON)**:
  신규 실시간 분석 서비스(BE-2) 규격과 기존 HDFS Parquet 로더 규격을 모두 포함하는 이중 호환 스키마입니다.

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

---

## 5. 실행 방법 (`simulator.py`)

### (1) 사전 준비

* **Python 버전**: **Python 3.10 이상** 필수 (비동기 이벤트 루프 및 SSLContext 검증 지원)
* **패키지 의존성 설치**:

```bash
pip install -r requirements.txt
```

*`requirements.txt`는 비동기 MQTT 클라이언트인 `aiomqtt>=2.0.0`을 포함합니다.*

### (2) 커맨드라인 옵션 상세

```bash
python simulator.py --help
```

| 옵션 | 단축키 | 기본값 | 설명 |
| :--- | :--- | :--- | :--- |
| `--scenario` | `-s` | `random` | 실행 시나리오 모드 (`random`: 연속 확률, `peak`: 10초 피크, `routine_missed`: 08:10 루틴 누락 이상치) |
| `--houses` | `-n` | `10` | 대상 가구 수 (`H001` ~ `H{n:03d}`) |
| `--interval` | `-i` | `1.0` | 데이터 발행 주기 (초 단위) |
| `--hz` | | `None` | 가구당 초당 측정 횟수 (지정 시 `interval = 1/hz` 자동 환산) |
| `--count` | `-c` | `0` | 전송 사이클 수 (`0`: 무한, `N > 0`: N회 전송 종료, peak 기본값: 60, routine_missed 기본값: 300) |
| `--start-time` | | `None` | 시작 가상 시각 (예: `08:15:00`). routine_missed 기본값: 오늘 아침 08:15:00 KST |
| `--host` | | `localhost` | MQTT 브로커 호스트 주소 (**TLS 사용 시 인증서 SAN과 반드시 일치해야 함. EC2-A 로컬 실행 시에도 localhost가 아닌 A 사설 IP 사용**) |
| `--port` | `-p` | `None` | MQTT 브로커 포트 번호 (미지정 시 `MQTT_PORT` 환경변수 또는 TLS 여부에 따라 `8883`/`1883` 자동 결정) |
| `--user` | `-u` | `simulator_user` | MQTT 인증 계정명 (환경변수: `MQTT_USER`) |
| `--password` | | `None` | MQTT 인증 비밀번호 (미지정 시 `MQTT_PASS` 환경변수 또는 `test1234`. **프로세스 노출 및 쉘 히스토리 방지를 위해 환경변수 또는 read 프롬프트 사용 권장**) |
| `--tls` / `--no-tls` | | `None` | MQTT TLS 암호화 연결 활성화 여부 (미지정 시 `MQTT_TLS_ENABLED` 환경변수 또는 `False`) |
| `--ca-file` | | `None` | 브로커 검증에 사용할 CA 인증서 파일 경로 (PEM 형식, 환경변수: `MQTT_CA_FILE`) |
| `--qos` | | `1` | MQTT QoS 레벨 (`0` 또는 `1`) |
| `--quiet` | `-q` | `False` | 요약 모드 (매초 상세 로그 생략, 5초 주기 누적 처리량 통계만 출력) |

> [!IMPORTANT]
> **포트 결정 우선순위**:
> `--port` CLI 명시 > `MQTT_PORT` 환경변수 > TLS 활성화 시 `8883` > 평문 연결 시 `1883`

---

### (3) 환경별 실행 가이드

#### 1) 로컬 개발 환경 (평문 1883 연결)

로컬에 구동된 Mosquitto 브로커(`localhost:1883`)로 평문 통신합니다. 별도의 TLS 환경변수 없이 바로 실행할 수 있습니다.

```bash
# 피크 60초 시연 모드 (10초 시점 3,000W+ 도달)
python simulator.py --scenario peak

# 08:10 루틴 누락(ROUTINE_MISSED) 300초 이상치 검증 모드
python simulator.py --scenario routine_missed

# 1,000가구 대규모 스트리밍 부하 테스트 (초당 1,000건)
python simulator.py -n 1000 -q
```

---

#### 2) 운영 EC2 환경 (TLS 8883 보안 연결)

운영 Mosquitto 브로커(EC2-A)는 **8883 포트의 TLS 리스너만 사용**하며, 서버 인증서에는 **EC2-A의 사설 IP(`A_PRIVATE_IP`)가 SAN(Subject Alternative Name)**에 등록되어 있습니다.

> [!WARNING]
> * **인증서 SAN 불일치 방지**: EC2-A 머신 내부에서 시뮬레이터를 실행하더라도 `--host`나 `MQTT_HOST`에 `localhost`를 사용할 수 없습니다. 반드시 인증서 SAN에 등록된 사설 IP `<A_PRIVATE_IP>`를 지정해야 TLS 핸드셰이크 검증에 통과합니다.
> * **비밀번호 노출 및 쉘 히스토리 방지**:
>   * **CLI 인자 노출 위험**: `--password <PASS>`를 커맨드라인에 직접 입력하면 프로세스 목록(`ps aux`, `/proc/<pid>/cmdline`)을 통해 동일 머신의 다른 사용자에게 노출될 위험이 있으므로 CLI 인자 전달을 지양합니다.
>   * **쉘 히스토리 노출 주의**: 단순히 쉘에 `export MQTT_PASS=...`를 직접 타이핑하면 `~/.bash_history` 등에 평문으로 저장됩니다. 히스토리 저장을 방지하려면 아래 방법 중 하나를 사용하십시오:
>     * **방법 1 (`read -rsp`)**: 화면 에코 및 쉘 히스토리 없이 프롬프트로 비밀번호 입력
>       ```bash
>       read -rsp "MQTT Password: " MQTT_PASS && export MQTT_PASS
>       ```
>     * **방법 2 (권한 제한 환경파일 생성)**:
>       ```bash
>       umask 077
>       read -rsp "MQTT Password: " MQTT_PASS
>       printf '\n'
>       printf 'MQTT_PASS=%q\n' "$MQTT_PASS" > ~/.nilm_mqtt.env
>       unset MQTT_PASS
>       set -a && source ~/.nilm_mqtt.env && set +a
>       ```
> * **CA 파일 권한 및 예시 경로**:
>   * EC2-A 실행 시 예시: `~/mqtt-ca/ca.crt`
>   * EC2-B 실행 시 예시: `/opt/nilm/mqtt/certs/ca.crt` (서버 권한에 따라 `sudo` 또는 그룹 읽기 권한 필요)

##### 방법 A. 환경변수 기반 실행 (권장)

```bash
# 1. 운영 브로커 접속 환경변수 설정
export MQTT_HOST=<A_PRIVATE_IP>
export MQTT_PORT=8883
export MQTT_USER=<MQTT_USER>
read -rsp "MQTT Password: " MQTT_PASS && export MQTT_PASS
export MQTT_TLS_ENABLED=true
export MQTT_CA_FILE=<CA_FILE_PATH>

# 2. 피크 시연 모드 실행
python simulator.py --scenario peak

# 3. 루틴 누락 검증 모드 실행
python simulator.py --scenario routine_missed
```

##### 방법 B. CLI 옵션 명시 실행

```bash
# 비밀번호는 프롬프트나 환경변수로 안전하게 주입
read -rsp "MQTT Password: " MQTT_PASS && export MQTT_PASS

# CLI 플래그로 운영 TLS 접속 정보 전달
python simulator.py \
  --host <A_PRIVATE_IP> \
  --port 8883 \
  --user <MQTT_USER> \
  --tls \
  --ca-file <CA_FILE_PATH> \
  --scenario peak
```

---

## 6. 실시간 웹 시각화 대시보드 (`waveform_viewer.html` & `web_server.py`)

웹 브라우저에서 직접 버튼을 클릭하여 시뮬레이터를 제어하고, 실시간 전력 파형 시각화와 **실제 Mosquitto MQTT 발행(Kafka 연동)**을 동시에 수행할 수 있습니다.

웹 컨트롤러 HTTP 서버는 보안을 위해 **기본적으로 `127.0.0.1`에만 바인딩**됩니다.

> [!CAUTION]
> * **0.0.0.0 바인딩 및 인증되지 않은 제어 API 노출 위험**:
>   웹 컨트롤러의 제어 API(`/api/start`, `/api/stop`, `/api/device` 등)는 별도의 사용자 인증이 없으므로, `--bind-host 0.0.0.0`으로 외부 네트워크에 개방할 경우 인가되지 않은 사용자가 전력 데이터 발행을 임의로 조작할 수 있습니다.
> * **권장 원격 접속 방식 (SSH 터널링)**:
>   웹 서버는 기본값(`127.0.0.1`)으로 안전하게 유지하고, 로컬 PC에서 SSH 포트 포워딩을 통해 암호화 터널로 접속하십시오:
>   ```bash
>   ssh -i <EC2_KEY.pem> -L 8085:127.0.0.1:8085 ubuntu@<EC2_PUBLIC_IP>
>   # 로컬 브라우저에서 http://localhost:8085 접속
>   ```
> * **부득이하게 `--bind-host 0.0.0.0`을 사용하는 경우**:
>   반드시 EC2 보안 그룹(Security Group)에서 웹 서버 포트(8085)의 인바운드 허용 소스 IP를 관리자 개인 공인 IP(`/32`)로 엄격히 제한하십시오.

### (1) 실행 방법

#### A. 로컬 평문 실행 (기본 localhost:1883)

```bash
python web_server.py
# 특정 웹 포트 지정: python web_server.py 8089
# 브라우저 접속: http://127.0.0.1:8085
```

#### B. 운영 EC2 TLS 실행 (8883 포트 보안 연결)
웹 시뮬레이터 역시 CLI와 동일한 공통 TLS 설정을 지원합니다. 웹 서버 기동 시점에 CA 파일 유효성을 사전 검증하며, 버튼 클릭(`/api/start`) 시에도 동기적으로 TLS 검증을 수행합니다.

```bash
# 환경변수 기반 운영 웹 서버 실행 (권장)
export MQTT_HOST=<A_PRIVATE_IP>
export MQTT_PORT=8883
export MQTT_USER=<MQTT_USER>
read -rsp "MQTT Password: " MQTT_PASS && export MQTT_PASS
export MQTT_TLS_ENABLED=true
export MQTT_CA_FILE=<CA_FILE_PATH>

python web_server.py

# 또는 CLI 인자로 지정하여 실행 (바인딩 호스트 및 웹 포트 지정 가능)
python web_server.py \
  --bind-host 127.0.0.1 \
  --web-port 8085 \
  --host <A_PRIVATE_IP> \
  --port 8883 \
  --user <MQTT_USER> \
  --tls \
  --ca-file <CA_FILE_PATH>
```

* **모드 B. 브라우저 단독 시각화 모드 (네트워크 미연동)**:
  파이썬/도커를 켤 필요 없이 파일 탐색기에서 `waveform_viewer.html`을 더블 클릭하여 로컬 브라우저에서 시각적 데모만 재생합니다.
  ```text
  file:///path/to/infrastructure/mqtt/simulator/waveform_viewer.html
  ```

### (2) 주요 대시보드 기능
* **`[10초 피크 시연 시작 (3,000W+)]` 버튼 (빨간색)**:
  * 클릭 즉시 실시간 애니메이션 차트 가동과 함께 **실제 MQTT 발행이 시작**되며, **10초 정각에 3,400~3,500W로 치솟고 붉은색 경보 배지가 점멸**하는 실시간 시연을 재생합니다 (60초 완주 후 자동 정지).
* **`[루틴 누락 시연 시작 (08:10+)]` 버튼 (주황색)**:
  * 08:10:01 KST 시점 기준으로 **전자레인지 미가동 대기전력(~60W) 데이터를 300초간 실시간 발행**하며, 실시간 AI 분석 서비스(`realtime-analysis-service`)의 299초 슬라이딩 윈도우 버퍼 충족 및 `ROUTINE_MISSED` 이상치 감지(`analysis.event.v1`)를 검증합니다.
* **`[연속 실시간 시뮬레이션]` 버튼 (파란색)**: 6대 가전이 확률에 따라 무한 연속 동작 및 MQTT 발행
* **실시간 제어 바**: 일시정지, 재생, 리셋(발행 중지), 재생 속도 조절(1x, 2x, 5x 배속)
* **실시간 가전 칩 패널**: 6대 가전의 실시간 ON/OFF 상태 및 소비전력 표시 (루틴 누락 시 전자레인지 칩 "미가동 감시" 강조)
* **CSV 내보내기**: 시뮬레이션된 시계열 데이터를 엑셀 호환 UTF-8 BOM CSV 파일로 즉시 저장

---

## 7. 정적 파형 시각화 도구 (`visualize_waveform.py`)

MQTT 브로커나 웹 서버 없이, CLI에서 파형 데이터를 생성하여 정적 HTML 대시보드 리포트(`waveform_report.html`) 및 CSV 파일로 추출할 수 있습니다.

```bash
# 1. 루틴 누락(ROUTINE_MISSED) 300초 대기전력 파형 생성 및 브라우저 확인
python visualize_waveform.py --scenario routine_missed --seconds 300 --csv

# 2. 10초 피크(3,000W+) 시연 파형 생성
python visualize_waveform.py --scenario peak --seconds 60

# 3. 6대 가전 대표 기동 데모 파형 (기본값)
python visualize_waveform.py --scenario demo

# 4. 순수 확률 기반 랜덤 시뮬레이션
python visualize_waveform.py --random --seconds 600
```

---

## 8. 전체 데이터 파이프라인 연동 흐름

```text
+------------------------------------+
|  스마트홈 전력 시뮬레이터          |
|  - simulator.py / waveform_viewer  |
|  - H001~H1000 전력 데이터 실시간 생성 |
+------------------------------------+
                  │
                  │ MQTT Publish (v1/power/sim/{house}/main, QoS 1)
                  ▼
+------------------------------------+
|  Mosquitto MQTT Broker (Port 1883) |
|  - max_connections 2000            |
|  - max_inflight_messages 200       |
|  - max_queued_messages 10000       |
+------------------------------------+
                  │
                  │ MQTT Subscribe (v1/power/sim/+/main)
                  ▼
+------------------------------------+
|  MQTT-Kafka Bridge (bridge.py)     |
|  - household_id 기반 파티셔닝 키   |
+------------------------------------+
                  │
                  │ Kafka Produce (Key: household_id)
                  ▼
+------------------------------------+
|  Apache Kafka Broker (Port 9092)   |
|  - Topic: power.raw.v1 (24개 파티션) |
|  - 24개 파티션 균등 해시 분산      |
+------------------------------------+
                  │
                  ▼
     다운스트림 서비스 (BE-2 Flink 분석, BE-3 모니터링 SSE, HDFS 로더)
```
