"use client";

import { useCallback, useRef, useState, type FormEvent } from "react";
import { getSports } from "@/api/club";
import { allPages } from "@/api/teams";
import { createMatchFormat, getMatchFormats, updateMatchFormat, type MatchFormatConfiguration, type MatchFormatResponse } from "@/api/matchFormats";
import { ApiError } from "@/api/api";
import { mapFieldErrors } from "@/api/errorMapper";
import type { SportResponse } from "@/api/types";
import { AccessGate } from "../AccessGate";
import { FormField } from "../FormField";
import { AdminNav } from "./AdminNav";
import { FormError, SelectField } from "./Fields";
import { EmptyState, Pagination, ResourceView, useResource } from "./Resource";

const defaultConfiguration: MatchFormatConfiguration = {
  scoreMode: "SUM_SCORE", allowDraw: false, periodsRequired: false, periodLabel: "Část",
  regulationPeriodCount: null, winsRequired: null, allowOvertime: false, allowShootout: false,
  shootoutWinnerBonus: 0, friendlyForfeitLoserScore: 0, friendlyForfeitWinnerScore: 1,
};
const loadSports = () => allPages(getSports);
const configurationFields = Object.keys(defaultConfiguration).map((key) => `configuration.${key}`);

function FormatForm({ sports, initial, onSaved, onCancel, onReload }: {
  sports: SportResponse[]; initial?: MatchFormatResponse; onSaved: () => void; onCancel?: () => void; onReload?: () => void;
}) {
  const [pending, setPending] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const c = initial?.configuration ?? defaultConfiguration;
  const [mode, setMode] = useState(c.scoreMode);
  const locked = initial?.lockedAt != null;
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    // React state disables controls after rendering; guard this event synchronously too.
    if (submitting.current) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const optionalInteger = (name: string) => String(data.get(name) ?? "") === "" ? null : Number(data.get(name));
    const configuration: MatchFormatConfiguration = locked ? c : {
      scoreMode: mode, allowDraw: data.has("allowDraw"), periodsRequired: data.has("periodsRequired"),
      periodLabel: String(data.get("periodLabel") ?? "").trim(), regulationPeriodCount: optionalInteger("regulationPeriodCount"),
      winsRequired: optionalInteger("winsRequired"), allowOvertime: data.has("allowOvertime"),
      allowShootout: data.has("allowShootout"), shootoutWinnerBonus: Number(data.get("shootoutWinnerBonus")),
      friendlyForfeitLoserScore: Number(data.get("friendlyForfeitLoserScore")), friendlyForfeitWinnerScore: Number(data.get("friendlyForfeitWinnerScore")),
    };
    submitting.current = true;
    setPending(true); setError(null); setErrors({});
    try {
      const name = String(data.get("name") ?? "").trim();
      if (initial) await updateMatchFormat(initial.id, { version: initial.version, name, configuration });
      else {
        await createMatchFormat({ sportId: String(data.get("sportId") ?? ""), name, configuration });
        form.reset(); setMode(defaultConfiguration.scoreMode);
      }
      onSaved();
    } catch (caught: unknown) { setError(caught); setErrors(mapFieldErrors(caught, ["name", "sportId", ...configurationFields])); }
    finally { submitting.current = false; setPending(false); }
  };
  const numberField = (name: "regulationPeriodCount" | "winsRequired" | "shootoutWinnerBonus" | "friendlyForfeitLoserScore" | "friendlyForfeitWinnerScore", label: string, optional = false) =>
    <FormField name={name} label={label} type="number" min={name === "regulationPeriodCount" || name === "winsRequired" || name === "friendlyForfeitWinnerScore" ? 1 : 0} max={2147483647} step={1} required={!optional} defaultValue={c[name] ?? ""} error={errors[`configuration.${name}`]} />;
  return <form className="club-form" aria-label={initial ? "Upravit formát zápasu" : "Nový formát zápasu"} onSubmit={submit} aria-busy={pending}>
    <h2>{initial ? "Upravit formát zápasu" : "Nový formát zápasu"}</h2>
    {initial ? <p>Sport: {initial.sportName}</p> : <SelectField name="sportId" label="Sport formátu" required disabled={pending} defaultValue="" error={errors.sportId}><option value="" disabled>Vyberte sport</option>{sports.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</SelectField>}
    <FormField name="name" label="Název formátu" required maxLength={120} defaultValue={initial?.name} disabled={pending} error={errors.name} />
    {locked && <p className="field-hint">Formát již byl použitý. Lze změnit pouze název; pro jiná pravidla vytvořte nový formát.</p>}
    <fieldset disabled={pending || locked} className="club-stack"><legend>Pravidla výsledku</legend>
      <SelectField name="scoreMode" label="Vyhodnocení výsledku" value={mode} error={errors["configuration.scoreMode"]} onChange={(event) => setMode(event.target.value as MatchFormatConfiguration["scoreMode"])}><option value="SUM_SCORE">Součet skóre</option><option value="WON_PARTS">Počet vyhraných částí</option></SelectField>
      <p className="field-hint">Počet vyhraných částí vyžaduje povinné části a počet vítězství. Nepovoluje remízy, prodloužení, nájezdy ani pevný počet částí.</p>
      <FormField name="periodLabel" label="Označení části" required maxLength={100} defaultValue={c.periodLabel} error={errors["configuration.periodLabel"]} />
      <div className="form-grid">{numberField("regulationPeriodCount", "Počet částí základní doby", true)}{numberField("winsRequired", "Počet částí potřebných k vítězství", true)}</div>
      <p className="field-hint">Prázdný počet částí znamená neomezený počet. Počet vítězných částí vyplňte pouze pro vyhodnocení podle vyhraných částí.</p>
      <label><input type="checkbox" name="periodsRequired" defaultChecked={c.periodsRequired} /> Povinná evidence částí</label>
      <label><input type="checkbox" name="allowDraw" defaultChecked={c.allowDraw} /> Povolit remízu</label>
      <label><input type="checkbox" name="allowOvertime" defaultChecked={c.allowOvertime} /> Povolit prodloužení</label>
      <label><input type="checkbox" name="allowShootout" defaultChecked={c.allowShootout} /> Povolit nájezdy</label>
      {numberField("shootoutWinnerBonus", "Bonus vítěze nájezdů")}
      <p className="field-hint">Bez povolených nájezdů musí být bonus nulový.</p>
      <div className="form-grid">{numberField("friendlyForfeitWinnerScore", "Přátelská kontumace: skóre vítěze")}{numberField("friendlyForfeitLoserScore", "Přátelská kontumace: skóre poraženého")}</div>
      <p className="field-hint">Skóre vítěze musí být vyšší. Soutěžní body a kontumace se budou nastavovat zvlášť v ročníku soutěže.</p>
    </fieldset>
    <FormError error={error} fieldErrors={errors} />
    <div className="actions"><button className="button" disabled={pending || (!initial && !sports.some((s) => s.active))}>{pending ? "Ukládám…" : initial ? "Uložit formát" : "Vytvořit formát"}</button>{onCancel && <button type="button" className="text-button" onClick={onCancel} disabled={pending}>Zrušit úpravy</button>}</div>
    {onReload && error instanceof ApiError && (error.code === "OPTIMISTIC_LOCK_CONFLICT" || error.code === "CONFIGURATION_LOCKED") && <button type="button" className="text-button" onClick={onReload}>Zahodit úpravy a načíst aktuální stav</button>}
  </form>;
}

