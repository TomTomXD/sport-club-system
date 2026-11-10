import MockAdapter from "axios-mock-adapter";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/api";
import type { CompetitionConfiguration, CompetitionSeasonDetail } from "@/api/competitions";
import type { AccountContext, UserResponse } from "@/api/types";
import { AuthProvider } from "@/context/AuthContext";
import { adminContext, adminUser, completeUser, userContext } from "@/test/fixtures";
import { CompetitionsManager } from "./CompetitionsManager";
import { CompetitionSeasonManager } from "./CompetitionSeasonManager";
import { CompetitionConfigurationFields, configurationFromForm } from "./CompetitionConfigurationFields";

const push = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => "/admin/competitions", useRouter: () => ({ push }) }));
const pageOf = <T,>(items: T[]) => ({ items, page: 0, size: 20, totalElements: String(items.length), totalPages: items.length ? 1 : 0 });
const configuration: CompetitionConfiguration = {
  headToHeadMissingPolicy: "REQUIRE_COMPLETE", headToHeadMeetingsPerPair: 2, restartHeadToHeadOnSubgroup: true,
  pointRules: [
    { outcome: "WIN", ending: "REGULATION", ownScoreMin: null, ownScoreMax: null, opponentScoreMin: null, opponentScoreMax: null, points: 3 },
    { outcome: "DRAW", ending: "REGULATION", ownScoreMin: null, ownScoreMax: null, opponentScoreMin: null, opponentScoreMax: null, points: 1 },
    { outcome: "LOSS", ending: "REGULATION", ownScoreMin: null, ownScoreMax: null, opponentScoreMin: null, opponentScoreMax: null, points: 0 },
  ],
  forfeitRules: [{ decision: "FORFEIT_HOME", homeScore: 0, awayScore: 3, homePoints: 0, awayPoints: 3 }, { decision: "FORFEIT_AWAY", homeScore: 3, awayScore: 0, homePoints: 3, awayPoints: 0 }],
  rankingCriteria: [{ priority: 1, metric: "POINTS", direction: "DESC" }, { priority: 2, metric: "HEAD_TO_HEAD_POINTS", direction: "DESC" }],
};
const season: CompetitionSeasonDetail["season"] = { id: "cs", competitionId: "competition", competitionName: "Liga", seasonId: "season", seasonName: "2026", sportId: "football", sportName: "Fotbal", matchFormatId: "format", matchFormatName: "Dva poločasy", version: "9007199254740993", resultsRevision: "0", lockedAt: null, archived: false };
let mock: MockAdapter, identity: UserResponse, context: AccountContext;
beforeEach(() => {
  mock = new MockAdapter(apiClient); identity = adminUser; context = adminContext; push.mockReset();
  mock.onGet("/auth/me").reply(() => [200, identity]); mock.onGet("/me/context").reply(() => [200, context]);
  mock.onGet("/auth/csrf").reply(200, { token: "csrf", headerName: "X-CSRF-TOKEN" });
  mock.onGet("/sports").reply(200, pageOf([{ id: "football", name: "Fotbal", code: "F", active: true }]));
  mock.onGet("/seasons").reply(200, pageOf([{ id: "season", name: "2026", archived: false }]));
  mock.onGet("/competitions").reply(200, pageOf([{ id: "competition", name: "Liga", sportId: "football", sportName: "Fotbal" }]));
  mock.onGet("/match-formats").reply(200, pageOf([{ id: "format", name: "Dva poločasy", sportId: "football", configuration: { scoreMode: "SUM_SCORE", allowDraw: true, allowOvertime: false, allowShootout: false } }]));
  mock.onGet("/competition-seasons").reply(200, pageOf([season]));
  mock.onGet("/competition-seasons/cs").reply(200, { season, configuration });
  mock.onGet("/competition-seasons/cs/entries").reply(200, pageOf([]));
  mock.onGet("/team-seasons").reply(200, pageOf([{ id: "team", displayName: "Klub 2026", isClubTeam: true }]));
});
afterEach(() => { mock.restore(); vi.restoreAllMocks(); });
const index = () => render(<AuthProvider><CompetitionsManager /></AuthProvider>);
const detail = () => render(<AuthProvider><CompetitionSeasonManager id="cs" /></AuthProvider>);

