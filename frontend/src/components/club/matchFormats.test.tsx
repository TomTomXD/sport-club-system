import MockAdapter from "axios-mock-adapter";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/api";
import type { AccountContext, UserResponse } from "@/api/types";
import type { MatchFormatResponse } from "@/api/matchFormats";
import { AuthProvider } from "@/context/AuthContext";
import { adminContext, adminUser, completeUser, userContext } from "@/test/fixtures";
import { MatchFormatsManager } from "./MatchFormatsManager";

vi.mock("next/navigation", () => ({ usePathname: () => "/admin/match-formats" }));
let mock: MockAdapter;
let identity: UserResponse;
let context: AccountContext;
const pageOf = <T,>(items: T[]) => ({ items, page: 0, size: 20, totalElements: String(items.length), totalPages: items.length ? 1 : 0 });
const sports = [{ id: "football", code: "F", name: "Fotbal", active: true }, { id: "basketball", code: "B", name: "Basketbal", active: true }];
const format: MatchFormatResponse = {
  id: "format", sportId: "football", sportName: "Fotbal", name: "Testovací formát", version: "9007199254740993", lockedAt: null,
  configuration: { scoreMode: "SUM_SCORE", allowDraw: true, periodsRequired: false, periodLabel: "Poločas", regulationPeriodCount: 2, winsRequired: null, allowOvertime: false, allowShootout: false, shootoutWinnerBonus: 0, friendlyForfeitLoserScore: 0, friendlyForfeitWinnerScore: 3 },
};
beforeEach(() => {
  mock = new MockAdapter(apiClient); identity = adminUser; context = adminContext;
  mock.onGet("/auth/me").reply(() => [200, identity]); mock.onGet("/me/context").reply(() => [200, context]);
  mock.onGet("/auth/csrf").reply(200, { token: "csrf", headerName: "X-CSRF-TOKEN" });
  mock.onGet("/sports").reply(200, pageOf(sports)); mock.onGet("/match-formats").reply(200, pageOf([]));
});
afterEach(() => { mock.restore(); vi.restoreAllMocks(); });
const view = () => render(<AuthProvider><MatchFormatsManager /></AuthProvider>);

