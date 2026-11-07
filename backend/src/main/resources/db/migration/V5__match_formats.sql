CREATE TABLE match_formats (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL REFERENCES sports(id),
    version BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(120) NOT NULL,
    score_mode VARCHAR(20) NOT NULL,
    allow_draw BOOLEAN NOT NULL,
    periods_required BOOLEAN NOT NULL DEFAULT FALSE,
    period_label VARCHAR(100) NOT NULL,
    regulation_period_count INTEGER,
    wins_required INTEGER,
    allow_overtime BOOLEAN NOT NULL DEFAULT FALSE,
    allow_shootout BOOLEAN NOT NULL DEFAULT FALSE,
    shootout_winner_bonus INTEGER NOT NULL DEFAULT 0,
    friendly_forfeit_loser_score INTEGER NOT NULL DEFAULT 0,
    friendly_forfeit_winner_score INTEGER NOT NULL DEFAULT 1,
    locked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_match_format_version CHECK (version >= 0),
    CONSTRAINT ck_match_format_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_match_format_period_label CHECK (length(trim(period_label)) > 0),
    CONSTRAINT ck_match_format_period_count CHECK (regulation_period_count IS NULL OR regulation_period_count > 0),
    CONSTRAINT ck_match_format_shootout CHECK (shootout_winner_bonus >= 0 AND (allow_shootout OR shootout_winner_bonus = 0)),
    CONSTRAINT ck_match_format_forfeit CHECK (friendly_forfeit_loser_score >= 0 AND friendly_forfeit_winner_score > friendly_forfeit_loser_score),
    CONSTRAINT ck_match_format_score_mode CHECK (
        (score_mode = 'SUM_SCORE' AND wins_required IS NULL) OR
        (score_mode = 'WON_PARTS' AND periods_required AND wins_required IS NOT NULL
            AND wins_required > 0 AND NOT allow_draw AND NOT allow_overtime
            AND NOT allow_shootout AND regulation_period_count IS NULL)
    )
);
CREATE INDEX ix_match_formats_sport ON match_formats(sport_id);

-- Future result writes set locked_at in their transaction; it can never be cleared.
-- Renaming remains possible without changing the historical scoring configuration.
CREATE FUNCTION guard_match_format_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.locked_at IS NOT NULL THEN
            RAISE EXCEPTION 'Used match format cannot be deleted' USING ERRCODE = '23514';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.sport_id IS DISTINCT FROM OLD.sport_id THEN
        RAISE EXCEPTION 'Match format identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.locked_at IS NOT NULL AND (
        NEW.locked_at IS DISTINCT FROM OLD.locked_at OR
        (to_jsonb(NEW) - ARRAY['name','version','locked_at']) IS DISTINCT FROM
        (to_jsonb(OLD) - ARRAY['name','version','locked_at'])
    ) THEN
        RAISE EXCEPTION 'Used match format configuration is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_match_format_history BEFORE UPDATE OR DELETE ON match_formats
    FOR EACH ROW EXECUTE FUNCTION guard_match_format_history();
