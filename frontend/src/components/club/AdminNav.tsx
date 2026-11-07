"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { Capability } from "@/api/types";
import { useAuth } from "@/context/AuthContext";

const links: { href: string; name: string; capability: Capability }[] = [
  { href: "/admin/accounts", name: "Přístupové účty", capability: "MANAGE_ACCOUNTS" },
  { href: "/admin/sports", name: "Sporty", capability: "MANAGE_SPORTS" },
  { href: "/admin/match-formats", name: "Formáty zápasu", capability: "MANAGE_CLUB" },
  { href: "/admin/competitions", name: "Soutěže a ročníky", capability: "MANAGE_CLUB" },
  { href: "/admin/seasons", name: "Sezóny", capability: "MANAGE_SEASONS" },
  { href: "/admin/teams", name: "Týmy a členství", capability: "MANAGE_TEAMS" },
  { href: "/admin/players", name: "Hráči a osoby", capability: "MANAGE_PLAYERS" },
];

export function AdminNav() {
  const { context } = useAuth();
  const pathname = usePathname();
  return <nav className="club-admin-nav" aria-label="Administrace klubu">{links.filter((link) => context?.capabilities.includes(link.capability)).map((link) => <Link key={link.href} href={link.href} aria-current={pathname === link.href ? "page" : undefined}>{link.name}</Link>)}</nav>;
}
