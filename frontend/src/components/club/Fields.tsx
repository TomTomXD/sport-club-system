"use client";

import { useId, type ReactNode, type SelectHTMLAttributes } from "react";
import { mapErrorToMessage } from "@/api/errorMapper";
import { FormField } from "../FormField";
import type { PersonRequest } from "@/api/types";

export function SelectField({ name, label, children, hint, error, ...input }: { name: string; label: string; children: ReactNode; hint?: string; error?: string } & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(" ");
  return <div className="field"><label htmlFor={id}>{label}</label><select {...input} id={id} name={name} aria-invalid={error ? true : undefined} aria-describedby={describedBy || undefined}>{children}</select>{hint && <span id={`${id}-hint`} className="field-hint">{hint}</span>}{error && <span id={`${id}-error`} className="field-error">{error}</span>}</div>;
}

export function FormError({ error, fieldErrors = {} }: { error: unknown; fieldErrors?: Record<string, string> }) {
  if (error === null || error === undefined) return null;
  return <p className="message message-error" role="alert">{Object.keys(fieldErrors).length ? "Zkontrolujte označená pole." : mapErrorToMessage(error)}</p>;
}

export function PersonFields({ initial, requiredBirthDate = false, disabled = false, errors = {} }: { initial?: PersonRequest; requiredBirthDate?: boolean; disabled?: boolean; errors?: Record<string, string> }) {
  return <><div className="form-grid"><FormField name="firstName" label="Jméno" defaultValue={initial?.firstName} maxLength={100} required disabled={disabled} error={errors.firstName} /><FormField name="lastName" label="Příjmení" defaultValue={initial?.lastName} maxLength={100} required disabled={disabled} error={errors.lastName} /></div><FormField name="birthDate" label="Datum narození" type="date" defaultValue={initial?.birthDate ?? ""} required={requiredBirthDate} max={clubToday()} disabled={disabled} error={errors.birthDate} hint={requiredBirthDate ? "Povinné pro hráčský profil." : "Nepovinné u osoby bez hráčského profilu."} /><FormField name="email" label="E-mail" type="email" defaultValue={initial?.email ?? ""} maxLength={254} disabled={disabled} error={errors.email} /><FormField name="phone" label="Telefon" type="tel" defaultValue={initial?.phone ?? ""} maxLength={40} disabled={disabled} error={errors.phone} /></>;
}

export function personFromForm(data: FormData): PersonRequest {
  return { firstName: String(data.get("firstName") ?? "").trim(), lastName: String(data.get("lastName") ?? "").trim(), birthDate: String(data.get("birthDate") ?? "") || null, email: String(data.get("email") ?? "").trim() || null, phone: String(data.get("phone") ?? "").trim() || null };
}

export function clubToday(): string { return new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date()); }
export function intervalLabel(from: string, to: string | null): string { return `${from} → ${to ?? "konec sezóny"} (konec vyloučen)`; }

export function intervalError(from: string, to: string | null, start: string, end: string): string | null {
  if (!from || from < start || from >= end) return "Začátek členství musí ležet uvnitř sezóny.";
  if (to && (to <= from || to > end)) return "Konec musí být po začátku, nejpozději na konci sezóny.";
  return null;
}

/** Auditní odůvodnění se neposílá v URL ani nevypisuje z odpovědí serveru. */
export function ReasonField({ label, disabled = false }: { label: string; disabled?: boolean }) {
  return <FormField name="reason" label={label} minLength={1} maxLength={500} required disabled={disabled} autoComplete="off" hint="Stručně odůvodněte ověřenou změnu. Nevkládejte hesla, tajné ani osobní údaje. Nejvýše 500 znaků." />;
}