function FormatCard({ format, sports, saved, reload }: { format: MatchFormatResponse; sports: SportResponse[]; saved: () => void; reload: () => void }) {
  const [editing, setEditing] = useState(false);
  return <article className="detail-card" aria-label={format.name}>
    <div className="list-heading"><h3>{format.name}</h3><span className="pill">{format.lockedAt ? "Použitý formát" : "Upravitelná pravidla"}</span></div>
    <p>{format.sportName} · {format.configuration.scoreMode === "SUM_SCORE" ? "Součet skóre" : "Vyhrané části"} · {format.configuration.allowDraw ? "Remíza povolena" : "Bez remízy"}</p>
    <p>Část: {format.configuration.periodLabel} · Počet: {format.configuration.regulationPeriodCount ?? "neomezený"}</p>
    {editing ? <FormatForm initial={format} sports={sports} onSaved={() => { setEditing(false); saved(); }} onCancel={() => setEditing(false)} onReload={reload} /> : <button className="text-button" onClick={() => setEditing(true)}>Upravit formát</button>}
  </article>;
}

function FormatsContent() {
  const [page, setPage] = useState(0);
  const [sport, setSport] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const load = useCallback(() => getMatchFormats(page, 20, sport || undefined), [page, sport]);
  const formats = useResource(load);
  const sports = useResource(loadSports);
  const saved = () => { setNotice("Formát zápasu byl uložen."); formats.reload(); };
  return <section className="club-page"><AdminNav /><p className="eyebrow">Administrace klubu</p><h1>Formáty zápasu</h1><p className="lead">Nastavte vyhodnocení výsledku a části pro libovolný sport. Formát neurčuje soutěžní body ani pořadí.</p>
    {notice && <p className="message message-success" role="status">{notice}</p>}
    <ResourceView resource={sports.resource} reload={sports.reload}>{(sportItems) => <>
      {!sportItems.some((s) => s.active) && <p className="message">Před vytvořením formátu založte nebo aktivujte sport ve správě sportů.</p>}
      <SelectField name="sportFilter" label="Filtr sportu" value={sport} onChange={(event) => { setSport(event.target.value); setPage(0); }}><option value="">Všechny sporty</option>{sportItems.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</SelectField>
      <div className="club-two-columns"><div className="detail-card"><FormatForm sports={sportItems} onSaved={() => { setPage(0); saved(); }} /></div>
        <ResourceView resource={formats.resource} reload={formats.reload}>{(result) => <div className="club-stack">{result.items.length ? result.items.map((format) => <FormatCard key={`${format.id}:${format.version}:${format.lockedAt ?? ""}`} format={format} sports={sportItems} saved={saved} reload={formats.reload} />) : <EmptyState>Zatím nejsou vytvořené žádné formáty pro tento výběr.</EmptyState>}<Pagination page={page} result={result} changePage={setPage} /></div>}</ResourceView>
      </div>
    </>}</ResourceView>
  </section>;
}

export function MatchFormatsManager() { return <AccessGate completeProfile capability="MANAGE_CLUB"><FormatsContent /></AccessGate>; }
