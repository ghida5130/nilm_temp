-- expected_until 기반 논리 중복 판별을 모니터링에서 제거한다.
-- 같은 판단의 중복 방지는 분석 서비스가 부여하는 event_id(PK)로만 처리하고,
-- reason.expected_until은 표시용 JSON으로만 유지한다.
ALTER TABLE analysis_event DROP CONSTRAINT IF EXISTS ux_analysis_event_logical;
ALTER TABLE analysis_event DROP COLUMN IF EXISTS event_date;
ALTER TABLE analysis_event DROP COLUMN IF EXISTS expected_until;
