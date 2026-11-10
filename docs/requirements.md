# Požadavky a sledovatelnost zadání

Zdroj: uživatelem dodané `zadani_sportovni_klub_v2.odt` („Zadání semestrální práce: Informační systém pro správu sportovního klubu“) a doplňující pokyny pro tento ročníkový projekt. ODT byl při návrhu přečten z jeho `content.xml`, nikoli odhadnut z názvu. Doplnění uživatele zpřesňuje databázi, bezpečnost a etapové předání; nezužuje finální funkční rozsah ODT.

**Aktuální rozsah je E0 + P1–P4.** V1–V4 mají 12 tabulek: identitu, osoby/hráče/sezóny/týmy/členství, účty na ověřené osoby, rodičovské vazby, Moje děti, omezený CHILD a scoped soupisky/historii. V5/V6 přidávají formáty, soutěže, ročníky, pravidla a přihlášky: celkem 19 tabulek, dalších 19 je plán. Události, výsledky/statistiky a výpočet tabulek, finance, notifikace a komunikace patří do P5–P9. Úplná akceptace E1/E3 ani projekt nejsou uzavřené. Implementace není automaticky akceptace; skutečné výsledky uvádí [ověření](verification.md).

## P4 — soutěžní konfigurace

P4 implementuje formáty, soutěže, ročníky a přihlášky sezónních klubových i externích týmů, úplné bodování podle výsledku/ukončení a rozsahů skóre, obě soutěžní kontumace, kritéria pořadí a H2H politiku. Backend a skutečné UI navazují na R12/R13/R14/R16 a T01/T03/T04/T07/T08/T10/T11. Výsledky, skutečné uzamčení prvním výsledkem a výpočty pořadí/statistik zůstávají v P5/P6. Konkrétní lokální akceptační scénáře a omezení uvádí [ověření](verification.md); P4 není uzavření celé E3 ani projektu.

## Funkční požadavky ODT

