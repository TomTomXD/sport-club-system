import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";

const apiUrl = process.env.E2E_API_URL ?? "http://localhost:8080/api";

// This setup scenario needs its own fresh DB, separate from the other setup specs.
test("P4: oba sporty, soutěže, ročníky, pravidla, přihlášky, validace a souběh", async ({ page, context }, testInfo) => {
  test.setTimeout(240_000);
  const token = process.env.E2E_SETUP_TOKEN;
  test.skip(!token, "Vyžaduje soukromý E2E_SETUP_TOKEN a novou izolovanou databázi.");
  test.skip(testInfo.project.name !== (process.env.E2E_SETUP_PROJECT ?? "desktop"), "Jeden setup projekt na jednu čistou databázi.");
  const suffix = randomUUID().slice(0, 8);
  const username = "competitions-e2e-" + suffix;
  await page.goto("/register");
  await page.getByLabel("Uživatelské jméno").fill(username);
  await page.getByLabel("Heslo", { exact: true }).fill("E2e-test-password-2026");
  await page.getByLabel("Heslo znovu").fill("E2e-test-password-2026");
  await page.getByRole("button", { name: "Vytvořit účet" }).click();
  await expect(page.getByRole("heading", { name: "Účet je připravený." })).toBeVisible();
  await page.goto("/login");
  await page.getByLabel("Uživatelské jméno").fill(username);
  await page.getByLabel("Heslo", { exact: true }).fill("E2e-test-password-2026");
  await page.getByRole("button", { name: "Přihlásit se" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto("/profile");
  await page.getByLabel("Jméno", { exact: true }).fill("Testovací");
  await page.getByLabel("Příjmení", { exact: true }).fill("Správce");
  const onboarding = page.waitForResponse(r => r.url().endsWith("/me/person") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Uložit osobní údaje" }).click();
  expect((await onboarding).status()).toBe(201);
  await expect.poll(async () => (await (await page.request.get(apiUrl + "/auth/me")).json()).profileState).toBe("COMPLETE");
  await page.goto("/setup");
  await page.getByLabel("Instalační token").fill(token!);
  const setup = page.waitForResponse(r => r.url().endsWith("/club/setup") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Převzít správu klubu" }).click();
  expect((await setup).status()).toBe(200);
  await expect.poll(async () => (await (await page.request.get(apiUrl + "/auth/me")).json()).role).toBe("ADMIN");

  const sports = ["Fotbal " + suffix, "Basketbal " + suffix];
  const formatNames = ["Poločasy " + suffix, "Čtvrtiny " + suffix];
  const competitionNames = ["Fotbalová liga " + suffix, "Basketbalová liga " + suffix];
  const seasonName = "Soutěžní sezóna " + suffix;
  await page.goto("/admin/sports");
  for (let i = 0; i < 2; i++) {
    await page.getByLabel("Kód sportu").fill("P4_" + i + "_" + suffix.toUpperCase());
    await page.getByLabel("Název sportu").fill(sports[i]);
    await page.getByRole("button", { name: "Přidat sport" }).click();
    await expect(page.getByRole("article", { name: sports[i], exact: true })).toBeVisible();
  }
  const sportRows = (await (await page.request.get(apiUrl + "/sports")).json()).items as { id: string; name: string }[];
  await page.goto("/admin/seasons");
  await page.getByLabel("Název sezóny").fill(seasonName);
  await page.getByLabel("Začátek sezóny").fill("2026-01-01");
  await page.getByLabel("Konec sezóny").fill("2027-01-01");
  await page.getByRole("button", { name: "Vytvořit sezónu" }).click();
  await expect(page.getByRole("article", { name: seasonName, exact: true })).toBeVisible();
  const seasonId = (await (await page.request.get(apiUrl + "/seasons")).json()).items[0].id as string;
  await page.goto("/admin/match-formats");
  for (let i = 0; i < 2; i++) {
    const form = page.getByRole("form", { name: "Nový formát zápasu" });
    await form.getByLabel("Sport formátu").selectOption({ label: sports[i] });
    await form.getByLabel("Název formátu").fill(formatNames[i]);
    await form.getByLabel("Označení části").fill(i === 0 ? "Poločas" : "Čtvrtina");
    await form.getByLabel("Počet částí základní doby").fill(i === 0 ? "2" : "4");
    await form.getByLabel("Povinná evidence částí").check();
    if (i === 0) await form.getByLabel("Povolit remízu").check(); else await form.getByLabel("Povolit prodloužení").check();
    await form.getByLabel("Přátelská kontumace: skóre vítěze").fill(i === 0 ? "3" : "20");
    await form.getByRole("button", { name: "Vytvořit formát" }).click();
    await expect(page.getByRole("article", { name: formatNames[i], exact: true })).toBeVisible();
  }
  const teamNames = ["Fotbal klub " + suffix, "Fotbal soupeř " + suffix, "Basketbal klub " + suffix, "Basketbal soupeř " + suffix];
  await page.goto("/admin/teams");
  for (let i = 0; i < 4; i++) {
    const form = page.getByRole("form", { name: "Nový tým", exact: true });
    await form.getByLabel("Název týmu", { exact: true }).fill(teamNames[i]);
    await form.getByLabel("Sport týmu").selectOption(sportRows.find((s) => s.name === sports[Math.floor(i / 2)])!.id);
    await form.getByLabel("Klubový tým").setChecked(i % 2 === 0);
    await form.getByRole("button", { name: "Vytvořit tým" }).click();
    await expect(page.getByRole("article", { name: teamNames[i], exact: true })).toBeVisible();
    const teams = (await (await page.request.get(apiUrl + "/teams")).json()).items as { id: string; name: string }[];
    const seasonForm = page.getByRole("form", { name: "Tým do sezóny", exact: true });
    await seasonForm.getByLabel("Tým k zařazení").selectOption(teams.find((t) => t.name === teamNames[i])!.id);
    await seasonForm.getByLabel("Sezóna pro tým").selectOption(seasonId);
    await seasonForm.getByLabel("Název týmu v sezóně").fill(teamNames[i] + " sezónní");
    await seasonForm.getByRole("button", { name: "Zařadit tým do sezóny" }).click();
    await expect(page.getByRole("article", { name: teamNames[i] + " sezónní", exact: true })).toBeVisible();
  }
  const ids: string[] = [];
  for (let i = 0; i < 2; i++) {
    await page.goto("/admin/competitions");
    const competitionForm = page.getByRole("form", { name: "Nová soutěž", exact: true });
    await competitionForm.getByLabel("Sport soutěže").selectOption({ label: sports[i] });
    await competitionForm.getByLabel("Název soutěže", { exact: true }).fill(competitionNames[i]);
    await competitionForm.getByRole("button", { name: "Vytvořit soutěž", exact: true }).click();
    await expect(page.getByRole("article", { name: competitionNames[i], exact: true })).toBeVisible();
    const form = page.getByRole("form", { name: "Nový soutěžní ročník" });
    await form.getByLabel("Soutěž ročníku").selectOption({ label: competitionNames[i] + " · " + sports[i] });
    await form.getByLabel("Sezóna ročníku").selectOption(seasonId);
    await form.getByLabel("Formát ročníku").selectOption({ label: formatNames[i] });
    const rules = form.getByRole("region", { name: /Bodovací pravidlo/ });
    await expect(rules).toHaveCount(i === 0 ? 3 : 4);
    for (const rule of await rules.all()) {
      const outcome = await rule.getByLabel("Výsledek", { exact: true }).inputValue();
      await rule.getByLabel("Přidělené body").fill(String(outcome === "WIN" ? i === 0 ? 3 : 2 : outcome === "DRAW" ? 1 : i === 0 ? 0 : 1));
    }
    for (const decision of ["Kontumace domácích", "Kontumace hostů"]) {
      const rule = form.getByRole("region", { name: decision, exact: true });
      const homeWins = decision === "Kontumace hostů";
      await rule.getByLabel("Skóre domácích").fill(String(homeWins ? i === 0 ? 3 : 20 : 0));
      await rule.getByLabel("Skóre hostů").fill(String(homeWins ? 0 : i === 0 ? 3 : 20));
      await rule.getByLabel("Body domácích").fill(String(homeWins ? i === 0 ? 3 : 2 : 0));
      await rule.getByLabel("Body hostů").fill(String(homeWins ? 0 : i === 0 ? 3 : 2));
    }
    await form.getByRole("button", { name: "Přidat kritérium", exact: true }).click();
    await form.getByRole("region", { name: "Kritérium 2", exact: true }).getByLabel("Metrika").selectOption("HEAD_TO_HEAD_POINTS");
    await form.getByRole("button", { name: "Přidat kritérium", exact: true }).click();
    await form.getByRole("region", { name: "Kritérium 3", exact: true }).getByLabel("Metrika").selectOption("SCORE_DIFFERENCE");
    await form.getByLabel("Počet vzájemných zápasů každé dvojice").fill("2");
    if (i === 1) await form.getByLabel("Neúplné vzájemné zápasy").selectOption("PLAYED_ONLY");
    const created = page.waitForResponse((r) => r.url().endsWith("/competition-seasons") && r.request().method() === "POST");
    await form.getByRole("button", { name: "Vytvořit soutěžní ročník" }).click(); expect((await created).status()).toBe(201);
    await expect(page).toHaveURL(/\/admin\/competitions\/[^/]+$/);
    ids.push(page.url().split("/").at(-1)!);
    await expect(page.getByRole("heading", { name: competitionNames[i] + " · " + seasonName, exact: true })).toBeVisible();
    for (let j = i * 2; j < i * 2 + 2; j++) {
      const entry = page.getByRole("form", { name: "Přihlásit tým", exact: true });
      await entry.getByLabel("Tým do soutěže").selectOption({ label: teamNames[j] + " sezónní" + (j % 2 ? " · externí soupeř" : "") });
      await entry.getByRole("button", { name: "Přihlásit tým", exact: true }).click();
      await expect(page.getByRole("article", { name: teamNames[j] + " sezónní", exact: true })).toBeVisible();
    }
    const current = await (await page.request.get(apiUrl + "/competition-seasons/" + ids[i])).json();
    expect(current.configuration.pointRules).toHaveLength(i === 0 ? 3 : 4);
    expect(current.configuration.forfeitRules.find((r: { decision: string }) => r.decision === "FORFEIT_HOME").awayPoints).toBe(i === 0 ? 3 : 2);
  }
  await page.goto("/admin/competitions/" + ids[0]);
  await page.getByRole("button", { name: "Upravit pravidla ročníku", exact: true }).click();
  const edit = page.getByRole("form", { name: "Upravit pravidla ročníku", exact: true });
  await edit.getByRole("button", { name: "Přidat bodovací pravidlo", exact: true }).click();
  const rejected = page.waitForResponse((r) => r.url().endsWith("/competition-seasons/" + ids[0]) && r.request().method() === "PUT");
  await edit.getByRole("button", { name: "Uložit pravidla ročníku", exact: true }).click(); expect((await rejected).status()).toBe(400);
  await expect(edit.getByRole("alert")).toBeVisible();
  await expect(edit.getByRole("region", { name: /Bodovací pravidlo/ })).toHaveCount(4);
  await edit.getByRole("region", { name: "Bodovací pravidlo 4", exact: true }).getByRole("button", { name: "Odebrat bodovací pravidlo" }).click();
  await edit.getByRole("button", { name: "Uložit pravidla ročníku", exact: true }).click();
  await expect(page.getByRole("button", { name: "Upravit pravidla ročníku", exact: true })).toBeVisible();
  const other = await context.newPage(); await other.goto("/admin/competitions/" + ids[0]);
  for (const tab of [page, other]) await tab.getByRole("button", { name: "Upravit pravidla ročníku", exact: true }).click();
  await page.getByLabel("Počet vzájemných zápasů každé dvojice").fill("3");
  await page.getByRole("button", { name: "Uložit pravidla ročníku", exact: true }).click();
  await expect(page.getByRole("button", { name: "Upravit pravidla ročníku", exact: true })).toBeVisible();
  await other.getByLabel("Počet vzájemných zápasů každé dvojice").fill("4");
  await other.getByRole("button", { name: "Uložit pravidla ročníku", exact: true }).click();
  await expect(other.getByRole("form", { name: "Upravit pravidla ročníku" }).getByRole("alert")).toBeVisible();
  await expect(other.getByLabel("Počet vzájemných zápasů každé dvojice")).toHaveValue("4");
  await other.getByRole("button", { name: "Zahodit úpravy a načíst aktuální ročník" }).click();
  await expect(other.getByRole("button", { name: "Upravit pravidla ročníku", exact: true })).toBeVisible(); await other.close();
  const csrf = await (await page.request.get(apiUrl + "/auth/csrf")).json() as { token: string; headerName: string };
  const headers = { [csrf.headerName]: csrf.token };
  const teams = (await (await page.request.get(apiUrl + "/team-seasons")).json()).items as { id: string; sportId: string }[];
  const current = await (await page.request.get(apiUrl + "/competition-seasons/" + ids[0])).json();
  const wrongSport = teams.find((t) => t.sportId !== current.season.sportId)!;
  expect((await page.request.post(apiUrl + "/competition-seasons/" + ids[0] + "/entries", { headers, data: { version: current.season.version, teamSeasonId: wrongSport.id } })).status()).toBe(400);
  for (let i = 0; i < 2; i++) {
    await page.goto("/admin/competitions/" + ids[i]); await page.getByText("Zobrazit pravidla ročníku", { exact: true }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `../.verification/p4-competition-${i}-${testInfo.project.name}.png`, fullPage: true });
  }
  expect((await page.request.post(apiUrl + "/seasons/" + seasonId + "/archive", { headers, data: {} })).status()).toBe(200);
  await page.reload(); await expect(page.getByText(/Sezóna je archivovaná/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Upravit pravidla ročníku", exact: true })).toHaveCount(0);
  await expect(page.getByRole("article")).toHaveCount(2);
});
