"use client";

import Link from "next/link";
import { AccessGate } from "../AccessGate";
import { AdminNav } from "./AdminNav";
import { useAuth } from "@/context/AuthContext";
import type { Capability } from "@/api/types";

const sections: { href: string; name: string; description: string; capability: Capability }[] = [
  { href: "/admin/accounts", name: "Přístupové účty", description: "Účty na ověřené osoby, propojení a aktivace dětského přístupu.", capability: "MANAGE_ACCOUNTS" },
  { href: "/admin/sports", name: "Sporty", description: "Sporty v databázi, přejmenování a deaktivace.", capability: "MANAGE_SPORTS" },
  { href: "/admin/match-formats", name: "Formáty zápasu", description: "Vyhodnocení výsledku, části a pravidla přátelské kontumace.", capability: "MANAGE_CLUB" },
  { href: "/admin/competitions", name: "Soutěže a ročníky", description: "Bodování, kontumace, pravidla pořadí a přihlášky týmů.", capability: "MANAGE_CLUB" },
  { href: "/admin/seasons", name: "Sezóny", description: "Datumové hranice, otevřené sezóny a archivace.", capability: "MANAGE_SEASONS" },
  { href: "/admin/teams", name: "Týmy a členství", description: "Klubové týmy i soupeři, sezónní názvy, hráči a trenéři.", capability: "MANAGE_TEAMS" },
  { href: "/admin/players", name: "Hráči a osoby", description: "Hráčské profily bez povinného účtu a ověřené osobní údaje.", capability: "MANAGE_PLAYERS" },
];

function AdminContent() {
  const { context } = useAuth();
  return <section className="club-page"><AdminNav /><p className="eyebrow">Administrace klubu</p><h1>Správa vašeho klubu</h1><p className="lead">Jeden administrátorský účet může vést více sportů a přiřadit sebe jako trenéra. Hráči nemusejí mít přihlášení ani evidovaného rodiče.</p><div className="club-cards">{sections.filter((section) => context?.capabilities.includes(section.capability)).map((section) => <article className="detail-card" key={section.href}><h2>{section.name}</h2><p>{section.description}</p><Link className="button button-secondary" href={section.href}>Otevřít {section.name.toLowerCase()}</Link></article>)}</div></section>;
}
export function AdminHome() { return <AccessGate completeProfile capability="MANAGE_CLUB"><AdminContent /></AccessGate>; }
