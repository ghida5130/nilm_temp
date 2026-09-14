package com.nilm.monitoring.subject.service;

import com.nilm.monitoring.common.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorCodec {
    private CursorCodec() {}

    public static String encode(int offset, String fingerprint) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (offset + "\n" + fingerprint).getBytes(StandardCharsets.UTF_8));
    }

    public static int decode(String cursor, String fingerprint) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\n", 2);
            int offset = Integer.parseInt(parts[0]);
            if (offset < 0 || parts.length != 2 || !parts[1].equals(fingerprint)) throw new Exception();
            return offset;
        } catch (Exception e) {
            throw new BadRequestException("cursor가 조회 조건과 일치하지 않습니다.");
        }
    }
}
