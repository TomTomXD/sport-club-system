# Mapa testů implementovaných funkcí E0 + P1–P4

Tato mapa propojuje aktuálně implementované funkční celky s testy požadovanými v T11 a v [pracovním plánu](implementation-plan.md). Nejde o měření procenta pokrytí řádků či větví ani o tvrzení, že je otestovaná každá kombinace vstupů a každý endpoint samostatně. Existence testu není důkazem jeho spuštění; datované výsledky a omezení patří výhradně do [ověření](verification.md).

## Funkční celky a testovací vrstvy

| Funkce / požadavky | Backend unit a HTTP/PostgreSQL | Frontend unit / komponenty s HTTP mockem | Browser proti skutečnému API |
| --- | --- | --- | --- |
| Registrace, BCrypt, login, session refresh, fixation, logout; T09/A01/A17 | [UserServiceTests][user-unit], [SpringProjectTemplateApplicationTests][base-http] | [api.test.ts][api], [components.test.tsx][components], [AuthContext.test.tsx][auth] | [auth.spec.ts][auth-e2e] |
| Veřejné info, verze, health, OpenAPI; T03/T14 | [InfoServiceTests][info-unit], [SpringProjectTemplateApplicationTests][base-http] | [api.test.ts][api], [components.test.tsx][components] | Verzi kontroluje [auth.spec.ts][auth-e2e] |
| Vlastní osoba, PENDING context, jednorázový setup, absent/chybný token, dvě souběžná převzetí; R02/U11/A27 | [SetupTokenVerifierTests][setup-unit], [IdentityAndClubSetupTests][p1-http], [SelfSessionRefresherTests][session-unit] | [p1.test.tsx][p1-fe], [club.test.ts][club-api], [AuthContext.test.tsx][auth] | [club-setup.spec.ts][setup-e2e]; setup je součástí dalších izolovaných doménových průchodů |
| Aktivní/neaktivní sporty, create/rename/deactivate, duplicity, stránkování; T04 | [IdentityAndClubSetupTests][p1-http] | [p1.test.tsx][p1-fe], [club.test.ts][club-api] | [club-setup.spec.ts][setup-e2e], [roster.spec.ts][roster-e2e], [competitions.spec.ts][competitions-e2e] |
| Osoby, offline hráči, výslovná existující osoba, DOB a minimální DTO; R01/U06 | [TeamsAndMembershipsTests][p2-http] | [p2.test.tsx][p2-fe], [p3.test.tsx][p3-fe], [teams.test.ts][teams-api] | [roster.spec.ts][roster-e2e], [guardians.spec.ts][guardians-e2e] |
| Sezóny, klubové/externí týmy, sezónní názvy, archivace; R01/R12/R16/T04/T08 | [TeamsAndMembershipsTests][p2-http] | [p2.test.tsx][p2-fe], [teams.test.ts][teams-api] | [roster.spec.ts][roster-e2e], [competitions.spec.ts][competitions-e2e] |
| Hráčská/trenérská členství, intervaly, přestup a historie, překryvy a souběh; R01/R16/U08/U11 | [TeamDomainServiceTests][team-unit], [TeamsAndMembershipsTests][p2-http] | [p2.test.tsx][p2-fe], [teams.test.ts][teams-api] | [roster.spec.ts][roster-e2e] |
| Scoped týmy, soupiska, vlastní hráčská historie, aktuální vztahy a counts; R02/T10/A02/A08/A16 | [TeamDomainServiceTests][team-unit], [TeamsAndMembershipsTests][p2-http], [GuardiansAndChildAccountsTests][p3-http] | [p2.test.tsx][p2-fe], [p3.test.tsx][p3-fe] | [roster.spec.ts][roster-e2e], [guardians.spec.ts][guardians-e2e] |
| Účet na ověřené osobě, person-link, role/type/enabled, důvod, poslední ADMIN; R02/U06/A19/A23/A26 | [GuardiansAndChildAccountsTests][p3-http], [SelfSessionRefresherTests][session-unit] | [p3.test.tsx][p3-fe], [accounts.test.ts][accounts-api] | [guardians.spec.ts][guardians-e2e] |
| Guardian grant/revoke/regrant, více rodičů/dětí, Moje děti, scoped detail a historie; R02/U06/A04/A20 | [GuardiansAndChildAccountsTests][p3-http] | [p3.test.tsx][p3-fe], [accounts.test.ts][accounts-api] | [guardians.spec.ts][guardians-e2e] |
| CHILD aktivace/login, ztráta posledního rodiče, neobnovení staré session, finanční deny hranice; U12/A21–A26 | [ChildAccessPolicyTests][child-unit], [GuardiansAndChildAccountsTests][p3-http], [FreshAccountFilterTests][fresh-unit] | [p3.test.tsx][p3-fe], [p2.test.tsx][p2-fe], [AuthContext.test.tsx][auth] | [guardians.spec.ts][guardians-e2e] |
| Formáty obou sportů, SUM_SCORE/WON_PARTS, validace, velké verze, konflikt, rename použitých pravidel; R13/R16/T04/T08 | [MatchFormatServiceTests][format-unit], [MatchFormatsTests][format-http] | [matchFormats.test.tsx][format-fe] | [match-formats.spec.ts][formats-e2e], [competitions.spec.ts][competitions-e2e] |
| Soutěže create/rename, celé ročníky, filtry a counts; R12/T01 | [CompetitionServiceTests][competition-unit], [CompetitionsTests][competition-http] | [competitions.test.tsx][competition-fe]; také zachování rozepsaného nového ročníku při create/rename jiné soutěže | [competitions.spec.ts][competitions-e2e] |
| Úplné bodování, inkluzivní rozsahy, mezery/překryvy, ukončení, obě kontumace, priority/H2H/PART_SCORE; konfigurační R13/R14 | [CompetitionConfigurationValidatorTests][rules-unit], [CompetitionsTests][competition-http] | [competitions.test.tsx][competition-fe] | [competitions.spec.ts][competitions-e2e]: dva sporty, rozdílné body/kontumace/H2H, odmítnutý překryv |
| Přihlášky klubových/externích týmů, stejný sport/sezóna, duplicita, add/remove, společná version; R12/R16 | [CompetitionsTests][competition-http], [CompetitionServiceTests][competition-unit] | [competitions.test.tsx][competition-fe] | [competitions.spec.ts][competitions-e2e]: oba typy týmů a odmítnutý cizí sport |
| Použité/archivované konfigurace, sdílený formát, data sezóny, souběh a auditní rollback; R16/T08/U08 | [MatchFormatsTests][format-http], [CompetitionsTests][competition-http], [CompetitionServiceTests][competition-unit] | [matchFormats.test.tsx][format-fe], [competitions.test.tsx][competition-fe] | [match-formats.spec.ts][formats-e2e], [competitions.spec.ts][competitions-e2e]: dvě karty, explicitní reload, archivovaný read-only |
| Upgrade V1–V6, zachování identit/BCrypt/historie/checksumů a PostgreSQL constraints; T07/T08/U08 | [IdentityMigrationTests][identity-migration], [RosterMigrationTests][roster-migration], [GuardianMigrationTests][guardian-migration], [MatchFormatMigrationTests][format-migration], [CompetitionMigrationTests][competition-migration] | Samostatné FE migrační testy se neuplatňují | Čistá DB se všemi migracemi je předpoklad izolovaných průchodů |
| CSRF, credentials, bezpečné chyby, 401 vs síť, auth race, explicitní retry a žádné automatické opakování mutací; T01/T10 | [SpringProjectTemplateApplicationTests][base-http], [IdentityAndClubSetupTests][p1-http], doménové HTTP testy, [FreshAccountFilterTests][fresh-unit] | [api.test.ts][api], [club.test.ts][club-api], [AuthContext.test.tsx][auth], doménové komponentové testy | [auth.spec.ts][auth-e2e] a doménové konfliktní průchody |

