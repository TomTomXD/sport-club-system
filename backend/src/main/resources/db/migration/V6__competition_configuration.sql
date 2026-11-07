CREATE TABLE competitions (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL REFERENCES sports(id),
    name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0)
);
CREATE INDEX ix_competitions_sport ON competitions(sport_id);

CREATE TABLE competition_seasons (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    competition_id UUID NOT NULL REFERENCES competitions(id),
    season_id UUID NOT NULL REFERENCES seasons(id),
    match_format_id UUID NOT NULL REFERENCES match_formats(id),
    results_revision BIGINT NOT NULL DEFAULT 0 CHECK (results_revision >= 0),
    head_to_head_missing_policy VARCHAR(20) NOT NULL DEFAULT 'REQUIRE_COMPLETE'
        CHECK (head_to_head_missing_policy IN ('REQUIRE_COMPLETE','PLAYED_ONLY')),
    head_to_head_meetings_per_pair INTEGER NOT NULL DEFAULT 1 CHECK (head_to_head_meetings_per_pair > 0),
    restart_head_to_head_on_subgroup BOOLEAN NOT NULL DEFAULT TRUE,
    locked_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (competition_id,season_id),
    UNIQUE (id,match_format_id)
);
CREATE INDEX ix_competition_seasons_season ON competition_seasons(season_id);
CREATE INDEX ix_competition_seasons_format ON competition_seasons(match_format_id);

CREATE TABLE competition_point_rules (
    id UUID PRIMARY KEY,
    competition_season_id UUID NOT NULL REFERENCES competition_seasons(id),
    outcome VARCHAR(4) NOT NULL CHECK (outcome IN ('WIN','DRAW','LOSS')),
    ending VARCHAR(10) NOT NULL CHECK (ending IN ('REGULATION','OVERTIME','SHOOTOUT')),
    own_score_min INTEGER CHECK (own_score_min >= 0),
    own_score_max INTEGER CHECK (own_score_max >= 0),
    opponent_score_min INTEGER CHECK (opponent_score_min >= 0),
    opponent_score_max INTEGER CHECK (opponent_score_max >= 0),
    points INTEGER NOT NULL CHECK (points >= 0),
    CHECK (own_score_min IS NULL OR own_score_max IS NULL OR own_score_min <= own_score_max),
    CHECK (opponent_score_min IS NULL OR opponent_score_max IS NULL OR opponent_score_min <= opponent_score_max),
    CONSTRAINT ex_competition_point_rules_overlap EXCLUDE USING gist (
        competition_season_id WITH =, outcome WITH =, ending WITH =,
        int8range(own_score_min::bigint,own_score_max::bigint + 1,'[)') WITH &&,
        int8range(opponent_score_min::bigint,opponent_score_max::bigint + 1,'[)') WITH &&
    )
);
CREATE INDEX ix_competition_point_rules_season ON competition_point_rules(competition_season_id);

CREATE TABLE competition_forfeit_rules (
    competition_season_id UUID NOT NULL REFERENCES competition_seasons(id),
    decision VARCHAR(12) NOT NULL CHECK (decision IN ('FORFEIT_HOME','FORFEIT_AWAY')),
    home_score INTEGER NOT NULL CHECK (home_score >= 0),
    away_score INTEGER NOT NULL CHECK (away_score >= 0),
    home_points INTEGER NOT NULL CHECK (home_points >= 0),
    away_points INTEGER NOT NULL CHECK (away_points >= 0),
    PRIMARY KEY (competition_season_id,decision),
    CHECK ((decision='FORFEIT_HOME' AND home_score < away_score) OR
           (decision='FORFEIT_AWAY' AND away_score < home_score))
);
CREATE TABLE competition_ranking_criteria (
    competition_season_id UUID NOT NULL REFERENCES competition_seasons(id),
    priority INTEGER NOT NULL CHECK (priority > 0),
    metric VARCHAR(32) NOT NULL CHECK (metric IN ('POINTS','WINS','SCORE_DIFFERENCE','SCORE_FOR','SCORE_RATIO',
        'PART_SCORE_DIFFERENCE','PART_SCORE_RATIO','HEAD_TO_HEAD_POINTS','HEAD_TO_HEAD_SCORE_DIFFERENCE','HEAD_TO_HEAD_SCORE_FOR')),
    direction VARCHAR(4) NOT NULL DEFAULT 'DESC' CHECK (direction IN ('ASC','DESC')),
    PRIMARY KEY (competition_season_id,priority),
    UNIQUE (competition_season_id,metric)
);
CREATE TABLE competition_entries (
    competition_season_id UUID NOT NULL REFERENCES competition_seasons(id),
    team_season_id UUID NOT NULL REFERENCES team_seasons(id),
    PRIMARY KEY (competition_season_id,team_season_id)
);
CREATE INDEX ix_competition_entries_team ON competition_entries(team_season_id,competition_season_id);

CREATE FUNCTION p4_competition_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.sport_id IS DISTINCT FROM OLD.sport_id THEN
        RAISE EXCEPTION 'Competition identity is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER competition_identity_guard BEFORE UPDATE ON competitions
    FOR EACH ROW EXECUTE FUNCTION p4_competition_identity_guard();

