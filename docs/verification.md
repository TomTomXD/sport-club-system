# Ověření projektu

## 6. 11. 2026 — dokončení celého P4 a lokální akceptace

Dokončen celý pracovní balíček **P4 — základ soutěží**, nikoli jen formáty: soutěže, ročníky, přihlášky klubových/externích sezónních týmů, úplné bodování podle výsledku/ukončení a rozsahů skóre, obě soutěžní kontumace, kritéria pořadí včetně H2H politiky, skutečné ADMIN API i UI. Navazuje na R12 a konfigurační části R13/R14/R16, T01–T04/T07/T08/T10/T11/T14 a obecné autorizační hranice. V5/V6 realizují 19 z 38 cílových tabulek. Výsledky, první uzamčení při výsledku a výpočet tabulek/statistik jsou samostatné P5/P6; lokální akceptace P4 neuzavírá E1/E3 ani celý projekt.

| Kontrola | Skutečný finální výsledek |
| --- | --- |
| Backend Java 17 / Maven Wrapper verify | **84/84 PASS**, 0 failures/errors/skipped, nový spustitelný JAR. Včetně všech P1–P3 regresí a dosavadních formátů P4; stejná lokální Java 17.0.21 a testovací Docker API 1.44 jako níže. |
| Nová soutěžní část | **6 HTTP/PostgreSQL + 7 unit validačních + 2 service + 1 migrační scénář PASS.** Uložení obou sportů, stejné scopes items/counts, mutace přihlášek, správné přesné verze, dvě souběžné úpravy s jediným vítězem, rollback pravidel/verze/přihlášky při selhání auditu. Audit přihlášky obsahuje oba klíče vztahu, žádný obsah formuláře. |
| Validace a oprávnění | Odmítnutý cizí sport/sezóna, duplicitní přihláška/ročník, překryv i mezera v bodování a neúplná konfigurace. Včetně inkluzivních hranic až integer max, WON_PARTS, nájezdů s nulovým bonusem, správného vítěze kontumace, H2H bloku a podmínek PART_SCORE. USER/PENDING/anonym a aktivní CHILD nemají správní obsah/podzdroje/counts; CSRF a neměnná DTO pole ověřena. Fresh auth následuje až po klubovém zámku. |
| Migrace a historie | V1–V6 aplikované na čistých PostgreSQL 16. Upgrade V5 → V6 zachoval snapshot všech původních 13 tabulek, identity/BCrypt/setup/past season a již zamčený formát i checksumy V1–V5; po upgradu **19 tabulek**, opakovaný migrate bez DDL. DB EXCLUDE odmítl překryv a guardy odmítly cizí sport, odemčení i změny použitých pravidel/kontumací/kritérií/přihlášek. Použitý ročník chrání i datumové hranice sezóny bez dosavadního týmu. Archivace ponechává čtení. |
| Frontend Node 24.14.0 | **101/101 PASS** přes `npm test -- --maxWorkers=1`, lint/typecheck/produkční build PASS. Skutečné REST/CSRF mutace, atomický create ročníku, zachování dynamických řádků při odebrání/přesunu, přesná velká version, zachování pravidel po 409 a výslovný reload, add/remove přihlášek a read-only archiv. Build používá izolované API `http://localhost:18080/api`. Žádná nová runtime závislost. |
| Browser desktop | Chromium + nový JAR + nový Next production standalone + nová izolovaná DB: **3/3 PASS, žádný skip**. Dva auth/session/výpadek regresní scénáře a celý průchod P4 (`competitions.spec.ts`). |
| Browser mobil | Pixel 7 nad druhou novou DB s nově aplikovanými V1–V6: **3/3 PASS, žádný skip**. Celkem znovu **6/6** browserových scénářů. |
| Vizuální kontrola | Prohlédnuté snímky fotbalového i basketbalového ročníku na desktopu a Pixelu 7 včetně bodování, kontumací, priorit a přihlášek; mobilní výřezy umožnily ověřit čitelnost. Browser výslovně ověřil absenci horizontálního přetékání u obou sportů. |
| Obsah, dokumentace a konfigurace | Veřejný obsah **239 souborů PASS**, lokální souborové odkazy v 15 Markdown dokumentech PASS, základní/dev/produkční Compose konfigurace a `git diff --check` PASS. Stavová matice, plán, API kontrakt, autorizace, DB popis, README a uživatelská příručka odpovídají celému P4. LICENSE/NOTICE a V1–V4 shodné s HEAD, V5 má nadále původní SHA-256 `31E00EC44F61B2358D4E9D0FB8B2ADD5D4E0F122EC5631B7A71DBE0ED74BD291`; V6 `F1B6432C72A68DE16FD37D9D56CE4EEDE3B925514022D3E37B8A9697DD9A16FF`. |

Browserový P4 průchod registruje/onboarduje jednoho ADMIN, převezme testovací klub, skutečnými formuláři založí fotbal a basketbal, sezónu, dva odlišné formáty, čtyři týmy (klub i externí soupeř pro každý sport), soutěže a úplné ročníky. Zadá fotbalové body 3/1/0 a basketbalové 2/1 pro základní dobu i prodloužení, oddělené kontumace 0:3/3:0 a 0:20/20:0, priority i rozdílnou H2H politiku; přihlásí všechny týmy. UI odmítne překryv a zachová rozpracovaný formulář. Dvě skutečné karty vyvolají optimistic konflikt a explicitní reload, přímé HTTP odmítne cizí sport. Archivace zachová oba přihlášené týmy a odstraní správní mutace v UI. Jde o akceptaci konfigurace P4, žádný fiktivní výpočet výsledků.