| ID | Požadavek ze zadání | Návrh / důkaz pro finální akceptaci | Cílová etapa | Stav E0 + P1–P4 |
| --- | --- | --- | --- | --- |
| R01 | Správa hráčů, trenérů, týmů a základních údajů | Profily oddělené od účtů, sport, týmy, sezónní intervalová členství; CRUD UI/API a negativní týmové testy | E1 | P1–P3 implementují osoby, offline hráče, sezóny, týmy, trenéry, členství a ověřené účty |
| R02 | Administrátor, trenér, hráč a rodič/zákonný zástupce s odpovídajícími oprávněními | Systémové USER/ADMIN + odvozené trenér/hráč/rodič kontexty; typ STANDARD/CHILD; dítě pod ověřeným rodičem bez přístupu k financím; solo trenér se správou vlastního týmu; [negativní scénáře](authorization.md) | E1–E6 | P1–P4 aktuální účet, sessions a role, týmové/rodičovské hranice a ADMIN konfigurace soutěží; další doménová práva v P5–P9 |
| R03 | Tréninky, zápasy a další klubové akce | Obecný Event TRAINING/MATCH/OTHER; kalendář, místo/čas, změna/zrušení, stejně pro oba sporty | E2 | Navrženo |
| R04 | Potvrzování účasti a omluvy hráčem/rodičem | Samostatná Availability s autorem; vlastní hráč nebo schválené dítě; uzávěrka a validace | E2 | Navrženo |
| R05 | Trenér z dostupnosti připraví nominaci | Samostatná Nomination; publikace, náhradníci, audit, notifikace; dostupnost nenominuje automaticky | E2 | Navrženo |
| R06 | Evidence skutečné docházky | Samostatná Attendance pro obecnou událost; skutečnost až po začátku, oddělená od dostupnosti/nominace | E2 | Navrženo |
| R07 | Členské příspěvky, splatnosti a stav úhrady; skutečné platby zadává admin | Předpis na osobu, částečné úhrady, evidovaná storna; odvozené nesplaceno/částečně/zaplaceno; příklad 1000→400→600→storno v [roadmapě](roadmap.md) | E4 | Navrženo |
| R08 | Upozornění na blížící se a prodlené splatnosti členovi i administrátorovi | Scheduler a schránka: standardní člen/oprávněný rodič a adminové, nikdy CHILD; deduplikace a zastavení po úhradě | E4 | Navrženo |
| R09 | Platební brána mimo rozsah | Zaznamenání platby provedené mimo aplikaci; žádná brána ani online strhávání | E4 | Respektováno v návrhu |
| R10 | Notifikace nových událostí, změn a nominací | Transakční oznámení a doručení, vlastní schránka, přečtení a deduplikace; rollback a opakování bez ztráty/duplicit | E2 | Navrženo |
| R11 | Vnitrosystémová komunikace trenérů, hráčů a rodičů | Vlastní Conversation/Participant/Message a UI vedle notifikací; scoped čtení/odeslání; A11 | E5 | Navrženo; není nahrazena notifikacemi |
| R12 | Správa soutěží a sezón | Sportovní Competition, CompetitionSeason, účastníci a archivace sezóny | E1+E3 | P2 sezóny a archivace; P4 soutěže, ročníky, přihlášky a jejich správní UI |
| R13 | Zápasy, soupeři, výsledky a individuální statistiky po zápasu | Match, externí Team, neměnné revize skóre/částí/účastníků, admin typed definice a hodnoty skutečných účastníků | E3 | P2 externí soupeři; P4 formáty a soutěžní pravidla; zápasy/výsledky/statistiky zbývají v P5/P6 |
| R14 | Tabulky a pořadí ze zaznamenaných výsledků | Jen aktivní potvrzené výsledky; admin bodování/tie breakers, minitabulka, přepočet a verze cache | E3 | P4 konfigurované bodování, kontumace, pořadí kritérií a H2H politika; výpočet ze skutečných výsledků až P6 |
| R15 | Týmové a individuální statistiky | Týmové součty z výsledků, individuální definice/typed hodnoty, agregace SUM/AVG/COUNT atd. podle typu; filtry tým/hráč/sezóna | E3 | Navrženo |
| R16 | Historie sezón, výsledků a statistik | Archivované sezóny, členství a revize; změna profilu/statistiky nepřepisuje význam minulých hodnot; upgrade/restore test | E1–E6 | P2 historie členství a sezón; P4 ochrana použitých/archivovaných pravidel; výsledky/statistiky/restore plán |

## Technické a výstupní požadavky ODT

