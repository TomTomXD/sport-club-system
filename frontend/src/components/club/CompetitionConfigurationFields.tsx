"use client";

import { useRef, useState } from "react";
import type { CompetitionConfiguration, Decision, Ending, Metric, Outcome, PointRule } from "@/api/competitions";
import type { MatchFormatConfiguration } from "@/api/matchFormats";
import { FormField } from "../FormField";
import { SelectField } from "./Fields";

const outcomes: Record<Outcome, string> = { WIN: "Výhra", DRAW: "Remíza", LOSS: "Prohra" };
const endings: Record<Ending, string> = { REGULATION: "Základní doba", OVERTIME: "Prodloužení", SHOOTOUT: "Nájezdy" };
const metrics: Record<Metric, string> = {
  POINTS: "Body", WINS: "Počet výher", SCORE_DIFFERENCE: "Rozdíl skóre", SCORE_FOR: "Dosažené skóre", SCORE_RATIO: "Poměr skóre",
  PART_SCORE_DIFFERENCE: "Rozdíl skóre částí", PART_SCORE_RATIO: "Poměr skóre částí", HEAD_TO_HEAD_POINTS: "Vzájemné body",
  HEAD_TO_HEAD_SCORE_DIFFERENCE: "Vzájemný rozdíl skóre", HEAD_TO_HEAD_SCORE_FOR: "Vzájemné dosažené skóre",
};
const blankPoint = (): PointRule => ({ outcome: "WIN", ending: "REGULATION", points: 0, ownScoreMin: null, ownScoreMax: null, opponentScoreMin: null, opponentScoreMax: null });

/** Empty editable inputs, derived from the selected format; never a saved sport preset. */
function startingConfiguration(format?: MatchFormatConfiguration): CompetitionConfiguration {
  const pointRules: PointRule[] = [];
  for (const ending of Object.keys(endings) as Ending[]) {
    if ((ending === "OVERTIME" && !format?.allowOvertime) || (ending === "SHOOTOUT" && !format?.allowShootout)) continue;
    for (const outcome of Object.keys(outcomes) as Outcome[]) {
      if (outcome === "DRAW" && (!format?.allowDraw || ending === "SHOOTOUT")) continue;
      pointRules.push({ ...blankPoint(), outcome, ending });
    }
  }
  return {
    headToHeadMissingPolicy: "REQUIRE_COMPLETE", headToHeadMeetingsPerPair: 1, restartHeadToHeadOnSubgroup: true, pointRules,
    forfeitRules: [
      { decision: "FORFEIT_HOME", homeScore: 0, awayScore: 1, homePoints: 0, awayPoints: 0 },
      { decision: "FORFEIT_AWAY", homeScore: 1, awayScore: 0, homePoints: 0, awayPoints: 0 },
    ], rankingCriteria: [{ priority: 1, metric: "POINTS", direction: "DESC" }],
  };
}
const number = (data: FormData, name: string) => Number(data.get(name));
const optional = (data: FormData, name: string) => data.get(name) === "" ? null : number(data, name);
export function configurationFromForm(data: FormData): CompetitionConfiguration {
  return {
    headToHeadMissingPolicy: String(data.get("headToHeadMissingPolicy")) as CompetitionConfiguration["headToHeadMissingPolicy"],
    headToHeadMeetingsPerPair: number(data, "headToHeadMeetingsPerPair"), restartHeadToHeadOnSubgroup: data.has("restartHeadToHeadOnSubgroup"),
    pointRules: data.getAll("pointKey").map((key) => {
      const p = `point.${key}.`;
      return { outcome: String(data.get(p + "outcome")) as Outcome, ending: String(data.get(p + "ending")) as Ending,
        points: number(data, p + "points"), ownScoreMin: optional(data, p + "ownScoreMin"), ownScoreMax: optional(data, p + "ownScoreMax"),
        opponentScoreMin: optional(data, p + "opponentScoreMin"), opponentScoreMax: optional(data, p + "opponentScoreMax") };
    }),
    forfeitRules: (["FORFEIT_HOME", "FORFEIT_AWAY"] as Decision[]).map((decision) => ({ decision,
      homeScore: number(data, `${decision}.homeScore`), awayScore: number(data, `${decision}.awayScore`),
      homePoints: number(data, `${decision}.homePoints`), awayPoints: number(data, `${decision}.awayPoints`) })),
    rankingCriteria: data.getAll("criterionKey").map((key, index) => ({ priority: index + 1,
      metric: String(data.get(`criterion.${key}.metric`)) as Metric, direction: String(data.get(`criterion.${key}.direction`)) as "ASC" | "DESC" })),
  };
}