Reprodukce z `frontend/`: nad **novou izolovanou DB** a odpovídajícím backendem/frontendem nastavte soukromé `CLUB_SETUP_TOKEN`/`E2E_SETUP_TOKEN` a testovací `E2E_BASE_URL`/`E2E_API_URL`; spusťte `E2E_SETUP_PROJECT=desktop npm run test:e2e -- auth.spec.ts competitions.spec.ts --project=desktop --workers=1`. Pro mobil připravte **další novou DB**, restart backendu a `mobile` v obou volbách. Tento setup scénář se nad stejnou DB nekombinuje s `match-formats.spec.ts`, `club-setup.spec.ts`, `roster.spec.ts` ani `guardians.spec.ts`. Každý přebírá nový klub pouze jednou. Běhy použily pouze vlastní zahoditelné kontejnery bez projektových volumes a porty API/frontend 18080/13000; DB port byl náhodný.

Lokální výstupy jsou ignorované `.verification/p4-full-*` a `.verification/p4-competition-*.png`; testovací procesy a kontejnery se po obou průchodech uklidily. Běžná uživatelova DB nebyla čtena ani změněna. Commit/push, GitHub CI tohoto necommitovaného stavu, nové celé Docker image buildy, produkční HTTPS/ACME ani provozní backup/restore neproběhly; před merge musí projít relevantní CI. P5–P9 zůstávají navazující plán.

## 6. 11. 2026 — druhá kontrola P4 před commitem

Znovu zkontrolován celý rozpracovaný průchod formátů zápasu: DTO/REST, transakční zámky a fresh auth, SQL scope seznamu i počtu, migrace a historický guard, skutečný formulář, bezpečné chyby a optimistic konflikty. Refaktor repository sdílí `FROM` i filtr mezi items/count, používá explicitní sloupce a přímý `count(*)` bez poddotazu s celou projekcí. Formulář má synchronní ochranu proti dalšímu submitu během probíhající mutace; po chybě lze požadavek znovu odeslat. API kontrakt, závislosti a V1–V5 se touto druhou kontrolou neměnily.

| Kontrola | Nový výsledek |
| --- | --- |
| Backend Java 17 `./mvnw.cmd -B -ntp verify` | **68 testů PASS**, žádné failures/errors/skips; včetně nového JAR. Použito stejné lokální nastavení Docker API 1.44 jako v prvním ověření níže. |
| Rozšířené HTTP regrese P4 | **8 scénářů PASS**: nově také odmítnutý pokus klienta nastavit identitu/sport/lockedAt, nekanonické a přetékající verze, prázdný filtr a stránka za koncem se správnými counts, auditní rollback vytvoření i úpravy bez dalšího řádku nebo auditu. Původní oprávnění, souběh a historické kontroly zůstaly zelené. |
| Frontend Node 24.14.0 | **94/94 testů PASS** přes `npm test -- --maxWorkers=1`, bez unhandled errors; nově opakovaný submit vytváří pouze jeden požadavek/jeden CSRF fetch a po validační chybě funguje další submit. Lint, typecheck a nový produkční build PASS. |
| Skutečný browser | Znovu **6/6 PASS, žádný skip**: desktop 3/3 a Pixel 7 3/3, každý nad novou izolovanou PostgreSQL 16 DB s V1–V5, skutečným novým JAR a novým Next production standalone buildem. |
| Obsah a konfigurace | Veřejný obsah, lokální Markdown souborové odkazy v 15 dokumentech, základní/dev/produkční Compose konfigurace a `git diff --check` znovu PASS. LICENSE, NOTICE a V1–V4 shodné s HEAD; V5 zdroj a použitý build mají shodný SHA-256. |

První souběžný frontendový běh skončil timeoutem startu tří Vitest workerů při nízké volné paměti; jeho dílčí výsledky se nepočítají jako úspěšné ověření. Celá sada byla následně zopakovaná s jedním workerem a prošla výše uvedenými 94 testy. Produkční konfigurace ani testy kvůli tomu nebyly oslabeny. Lokální logy druhé kontroly jsou ignorované `.verification/p4-review-*`; browserové logy/snímky se obnovily v `.verification/p4-browser-*` a `.verification/p4-*.png`. Nové desktopové i mobilní snímky byly vizuálně zkontrolované a test opět ověřil absenci horizontálního přetékání. Testovací procesy byly ukončené a oba zahoditelné DB kontejnery odstraněné; běžná klubová DB ani projektové volumes nebyly použity. Commit/push, GitHub CI a produkční nasazení nebyly provedeny.

## 6. 11. 2026 — první část P4, izolované lokální ověření

Navázáno na rozpracované změny ve větvi `feat/p4-match-formats` nad `0573a6c`. Uživatel výslovně povolil izolované ověření, čímž pro tyto běhy nahradil omezení ze 4. 11. Běžná klubová DB ani projektové volumes nebyly použity. Rozsah: ADMIN formáty zápasu přes backend a skutečné UI, Flyway V5; dílčí R12/R13/R16 a T01/T03/T04/T07/T08/T10/T11. P4 jako celek ani uvedené požadavky nejsou plně akceptované.

