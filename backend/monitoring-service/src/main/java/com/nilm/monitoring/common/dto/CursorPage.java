package com.nilm.monitoring.common.dto;

import java.time.Instant;
import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext, Instant serverTime) {
}
