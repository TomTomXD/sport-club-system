package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos;
import org.springframework.stereotype.Component;
import java.util.*;

/** Validates data-defined rules without branching on a sport name or code. */
@Component
public class CompetitionConfigurationValidator {
    private static final long MAX = Integer.MAX_VALUE;

    public void validate(Configuration c, MatchFormatDtos.Configuration format) {
        validatePoints(c.pointRules(), format);
        var decisions = EnumSet.noneOf(Decision.class);
        for (var r : c.forfeitRules()) {
            if (!decisions.add(r.decision()) || (r.decision() == Decision.FORFEIT_HOME
                    ? r.homeScore() >= r.awayScore() : r.awayScore() >= r.homeScore())) {
                invalid("forfeitRules", "Každá kontumace musí právě jednou určit správného vítěze.");
            }
        }
        if (decisions.size() != 2) invalid("forfeitRules", "Doplňte kontumaci domácích i hostů.");
        var metrics = EnumSet.noneOf(Metric.class);
        var sorted = c.rankingCriteria().stream().sorted(Comparator.comparing(RankingCriterion::priority)).toList();
        if (sorted.isEmpty()) invalid("rankingCriteria", "Zadejte alespoň jedno kritérium pořadí.");
        boolean h2hStarted = false, h2hEnded = false;
        for (int i = 0; i < sorted.size(); i++) {
            var r = sorted.get(i);
            if (r.priority() != i + 1 || !metrics.add(r.metric())) {
                invalid("rankingCriteria", "Kritéria musí být jedinečná a priority souvislé od 1.");
            }
            if (r.metric().name().startsWith("PART_SCORE_") && !format.periodsRequired()) {
                invalid("rankingCriteria", "Skóre částí vyžaduje povinnou evidenci částí ve formátu.");
            }
            if (r.metric().name().startsWith("HEAD_TO_HEAD_")) {
                if (h2hEnded) invalid("rankingCriteria", "Vzájemná kritéria musí tvořit souvislý blok.");
                h2hStarted = true;
            } else if (h2hStarted) h2hEnded = true;
        }
    }

    private void validatePoints(List<PointRule> rules, MatchFormatDtos.Configuration f) {
        for (var r : rules) {
            if (!allowed(r.ending(), f) || (r.outcome() == Outcome.DRAW && (!f.allowDraw() || r.ending() == Ending.SHOOTOUT))) {
                invalid("pointRules", "Výsledek nebo ukončení není povolené formátem.");
            }
            if (min(r.ownScoreMin()) > max(r.ownScoreMax()) || min(r.opponentScoreMin()) > max(r.opponentScoreMax())) {
                invalid("pointRules", "Dolní hranice skóre nesmí převyšovat horní.");
            }
            long[] d = domain(r.outcome(), f);
            if (!possible(Math.max(d[0], min(r.ownScoreMin())), Math.min(d[1], max(r.ownScoreMax())),
                    Math.max(d[2], min(r.opponentScoreMin())), Math.min(d[3], max(r.opponentScoreMax())), r.outcome(), r.ending(), f)) {
                invalid("pointRules", "Rozsah neobsahuje žádný výsledek povolený formátem.");
            }
        }
        for (Ending ending : Ending.values()) {
            if (!allowed(ending, f)) continue;
            for (Outcome outcome : Outcome.values()) {
                if (outcome == Outcome.DRAW && (!f.allowDraw() || ending == Ending.SHOOTOUT)) continue;
                var group = rules.stream().filter(r -> r.ending() == ending && r.outcome() == outcome).toList();
                for (int i = 0; i < group.size(); i++) for (int j = i + 1; j < group.size(); j++) {
                    var a = group.get(i); var b = group.get(j);
                    if (Math.max(min(a.ownScoreMin()), min(b.ownScoreMin())) <= Math.min(max(a.ownScoreMax()), max(b.ownScoreMax()))
                            && Math.max(min(a.opponentScoreMin()), min(b.opponentScoreMin())) <= Math.min(max(a.opponentScoreMax()), max(b.opponentScoreMax()))) {
                        invalid("pointRules", "Bodovací pravidla pro stejný výsledek a ukončení se překrývají.");
                    }
                }
                ensureCoverage(group, outcome, ending, f);
            }
        }
    }

    private void ensureCoverage(List<PointRule> group, Outcome outcome, Ending ending, MatchFormatDtos.Configuration f) {
        long[] d = domain(outcome, f);
        var x = new TreeSet<Long>(List.of(d[0], d[1] + 1));
        var y = new TreeSet<Long>(List.of(d[2], d[3] + 1));
        for (var r : group) {
            boundary(x, min(r.ownScoreMin()), d[0], d[1]); boundary(x, max(r.ownScoreMax()) + 1, d[0], d[1]);
            boundary(y, min(r.opponentScoreMin()), d[2], d[3]); boundary(y, max(r.opponentScoreMax()) + 1, d[2], d[3]);
        }
        var xs = new ArrayList<>(x); var ys = new ArrayList<>(y);
        // Membership in any rectangle is constant within these cells. This covers all
        // integer scores, including unbounded ranges and MAX_VALUE, without enumerating scores.
        for (int i = 0; i + 1 < xs.size(); i++) for (int j = 0; j + 1 < ys.size(); j++) {
            long a = xs.get(i), b = xs.get(i + 1) - 1, c = ys.get(j), dmax = ys.get(j + 1) - 1;
            if (!possible(a, b, c, dmax, outcome, ending, f)) continue;
            if (group.stream().noneMatch(r -> min(r.ownScoreMin()) <= a && max(r.ownScoreMax()) >= a
                    && min(r.opponentScoreMin()) <= c && max(r.opponentScoreMax()) >= c)) {
                invalid("pointRules", "Bodování nepokrývá všechny povolené výsledky a ukončení.");
            }
        }
    }

    private long[] domain(Outcome outcome, MatchFormatDtos.Configuration f) {
        if (f.scoreMode() == MatchFormatDtos.ScoreMode.WON_PARTS) {
            long wins = f.winsRequired();
            return outcome == Outcome.WIN ? new long[]{wins, wins, 0, wins - 1} : new long[]{0, wins - 1, wins, wins};
        }
        return new long[]{0, MAX, 0, MAX};
    }
    private boolean possible(long a, long b, long c, long d, Outcome outcome, Ending ending, MatchFormatDtos.Configuration f) {
        if (a > b || c > d) return false;
        if (ending == Ending.SHOOTOUT) {
            long delta = outcome == Outcome.WIN ? f.shootoutWinnerBonus() : -((long) f.shootoutWinnerBonus());
            return a - d <= delta && b - c >= delta;
        }
        return switch (outcome) { case WIN -> b > c; case LOSS -> a < d; case DRAW -> Math.max(a, c) <= Math.min(b, d); };
    }
    private boolean allowed(Ending e, MatchFormatDtos.Configuration f) {
        return switch (e) { case REGULATION -> true; case OVERTIME -> f.allowOvertime(); case SHOOTOUT -> f.allowShootout(); };
    }
    private long min(Integer n) { return n == null ? 0 : n; }
    private long max(Integer n) { return n == null ? MAX : n; }
    private void boundary(Set<Long> set, long value, long low, long high) { if (value > low && value <= high) set.add(value); }
    private void invalid(String field, String message) { throw ApiException.invalidField("configuration." + field, message); }
}