| Kontrola | Výsledek a důkaz |
| --- | --- |
| Backend Java 17, Maven Wrapper `verify` | **PASS: 67 testů, 0 failures, 0 errors, 0 skipped**, včetně balení spustitelného JAR. Java 17.0.21 z lokálních ignorovaných nástrojů, globální Java se neměnila. |
| P4 backend | **7 HTTP + 5 service + 1 migrační test PASS**: oba sporty, stránkování a filtr/count, neplatné kombinace, velké řetězcové verze, konflikt souběžných editorů, auditní rollback, historický guard, ADMIN/USER/PENDING/aktivní CHILD/CSRF a odvolaná ADMIN session. |
| Migrace | Čisté Testcontainers DB aplikovaly V1–V5. Samostatný upgrade V4 → V5 zachoval všechny řádky původních tabulek a jejich migrační checksumy, osoby/UUID/BCrypt hash/setup/hráče/rodičovskou vazbu. Druhý migrate neprovedl žádnou migraci. V1–V4 shodné s HEAD; schéma po V5 má 13 tabulek. Běžná uživatelova DB nebyla kontrolována ani migrována. |
| Frontend Node 24.14.0 | **93/93 testů PASS**, lint, typecheck a produkční build PASS. Ověřen REST+CSRF, přesná version, zachování rozepsaných hodnot, explicitní reload, použitá konfigurace, USER/CHILD brána a přístupná chyba pole sportu. Build použil testovací API URL `http://localhost:18080/api`. |
| Browser desktop + mobil | **6/6 PASS, žádný skip**: na každé ze dvou nových izolovaných PostgreSQL 16 DB auth/session/výpadek a celý P4 průchod přes skutečný JAR + Next production standalone. UI vytvořilo sporty i oba formáty, filtr omezil seznam i count, dvě karty vyvolaly skutečný optimistic konflikt a explicitní reload zachoval/odstranil úpravy podle pokynu. |
| Vizuální kontrola a úklid | Prohlédnuty desktopový i Pixel 7 snímek formulářů; browserový test ověřil absenci horizontálního přetékání. Testovací backend/frontend byly ukončeny a oba zahoditelné DB kontejnery odstraněny bez projektových volumes. Logy/snímky zůstávají ignorované v `.verification/`. |
| Veřejný obsah a dokumentace | Kontrola veřejného obsahu PASS; lokální souborové Markdown odkazy v 15 dokumentech PASS; základní/dev/produkční Compose konfigurace PASS (produkční soubor jako override základního); `git diff --check` PASS. |

První pokusy odhalily chybějící povinná pole v mockovaných JSON chybách a chyby testovacího setupu pro hráče/audit; po opravě jsou výše uvedené finální běhy zelené. Docker Desktop 29.1.3 vyžadoval pro stávající Testcontainers klient explicitní API 1.44 pouze v testovacím shellu (`JAVA_TOOL_OPTIONS=-Dapi.version=1.44`); první pokus bez něj selhal ještě před integračními scénáři. Závislosti byly instalované přes `npm ci`; žádné runtime závislosti se neměnily. Lokální logy a nástroje jsou pouze v ignorované `.verification/`.

Reprodukce browserového průchodu z `frontend/`: nad novou izolovanou DB a sestavou s odpovídajícími `E2E_BASE_URL`, `E2E_API_URL`, soukromým backendovým `CLUB_SETUP_TOKEN` a shodným `E2E_SETUP_TOKEN` spusťte `E2E_SETUP_PROJECT=desktop npm run test:e2e -- auth.spec.ts match-formats.spec.ts --project=desktop --workers=1`. Pro mobil použijte další čistou DB, restart backendu a `mobile` v obou volbách. Každá DB dovolí právě jeden setup scénář; nekombinujte jej nad stejnou DB s `club-setup.spec.ts`, `roster.spec.ts` nebo `guardians.spec.ts`. Lokální běh použil náhodné DB porty a testovací porty API/frontend 18080/13000; připravenost DB se čekala přes TCP `pg_isready` před startem JAR.

Soutěže, ročníky, přihlášky, soutěžní body/kontumace, pořadí, výsledky a automatické uzamčení při prvním výsledku zůstávají navazující prací. Neproběhlo produkční nasazení, HTTPS/ACME, provozní záloha/obnova, nové celé Docker image buildy ani GitHub CI tohoto necommitovaného stavu. Tento záznam nenahrazuje jejich budoucí akceptaci.

## 4. 11. 2026 — rozpracovaná první P4, bez spuštění

Před zahájením práce byl po fetch ověřen čistý main `0573a6c`, shodný s origin/main i upstream/main. Práce vzniká pouze v samostatném sport-club-system ve větvi `feat/p4-match-formats`.

Připravené zdrojové soubory: formáty zápasu service/repository/REST/UI, validační a historická pravidla, neaplikovaný návrh V5. Migrační návaznost na skutečnou uživatelovu DB je otevřená do vyjasnění jejího fyzického stavu. Připravené testy zahrnují konfiguraci dvou sportů, neplatné kombinace, přesné verze, souběžné editory, uzamčená pravidla, auditní rollback, HTTP ADMIN/USER/PENDING/CSRF a UI konflikty/CHILD bránu. **Žádný z těchto testů nebyl spuštěn.**