describe("P4 soutěže a ročníky", () => {
  it.each(["STANDARD", "CHILD"] as const)("neoprávněný účet %s vůbec nespustí soutěžní dotazy", async (accountType) => {
    identity = { ...completeUser, accountType }; context = { ...userContext, accountType }; index();
    await screen.findByRole("heading", { name: "Tato stránka vyžaduje oprávnění správce." });
    expect(mock.history.get.map((r) => r.url)).not.toContain("/competitions");
    expect(mock.history.get.map((r) => r.url)).not.toContain("/competition-seasons");
  });
  it("vytvoří celý ročník jedním REST požadavkem s CSRF", async () => {
    mock.onPost("/competition-seasons").reply(201, { season, configuration });
    const actor = userEvent.setup(); index(); const form = await screen.findByRole("form", { name: "Nový soutěžní ročník" });
    await actor.selectOptions(within(form).getByLabelText("Soutěž ročníku"), "competition");
    await actor.selectOptions(within(form).getByLabelText("Sezóna ročníku"), "season");
    await actor.selectOptions(within(form).getByLabelText("Formát ročníku"), "format");
    for (let i = 0; i < 3; i++) {
      const row = within(form).getByRole("region", { name: `Bodovací pravidlo ${i + 1}` });
      const points = within(row).getByLabelText("Přidělené body"); await actor.clear(points); await actor.type(points, String([3, 1, 0][i]));
    }
    await actor.click(within(form).getByRole("button", { name: "Vytvořit soutěžní ročník" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/competitions/cs"));
    expect(mock.history.post).toHaveLength(1); expect(mock.history.post[0].headers?.["X-CSRF-TOKEN"]).toBe("csrf");
    const body = JSON.parse(mock.history.post[0].data as string) as { configuration: CompetitionConfiguration };
    expect(body).toMatchObject({ competitionId: "competition", seasonId: "season", matchFormatId: "format" });
    expect(body.configuration.pointRules).toEqual(configuration.pointRules);
    expect(body.configuration.forfeitRules.map((r) => r.decision)).toEqual(["FORFEIT_HOME", "FORFEIT_AWAY"]);
    expect(body.configuration.rankingCriteria).toEqual([{ priority: 1, metric: "POINTS", direction: "DESC" }]);
  });
  it("smazání řádku a změna priorit neztratí rozepsané hodnoty jiných pravidel", async () => {
    let captured: CompetitionConfiguration | undefined;
    render(<form aria-label="Editor" onSubmit={(event) => { event.preventDefault(); captured = configurationFromForm(new FormData(event.currentTarget)); }}><CompetitionConfigurationFields initial={configuration} disabled={false} /><button>Odeslat editor</button></form>);
    const actor = userEvent.setup(); const first = screen.getByRole("region", { name: "Bodovací pravidlo 1" });
    const points = within(first).getByLabelText("Přidělené body"); await actor.clear(points); await actor.type(points, "9");
    await actor.click(within(screen.getByRole("region", { name: "Bodovací pravidlo 2" })).getByRole("button", { name: "Odebrat bodovací pravidlo" }));
    await actor.click(within(screen.getByRole("region", { name: "Kritérium 2" })).getByRole("button", { name: "Posunout výš" }));
    await actor.click(screen.getByRole("button", { name: "Odeslat editor" }));
    expect(captured?.pointRules.map((r) => [r.outcome, r.points])).toEqual([["WIN", 9], ["LOSS", 0]]);
    expect(captured?.rankingCriteria).toEqual([{ priority: 1, metric: "HEAD_TO_HEAD_POINTS", direction: "DESC" }, { priority: 2, metric: "POINTS", direction: "DESC" }]);
  });
  it.each(["create", "rename"] as const)("uložení soutěže (%s) zachová celý rozepsaný ročník a aktualizuje výběr", async (operation) => {
    const original = { id: "competition", name: "Liga", sportId: "football", sportName: "Fotbal" };
    const saved = { ...original, id: operation === "create" ? "new-competition" : original.id, name: "Nová liga" };
    const reply = (): [number, typeof saved] => {
      mock.onGet("/competitions").reply(200, pageOf(operation === "create" ? [original, saved] : [saved]));
      return [operation === "create" ? 201 : 200, saved];
    };
    if (operation === "create") mock.onPost("/competitions").reply(reply);
    else mock.onPut("/competitions/competition").reply(reply);
    mock.onPost("/competition-seasons").reply(201, { season, configuration });
    const actor = userEvent.setup(); index();
    const form = await screen.findByRole("form", { name: "Nový soutěžní ročník" });
    await actor.selectOptions(within(form).getByLabelText("Soutěž ročníku"), "competition");
    await actor.selectOptions(within(form).getByLabelText("Sezóna ročníku"), "season");
    await actor.selectOptions(within(form).getByLabelText("Formát ročníku"), "format");
    const win = within(form).getByRole("region", { name: "Bodovací pravidlo 1" });
    await actor.clear(within(win).getByLabelText("Přidělené body"));
    await actor.type(within(win).getByLabelText("Přidělené body"), "9");
    await actor.type(within(win).getByLabelText("Vlastní skóre od"), "2");
    const forfeit = within(form).getByRole("region", { name: "Kontumace domácích" });
    await actor.clear(within(forfeit).getByLabelText("Body hostů"));
    await actor.type(within(forfeit).getByLabelText("Body hostů"), "7");
    await actor.click(within(form).getByRole("button", { name: "Přidat kritérium" }));
    await actor.selectOptions(within(within(form).getByRole("region", { name: "Kritérium 2" })).getByLabelText("Metrika"), "SCORE_DIFFERENCE");
    await actor.selectOptions(within(form).getByLabelText("Neúplné vzájemné zápasy"), "PLAYED_ONLY");
    await actor.clear(within(form).getByLabelText("Počet vzájemných zápasů každé dvojice"));
    await actor.type(within(form).getByLabelText("Počet vzájemných zápasů každé dvojice"), "3");
    await actor.click(within(form).getByLabelText("Zopakovat vzájemná kritéria pro menší shodnou podskupinu"));
    const draft = configurationFromForm(new FormData(form as HTMLFormElement));
    const competitionForm = screen.getByRole("form", { name: operation === "create" ? "Nová soutěž" : "Přejmenovat soutěž Liga" });
    if (operation === "create") await actor.selectOptions(within(competitionForm).getByLabelText("Sport soutěže"), "football");
    const name = within(competitionForm).getByLabelText(operation === "create" ? "Název soutěže" : "Nový název soutěže");
    await actor.clear(name); await actor.type(name, "Nová liga");
    await actor.click(within(competitionForm).getByRole("button", { name: operation === "create" ? "Vytvořit soutěž" : "Přejmenovat soutěž" }));
    await screen.findByRole("option", { name: "Nová liga · Fotbal" });
    const current = screen.getByRole("form", { name: "Nový soutěžní ročník" });
    expect(within(current).getByLabelText("Soutěž ročníku")).toHaveValue("competition");
    expect(within(current).getByLabelText("Sezóna ročníku")).toHaveValue("season");
    expect(within(current).getByLabelText("Formát ročníku")).toHaveValue("format");
    expect(configurationFromForm(new FormData(current as HTMLFormElement))).toEqual(draft);
    await actor.click(within(current).getByRole("button", { name: "Vytvořit soutěžní ročník" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/competitions/cs"));
    const request = mock.history.post.find((r) => r.url === "/competition-seasons")!;
    expect(JSON.parse(request.data as string)).toEqual({ competitionId: "competition", seasonId: "season", matchFormatId: "format", configuration: draft });
    expect(request.headers?.["X-CSRF-TOKEN"]).toBe("csrf");
    const mutation = (operation === "create" ? mock.history.post : mock.history.put).filter((r) => r.url === (operation === "create" ? "/competitions" : "/competitions/competition"));
    expect(mutation).toHaveLength(1);
    expect(mutation[0].headers?.["X-CSRF-TOKEN"]).toBe("csrf");
  });
  it("409 zachová celé pravidlo i přesnou version a reload je výslovný", async () => {
    mock.onPut("/competition-seasons/cs").reply(409, { timestamp: "2026-11-06T10:00:00Z", code: "OPTIMISTIC_LOCK_CONFLICT", message: "private internal detail" });
    const actor = userEvent.setup(); detail(); await screen.findByRole("heading", { name: "Liga · 2026" });
    await actor.click(screen.getByRole("button", { name: "Upravit pravidla ročníku" }));
    const form = screen.getByRole("form", { name: "Upravit pravidla ročníku" });
    const points = within(within(form).getByRole("region", { name: "Bodovací pravidlo 1" })).getByLabelText("Přidělené body");
    await actor.clear(points); await actor.type(points, "9");
    expect(screen.getByRole("button", { name: "Přihlásit tým" })).toBeDisabled();
    await actor.click(within(form).getByRole("button", { name: "Uložit pravidla ročníku" }));
    await within(form).findByRole("alert"); expect(points).toHaveValue(9);
    expect(JSON.parse(mock.history.put[0].data as string)).toMatchObject({ version: season.version, configuration: { pointRules: [{ ...configuration.pointRules[0], points: 9 }, ...configuration.pointRules.slice(1)] } });
    expect(mock.history.put).toHaveLength(1); expect(mock.history.get.filter((r) => r.url === "/competition-seasons/cs")).toHaveLength(1);
    expect(screen.queryByText("private internal detail")).not.toBeInTheDocument();
    mock.onGet("/competition-seasons/cs").reply(200, { season: { ...season, version: "9007199254740994", lockedAt: "2026-11-06T12:00:00Z" }, configuration });
    await actor.click(within(form).getByRole("button", { name: "Zahodit úpravy a načíst aktuální ročník" }));
    await screen.findByText(/Pravidla a přihlášky jsou pouze ke čtení/);
    expect(screen.queryByRole("button", { name: "Upravit pravidla ročníku" })).not.toBeInTheDocument();
  });
  it("přihláška používá verzi ročníku a nové načtení, odebrání má CSRF a přesnou verzi", async () => {
    mock.onPost("/competition-seasons/cs/entries").reply(() => {
      mock.onGet("/competition-seasons/cs").reply(200, { season: { ...season, version: "9007199254740994" }, configuration });
      mock.onGet("/competition-seasons/cs/entries").reply(200, pageOf([{ teamSeasonId: "team", displayName: "Klub 2026", isClubTeam: true }]));
      return [200, { ...season, version: "9007199254740994" }];
    });
    mock.onDelete("/competition-seasons/cs/entries/team").reply(() => {
      mock.onGet("/competition-seasons/cs").reply(200, { season: { ...season, version: "9007199254740995" }, configuration });
      mock.onGet("/competition-seasons/cs/entries").reply(200, pageOf([])); return [200, season];
    });
    const actor = userEvent.setup(); detail(); const form = await screen.findByRole("form", { name: "Přihlásit tým" });
    await actor.selectOptions(within(form).getByLabelText("Tým do soutěže"), "team"); await actor.click(within(form).getByRole("button", { name: "Přihlásit tým" }));
    const entry = await screen.findByRole("article", { name: "Klub 2026" });
    expect(JSON.parse(mock.history.post[0].data as string)).toEqual({ teamSeasonId: "team", version: season.version });
    await actor.click(within(entry).getByRole("button", { name: "Odebrat přihlášku" }));
    await screen.findByText("Zatím nejsou přihlášené týmy.");
    expect(JSON.parse(mock.history.delete[0].data as string)).toEqual({ version: "9007199254740994" });
    expect(mock.history.delete[0].headers?.["X-CSRF-TOKEN"]).toBe("csrf");
    expect(mock.history.get.find((r) => r.url === "/team-seasons")?.params).toMatchObject({ seasonId: "season", sportId: "football" });
  });
  it("archivovaný ročník ponechá čtení a nenabídne správní mutace", async () => {
    mock.onGet("/competition-seasons/cs").reply(200, { season: { ...season, archived: true }, configuration });detail();
    await screen.findByText(/Sezóna je archivovaná/);
    expect(screen.queryByRole("button", { name: "Přihlásit tým" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upravit pravidla ročníku" })).not.toBeInTheDocument();
  });
});