Backend unit testy nepoužívají DB jako náhradu validace. HTTP/PostgreSQL testy používají Testcontainers, skutečné Flyway migrace, sessions a CSRF; zahrnují negativní autorizaci, atomický rollback a skutečné souběžné požadavky. FE komponentové testy používají skutečný REST klient s HTTP mockem. Browserové testy vyžadují skutečný backend a vlastní izolovanou DB.

## Meze kontroly a zbývající akceptace

- Testy existují pro každý výše uvedený implementovaný funkční celek. Tato mapa není tvrzením o dokončení všech R01–R16 nebo A01–A27 v jejich finálním rozsahu.
- R03–R08/R10/R11/R15 a výsledkové/výpočetní části R13/R14/R16 čekají na P5–P9. CHILD test odmítnuté finanční cesty neověřuje dosud neexistující platby, scheduler ani notifikace.
- Automatické nastavení lockedAt prvním výsledkem, výpočet tabulek, restart H2H ve skutečné minitabulce, statistiky a výsledkové revize vyžadují nové testy P5/P6. P4 testuje konfiguraci a ochranu již zamčeného stavu.
- [CI](../.github/workflows/ci.yml) spouští Maven verify, FE lint/typecheck/test/build a kontrolu obsahu/Compose. Playwright není v tomto workflow automatizovaný; browserová akceptace zůstává samostatným izolovaným během.
- Produkční HTTPS, skutečná obnova zálohy, úplná přístupnost a finální akceptace E1/E3/E6 nejsou touto mapou uzavřené. Dřívější výsledky se nepřenášejí na nový commit jako nový test.

