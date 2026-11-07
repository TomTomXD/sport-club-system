import type { Metadata } from "next";
import { MatchFormatsManager } from "@/components/club/MatchFormatsManager";

export const metadata: Metadata = { title: "Formáty zápasu" };
export default function Page() { return <MatchFormatsManager />; }
