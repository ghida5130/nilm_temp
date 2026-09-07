# MQTT Broker와 시뮬레이터

Mosquitto 서비스 실행은 [local Compose](../local/compose.yaml) 또는 [EC2-A Compose](../ec2-a/compose.yaml)에서 관리한다. 이 폴더에서는 설정 파일과 테스트 발행 코드를 관리한다.

## 파일

- config/mosquitto.local.conf: 로컬 1883 리스너와 계정 인증.
- config/mosquitto.production.conf: 운영 8883 TLS 리스너와 계정 인증.
- config/passwd: 로컬 계정 해시 파일. Git 제외.
- config/mosquitto.conf: 이전 컨테이너 바인드 경로 보존용. 새 Compose는 사용하지 않는다.
- simulator/simulator.py: 로컬 데이터 발행 도구.

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

현재 passwd 방식에는 사용자별 토픽 ACL이 없다. ACL 도입 시 센서 발행·Bridge 구독 권한을 구분하고 smoke 검증 계정의 발행 권한도 맞춰야 한다.