| ID | Požadavek ze zadání | Konkrétní řešení a akceptace | Cílová etapa | Stav E0 + P1–P4 |
| --- | --- | --- | --- | --- |
| T01 | Samostatný webový frontend přes REST API | Next.js App Router/TypeScript, skutečný Spring JSON klient, credentials/CSRF; žádná doménová business logika v Next serveru | E0–E6 | P1–P4 skutečné identity, týmové, account/rodičovské a soutěžní UI; ostatní domény plán |
| T02 | Responzivní desktop a mobil | Responzivní skeleton; všechny následující tabulky, kalendář, formuláře a konverzace mají mobilní variantu a akceptaci | E0–E6 | P1–P4 responzivní formuláře, přehledy, soupisky a soutěžní konfigurace; provedené kontroly viz ověření |
| T03 | Java/Spring Boot REST backend | Zachované vrstvy/DTO šablony, Java 17/Spring Boot 4, transakční service a omezené repository dotazy | E0–E6 | Implementovaný auth/info, členský/rodičovský a soutěžní konfigurační backend P1–P4 |
| T04 | Alespoň dva týmové sporty bez změny kódu | Sporty v DB v jedné instalaci současně; každý tým právě jeden sport; fotbal a basketbal s odlišným bodováním, kritérii shody a statistikami; dva plné scénáře E3 | E1+E3 | P1–P4 oba sporty, týmy a soutěžní konfigurace bez sportovních větví; úplné výsledkové scénáře až P6 |
| T05 | Admin spravuje názvy a typy individuálních statistik v UI | Stabilní definice a neměnné verze; INTEGER/DECIMAL/BOOLEAN/TEXT, kompatibilní validace/agregace; dynamický formulář | E3 | Navrženo; žádná pevná sportovní pole |
| T06 | Obecný model akcí nezávislý na sportu | Jeden Event se třemi typy, specializovaný Match jen pro výsledkové údaje | E2 | Navrženo |
| T07 | Relační DB pro členy/týmy/akce/soutěže/sezóny | PostgreSQL 16, UUID/FK/constraints, etapové Flyway migrace, validate; ERD a návrh SQL v [DB](database-design.md) | E0–E5 | V1–V6: 19 tabulek včetně soutěží, pravidel a přihlášek; dalších 19 tabulek plán |
| T08 | Uložení a analýza historie sezón | Nemazat minulá členství/výsledky/statistiky, archivované přehledy; verzování definic a pravidel | E1–E6 | P2 historie členství a sezón; P4 neměnnost použité konfigurace a archivované read-only; výsledky/statistiky/restore plán |
| T09 | Registrace, login, bezpečné hashování hesla | Zachovaný BCrypt UserService, JSON session tok, CSRF, session fixation ochrana, logout, 401/403 | E0 | Implementováno E0 + P1–P3 včetně odvolání sessions; provedené kontroly viz [ověření](verification.md) |
| T10 | Víceúrovňový role přístup | Systémová role + typ účtu + tým/dítě + čas/stav; CHILD finance deny; serverová kontrola každého detailu/seznamu/zápisu; negativní scénáře autorizace | E0–E6 | P1–P4 aktuální účet, ADMIN, týmové/rodičovské čtení a counts, konfigurace soutěží; CHILD allowlist, funkční finance plán |
| T11 | Unit a integrační testy backendu i frontendu | Backend unit a PostgreSQL/Spring integration; frontend unit a komponentové/provider HTTP integration; stage akceptace a CI | E0–E6 | Unit/HTTP/PostgreSQL a frontend/browserové scénáře P1–P4; [mapa funkcí a testů](test-coverage.md), konkrétní výsledky jen v ověření |
| T12 | Nasazení s Docker/Compose a HTTPS | Multi-stage images, DB healthcheck, TLS proxy/secure cookie, záloha a obnova podle [deployment](deployment.md) | E0+E6 | Konfigurace a návod připravené; skutečné produkční HTTPS nasazení zatím neproběhlo |
| T13 | Kompletní funkční webový IS | Všechny R01–R16 a T01–T12 mají implementaci a akceptační důkaz, včetně obou sportů | E6 | Nedokončeno — E0 + P1–P4 neuzavírá úplnou akceptaci E1/E3 ani celou klubovou aplikaci |
| T14 | Projektová dokumentace a uživatelská příručka | Aktuální skeleton postup a plánované workflow čtyř rolí, následně skutečné obrazovky a postupy [příručky](user-guide.md) | E0–E6 | Příručka skutečného P1–P4; čtyři úplné klubové postupy se dokončí s navazujícími funkcemi |

## Dodatečná omezení uživatele