Na výslovný pokyn uživatele po zákazu nebyl spuštěn Docker/container, databáze, aplikace, Maven/npm kontroly, testy, build ani validační skripty. Před tímto pokynem proběhl pouze dotaz na verzi již běžícího Docker daemonu a příprava lokálních nástrojů mimo repozitář, bez startu sestavy. Neproběhla DB migrace ani browserová akceptace. Tato sekce není úspěšným ověřením P4; historické výsledky níže platí pouze pro tehdejší dodávky.

Historické ověření E0 z 5. 10. 2026 nad novým samostatným `sport-club-system`, odvozeným ze šablony commit 32dded5e33b83f038655199eb81044fa3d4c66a8. Výchozí stav a chyby před úpravami jsou v [baseline](baseline.md). Aktuální ověření P1 z 27. 10. 2026 je na konci dokumentu; starší výsledky nejsou automaticky důkazem nové verze.

## Provedené kontroly E0 — 5. 10. 2026

| Kontrola | Výsledek a důkaz |
| --- | --- |
| Backend Java 17 / Maven Wrapper verify | **PASS: 9 testů, 0 failures, 0 errors, 0 skipped**; unit testy UserService/InfoService a HTTP integrační testy nad čerstvým PostgreSQL 16 Testcontainerem. |
| Migrace a schéma | Flyway V1 vytvoří users, Hibernate validate projde; ověřeno v integračním testu i v čistém volume celé Compose sestavy. |
| Autentizace a role | Registrace vždy USER i při zaslaném role=ADMIN; BCrypt hash bez hesla v odpovědi; login/me/nový HTTP klient s původní cookie; bez HTTP Basic. |
| CSRF a session | Mutace bez tokenu odmítnuty; login změní session ID a zruší starý token; logout invaliduje session a cookie na Path=/api; stará cookie neumožní me. |
| Validace BCrypt | Unicode heslo překračující 72 UTF-8 bajtů vrací 400 VALIDATION_ERROR a nevytvoří účet; frontend limit také kontroluje. |
| Security a CORS | JSON 401/403 i ve filtrech včetně odmítnutého CORS preflight a GET; skutečný context-path /api; localhost:3000 s credentials a X-CSRF-TOKEN povolen, cizí origin odmítnut. |
| Info, health a OpenAPI | Všechny info endpointy veřejně dostupné; minimální health jen status, metrics chráněné; springdoc 3.0.3 runtime; classpath VERSION=0.1.0 v JAR. |
| Frontend | npm ci, lint, typecheck, build **PASS**; **16/16 unit a integračních testů PASS** (API/CSRF, AuthProvider, formulář a dashboard, chyby a opožděné odpovědi). |
| Docker obrazy a celá sestava | Backend i Next.js standalone sestavené; DB/backend/frontend běží **healthy**. Runtime /api/info/version vrací 0.1.0 bez souboru VERSION vedle JAR. |
| Browser HTTP | Playwright Chromium: **4/4 PASS**, desktop a Pixel 7; skutečné API, registrace, chybné i správné přihlášení, session refresh, logout a výpadek API/retry. |
| Browser lokální HTTPS | Izolovaný Caddy s interním certifikátem: **4/4 PASS**, stejný tok na desktopu/mobilu, cookie HttpOnly/Secure/SameSite=Lax/Path=/api. curl s explicitní testovací CA ověřil certifikát a hostname. Browser pro lokální interní CA použil ignoreHTTPSErrors; systémová důvěra certifikátů se neměnila. |
| Vizuální/responzivní kontrola | Zkontrolované desktopové a mobilní screenshoty úvodu; dashboard E2E ověřuje žádný horizontální overflow. |
| Compose a CI syntax | Všechny tři verzované Compose konfigurace validní; YAML a actionlint 1.7.12 kontrola workflow **PASS**. |
| Veřejný obsah a původ | Vyloučeny skutečné source .env, .git historie, build outputs a keys. scripts/check_public_content.py ověřuje verzované/neignorované soubory a známé credential patterny bez výpisu hodnot. LICENSE shodná byte-for-byte, kontrolní hashe všech sledovaných souborů původního místního template shodné. |

Testcontainers na macOS použil Docker socket `unix://$HOME/.docker/run/docker.sock`; Docker 28.5.1, Compose 2.40.2. Node 24.14.0 je shodný lokálně, v CI i Dockerfile. Není použit žádný produkční default účet.

## Browserové testy zopakovat

Po `docker compose up --build -d --wait` z kořene:

```sh
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e
```

Testy vytvářejí jedinečné smyšlené účty; spouštějte je nad vývojovým prostředím. Výstupy jsou ignorované v `.verification/e2e`. E2E test config dovoluje změnit E2E_BASE_URL a E2E_API_URL pro vlastní testovací nasazení. Ověření skutečného veřejného certifikátu nepoužívá ignoreHTTPSErrors.

## Známé limity a neprovedené kontroly