CREATE FUNCTION p4_competition_season_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE competition_sport UUID; format_sport UUID; archived_season BOOLEAN;
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.locked_at IS NOT NULL THEN
            RAISE EXCEPTION 'Competition season history is immutable' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    SELECT sport_id INTO competition_sport FROM competitions WHERE id=NEW.competition_id;
    SELECT sport_id INTO format_sport FROM match_formats WHERE id=NEW.match_format_id;
    SELECT archived INTO archived_season FROM seasons WHERE id=NEW.season_id;
    IF competition_sport IS DISTINCT FROM format_sport THEN
        RAISE EXCEPTION 'Competition and format must share sport' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' AND archived_season THEN
        RAISE EXCEPTION 'Season archived' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF (NEW.id,NEW.competition_id,NEW.season_id,NEW.match_format_id) IS DISTINCT FROM
           (OLD.id,OLD.competition_id,OLD.season_id,OLD.match_format_id) THEN
            RAISE EXCEPTION 'Competition season identity is immutable' USING ERRCODE='23514';
        END IF;
        IF OLD.locked_at IS NOT NULL AND NEW.locked_at IS DISTINCT FROM OLD.locked_at THEN
            RAISE EXCEPTION 'Competition season cannot be unlocked' USING ERRCODE='23514';
        END IF;
        IF (OLD.locked_at IS NOT NULL OR archived_season) AND
           (NEW.head_to_head_missing_policy,NEW.head_to_head_meetings_per_pair,NEW.restart_head_to_head_on_subgroup) IS DISTINCT FROM
           (OLD.head_to_head_missing_policy,OLD.head_to_head_meetings_per_pair,OLD.restart_head_to_head_on_subgroup) THEN
            RAISE EXCEPTION 'Competition configuration locked' USING ERRCODE='23514';
        END IF;
        IF NEW.results_revision < OLD.results_revision THEN
            RAISE EXCEPTION 'Results revision cannot decrease' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER competition_season_guard BEFORE INSERT OR UPDATE OR DELETE ON competition_seasons
    FOR EACH ROW EXECUTE FUNCTION p4_competition_season_guard();

CREATE FUNCTION p4_competition_child_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target UUID; cs competition_seasons; archived_season BOOLEAN; team_sport UUID; team_season UUID; competition_sport UUID;
BEGIN
    IF TG_OP='DELETE' THEN target=OLD.competition_season_id; ELSE target=NEW.competition_season_id; END IF;
    IF TG_OP='UPDATE' AND NEW.competition_season_id IS DISTINCT FROM OLD.competition_season_id THEN
        RAISE EXCEPTION 'Competition child identity is immutable' USING ERRCODE='23514';
    END IF;
    SELECT * INTO cs FROM competition_seasons WHERE id=target FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Unknown competition season' USING ERRCODE='23503'; END IF;
    SELECT archived INTO archived_season FROM seasons WHERE id=cs.season_id;
    IF cs.locked_at IS NOT NULL OR archived_season THEN
        RAISE EXCEPTION 'Competition configuration locked' USING ERRCODE='23514';
    END IF;
    IF TG_TABLE_NAME='competition_entries' AND TG_OP<>'DELETE' THEN
        SELECT t.sport_id,ts.season_id INTO team_sport,team_season FROM team_seasons ts JOIN teams t ON t.id=ts.team_id WHERE ts.id=NEW.team_season_id;
        SELECT sport_id INTO competition_sport FROM competitions WHERE id=cs.competition_id;
        IF team_sport IS DISTINCT FROM competition_sport OR team_season IS DISTINCT FROM cs.season_id THEN
            RAISE EXCEPTION 'Entry must share sport and season' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER point_rule_guard BEFORE INSERT OR UPDATE OR DELETE ON competition_point_rules FOR EACH ROW EXECUTE FUNCTION p4_competition_child_guard();
CREATE TRIGGER forfeit_rule_guard BEFORE INSERT OR UPDATE OR DELETE ON competition_forfeit_rules FOR EACH ROW EXECUTE FUNCTION p4_competition_child_guard();
CREATE TRIGGER ranking_criterion_guard BEFORE INSERT OR UPDATE OR DELETE ON competition_ranking_criteria FOR EACH ROW EXECUTE FUNCTION p4_competition_child_guard();
CREATE TRIGGER competition_entry_guard BEFORE INSERT OR UPDATE OR DELETE ON competition_entries FOR EACH ROW EXECUTE FUNCTION p4_competition_child_guard();

CREATE FUNCTION p4_season_dates_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.starts_on,NEW.ends_on) IS DISTINCT FROM (OLD.starts_on,OLD.ends_on)
       AND EXISTS(SELECT 1 FROM competition_seasons WHERE season_id=OLD.id) THEN
        RAISE EXCEPTION 'Season dates in use by competition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER competition_season_dates_guard BEFORE UPDATE ON seasons FOR EACH ROW EXECUTE FUNCTION p4_season_dates_guard();