| ID | Upřesnění | Řešení / hranice |
| --- | --- | --- |
| U01 | Template pouze zdroj, žádný commit/push/nastavení/viditelnost původního repozitáře | Práce výhradně v novém `sport-club-system`, vlastní Git historie; původ a licence zachované. Nový [veřejný repozitář](https://github.com/Dasky02/sport-club-system) byl publikován jako samostatný závěrečný krok po kontrole obsahu. |
| U02 | Zachovat vhodnou architekturu a implementace šablony | Vrstvy, package, UUID User/BCrypt, UserRepository/UserService, AuthController/records, error handler, info/OpenAPI, wrapper, PostgreSQL/Testcontainers; cílené opravy místo paralelní autentizace. |
| U03 | Převod Vite do Next.js; nový monorepo základ | `backend/`, `frontend/`, Docker/CI/cache paths a VERSION; nepoužívat Vite/Apache ani browser URL s Docker hostname. |
| U04 | Dynamické statistiky s validací a agregací | Verze definice, typed hodnoty, seznam povolených agregací, null/zero pravidla a historická kompatibilita v [modelu](domain-model.md). |
| U05 | Konfigurovatelné tabulky pro oba sporty | Admin datová pravidla, pořadí kritérií shody, minitabulka, chybějící vzájemné zápasy, přepočet potvrzených výsledků; ne ruční pořadí bodů. |
| U06 | Oddělení účtu, hráče a rodičovské vazby; týmová autorizace | Person/User/PlayerProfile, GuardianLink přes osobu a intervalová členství; USER nemá automaticky tým ani dítě. Dětský účet vyžaduje rodiče, evidovaný hráč bez účtu nikoli. |
| U07 | Částečné úhrady, splatnosti, admin zaznamenává platbu | Několik fee_payments na předpis a auditem podložené storno, transakční limity; jen skutečně provedené platby. Kredit a alokace více předpisů nejsou povinné zadáním ani součástí tohoto modelu. |
| U08 | Historie členství a výsledků/statistik nesmí zmizet | Vlastní sezónní data, datumované vztahy, append-only revize, archivace a řízené admin opravy. |
| U09 | Rozsáhlé funkce navazující, pravdivý stav | Žádné prázdné controllery/fiktivní API v E0. Tento přehled, roadmapa a příručka výslovně odlišují skutečný skeleton a plán. |
| U10 | První MVP pro jeden sport není finální rozsah | První vertikální scénář může být fotbal; E3 a E6 musí akceptovat basketbal konfiguračně bez úprav kódu ve stejné aplikaci. |
| U11 | Trenér může být jediný uživatel a spravovat svůj tým | Jeden STANDARD účet může mít správu instalace a trenérské přiřazení; hráči, rodiče ani další administrátor nepotřebují účty. Bezpečný jednorázový setup je implementovaný P1; týmy, hráči a vlastní trenérská přiřazení jsou implementovaná P2. |
| U12 | Dětský účet je potomek rodiče s omezenými právy | P3 CHILD má vlastní hráčský profil, schváleného aktivního STANDARD rodiče a samostatnou aktivaci. Žádné finance či finanční notifikace, žádné dědění rolí. Poslední kvalifikovaný rodič zanikne → zákaz a odvolání sessions včetně starých cookies po reaktivaci. Regrant/povolení rodiče dítě automaticky neaktivuje. Funkční finance/schránka zůstávají plánované. |

## Hodnocení a důkazy

ODT hodnotí úplnost funkcí, kvalitu kódu a dobrou praxi, přívětivé UI, správné role, obecnost sportů a kvalitu dokumentace. Důkazem je fungující vertikální scénář, reviewed PR a relevantní test, ne počet tabulek či názvů rolí. Každý navazující PR uvádí ID požadavků, skutečně dodaný rozsah, migrační dopad a provedené testy. Hotová funkce aktualizuje tuto tabulku z „navrženo“ na „implementováno“ až po konkrétní akceptaci.

Závěrečná akceptace E6 musí projít všechny čtyři klubové kontexty, omezené dětské přihlášení pod rodičem i režim jediného trenéra a celé dvě sportovní konfigurace od správy členů po archivovanou sezónu, včetně finance+notifikací+samostatné komunikace. Všechny neprovedené kontroly, známé chyby a nedokončené funkce jsou viditelné ve výsledku předání; není dovoleno je schovat pod „další rozšíření“, když jsou součástí ODT.

Relativní doporučený harmonogram ODT (2 týdny návrh, 5 implementace, 2 testování/nasazení) je zachovaný v [roadmapě](roadmap.md). Konkrétní počáteční datum a kapacita týmu zatím nejsou sjednané.
