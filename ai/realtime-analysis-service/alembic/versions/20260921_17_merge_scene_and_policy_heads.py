"""Merge selected-scene and policy tuning migration heads."""

from collections.abc import Sequence

from alembic import op


revision: str = "20260921_17"
down_revision: tuple[str, str] = ("20260921_16", "20260921_16_scene")
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Reapply the intended values for databases stamped by the old colliding
    # selected-scene revision ID, where Alembic could not identify the branch.
    op.execute(
        """
        UPDATE analysis_policy
        SET parameters = jsonb_set(parameters, '{inactivity_hours}', '0.0167'::jsonb, true)
        WHERE event_type = 'PROLONGED_INACTIVITY'
        """
    )
    op.execute(
        """
        UPDATE analysis_policy
        SET parameters = jsonb_set(
            parameters,
            '{limits_minutes}',
            '{"INDUCTION": 2, "IRON": 2}'::jsonb,
            true
        )
        WHERE event_type = 'PROLONGED_APPLIANCE_USE'
        """
    )


def downgrade() -> None:
    pass
