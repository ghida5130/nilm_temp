"""Guard against combining daily slices prepared from different session states."""

from __future__ import annotations

import re


_SESSION_TOKEN = re.compile(r"(?:^|;)sessions=([^;]+)")


def session_state_token(ref) -> str | None:
    """Return the session manifest-set token embedded by analysis-usage-daily."""

    match = _SESSION_TOKEN.search(ref.config_version or "")
    return match.group(1) if match else None


def require_one_session_snapshot(refs, expected_dates) -> str:
    """Require every selected date to be rebuilt from one global session state.

    A delete produces no slice, so looking only at slice rows cannot distinguish
    deletion from a date that has not yet been rebuilt.  The upstream daily job
    embeds its global session-manifest-set digest in ``config_version``.  Gold
    must wait until all dates in its window carry the same digest.
    """

    refs = list(refs)
    expected_dates = tuple(expected_dates)
    if not refs and not expected_dates:
        return "NO_SESSION_INPUT"
    if len(refs) != len(expected_dates):
        raise ValueError("session slices are missing for part of the profile window")
    tokens = {session_state_token(ref) for ref in refs}
    if None in tokens:
        raise ValueError("session slice version lacks sessions=<manifest-set> provenance")
    if len(tokens) != 1:
        raise ValueError(
            "session slice dates were prepared from different session snapshots: "
            + ", ".join(sorted(tokens))
        )
    return next(iter(tokens))
