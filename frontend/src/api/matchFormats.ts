import { apiClient, mutateWithCsrf } from "./api";
import type { PageResponse } from "./types";

export interface MatchFormatConfiguration {
  scoreMode: "SUM_SCORE" | "WON_PARTS";
  allowDraw: boolean;
  periodsRequired: boolean;
  periodLabel: string;
  regulationPeriodCount: number | null;
  winsRequired: number | null;
  allowOvertime: boolean;
  allowShootout: boolean;
  shootoutWinnerBonus: number;
  friendlyForfeitLoserScore: number;
  friendlyForfeitWinnerScore: number;
}
export interface MatchFormatRequest { sportId: string; name: string; configuration: MatchFormatConfiguration }
export interface MatchFormatResponse extends MatchFormatRequest { id: string; sportName: string; version: string; lockedAt: string | null }
export interface MatchFormatUpdate { version: string; name: string; configuration: MatchFormatConfiguration }

export async function getMatchFormats(page = 0, size = 20, sportId?: string): Promise<PageResponse<MatchFormatResponse>> {
  const { data } = await apiClient.get<PageResponse<MatchFormatResponse>>("/match-formats", { params: { page, size, sportId } });
  return data;
}
export const getMatchFormat = async (id: string): Promise<MatchFormatResponse> => {
  const { data } = await apiClient.get<MatchFormatResponse>(`/match-formats/${encodeURIComponent(id)}`);
  return data;
};
export const createMatchFormat = (body: MatchFormatRequest) => mutateWithCsrf<MatchFormatResponse, MatchFormatRequest>("POST", "/match-formats", body);
export const updateMatchFormat = (id: string, body: MatchFormatUpdate) => mutateWithCsrf<MatchFormatResponse, MatchFormatUpdate>("PUT", `/match-formats/${encodeURIComponent(id)}`, body);