- **Produkční npm audit: 0 známých zranitelností. Celý audit: 5 high položek v dev řetězci Next ESLint → braces**, jedna neopravená chyba se závislými balíčky. Advisory a řetězec jsou v [frontend README](../frontend/README.md). Není to čistý audit všech závislostí; prod standalone tento řetězec neobsahuje.
- **GitHub CI spuštěno při publikaci** do [Dasky02/sport-club-system](https://github.com/Dasky02/sport-club-system); aktuální stav jednotlivých commitů je v [Actions](https://github.com/Dasky02/sport-club-system/actions/workflows/ci.yml). Místní kontroly odpovídající CI a syntaktický actionlint prošly. **Release a GHCR dosud nespouštěny**: publikování zdrojového repozitáře nevydalo release ani nenahrálo images.
- **Veřejné produkční HTTPS/ACME nasazení s vlastní doménou neproběhlo.** HTTPS je konfigurované a lokálně ověřené, nikoli živě veřejně nasazené. Produkční DNS/porty/certifikát a cookies tým ověří dle [deployment](deployment.md).
- **Záloha/obnova nebyla provozně provedena.** Postup je dokumentovaný, finální akceptace musí ověřit obnovitelnost skutečné zálohy.
- **P1–P3 dodávají identitu, týmový a rodičovský základ, podrobné datované výsledky jsou níže.** Události/účast, soutěže, statistiky, finance, notifikace, messaging a výsledková historie stále vyžadují navazující implementaci. Samotné založení sportů není akceptace jejich výsledkových scénářů. Přehled v [requirements](requirements.md) a [roadmap](roadmap.md).
- Statický credential check je kontrola známých patternů a souborů, ne záruka neexistence libovolné tajné hodnoty. Před veřejným publikováním byl finální Git obsah zkontrolovaný; žádné skutečné env hodnoty se do repozitáře nepřevzaly. Kontrola se opakuje v CI.

Nový [veřejný repozitář](https://github.com/Dasky02/sport-club-system) byl vytvořen a nahrán po kontrole obsahu na výslovnou žádost uživatele. GitHub uvádí PUBLIC, isFork=false a výchozí větev main; historie začíná vlastním root commitem. Původní privátní template nebyl cílem žádné write operace.

## Kontrola přípravy implementace — 27. 10. 2026

Nad výchozím `main` `c0b52d6` byl doplněn [pracovní plán](implementation-plan.md), [plánovaný API kontrakt](api-contract.md) a [pokyny pro navazující práci](../AGENTS.md). Doménové SQL/API/UI se touto změnou neimplementují. Předchozí výsledky z 5. 10. nejsou novým testem této přípravy.

- **PASS:** kontrola veřejného obsahu nad 108 verzovanými/neignorovanými soubory, kontrola lokálních Markdown odkazů, čtyř JSON příkladů API a whitespace v upravené dokumentaci.
- **PASS:** porovnání migračního plánu s DBML pokrývá všech 38 tabulek právě jednou. Prověřeny závislosti fee tabulek před notifikacemi a dodatečného vytvoření cyklického FK aktivní výsledkové revize. Jde o kontrolu návrhu, nikoli provedenou migraci.
- **Ověřeno prostředí:** dostupná Java 17.0.15 a Maven Wrapper 3.9.12; výchozí shell má Java 25.0.2, proto je nutná volba JDK 17 pro projekt. Node 24.14.0, npm 11.15.0, Python 3.12.5, běžící Docker daemon 28.5.1. PostgreSQL image nebyla v lokální cache; stav běžící aplikace ani vývojové DB tato kontrola nepotvrzuje.
- **Neprovedeno v této změně:** nové backend/frontend testy, browserové průchody, audit závislostí, produkční nasazení a restore. Změněna je pouze dokumentace; před prvním implementačním balíčkem se obnoví technický baseline podle pracovního plánu.

## Implementace a ověření P1 — 27. 10. 2026

Lokální větev `feat/identity-and-club-setup`, výchozí `main` `c0b52d6`. Dodané skutečné funkce: V2 identity/klub/audit/sporty, vlastní onboarding, USER/ADMIN a STANDARD/CHILD datový základ, aktuální serverový kontext, jednorázový setup a administrátorská správa sportů. CHILD je do doplnění hráčských a rodičovských vazeb zablokovaný; P1 ještě nedodává P2/P3 ani celou E1.

| Kontrola | Skutečný výsledek |
| --- | --- |
| Výchozí baseline před implementací | Maven verify **9/9 PASS**, frontend lint/typecheck a **16/16 testů PASS**. |
| Finální backend | Java 17 / `./mvnw -B -ntp verify`: **25 testů PASS**, 0 failures, 0 errors, 0 skipped. Šest unit testů a 19 integračních/upgrade scénářů. Výstupní JAR sestaven. |
| Upgrade V1 → V2 | Skutečná V1 fixture se všemi pěti původními rolemi: zachována všechna UUID, hashe a časy. COACH/PLAYER/PARENT přejdou na USER bez domyšlených osob/vztahů. Existující nejstarší ADMIN zůstane a uzavře setup s auditem; bez ADMIN migrace nikoho nepovýší. V1 soubor se nezměnil. |
| Autorizace a souběh | PENDING omezený na vlastní onboarding/kontext; role a enabled aktuální i se starou session. CHILD/deaktivovaný/smazaný účet odmítnut. Dva setupy mají právě jednoho vítěze, souběžný onboarding nevytvoří osiřelou osobu. Service po čekání na klubový zámek znovu odmítne odebraného ADMIN. Requestové security contexty nesdílejí mutovanou session instanci. |
| Setup, validace a sporty | Chybný token nic nezmění; prázdná konfigurace vrátí nedostupný setup; opakování po úspěchu odmítnuto. Strukturované chyby bez vstupních tajných hodnot. Fotbal a basketbal vytvořeny přes stejné API, stránkování, stabilní kód, přejmenování a deaktivace; běžný účet nemá přístup. Chybějící CSRF odmítnut, nepovolený DELETE vrátí 405 bez smazání. |
| Finální frontend | **47/47 testů PASS**, lint/typecheck/build **PASS**. Testy zahrnují skutečný API transport s HTTP mockem, osobu, setup, nezapisování tokenu do storage, obnovu role/kontextu, auth race, chyby a sportovní formuláře. Partial PATCH mění pouze požadované pole; deaktivace ze staré karty nepřepíše cizí přejmenování. |
| Browser desktop | Chromium proti skutečnému JAR a produkčnímu Next standalone, PostgreSQL 16: **3/3 PASS**. Auth/session/logout, odlišení výpadku, celý P1 průchod včetně chybného/opakovaného setupu a přejmenování/deaktivace. |
| Browser mobil | Pixel 7, nezávislá čistá testovací DB se znovu provedenou V1/V2: **3/3 PASS**, stejné scénáře. Celkem desktop+mobil **6/6**, žádný přeskočený scénář. |
| Vizuální kontrola | Prohlédnuty desktopový a mobilní screenshot správy sportů; čitelné formuláře/karty a ověřená absence horizontálního přetékání. |
| Konfigurace a obsah | Všechny tři Compose konfigurace validní s `.env.example`; kontrola veřejného obsahu a `git diff --check` prošly. Token je jen soukromý backend env, nemá výchozí hodnotu a neposílá se do frontend buildu. |

Browserové ověření použilo pouze vytvořenou zahoditelnou DB na portu 15432, backend 18080 a frontend 13000; běžná vývojová data nebyla resetována. Frontend byl sestaven s testovací veřejnou URL `http://localhost:18080/api`. Pro obvyklý vývoj/build použijte vlastní `NEXT_PUBLIC_API_URL` podle README. Testovací přihlašovací údaje nebyly produkční a nejsou seedem aplikace. Lokální logy/screenshoty jsou ignorované v `.verification/`.

Reprodukce P1 browserového scénáře: nad **samostatnou novou testovací DB** nakonfigurujte backendový `CLUB_SETUP_TOKEN`, spusťte odpovídající frontend/backend a nastavte tutéž hodnotu jako soukromý `E2E_SETUP_TOKEN` jen v shellu testovacího procesu. Dále:

```sh
cd frontend
E2E_SETUP_PROJECT=desktop npm run test:e2e -- auth.spec.ts club-setup.spec.ts --project=desktop
```

Mobilní průchod spusťte až nad další čistou izolovanou DB/restartovaným backendem:

```sh
E2E_SETUP_PROJECT=mobile npm run test:e2e -- auth.spec.ts club-setup.spec.ts --project=mobile
```

Pro jiné porty přidejte `E2E_BASE_URL` a `E2E_API_URL`. Token nikdy nepatří do `NEXT_PUBLIC_*`, URL ani sdíleného příkazu s jeho hodnotou. Bez explicitního testovacího tokenu je jednorázový P1 scénář přeskočen; obyčejné auth scénáře běží dál. Tyto testy se nepouštějí nad živým klubem a běžná data se kvůli nim nemažou.

**Neprovedeno pro P1:** nové sestavení celých Docker images, nové HTTPS/ACME ověření, veřejné nasazení, provozní restore, nový audit závislostí a GitHub CI této lokální větve. Produkční runtime byl ověřen jako sestavený JAR + Next standalone, Compose jako konfigurace. Staré HTTPS ověření E0 zůstává historickým výsledkem. Žádný plánovaný týmový, finanční nebo sportovní výsledkový scénář se tímto neoznačuje za hotový.


## Publikace P1 a ověření P2 — 27. 10. 2026

P1 commit `192dd6a` je publikovaný v [PR #23](https://github.com/Dasky02/sport-club-system/pull/23), základ `main` `c0b52d6`. GitHub CI tohoto PR má tři úspěšné joby: Backend verify, Frontend checks, Public content and Compose ([běh](https://github.com/Dasky02/sport-club-system/actions/runs/40494184203)). PR nebyl automaticky sloučen. P2 je na samostatné větvi `feat/teams-and-memberships` nad P1, bez změny zdrojového template.

P2 dodává V3 a skutečné sezóny, vlastní i externí týmy, sezónní názvy, ADMIN osoby/offline hráče, intervalová hráčská a trenérská členství, self-coach a scoped soupisky/historii. Pokrývá týmový rozsah R01, část R02/R12/R16, T01–T04/T07–T11 a U06/U08/U11. Neuzavírá celou E1. `guardian_links` je jen DB příprava P3; CHILD je stále blokovaný, rodičovské a finanční schopnosti nejsou dostupné.

| Kontrola | Skutečný výsledek |
| --- | --- |
| Backend Java 17 / Maven verify | **40/40 PASS**, 0 failures/errors/skipped. Včetně všech P1 regresí, 8 P2 HTTP/PostgreSQL scénářů, 6 unit scénářů nové služby a samostatného V2→V3 migračního testu. JAR sestaven; po textové aktualizaci info resources znovu package se skipTests, obsah resources porovnán byte-for-byte. |
| Upgrade a integrita | V2→V3 zachová UUID/osoby/BCrypt/admin setup/audit/sporty, nevytvoří smyšlené hráče. Skutečné PostgreSQL 16 provede V1–V3 a btree_gist, celkem 12 tabulek. Překryv stejné dvojice odmítá EXCLUDE (23P01), sousední intervaly i souběh v jiném týmu projdou. V1/V2 zůstávají beze změny. |
| Oprávnění a souběh | Negativní scoped detail/list/count, minimální DTO i pro ADMIN, konec a budoucí začátek vztahu, revokace při staré session, archivovaný read/write, souběžné členství a atomický rollback osoby/hráče. Jednotný datumový snapshot rosteru/historie ověřen dvěma testy přes pražskou půlnoc. |
| Frontend | **64/64 PASS**, lint/typecheck/build PASS. P2 přidává 17 transportních/komponentových/unit scénářů: přístupové brány, sezóna/archiv, atomický hráč, výslovná existující osoba, konflikt bez retry/ztráty formuláře, minimální roster, intervaly, pozdní odpovědi a načtení výběrů přes první stovku. |
| Browser desktop | Chromium, skutečný JAR + Next production standalone + izolovaný PostgreSQL 16: **3/3 PASS**, 0 skipped. Dva původní auth/session/výpadek scénáře a nový úplný P1/P2 průchod. |
| Browser mobil | Pixel 7 nad další čistou testovací DB se znovu aplikovanými V1–V3: **3/3 PASS**, 0 skipped. Celkem desktop+mobil **6/6 PASS**. |
| P2 průchod | Registrace → osoba → jednorázový setup → fotbal+basketbal → sezóna a oba týmy → dva hráči bez účtů/rodičů → jediný ADMIN trenérem obou → přejmenování základního týmu zachová sezónní název → ukončení/přestup/historie → historická soupiska → archiv blokuje mutaci a ponechá read. JSON rosteru ani základního player detailu nemá kontakty/DOB. |
| Vizuální kontrola | Prohlédnuty desktopový a Pixel 7 screenshot soupisky. Čitelné karty a ovládání, testovaná absence horizontálního přetékání. |
| Konfigurace a obsah | Všechny tři Compose konfigurace PASS. Veřejný obsah PASS; lokální Markdown odkazy, 5 JSON příkladů, 38 cílových/12 migrovaných tabulek a git diff --check PASS. Byte-for-byte V1/V2 zachované. |

Běhy používaly pouze vlastní zahoditelný container/DB bez projektu volumes: DB 15432, API 18080, standalone 13000. Ostatní data se neresetovala. Výstupy jsou ignorované v `.verification/`; přihlašovací údaje jsou smyšlené, žádný produkční seed. Pro reprodukci nastavte soukromý backendový CLUB_SETUP_TOKEN a stejný E2E_SETUP_TOKEN jen pro testovací proces a vyberte jeden setup scénář:

```sh
# frontend/; samostatná čistá testovací DB pro každý projekt
E2E_SETUP_PROJECT=desktop npm run test:e2e -- auth.spec.ts roster.spec.ts --project=desktop
# po přípravě druhé čisté izolované DB a restartu backendu
E2E_SETUP_PROJECT=mobile npm run test:e2e -- auth.spec.ts roster.spec.ts --project=mobile
```

Pro jiné porty nastavte E2E_BASE_URL/E2E_API_URL; frontend build musí mít odpovídající NEXT_PUBLIC_API_URL. Token nikdy do veřejné proměnné/URL nebo příkazu s jeho skutečnou hodnotou. `roster.spec.ts` a `club-setup.spec.ts` nepouštějte se setup tokenem nad stejnou DB současně: oba vyžadují nedokončený klub. Bez tokenu se tyto opt-in scénáře přeskočí; obyčejné auth testy běží dál.

**Neprovedeno pro P2:** GitHub CI nové P2 větve, nové Docker image buildy, nové HTTPS/ACME ověření, veřejné nasazení, provozní restore a nový audit závislostí. P1 CI výsledek není CI důkaz P2. Historie výsledků/statistik, rodičovský průchod a CHILD akceptace čekají na navazující implementaci; samotný DB základ tyto požadavky nesplňuje.


## Dokončení P3 a sloučení P1 — 28. 10. 2026

Na výslovnou žádost uživatele byl [PR #23](https://github.com/Dasky02/sport-club-system/pull/23) sloučen do main merge commitem `d7add37`. Jeho tři CI kontroly byly úspěšné a následný [běh nad main](https://github.com/Dasky02/sport-club-system/actions/runs/40588664240) také skončil úspěšně. Navazující větev `feat/guardians-and-child-accounts` zachovává P2 `cf4f6a7` a doplňuje P3; nové předání proto zahrnuje P2/P3 nad sloučeným P1.

P3 skutečně dodává správu účtů na ověřených osobách, person-link neúplného účtu, role/typ/přístup/aktivaci s odůvodněním, guardian grant/revoke, Moje děti a omezené CHILD přihlášení se sportovním čtením. Rozšiřuje R02, T10, U06/U12 a přístupové A04/A16/A20/A23–A27 v rozsahu existujících funkcí. Příspěvky, notifikace, události, výsledky a komunikace stále nejsou implementované; jejich deny testy pro dítě nejsou funkční akceptací těchto domén.

| Kontrola | Skutečný výsledek |
| --- | --- |
| Backend Java 17, Maven verify | **54/54 PASS**, 0 failures/errors/skipped. Oproti P2 devět nových skutečných HTTP/PostgreSQL scénářů, čtyři unit testy child policy a bezpečné obnovy vlastní session a jeden upgrade V3→V4. Zachované P1/P2 regrese. Po aktualizaci textových resources znovu package se skipTests; všech devět resources v JAR porovnáno byte-for-byte. |
| Migrace V3→V4 | Zachována UUID, osoby, BCrypt, setup, sporty, tým a členství. Unsupported legacy CHILD je zakázán bez vymyšlené osoby. V4 přidává auth_revision/guardy, stále 12 tabulek. Ověřena trvalá změna revize po odvolání a reaktivaci, regrant/povolení rodiče dítě samo nezapnou; self guardian a přepojení identity DB odmítá. V1/V2/V3 beze změny. |
| Oprávnění a transakce | Dva rodiče/dvě děti, scoped seznamy i counts a cizí detail, parent history vs current team. CHILD bez profilu/aktivního rodiče nelze aktivovat; žádný ADMIN/coach/guardian a žádné samoobslužné změny osoby. Poslední enabled STANDARD COMPLETE ADMIN chráněn v souběhu. Ověřen rollback při selhání auditu. |
| Sessions a finanční hranice | Nepoužitá stará cookie po disable→re-enable/aktivaci zůstává odvolaná. Aktuální revision se kontroluje i po čekání na zámek. Vlastní onboarding/setup přijmou jen očekávanou revizi +1. Child allowlist odmítá přímé fee/payment/export/notifikační cesty včetně count/read; finanční služby ještě neexistují. |
| Frontend | **85/85 PASS**, lint/typecheck a produkční build PASS s testovací API URL 18080. Reálné clients/formuláře, reason a potvrzení změn, vymazání prvního hesla ihned při odeslání i chybě, ochrana citlivých requests, vlastní CHILD read, parent profil/history, pozdní session/zdrojové odpovědi. Žádné nové runtime závislosti. |
| Browser P3 desktop | Chromium + skutečný JAR/Next production standalone/PostgreSQL16: **3/3 PASS**, žádný skip. Auth/session/výpadek a úplný P1/P3 průchod. |
| Browser P3 mobil | Pixel7, druhá čistá izolovaná DB se znovu provedenými V1–V4: **3/3 PASS**, žádný skip. Celkem P3 **6/6**. |
| Browser P2 regrese | Po finálním buildu další čistá DB, desktop auth + celý původní P2 průchod obou sportů, offline hráčů, trenéra, přestupu, historie a archivu: **3/3 PASS**. Celkem v dokončení P3 **9/9 browserových scénářů**. |
| Vizuální a návrhová kontrola | Prohlédnuty desktopové i mobilní Moje děti a CHILD soupiska, absence horizontálního přetékání ověřena. Aktualizované DBML parse/export PASS: 38 cílových tabulek a 76 vztahů; ERD obnoven lokálně a users/auth_revision vizuálně zkontrolován. |
| Konfigurace a veřejný obsah | Všechny tři Compose konfigurace PASS, kontrola veřejného obsahu a git diff --check PASS. Lokální Markdown odkazy a JSON příklady validní; V1–V3 byte-for-byte shodné s P2. |

P3 browserový průchod vytvoří dva rodiče a dva hráče se samostatnými účty přes skutečné formuláře, odmítne předčasnou CHILD aktivaci, schválí tři vazby, aktivuje účty a ověří oddělené parent/child scopes i přímé zákazy. Odvolání jedné ze dvou vazeb přístup dítěte zachová; poslední účet zakáže. Nové schválení vyžaduje ruční aktivaci, původní cookie nepoužitá během zákazu se neobnoví. Zákaz posledního rodiče zakáže oba potomky; jeho nové povolení děti neaktivuje. Ověřena ochrana posledního ADMIN.

Testy čekají na dokončení vlastní onboarding/setup POST odpovědi před ověřením session, stejně jako skutečné UI. Souběžný starý request v jiné kartě během bezpečnostní změny může skončit 401; session se neobnovuje převzetím pozdější správní revize. Původní P1 očekávání souběžného onboardingu nyní připouští bezpečné 401 i konflikt, stále vznikne právě jedna osoba.

Reprodukce: nad vlastní novou testovací DB nastavte soukromý backend CLUB_SETUP_TOKEN a shodný E2E_SETUP_TOKEN pouze pro testovací proces. Vyberte právě jeden setup scénář nad každou DB:

```sh
# frontend/; odpovídající E2E_BASE_URL/E2E_API_URL a frontend build
E2E_SETUP_PROJECT=desktop npm run test:e2e -- auth.spec.ts guardians.spec.ts --project=desktop
# druhá nová izolovaná DB a restart backendu
E2E_SETUP_PROJECT=mobile npm run test:e2e -- auth.spec.ts guardians.spec.ts --project=mobile
```

`club-setup.spec.ts`, `roster.spec.ts` a `guardians.spec.ts` každý přebírá první správu klubu. Nepouštějte je se setup tokenem zároveň nad stejnou DB; zvolte jeden. Regresi rosteru spusťte nad další čistou testovací DB. Běžná klubová data se kvůli testům nemažou. Lokální běhy používaly výhradně vlastní zahoditelný container bez projektových volumes (15432/18080/13000); soukromá konfigurace není verzovaná a po ověření je odstraněna.

**Neprovedeno pro P3:** nové celé Docker image buildy, nové HTTPS/ACME nasazení, provozní restore nebo nový audit závislostí. Ověřen byl JAR + produkční Next standalone; Compose jako konfigurace. Tento lokální záznam vzniká před publikací PR; aktuální GitHub CI posuzujte podle konkrétního head commitu v PR/Actions. Budoucí domény P4–P9 nejsou tímto dokončené.
