# collection — 원본 데이터 수집

Kafka에 들어온 원본 메시지를 가공 없이 데이터 레이크에 적재하는 코드를 둔다.
실행 구성(Compose)은 `infrastructure/`에 남기고, 여기에는 적재기 코드와 이미지 빌드만 둔다.

## 폴더별 역할

- `bronze-loader/`: `power.raw.v1`을 구독해 HDFS Bronze에 Parquet으로 적재하는 원본 적재기.
  안전 커밋(임시 경로 -> 크기 검증 -> rename -> manifest -> offset commit), 오류 격리(quarantine),
  파티션 단위 배치 플러시를 담당한다. 원본 payload 바이트를 함께 보존해 재처리·재해석이 가능하다.
- `dlq-loader/`: `dlq.analysis`, `dlq.monitoring` HDFS 보관을 위한 [설계](dlq-loader/README.md).
  기존 Bronze quarantine과 저장 책임을 분리하고 원천 Kafka 위치로 연결한다. 현재 설계 단계다.

## 실행

`infrastructure/hdfs/docker-compose.yml`의 `bronze-loader` 서비스가 이 폴더를 빌드 컨텍스트로 쓴다.
HDFS 클러스터와 함께 올린다.

```bash
docker compose -f infrastructure/hdfs/docker-compose.yml up -d --build
```

컨테이너 이름은 `nilm-bronze-loader`, Kafka consumer group은 `hdfs-bronze-loader`다.
토픽·플러시 주기·HDFS 경로는 모두 Compose의 환경변수로 조정한다.

## 관련 문서

- [HDFS 수집·일일배치 설계안](../docs/진행상황/이정민/0914/HDFS_수집·일일배치_설계안.md)