[user-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/user/UserServiceTests.java
[base-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/SpringProjectTemplateApplicationTests.java
[info-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/info/InfoServiceTests.java
[setup-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/club/SetupTokenVerifierTests.java
[session-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/user/SelfSessionRefresherTests.java
[p1-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/IdentityAndClubSetupTests.java
[p2-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/TeamsAndMembershipsTests.java
[team-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/team/TeamDomainServiceTests.java
[p3-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/GuardiansAndChildAccountsTests.java
[child-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/access/ChildAccessPolicyTests.java
[fresh-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/config/FreshAccountFilterTests.java
[format-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/competition/MatchFormatServiceTests.java
[format-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/MatchFormatsTests.java
[competition-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/competition/CompetitionServiceTests.java
[competition-http]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/CompetitionsTests.java
[rules-unit]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/service/competition/CompetitionConfigurationValidatorTests.java
[identity-migration]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/IdentityMigrationTests.java
[roster-migration]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/RosterMigrationTests.java
[guardian-migration]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/GuardianMigrationTests.java
[format-migration]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/MatchFormatMigrationTests.java
[competition-migration]: ../backend/src/test/java/cz/jpmad/springprojecttemplate/CompetitionMigrationTests.java
[api]: ../frontend/src/api/api.test.ts
[club-api]: ../frontend/src/api/club.test.ts
[teams-api]: ../frontend/src/api/teams.test.ts
[accounts-api]: ../frontend/src/api/accounts.test.ts
[auth]: ../frontend/src/context/AuthContext.test.tsx
[components]: ../frontend/src/components/components.test.tsx
[p1-fe]: ../frontend/src/components/p1.test.tsx
[p2-fe]: ../frontend/src/components/club/p2.test.tsx
[p3-fe]: ../frontend/src/components/club/p3.test.tsx
[format-fe]: ../frontend/src/components/club/matchFormats.test.tsx
[competition-fe]: ../frontend/src/components/club/competitions.test.tsx
[auth-e2e]: ../frontend/e2e/auth.spec.ts
[setup-e2e]: ../frontend/e2e/club-setup.spec.ts
[roster-e2e]: ../frontend/e2e/roster.spec.ts
[guardians-e2e]: ../frontend/e2e/guardians.spec.ts
[formats-e2e]: ../frontend/e2e/match-formats.spec.ts
[competitions-e2e]: ../frontend/e2e/competitions.spec.ts
