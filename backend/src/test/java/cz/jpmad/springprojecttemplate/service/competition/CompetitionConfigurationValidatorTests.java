package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CompetitionConfigurationValidatorTests {
    private final CompetitionConfigurationValidator validator=new CompetitionConfigurationValidator();
    private final MatchFormatDtos.Configuration football=format(true,false,false,0);
    private MatchFormatDtos.Configuration format(boolean draw,boolean overtime,boolean shootout,int bonus) {
        return new MatchFormatDtos.Configuration(MatchFormatDtos.ScoreMode.SUM_SCORE,draw,false,"Část",null,null,overtime,shootout,bonus,0,3);
    }
    private PointRule point(Outcome outcome,Ending ending,Integer ownMin,Integer ownMax,Integer oppMin,Integer oppMax,int points) {
        return new PointRule(outcome,ending,ownMin,ownMax,oppMin,oppMax,points);
    }
    private List<PointRule> rules(boolean draw,Ending... endings) {
        var result=new ArrayList<PointRule>();
        for (var e:endings) for (var o:Outcome.values()) if(o!=Outcome.DRAW || draw && e!=Ending.SHOOTOUT) result.add(point(o,e,null,null,null,null,o==Outcome.WIN?3:o==Outcome.DRAW?1:0));
        return result;
    }
    private Configuration config(List<PointRule> rules) {
        return new Configuration(MissingPolicy.REQUIRE_COMPLETE,2,true,rules,
                List.of(new ForfeitRule(Decision.FORFEIT_HOME,0,3,0,3),new ForfeitRule(Decision.FORFEIT_AWAY,3,0,3,0)),List.of(new RankingCriterion(1,Metric.POINTS,Direction.DESC)));
    }
    private Configuration ranking(Configuration c,Metric... metrics) {
        var ranking=new ArrayList<RankingCriterion>();for(int i=0;i<metrics.length;i++) ranking.add(new RankingCriterion(i+1,metrics[i],Direction.DESC));
        return new Configuration(c.headToHeadMissingPolicy(),c.headToHeadMeetingsPerPair(),c.restartHeadToHeadOnSubgroup(),c.pointRules(),c.forfeitRules(),ranking);
    }
    private void invalid(Configuration c,MatchFormatDtos.Configuration f,String field) {
        assertThatThrownBy(()->validator.validate(c,f)).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.code()).isEqualTo("VALIDATION_ERROR"); assertThat(e.fieldErrors().get(0).field()).isEqualTo("configuration."+field);
        });
    }
    @Test void footballAndBasketballUseDataDefinedRulesAndAllAllowedEndings() {
        validator.validate(config(rules(true,Ending.REGULATION)),football);
        var basketball=format(false,true,false,0);
        validator.validate(ranking(config(rules(false,Ending.REGULATION,Ending.OVERTIME)),Metric.POINTS,Metric.HEAD_TO_HEAD_POINTS,Metric.HEAD_TO_HEAD_SCORE_DIFFERENCE,Metric.SCORE_FOR),basketball);
        invalid(config(rules(false,Ending.REGULATION)),basketball,"pointRules");
        invalid(config(rules(true,Ending.REGULATION,Ending.OVERTIME)),basketball,"pointRules");
    }
    @Test void inclusivePartitionsCoverAllScoresIncludingMaximumAndRejectOnePointGapsOrOverlaps() {
        var rules=rules(true,Ending.REGULATION);rules.removeIf(r->r.outcome()==Outcome.WIN);
        rules.add(point(Outcome.WIN,Ending.REGULATION,null,Integer.MAX_VALUE,null,1,3));
        rules.add(point(Outcome.WIN,Ending.REGULATION,null,null,2,Integer.MAX_VALUE,2));
        validator.validate(config(rules),football);
        rules.set(rules.size()-1,point(Outcome.WIN,Ending.REGULATION,null,null,1,null,2));
        invalid(config(rules),football,"pointRules");
        rules.set(rules.size()-1,point(Outcome.WIN,Ending.REGULATION,null,null,3,null,2));
        invalid(config(rules),football,"pointRules");
    }
    @Test void wonPartsCompletenessOnlyRequiresReachableFinalSetScores() {
        var f=new MatchFormatDtos.Configuration(MatchFormatDtos.ScoreMode.WON_PARTS,false,true,"Set",null,3,false,false,0,0,3);
        var rules=new ArrayList<>(List.of(
                point(Outcome.WIN,Ending.REGULATION,3,3,0,1,3),point(Outcome.WIN,Ending.REGULATION,3,3,2,2,2),
                point(Outcome.LOSS,Ending.REGULATION,0,1,3,3,0),point(Outcome.LOSS,Ending.REGULATION,2,2,3,3,1)));
        validator.validate(config(rules),f);
        rules.remove(1); invalid(config(rules),f,"pointRules");
    }
    @Test void shootoutWithZeroBonusCanHaveEqualFinalScoresButStillHasWinnerAndLoser() {
        var f=format(false,true,true,0);var rules=rules(false,Ending.REGULATION,Ending.OVERTIME,Ending.SHOOTOUT);
        validator.validate(config(rules),f);
        rules.replaceAll(r->r.ending()==Ending.SHOOTOUT && r.outcome()==Outcome.WIN?point(r.outcome(),r.ending(),1,null,null,null,3):r);
        invalid(config(rules),f,"pointRules"); // 0:0 after shootout is still possible.
        validator.validate(config(rules(false,Ending.REGULATION,Ending.OVERTIME,Ending.SHOOTOUT)),format(false,true,true,Integer.MAX_VALUE));
    }
    @Test void impossibleRangesAndMissingDrawZeroZeroAreRejected() {
        var rules=rules(true,Ending.REGULATION);
        rules.replaceAll(r->r.outcome()==Outcome.WIN?point(r.outcome(),r.ending(),0,1,2,3,3):r);
        invalid(config(rules),football,"pointRules");
        rules=rules(true,Ending.REGULATION);rules.replaceAll(r->r.outcome()==Outcome.DRAW?point(r.outcome(),r.ending(),1,null,1,null,1):r);
        invalid(config(rules),football,"pointRules");
    }
    @Test void headToHeadBlockMustBeContiguousAndPartsMetricsNeedRequiredParts() {
        var c=config(rules(true,Ending.REGULATION));
        invalid(ranking(c,Metric.HEAD_TO_HEAD_POINTS,Metric.POINTS,Metric.HEAD_TO_HEAD_SCORE_FOR),football,"rankingCriteria");
        invalid(ranking(c,Metric.POINTS,Metric.POINTS),football,"rankingCriteria");
        invalid(ranking(c,Metric.PART_SCORE_RATIO),football,"rankingCriteria");
        validator.validate(ranking(c,Metric.SCORE_RATIO,Metric.HEAD_TO_HEAD_POINTS,Metric.HEAD_TO_HEAD_SCORE_FOR),football);
    }
    @Test void bothDistinctForfeitDecisionsMustDetermineTheCorrectWinner() {
        var c=config(rules(true,Ending.REGULATION));
        invalid(new Configuration(c.headToHeadMissingPolicy(),1,true,c.pointRules(),List.of(c.forfeitRules().get(0),c.forfeitRules().get(0)),c.rankingCriteria()),football,"forfeitRules");
        invalid(new Configuration(c.headToHeadMissingPolicy(),1,true,c.pointRules(),List.of(new ForfeitRule(Decision.FORFEIT_HOME,3,0,0,3),c.forfeitRules().get(1)),c.rankingCriteria()),football,"forfeitRules");
    }
}
