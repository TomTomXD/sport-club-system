import { apiClient, mutateWithCsrf } from "./api";
import type { PageResponse } from "./types";

export type Outcome = "WIN" | "DRAW" | "LOSS";
export type Ending = "REGULATION" | "OVERTIME" | "SHOOTOUT";
export type Decision = "FORFEIT_HOME" | "FORFEIT_AWAY";
export type Metric = "POINTS" | "WINS" | "SCORE_DIFFERENCE" | "SCORE_FOR" | "SCORE_RATIO" | "PART_SCORE_DIFFERENCE" | "PART_SCORE_RATIO" | "HEAD_TO_HEAD_POINTS" | "HEAD_TO_HEAD_SCORE_DIFFERENCE" | "HEAD_TO_HEAD_SCORE_FOR";
export interface PointRule { outcome: Outcome; ending: Ending; ownScoreMin: number | null; ownScoreMax: number | null; opponentScoreMin: number | null; opponentScoreMax: number | null; points: number }
export interface ForfeitRule { decision: Decision; homeScore: number; awayScore: number; homePoints: number; awayPoints: number }
export interface RankingCriterion { priority: number; metric: Metric; direction: "ASC" | "DESC" }
export interface CompetitionConfiguration {
  headToHeadMissingPolicy: "REQUIRE_COMPLETE" | "PLAYED_ONLY";
  headToHeadMeetingsPerPair: number; restartHeadToHeadOnSubgroup: boolean;
  pointRules: PointRule[]; forfeitRules: ForfeitRule[]; rankingCriteria: RankingCriterion[];
}
export interface Competition { id: string; sportId: string; sportName: string; name: string }
export interface CompetitionSeason {
  id: string; competitionId: string; competitionName: string; seasonId: string; seasonName: string;
  sportId: string; sportName: string; matchFormatId: string; matchFormatName: string;
  version: string; resultsRevision: string; lockedAt: string | null; archived: boolean;
}
export interface CompetitionSeasonDetail { season: CompetitionSeason; configuration: CompetitionConfiguration }
export interface CompetitionEntry { teamSeasonId: string; displayName: string; isClubTeam: boolean }
export interface CreateCompetitionSeason { competitionId: string; seasonId: string; matchFormatId: string; configuration: CompetitionConfiguration }
const path = (id: string) => `/competition-seasons/${encodeURIComponent(id)}`;
export async function getCompetitions(page = 0, size = 20, sportId?: string): Promise<PageResponse<Competition>> {
  return (await apiClient.get<PageResponse<Competition>>("/competitions", { params: { page, size, sportId } })).data;
}
export const createCompetition = (body: { sportId: string; name: string }) => mutateWithCsrf<Competition, typeof body>("POST", "/competitions", body);
export const renameCompetition = (id: string, name: string) => mutateWithCsrf<Competition, { name: string }>("PUT", `/competitions/${encodeURIComponent(id)}`, { name });
export async function getCompetitionSeasons(page = 0, size = 20, filters?: { sportId?: string; competitionId?: string; seasonId?: string }): Promise<PageResponse<CompetitionSeason>> {
  return (await apiClient.get<PageResponse<CompetitionSeason>>("/competition-seasons", { params: { page, size, ...filters } })).data;
}
export async function getCompetitionSeason(id: string): Promise<CompetitionSeasonDetail> { return (await apiClient.get<CompetitionSeasonDetail>(path(id))).data; }
export const createCompetitionSeason = (body: CreateCompetitionSeason) => mutateWithCsrf<CompetitionSeasonDetail, CreateCompetitionSeason>("POST", "/competition-seasons", body);
export const updateCompetitionSeason = (id: string, body: { version: string; configuration: CompetitionConfiguration }) => mutateWithCsrf<CompetitionSeasonDetail, typeof body>("PUT", path(id), body);
export async function getCompetitionEntries(id: string, page = 0, size = 20): Promise<PageResponse<CompetitionEntry>> {
  return (await apiClient.get<PageResponse<CompetitionEntry>>(`${path(id)}/entries`, { params: { page, size } })).data;
}
export const addCompetitionEntry = (id: string, body: { version: string; teamSeasonId: string }) => mutateWithCsrf<CompetitionSeason, typeof body>("POST", `${path(id)}/entries`, body);
export const removeCompetitionEntry = (id: string, team: string, version: string) => mutateWithCsrf<CompetitionSeason, { version: string }>("DELETE", `${path(id)}/entries/${encodeURIComponent(team)}`, { version });
