"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useRef, useState, type FormEvent } from "react";
import { getSports } from "@/api/club";
import { allPages, getSeasons, type SeasonResponse } from "@/api/teams";
import { getMatchFormats, type MatchFormatResponse } from "@/api/matchFormats";
import { createCompetition, createCompetitionSeason, getCompetitions, getCompetitionSeasons, renameCompetition, type Competition } from "@/api/competitions";
import { mapFieldErrors } from "@/api/errorMapper";
import { ApiError } from "@/api/api";
import type { SportResponse } from "@/api/types";
import { AccessGate } from "../AccessGate";
import { FormField } from "../FormField";
import { AdminNav } from "./AdminNav";
import { FormError, SelectField } from "./Fields";
import { EmptyState, Pagination, ResourceView, useResource } from "./Resource";
import { CompetitionConfigurationFields, configurationFromForm } from "./CompetitionConfigurationFields";

export function competitionFieldErrors(error: unknown): Record<string, string> {
  const result = mapFieldErrors(error, ["name", "sportId", "competitionId", "seasonId", "matchFormatId", "teamSeasonId", "configuration.headToHeadMissingPolicy", "configuration.headToHeadMeetingsPerPair", "configuration.restartHeadToHeadOnSubgroup"]);
  if (error instanceof ApiError) for (const group of ["pointRules", "forfeitRules", "rankingCriteria"]) {
    if (error.body?.fieldErrors?.some((field) => field.field === `configuration.${group}` || field.field.startsWith(`configuration.${group}[`))) {
      result[`configuration.${group}`] = "Zkontrolujte tuto skupinu pravidel; hodnoty musí být úplné a vzájemně slučitelné.";
    }
  }
  return result;
}
const loadOptions = async () => {
  const [sports, seasons, competitions, formats] = await Promise.all([allPages(getSports), allPages(getSeasons), allPages(getCompetitions), allPages(getMatchFormats)]);
  return { sports, seasons, competitions, formats };
};
type Options = { sports: SportResponse[]; seasons: SeasonResponse[]; competitions: Competition[]; formats: MatchFormatResponse[] };

