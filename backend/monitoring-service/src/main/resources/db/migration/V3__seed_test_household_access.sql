-- 테스트·시연용 가구-보호자 매핑.
-- APP_SECURITY_ENABLED=false 환경에서는 모든 요청자가 'local-user'로 처리되므로,
-- H001 가구의 알림이 local-user에게 가도록 미리 연결해 둔다.
-- 이미 수동으로 넣은 환경(로컬)에서도 실패하지 않도록 존재 여부를 확인한 뒤 삽입한다.
INSERT INTO household_access (household_id, user_id)
SELECT 'H001', 'local-user'
WHERE NOT EXISTS (
    SELECT 1 FROM household_access
     WHERE household_id = 'H001' AND user_id = 'local-user'
);
