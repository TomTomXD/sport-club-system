import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";

const apiUrl = process.env.E2E_API_URL ?? "http://localhost:8080/api";

// This setup scenario needs its own fresh DB, separate from the other setup specs.
test("správce nastaví formáty dvou sportů a vyřeší souběžnou úpravu", async ({ page, context }, testInfo) => {
  test.setTimeout(120_000);
  const token = process.env.E2E_SETUP_TOKEN;
  test.skip(!token, "Vyžaduje soukromý E2E_SETUP_TOKEN a novou izolovanou databázi.");
  test.skip(testInfo.project.name !== (process.env.E2E_SETUP_PROJECT ?? "desktop"), "Jeden setup projekt na jednu čistou databázi.");
  const suffix = randomUUID().slice(0, 8);
  const username = "formats-e2e-" + suffix;
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
  await page.goto("/admin/sports");
  for (let i = 0; i < sports.length; i++) {
    await page.getByLabel("Kód sportu").fill("FORMAT_" + i + "_" + suffix.toUpperCase());
    await page.getByLabel("Název sportu").fill(sports[i]);
    await page.getByRole("button", { name: "Přidat sport" }).click();
    await expect(page.getByRole("article", { name: sports[i], exact: true })).toBeVisible();
  }

  await page.goto("/admin/match-formats");
  const names = ["Fotbalový formát " + suffix, "Basketbalový formát " + suffix];
  for (let i = 0; i < sports.length; i++) {
    const form = page.getByRole("form", { name: "Nový formát zápasu" });
    await form.getByLabel("Sport formátu").selectOption({ label: sports[i] });
    await form.getByLabel("Název formátu").fill(names[i]);
    await form.getByLabel("Označení části").fill(i === 0 ? "Poločas" : "Čtvrtina");
    await form.getByLabel("Počet částí základní doby").fill(i === 0 ? "2" : "4");
    await form.getByLabel("Povinná evidence částí").check();
    if (i === 0) await form.getByLabel("Povolit remízu").check();
    else await form.getByLabel("Povolit prodloužení").check();
    await form.getByLabel("Přátelská kontumace: skóre vítěze").fill(i === 0 ? "3" : "20");
    await form.getByRole("button", { name: "Vytvořit formát" }).click();
    await expect(page.getByRole("article", { name: names[i], exact: true })).toBeVisible();
  }

  const formats = await (await page.request.get(apiUrl + "/match-formats")).json();
  expect(formats.totalElements).toBe("2");
  expect(formats.items.find((f: { name: string }) => f.name === names[1]).configuration.allowOvertime).toBe(true);
  await page.getByLabel("Filtr sportu").selectOption({ label: sports[0] });
  await expect(page.getByRole("article", { name: names[1], exact: true })).toHaveCount(0);
  await expect(page.getByText(/Celkem 1/)).toBeVisible();
  await page.getByLabel("Filtr sportu").selectOption("");
  await expect(page.getByRole("article", { name: names[1], exact: true })).toBeVisible();

  const second = await context.newPage();
  await second.goto("/admin/match-formats");
  const firstCard = page.getByRole("article", { name: names[0], exact: true });
  const staleCard = second.getByRole("article", { name: names[0], exact: true });
  await firstCard.getByRole("button", { name: "Upravit formát" }).click();
  await staleCard.getByRole("button", { name: "Upravit formát" }).click();
  const renamed = "Aktuální formát " + suffix;
  await firstCard.getByLabel("Název formátu").fill(renamed);
  await firstCard.getByRole("button", { name: "Uložit formát" }).click();
  await expect(page.getByRole("article", { name: renamed, exact: true })).toBeVisible();
  await staleCard.getByLabel("Název formátu").fill("Rozepsaná úprava");
  await staleCard.getByRole("button", { name: "Uložit formát" }).click();
  await expect(staleCard.getByRole("alert")).toBeVisible();
  await expect(staleCard.getByLabel("Název formátu")).toHaveValue("Rozepsaná úprava");
  await staleCard.getByRole("button", { name: "Zahodit úpravy a načíst aktuální stav" }).click();
  await expect(second.getByRole("article", { name: renamed, exact: true })).toBeVisible();
  await second.close();

  await page.getByRole("article", { name: renamed, exact: true }).getByRole("button", { name: "Upravit formát" }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `../.verification/p4-${testInfo.project.name}.png`, fullPage: true });
});