describe("P4 formáty zápasu", () => {
  it.each(["STANDARD", "CHILD"] as const)("účet %s bez ADMIN nenačte správní data", async (accountType) => {
    identity = { ...completeUser, accountType }; context = { ...userContext, accountType };
    view(); await screen.findByRole("heading", { name: "Tato stránka vyžaduje oprávnění správce." });
    expect(mock.history.get.map((r) => r.url)).not.toContain("/match-formats");
    expect(mock.history.get.map((r) => r.url)).not.toContain("/sports");
  });
  it("označí chybný sport a zachová formulář bez zobrazení serverových detailů", async () => {
    mock.onPost("/match-formats").reply(400, {
      code: "VALIDATION_ERROR",
      timestamp: "2026-11-06T10:00:00Z", message: "private detail",
      fieldErrors: [{ field: "sportId", message: "private detail", rejectedValue: "private value" }],
    });
    const actor = userEvent.setup(); view();
    const form = await screen.findByRole("form", { name: "Nový formát zápasu" });
    const sport = within(form).getByLabelText("Sport formátu");
    await actor.selectOptions(sport, "football");
    await actor.type(within(form).getByLabelText("Název formátu"), "Rozepsaný formát");
    await actor.click(within(form).getByRole("button", { name: "Vytvořit formát" }));
    await within(form).findByRole("alert");
    expect(sport).toHaveAttribute("aria-invalid", "true");
    expect(sport).toHaveAccessibleDescription("Zkontrolujte hodnotu tohoto pole.");
    expect(sport).toHaveValue("football");
    expect(within(form).getByLabelText("Název formátu")).toHaveValue("Rozepsaný formát");
    expect(screen.queryByText(/private/)).not.toBeInTheDocument();
    mock.onPost("/match-formats").reply(201, format);
    await actor.click(within(form).getByRole("button", { name: "Vytvořit formát" }));
    await screen.findByText("Formát zápasu byl uložen.");
    expect(mock.history.post).toHaveLength(2);
  });
  it("souběžné odeslání téhož formuláře vytvoří jen jeden požadavek", async () => {
    let finish!: () => void;
    const response = new Promise<[number, MatchFormatResponse]>((resolve) => {
      finish = () => resolve([201, format]);
    });
    mock.onPost("/match-formats").reply(() => response);
    const actor = userEvent.setup(); view();
    const form = await screen.findByRole("form", { name: "Nový formát zápasu" });
    await actor.selectOptions(within(form).getByLabelText("Sport formátu"), "football");
    await actor.type(within(form).getByLabelText("Název formátu"), "Jeden formát");
    try {
      fireEvent.submit(form);
      fireEvent.submit(form);
      await waitFor(() => expect(mock.history.post).toHaveLength(1));
      fireEvent.submit(form);
      expect(within(form).getByRole("button", { name: "Ukládám…" })).toBeDisabled();
    } finally { finish(); }
    await screen.findByText("Formát zápasu byl uložen.");
    expect(mock.history.post).toHaveLength(1);
    expect(mock.history.get.filter((r) => r.url === "/auth/csrf")).toHaveLength(1);
  });
  it.each([
    ["football", "Fotbalový formát", "Poločas", "2", true, false],
    ["basketball", "Basketbalový formát", "Čtvrtina", "4", false, true],
  ] as const)("uloží %s přes REST s CSRF a nezávislými pravidly", async (sport, name, label, count, draw, overtime) => {
    mock.onPost("/match-formats").reply(201, format);
    const actor = userEvent.setup(); view(); const form = await screen.findByRole("form", { name: "Nový formát zápasu" });
    await actor.selectOptions(within(form).getByLabelText("Sport formátu"), sport);
    await actor.type(within(form).getByLabelText("Název formátu"), name);
    const period = within(form).getByLabelText("Označení části"); await actor.clear(period); await actor.type(period, label);
    await actor.type(within(form).getByLabelText("Počet částí základní doby"), count);
    if (draw) await actor.click(within(form).getByLabelText("Povolit remízu"));
    if (overtime) await actor.click(within(form).getByLabelText("Povolit prodloužení"));
    await actor.click(within(form).getByRole("button", { name: "Vytvořit formát" }));
    await screen.findByText("Formát zápasu byl uložen.");
    expect(mock.history.post).toHaveLength(1);
    expect(mock.history.post[0].headers?.["X-CSRF-TOKEN"]).toBe("csrf");
    expect(JSON.parse(mock.history.post[0].data as string)).toEqual({ sportId: sport, name, configuration: { ...format.configuration, periodLabel: label, regulationPeriodCount: Number(count), allowDraw: draw, allowOvertime: overtime, friendlyForfeitWinnerScore: 1 } });
  });
  it("při konfliktu zachová úpravy i přesnou verzi a načte nový stav až na pokyn", async () => {
    mock.onGet("/match-formats").reply(200, pageOf([format]));
    mock.onPut("/match-formats/format").reply(409, { code: "OPTIMISTIC_LOCK_CONFLICT", timestamp: "2026-11-06T10:00:00Z", message: "private must not render" });
    const actor = userEvent.setup(); view(); const card = await screen.findByRole("article", { name: format.name });
    await actor.click(within(card).getByRole("button", { name: "Upravit formát" }));
    const form = within(card).getByRole("form"); const name = within(form).getByLabelText("Název formátu");
    await actor.clear(name); await actor.type(name, "Rozepsaná změna"); await actor.click(within(form).getByRole("button", { name: "Uložit formát" }));
    await within(form).findByRole("alert"); expect(name).toHaveValue("Rozepsaná změna");
    expect(JSON.parse(mock.history.put[0].data as string).version).toBe("9007199254740993");
    expect(mock.history.put).toHaveLength(1); expect(mock.history.get.filter((r) => r.url === "/match-formats")).toHaveLength(1);
    expect(screen.queryByText("private must not render")).not.toBeInTheDocument();
    mock.onGet("/match-formats").reply(200, pageOf([{ ...format, version: "9007199254740994", name: "Aktuální název" }]));
    await actor.click(within(form).getByRole("button", { name: "Zahodit úpravy a načíst aktuální stav" }));
    await screen.findByRole("article", { name: "Aktuální název" });
  });
  it("použitý formát přejmenuje se zachováním všech zakázaných polí", async () => {
    const locked = { ...format, lockedAt: "2026-11-01T10:00:00Z" };
    mock.onGet("/match-formats").reply(200, pageOf([locked])); mock.onPut("/match-formats/format").reply(200, locked);
    const actor = userEvent.setup(); view(); const card = await screen.findByRole("article", { name: format.name });
    await actor.click(within(card).getByRole("button", { name: "Upravit formát" })); const form = within(card).getByRole("form");
    expect(within(form).getByLabelText("Povolit remízu")).toBeDisabled(); expect(within(form).getByLabelText("Vyhodnocení výsledku")).toBeDisabled();
    await actor.clear(within(form).getByLabelText("Název formátu")); await actor.type(within(form).getByLabelText("Název formátu"), "Nový název");
    await actor.click(within(form).getByRole("button", { name: "Uložit formát" }));
    await waitFor(() => expect(mock.history.put).toHaveLength(1));
    expect(JSON.parse(mock.history.put[0].data as string)).toEqual({ version: format.version, name: "Nový název", configuration: format.configuration });
  });
  it("prázdný aktivní sport nenahrazuje mockem a nezpřístupní vytvoření", async () => {
    mock.onGet("/sports").reply(200, pageOf([{ ...sports[0], active: false }])); view();
    const form = await screen.findByRole("form", { name: "Nový formát zápasu" });
    expect(within(form).getByRole("button", { name: "Vytvořit formát" })).toBeDisabled();
    expect(mock.history.post).toHaveLength(0);
  });
});
