import type { Metadata } from "next";
import { CompetitionSeasonManager } from "@/components/club/CompetitionSeasonManager";
export const metadata: Metadata = { title: "Pravidla a přihlášky ročníku" };
export default async function Page({ params }: { params: Promise<{ competitionSeasonId: string }> }) {
  const { competitionSeasonId } = await params;
  return <CompetitionSeasonManager id={competitionSeasonId} />;
}