function CompetitionForm({ sports, initial, saved }: { sports: SportResponse[]; initial?: Competition; saved: () => void }) {
  const [error, setError] = useState<unknown>(null), [pending, setPending] = useState(false);
  const submitting = useRef(false);
  const errors = competitionFieldErrors(error);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (submitting.current) return;
    const form = event.currentTarget, data = new FormData(form);
    submitting.current = true; setPending(true); setError(null);
    try {
      const name = String(data.get("name") ?? "").trim();
      if (initial) await renameCompetition(initial.id, name);
      else { await createCompetition({ name, sportId: String(data.get("sportId")) }); form.reset(); }
      saved();
    } catch (caught) { setError(caught); } finally { submitting.current = false; setPending(false); }
  };
  return <form className="club-form" aria-label={initial ? `Přejmenovat soutěž ${initial.name}` : "Nová soutěž"} onSubmit={submit} aria-busy={pending}>
    {!initial && <h2>Nová soutěž</h2>}
    {!initial && <SelectField name="sportId" label="Sport soutěže" required defaultValue="" disabled={pending} error={errors.sportId}><option value="" disabled>Vyberte sport</option>{sports.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</SelectField>}
    <FormField name="name" label={initial ? "Nový název soutěže" : "Název soutěže"} maxLength={160} defaultValue={initial?.name} required disabled={pending} error={errors.name} />
    <FormError error={error} fieldErrors={errors} /><button className="button" disabled={pending || (!initial && !sports.some((s) => s.active))}>{pending ? "Ukládám…" : initial ? "Přejmenovat soutěž" : "Vytvořit soutěž"}</button>
  </form>;
}
function NewSeasonForm({ options }: { options: Options }) {
  const router = useRouter();
  const [competitionId, setCompetitionId] = useState(""), [formatId, setFormatId] = useState("");
  const [error, setError] = useState<unknown>(null), [pending, setPending] = useState(false);
  const submitting = useRef(false), errors = competitionFieldErrors(error);
  const competition = options.competitions.find((c) => c.id === competitionId);
  const formats = options.formats.filter((f) => f.sportId === competition?.sportId);
  const format = formats.find((f) => f.id === formatId);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (submitting.current || !format) return;
    const data = new FormData(event.currentTarget);
    submitting.current = true; setPending(true); setError(null);
    try {
      const result = await createCompetitionSeason({ competitionId, seasonId: String(data.get("seasonId")), matchFormatId: formatId, configuration: configurationFromForm(data) });
      router.push(`/admin/competitions/${encodeURIComponent(result.season.id)}`);
    } catch (caught) { setError(caught); } finally { submitting.current = false; setPending(false); }
  };
  return <form className="club-form" aria-label="Nový soutěžní ročník" onSubmit={submit} aria-busy={pending}><h2>Nový soutěžní ročník</h2>
    <p className="field-hint">Nejprve založte soutěž, otevřenou sezónu a formát jejího sportu. Ročník se vytvoří až s úplnými platnými pravidly.</p>
    <SelectField name="competitionId" label="Soutěž ročníku" required value={competitionId} disabled={pending} error={errors.competitionId} onChange={(event) => { setCompetitionId(event.target.value); setFormatId(""); }}><option value="" disabled>Vyberte soutěž</option>{options.competitions.map((c) => <option key={c.id} value={c.id}>{c.name} · {c.sportName}</option>)}</SelectField>
    <SelectField name="seasonId" label="Sezóna ročníku" required defaultValue="" disabled={pending} error={errors.seasonId}><option value="" disabled>Vyberte sezónu</option>{options.seasons.filter((s) => !s.archived).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</SelectField>
    <SelectField name="matchFormatId" label="Formát ročníku" required value={formatId} disabled={pending || !competition} error={errors.matchFormatId} onChange={(event) => setFormatId(event.target.value)}><option value="" disabled>Vyberte formát</option>{formats.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}</SelectField>
    {competition && formats.length === 0 && <p className="message">Pro tento sport nejprve vytvořte formát zápasu.</p>}
    {format && <CompetitionConfigurationFields key={format.id} format={format.configuration} disabled={pending} errors={errors} />}
    <FormError error={error} fieldErrors={errors} /><button className="button" disabled={pending || !format}>{pending ? "Ukládám…" : "Vytvořit soutěžní ročník"}</button>
  </form>;
}
function Content() {
  const [competitionPage, setCompetitionPage] = useState(0), [seasonPage, setSeasonPage] = useState(0), [sport, setSport] = useState("");
  const options = useResource(loadOptions);
  const loadCompetitions = useCallback(() => getCompetitions(competitionPage, 20, sport || undefined), [competitionPage, sport]);
  const loadSeasons = useCallback(() => getCompetitionSeasons(seasonPage, 20, { sportId: sport || undefined }), [seasonPage, sport]);
  const competitions = useResource(loadCompetitions), seasons = useResource(loadSeasons);
  const saved = () => { competitions.reload(); seasons.reload(); options.reload(); };
  return <section className="club-page"><AdminNav /><p className="eyebrow">Administrace klubu</p><h1>Soutěže a ročníky</h1><p className="lead">Soutěž patří jednomu sportu. Každý její ročník má vlastní úplné bodování, kontumace, pořadí a přihlášené týmy.</p>
    <ResourceView resource={options.resource} reload={options.reload}>{(data) => <>
      <SelectField name="sportFilter" label="Filtr sportu soutěží" value={sport} onChange={(event) => { setSport(event.target.value); setCompetitionPage(0); setSeasonPage(0); }}><option value="">Všechny sporty</option>{data.sports.map((s) => <option value={s.id} key={s.id}>{s.name}</option>)}</SelectField>
      <div className="club-two-columns"><section className="detail-card"><CompetitionForm sports={data.sports} saved={saved} /></section><section className="detail-card"><NewSeasonForm options={data} /></section></div>
      <h2>Soutěžní ročníky</h2><ResourceView resource={seasons.resource} reload={seasons.reload}>{(result) => <div className="club-stack">{result.items.length ? result.items.map((s) => <article className="detail-card" key={s.id} aria-label={`${s.competitionName} ${s.seasonName}`}><h3>{s.competitionName} · {s.seasonName}</h3><p>{s.sportName} · {s.matchFormatName} · {s.archived ? "Archivováno" : s.lockedAt ? "Použitá pravidla" : "Otevřený ročník"}</p><Link className="button button-secondary" href={`/admin/competitions/${encodeURIComponent(s.id)}`}>Pravidla a přihlášky</Link></article>) : <EmptyState>Zatím nejsou založené soutěžní ročníky.</EmptyState>}<Pagination page={seasonPage} result={result} changePage={setSeasonPage} /></div>}</ResourceView>
      <h2>Soutěže</h2><ResourceView resource={competitions.resource} reload={competitions.reload}>{(result) => <div className="club-cards">{result.items.map((c) => <article key={c.id} className="detail-card" aria-label={c.name}><h3>{c.name}</h3><p>{c.sportName}</p><CompetitionForm sports={data.sports} initial={c} saved={saved} /></article>)}<Pagination page={competitionPage} result={result} changePage={setCompetitionPage} /></div>}</ResourceView>
    </>}</ResourceView>
  </section>;
}
export function CompetitionsManager() { return <AccessGate completeProfile capability="MANAGE_CLUB"><Content /></AccessGate>; }
