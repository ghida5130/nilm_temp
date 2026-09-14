-- 테스트·시연용 가구-보호자 매핑 확장: H002, H003도 local-user에 연결한다.
-- V3는 이미 운영 DB에 적용되어 체크섬이 고정됐으므로 수정하지 않고 새 버전으로 추가한다.
-- household_access(household_id, user_id)에 UNIQUE 제약이 있어 재실행·수동 삽입 환경에서도 실패하지 않는다.
INSERT INTO household_access (household_id, user_id)
VALUES
    ('H002', 'local-user'),
    ('H003', 'local-user')
ON CONFLICT DO NOTHING;