function NumberField({ name, label, value, optional = false, min = 0 }: { name: string; label: string; value?: number | null; optional?: boolean; min?: number }) {
  return <FormField name={name} label={label} type="number" min={min} max={2147483647} step={1} required={!optional} defaultValue={value ?? ""} />;
}
export function CompetitionConfigurationFields({ initial, format, disabled, errors = {} }: {
  initial?: CompetitionConfiguration; format?: MatchFormatConfiguration; disabled: boolean; errors?: Record<string, string>;
}) {
  const [configuration] = useState(() => initial ?? startingConfiguration(format));
  const [points, setPoints] = useState(() => configuration.pointRules.map((rule, key) => ({ rule, key })));
  const [ranking, setRanking] = useState(() => configuration.rankingCriteria.map((rule, key) => ({ rule, key })));
  const nextPoint = useRef(points.length), nextCriterion = useRef(ranking.length);
  const move = (index: number, delta: number) => setRanking((rows) => {
    const copy = [...rows]; [copy[index], copy[index + delta]] = [copy[index + delta], copy[index]]; return copy;
  });
  return <fieldset className="club-stack" disabled={disabled}><legend>Konfigurace ročníku</legend>
    <h3>Bodování běžných výsledků</h3>
    <p className="field-hint">Zadejte body pro každý povolený výsledek a ukončení. Prázdné hranice skóre jsou neomezené, zadané hranice se započítávají. Server odmítne překryvy i mezery. Kontumace mají samostatná pravidla níže.</p>
    {errors["configuration.pointRules"] && <p className="field-error">{errors["configuration.pointRules"]}</p>}
    {points.map(({ key, rule }, index) => <section className="detail-card club-stack" key={key} aria-label={`Bodovací pravidlo ${index + 1}`}>
      <input type="hidden" name="pointKey" value={key} /><h4>Pravidlo {index + 1}</h4>
      <div className="form-grid"><SelectField name={`point.${key}.outcome`} label="Výsledek" defaultValue={rule.outcome}>{Object.entries(outcomes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectField>
        <SelectField name={`point.${key}.ending`} label="Ukončení" defaultValue={rule.ending}>{Object.entries(endings).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectField></div>
      <NumberField name={`point.${key}.points`} label="Přidělené body" value={rule.points} />
      <div className="form-grid"><NumberField name={`point.${key}.ownScoreMin`} label="Vlastní skóre od" value={rule.ownScoreMin} optional /><NumberField name={`point.${key}.ownScoreMax`} label="Vlastní skóre do" value={rule.ownScoreMax} optional />
        <NumberField name={`point.${key}.opponentScoreMin`} label="Skóre soupeře od" value={rule.opponentScoreMin} optional /><NumberField name={`point.${key}.opponentScoreMax`} label="Skóre soupeře do" value={rule.opponentScoreMax} optional /></div>
      <button type="button" className="text-button" onClick={() => setPoints((rows) => rows.filter((r) => r.key !== key))}>Odebrat bodovací pravidlo</button>
    </section>)}
    <button type="button" className="button button-secondary" disabled={points.length >= 100} onClick={() => setPoints((rows) => [...rows, { key: nextPoint.current++, rule: blankPoint() }])}>Přidat bodovací pravidlo</button>
    <h3>Soutěžní kontumace</h3>
    {errors["configuration.forfeitRules"] && <p className="field-error">{errors["configuration.forfeitRules"]}</p>}
    {configuration.forfeitRules.map((r) => <section className="detail-card club-stack" key={r.decision} aria-label={r.decision === "FORFEIT_HOME" ? "Kontumace domácích" : "Kontumace hostů"}>
      <h4>{r.decision === "FORFEIT_HOME" ? "Provinili se domácí — vítězí hosté" : "Provinili se hosté — vítězí domácí"}</h4>
      <div className="form-grid"><NumberField name={`${r.decision}.homeScore`} label="Skóre domácích" value={r.homeScore} /><NumberField name={`${r.decision}.awayScore`} label="Skóre hostů" value={r.awayScore} />
        <NumberField name={`${r.decision}.homePoints`} label="Body domácích" value={r.homePoints} /><NumberField name={`${r.decision}.awayPoints`} label="Body hostů" value={r.awayPoints} /></div>
    </section>)}
    <h3>Kritéria pořadí</h3><p className="field-hint">Kritéria se použijí shora dolů. Vzájemná kritéria musí tvořit souvislý blok; skóre částí vyžaduje povinnou evidenci částí ve formátu.</p>
    {errors["configuration.rankingCriteria"] && <p className="field-error">{errors["configuration.rankingCriteria"]}</p>}
    {ranking.map(({ key, rule }, index) => <section className="detail-card club-stack" key={key} aria-label={`Kritérium ${index + 1}`}>
      <input type="hidden" name="criterionKey" value={key} /><h4>Priorita {index + 1}</h4>
      <SelectField name={`criterion.${key}.metric`} label="Metrika" defaultValue={rule.metric}>{Object.entries(metrics).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectField>
      <SelectField name={`criterion.${key}.direction`} label="Směr řazení" defaultValue={rule.direction}><option value="DESC">Vyšší hodnota první</option><option value="ASC">Nižší hodnota první</option></SelectField>
      <div className="actions"><button type="button" className="text-button" disabled={index === 0} onClick={() => move(index, -1)}>Posunout výš</button><button type="button" className="text-button" disabled={index + 1 === ranking.length} onClick={() => move(index, 1)}>Posunout níž</button><button type="button" className="text-button" onClick={() => setRanking((rows) => rows.filter((r) => r.key !== key))}>Odebrat kritérium</button></div>
    </section>)}
    <button type="button" className="button button-secondary" disabled={ranking.length >= 10} onClick={() => setRanking((rows) => [...rows, { key: nextCriterion.current++, rule: { priority: rows.length + 1, metric: "POINTS", direction: "DESC" } }])}>Přidat kritérium</button>
    <h3>Vzájemné zápasy</h3>
    <SelectField name="headToHeadMissingPolicy" label="Neúplné vzájemné zápasy" defaultValue={configuration.headToHeadMissingPolicy}><option value="REQUIRE_COMPLETE">Vyžadovat úplnou sadu</option><option value="PLAYED_ONLY">Použít pouze odehrané</option></SelectField>
    <NumberField name="headToHeadMeetingsPerPair" label="Počet vzájemných zápasů každé dvojice" value={configuration.headToHeadMeetingsPerPair} min={1} />
    <label><input type="checkbox" name="restartHeadToHeadOnSubgroup" defaultChecked={configuration.restartHeadToHeadOnSubgroup} /> Zopakovat vzájemná kritéria pro menší shodnou podskupinu</label>
  </fieldset>;
}
