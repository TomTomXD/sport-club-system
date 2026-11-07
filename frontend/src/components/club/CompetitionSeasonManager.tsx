"use client";

import { useCallback, useRef, useState, type FormEvent } from "react";
import { ApiError } from "@/api/api";
import { addCompetitionEntry, getCompetitionEntries, getCompetitionSeason, removeCompetitionEntry, updateCompetitionSeason, type CompetitionSeasonDetail } from "@/api/competitions";
import { allPages, getTeamSeasons } from "@/api/teams";
import { AccessGate } from "../AccessGate";
import { AdminNav } from "./AdminNav";
import { competitionFieldErrors } from "./CompetitionsManager";
import { CompetitionConfigurationFields, configurationFromForm } from "./CompetitionConfigurationFields";
import { FormError, SelectField } from "./Fields";
import { EmptyState, Pagination, ResourceView, useResource } from "./Resource";

function ReloadConflict({ error, reload }: { error: unknown; reload: () => void }) {
  return error instanceof ApiError && ["OPTIMISTIC_LOCK_CONFLICT", "CONFIGURATION_LOCKED"].includes(error.code)
    ? <button type="button" className="text-button" onClick={reload}>Zahodit úpravy a načíst aktuální ročník</button> : null;
}
function Detail({ detail, reload }: { detail: CompetitionSeasonDetail; reload: () => void }) {
  const { season, configuration } = detail;
  const [editing, setEditing] = useState(false), [pending, setPending] = useState(false), [error, setError] = useState<unknown>(null);
  const [page, setPage] = useState(0); const submitting = useRef(false);
  const loadEntries = useCallback(() => getCompetitionEntries(season.id, page, 20), [season.id, page]);
  const loadCandidates = useCallback(() => allPages((p, size) => getTeamSeasons(p, size, { seasonId: season.seasonId, sportId: season.sportId })), [season.seasonId, season.sportId]);
  const entries = useResource(loadEntries), candidates = useResource(loadCandidates);
  const locked = season.archived || season.lockedAt != null, errors = competitionFieldErrors(error);
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (submitting.current || locked) return;
    const data = new FormData(event.currentTarget);
    submitting.current = true; setPending(true); setError(null);
    try { await updateCompetitionSeason(season.id, { version: season.version, configuration: configurationFromForm(data) }); reload(); }
    catch (caught) { setError(caught); } finally { submitting.current = false; setPending(false); }
  };
  const entryMutation = async (team: string, remove: boolean) => {
    if (submitting.current || editing || locked) return;
    submitting.current = true; setPending(true); setError(null);
    try {
      if (remove) await removeCompetitionEntry(season.id, team, season.version);
      else await addCompetitionEntry(season.id, { version: season.version, teamSeasonId: team });
      reload();
    } catch (caught) { setError(caught); } finally { submitting.current = false; setPending(false); }
  };
  return <><h1>{season.competitionName} · {season.seasonName}</h1><p className="lead">{season.sportName} · {season.matchFormatName}</p>
    {locked && <p className="message">{season.archived ? "Sezóna je archivovaná." : "Ročník má historicky použitá pravidla."} Pravidla a přihlášky jsou pouze ke čtení.</p>}
    <section className="detail-card"><h2>Pravidla ročníku</h2>
      {editing ? <form className="club-form" aria-label="Upravit pravidla ročníku" onSubmit={save} aria-busy={pending}>
        <CompetitionConfigurationFields initial={configuration} disabled={pending || locked} errors={errors} />
        <FormError error={error} fieldErrors={errors} /><div className="actions"><button className="button" disabled={pending || locked}>{pending ? "Ukládám…" : "Uložit pravidla ročníku"}</button><button type="button" className="text-button" disabled={pending} onClick={() => { setEditing(false); setError(null); }}>Zrušit úpravy</button></div><ReloadConflict error={error} reload={reload} />
      </form> : <><details><summary>Zobrazit pravidla ročníku</summary><CompetitionConfigurationFields initial={configuration} disabled /></details>{!locked && <button className="button button-secondary" disabled={pending} onClick={() => { setEditing(true); setError(null); }}>Upravit pravidla ročníku</button>}</>}
    </section>
    <section className="detail-card club-stack" aria-label="Přihlášky týmů"><h2>Přihlášky týmů</h2>
      {editing && <p className="field-hint">Nejprve uložte nebo zrušte úpravu pravidel.</p>}
      {!locked && <ResourceView resource={candidates.resource} reload={candidates.reload}>{(teams) => <form className="club-form" aria-label="Přihlásit tým" onSubmit={(event) => { event.preventDefault(); void entryMutation(String(new FormData(event.currentTarget).get("teamSeasonId")), false); }}>
        <SelectField name="teamSeasonId" label="Tým do soutěže" required defaultValue="" disabled={pending || editing} error={errors.teamSeasonId}><option value="" disabled>Vyberte sezónní tým</option>{teams.map((t) => <option key={t.id} value={t.id}>{t.displayName}{t.isClubTeam ? "" : " · externí soupeř"}</option>)}</SelectField>
        {!teams.length && <p className="message">Nejprve zařaďte tým tohoto sportu do stejné sezóny ve správě týmů.</p>}
        <button className="button" disabled={pending || editing || !teams.length}>Přihlásit tým</button></form>}</ResourceView>}
      {!editing && <><FormError error={error} fieldErrors={errors} /><ReloadConflict error={error} reload={reload} /></>}
      <ResourceView resource={entries.resource} reload={entries.reload}>{(result) => <>{result.items.length ? result.items.map((e) => <article className="detail-card" key={e.teamSeasonId} aria-label={e.displayName}><h3>{e.displayName}</h3><p>{e.isClubTeam ? "Klubový tým" : "Externí soupeř"}</p>{!locked && <button className="text-button" disabled={pending || editing} onClick={() => void entryMutation(e.teamSeasonId, true)}>Odebrat přihlášku</button>}</article>) : <EmptyState>Zatím nejsou přihlášené týmy.</EmptyState>}<Pagination page={page} result={result} changePage={setPage} /></>}</ResourceView>
    </section></>;
}
function Content({ id }: { id: string }) {
  const load = useCallback(() => getCompetitionSeason(id), [id]); const detail = useResource(load);
  return <section className="club-page"><AdminNav /><p className="eyebrow">Soutěžní ročník</p><ResourceView resource={detail.resource} reload={detail.reload}>{(data) => <Detail key={`${data.season.id}:${data.season.version}`} detail={data} reload={detail.reload} />}</ResourceView></section>;
}
export function CompetitionSeasonManager({ id }: { id: string }) { return <AccessGate completeProfile capability="MANAGE_CLUB"><Content id={id} /></AccessGate>; }
