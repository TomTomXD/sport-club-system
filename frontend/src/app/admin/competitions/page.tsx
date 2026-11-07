import type { Metadata } from "next";
import { CompetitionsManager } from "@/components/club/CompetitionsManager";
export const metadata: Metadata = { title: "Soutěže a ročníky" };
export default function Page() { return <CompetitionsManager />; }
