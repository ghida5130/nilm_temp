## Git Convention

### Git Flow

프로젝트는 Git Flow 전략을 기반으로 브랜치를 관리한다.

| Branch      | 역할                       |
| ----------- | -------------------------- |
| `main`      | 배포 가능한 운영 버전      |
| `develop`   | 다음 배포를 위한 개발 통합 |
| `feature/*` | 기능 개발                  |
| `release/*` | 배포 준비 및 최종 수정     |
| `hotfix/*`  | 운영 환경 긴급 수정        |

```text
main
│
├── hotfix/*
│
└── develop
    ├── feature/*
    └── release/*
```

### Branch Flow

#### Feature

```text
develop
  ↓
feature/*
  ↓
develop
```

#### Release

```text
develop
  ↓
release/*
  ├─→ main
  └─→ develop
```

#### Hotfix

```text
main
 ↓
hotfix/*
 ├─→ main
 └─→ develop
```

`main`, `develop`에는 직접 Push하지 않고 Pull Request를 통해 반영한다.

---

### Branch Naming

```text
{type}/{scope}/{description}
```

#### Type

```text
feature
release
hotfix
```

#### Scope

| Scope     | 대상                       |
| --------- | -------------------------- |
| `front`   | Frontend                   |
| `back`    | Backend                    |
| `ai`      | AI / ML                    |
| `data`    | 데이터 수집 / 전처리 / ETL |
| `bigdata` | 대용량 데이터 처리         |
| `spark`   | Spark / 분산 처리          |
| `infra`   | Docker / 서버 / 배포 환경  |
| `common`  | 공통 작업                  |

예시:

```text
feature/front/login
feature/back/auth-api
feature/ai/recommendation
feature/data/preprocessing
feature/spark/batch-processing

release/1.0.0
hotfix/back/auth-error
```

여러 영역이 하나의 기능에 포함되는 경우 기능 단위 브랜치를 사용할 수 있다.

```text
feature/recommendation
feature/search-system
```

---

### Commit Convention

```text
type(scope): message
```

| Type       | 설명                              |
| ---------- | --------------------------------- |
| `feat`     | 기능 추가                         |
| `fix`      | 버그 수정                         |
| `refactor` | 코드 구조 개선                    |
| `perf`     | 성능 개선                         |
| `docs`     | 문서 수정                         |
| `test`     | 테스트 추가 / 수정                |
| `style`    | 동작에 영향을 주지 않는 코드 수정 |
| `chore`    | 설정 / 패키지 / 빌드 작업         |
| `data`     | 데이터셋 / 전처리 변경            |

예시:

```text
feat(front): 검색 페이지 구현
feat(back): 검색 API 구현
feat(ai): 추천 모델 추가

data(data): 데이터 전처리 로직 추가
feat(spark): 분산 집계 작업 구현
perf(bigdata): 대용량 데이터 처리 성능 개선

fix(back): 인증 오류 수정
chore(infra): Docker 설정 변경
```

---

### Data / Big Data Convention

대용량 원본 데이터 및 생성 결과물은 Git에 직접 커밋하지 않는다.

```text
data/raw/
data/output/
logs/
output/
checkpoints/
spark-warehouse/

*.csv
*.parquet
*.jsonl
```

Git에는 다음 항목을 중심으로 관리한다.

- 데이터 처리 코드
- 데이터 스키마
- 샘플 데이터
- 데이터 출처 및 생성 방법
- Spark / 분산 처리 설정

환경 변수 및 민감 정보는 커밋하지 않는다.

```text
.env
.env.*
*.pem
*.key
```

---

### Pull Request

PR은 하나의 기능 또는 작업 단위로 생성한다.

```text
feature/front/login → develop
feature/recommendation → develop

release/1.0.0 → main
release/1.0.0 → develop

hotfix/back/auth-error → main
hotfix/back/auth-error → develop
```

PR 제목은 Commit Convention과 동일한 형식을 사용한다.

```text
feat(front): 로그인 페이지 구현
feat(ai): 추천 모델 구현
perf(bigdata): 데이터 처리 성능 개선
```
