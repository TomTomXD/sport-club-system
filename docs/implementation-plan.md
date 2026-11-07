# Příprava úplné implementace

Historický podklad: příprava nad main c0b52d6 vycházela z E0 a V1 users. **P1–P3 dodávají členský a rodičovský základ, P4 soutěžní konfiguraci.** V1–V4 mají 12 tabulek, V5/V6 celkem 19: formáty, soutěže, ročníky, bodování, obě kontumace, kritéria pořadí/H2H a přihlášky. Dalších 19 tabulek a P5–P9 zůstává plánovaných. Úplná akceptace E1 ani projekt nejsou uzavřené. Tento dokument není záznam úspěšných testů; výsledky eviduje pouze [ověření](verification.md).

P3 poskytuje správu účtů na ověřené osoby, person-link, odůvodněné změny role/typu/přístupu a CHILD aktivaci, guardian grant/revoke a Moje děti. PENDING má jen povolené auth/onboarding/context operace. CHILD má vlastní osobu ke čtení a povolené vlastní sportovní čtení; finanční/notifikační cesty odmítá předběžný allowlist. ADMIN má osm skutečných správních capabilities; READ_TEAM plyne z dnešních vlastních/trenérských/dětských vazeb, READ_CHILD jen ze schváleného vztahu. Všechny zbývající R/T/U zůstávají povinné.

## P4 — úplná soutěžní konfigurace

Ve větvi `feat/p4-match-formats` je implementovaný celý rozsah P4: ADMIN formáty, soutěže, ročníky a přihlášky klubových i externích týmů, úplné bodování včetně rozsahů a povolených ukončení, obě soutěžní kontumace, pořadí kritérií a H2H politika. Skutečné REST formuláře navazují na R12/R13/R14/R16 a T01/T03/T04/T07/T08/T10/T11. P5/P6 dodají události, výsledky, automatické uzamčení první částí/výsledkem a výpočty; nepatří do P4.

`V5__match_formats.sql` realizuje formáty, `V6__competition_configuration.sql` dalších šest soutěžních tabulek z DBML: celkem 19, dalších 19 zbývá. V1–V5 se při V6 nemění. Běžná uživatelova DB nebyla kontrolována ani měněna; ověření používá izolované testovací DB. Historický návrh jednoho V5 pro celý P4 se nepoužije; V5/V6 jsou již obsazené a další číslo se potvrdí až při skutečné dodávce.

Uživatel 6. 11. 2026 povolil izolované ověření. Konkrétní výsledky a omezení zaznamenává pouze [ověření](verification.md); samotná implementace ani existence testu nejsou akceptací celého P4.

## Podklady a jejich účel

- [Požadavky](requirements.md): úplný rozsah R01–R16, T01–T14 a U01–U12 podle původního ODT a rozhodnutí uživatele.
- [Roadmapa](roadmap.md): etapy E0–E6 a konkrétní sportovní akceptační scénáře.
- [Databáze](database.md) a [DBML](sport-club.dbml): cílová struktura, omezení a transakční pravidla; [oprávnění](authorization.md): přístupová pravidla a A01–A27.
- [Kontrakt API](api-contract.md): společná pravidla a plánované rozhraní pro backend i frontend.
- Tento plán: pořadí práce, přechod dnešních dat, závislosti, malé dodávky a důkazy dokončení. Nemění cílový rozsah na pouhé MVP.

Pracuje se výhradně v `sport-club-system`. Původní privátní `petrsafrata/Spring-project-template` zůstává beze změn; zachovává se licence, NOTICE a vhodné implementace šablony.

## Pevná rozhodnutí pro implementaci

| Oblast | Závazný postup |
| --- | --- |
| Klub a sporty | Jedna instalace pro jeden klub, současně více sportů z DB. Každý tým patří právě jednomu sportu. `is_club_team` odlišuje vlastní tým od soupeře; nyní se nezavádí víceklubová aplikace. Fotbal i basketbal ověřit od prvních týmových formulářů. |
| Identita | Osoba, účet a hráčský profil jsou oddělené identity. Profil i osoba rodiče mohou existovat bez přihlášení. Žádné automatické propojení podle jména, uživatelského jména ani libovolného klientského UUID. |
| Role | Systémově USER/ADMIN, typ STANDARD/CHILD; trenér/hráč/rodič podle aktuálních vazeb. Jediný STANDARD/ADMIN účet si může přiřadit trenérství a vést hráče bez účtů. |
| Dítě | CHILD patří vlastní osobě a hráčskému profilu pod schváleným rodičem s aktivním STANDARD účtem. Nemá žádný finanční přístup, ani vlastní částky, exporty či počty finančních oznámení. Ztráta posledního platného rodiče blokuje účet a session; neodstraňuje sportovní historii. |
| Událost | TRAINING/MATCH/OTHER, dostupnost, nominace a skutečná docházka samostatně. Účastníci se připraví **při první publikaci** ze členství platných k začátku akce. Pozdější doplnění hráče je výslovná validovaná operace. |
| Výsledky | Návrh se nepočítá. Potvrzení, oprava a anulování přepínají oficiální stav atomicky; původní revize, části, hráči i statistiky zůstávají. Konfigurace použitá v historii se nepřepisuje. |
| Statistiky a tabulky | Definice a jejich verze spravuje ADMIN; typ, meze a agregace se validují na serveru. Bodování a pořadí shody jsou data, nikoli větvení podle názvu sportu. |
| Příspěvky | Předpis na osobu, částečné skutečně přijaté platby zaznamenává ADMIN. Storno s důvodem zachová původní zápis. Bez platební brány, kreditu a rozdělování jedné platby na více předpisů. |
| Komunikace | Notifikační schránka a konverzace jsou dvě funkce. ADMIN nemá automatický přístup do soukromých konverzací. Ukončená účast se neobnovuje; další komunikace používá novou konverzaci. |

Technické výchozí volby: stránkování od 0, velikost 20 a nejvýše 100; přesné částky a velká čísla jako řetězce; časové okamžiky UTC a klubové kalendářní dny v `Europe/Prague`. Připomínky začnou předstihem 7 dní a denním během v 09:00 klubového času. Předstih, čas a zóna budou soukromou konfigurací backendu; toto nastavení dnes ještě není implementované. Prodlení začíná při `today > due_on` a kladném zůstatku. Po odstávce se doručí aktuálně relevantní dosud neposlané upozornění, nikoli záplava historických denních zpráv.

## Převod současných účtů bez ztráty identity

V1 zná pouze username, BCrypt hash, roli a časy. Neobsahuje skutečné jméno, datum narození, tým ani dítě. Migrační fixture proto musí mít všech pět starých rolí a zachovat jejich UUID, username, hashe i časové údaje.

1. **Rozšíření V2:** přidat `persons`, `users.person_id` jako dočasně nullable UNIQUE FK, `account_type` a `enabled`. Nevyplňovat jména z loginu. `legacy_role` uloží starou hodnotu pouze jako neveřejnou migrační evidenci, nikdy jako zdroj oprávnění. ADMIN zůstává ADMIN, ostatní staré klubové role se převedou na USER. Cílové nové účty jsou USER/STANDARD.
2. **Stejná dodávka aplikace:** nový autorizační základ načítá současný účet z DB. Starý session principal nesmí ponechat starou roli. Účet bez osoby dostane pouze vlastní onboarding, `/auth/me`, CSRF a logout; po migraci se session obnoví nebo zneplatní. Pouhé `isEnabled()` při loginu nestačí.
3. **Doplnění osoby:** vlastník účtu vyplní skutečné povinné osobní údaje. Pokud už klub eviduje jeho osobu, propojení vyřídí ADMIN po ověření identity. Použitý účet se nesmí svévolně přepojit na jinou osobu a dvě osoby se neslučují podle podobného jména.
4. **Dosavadní správce:** existuje-li již oprávněný ADMIN, V2 označí setup migrovaného klubu za dokončený a zapíše audit. Pro `setup_completed_by` použije nejdříve vytvořeného existujícího ADMIN, při shodě UUID. Nevytváří tím nové oprávnění. Instalační token nesmí převzít už spravovanou instalaci. Tento ADMIN si také nejprve dokončí osobu.
5. **Nová instalace bez ADMIN:** první správce vznikne až přihlášenému povolenému STANDARD účtu s dokončenou osobou po jednorázovém setupu se soukromým tokenem, pod zámkem klubu a s auditem. První veřejná registrace žádná správní práva nedostane.
6. **V3 a skutečné vazby:** ADMIN explicitně založí/ověří hráčské profily, členství a trenérská přiřazení; rodiče až v P3. Z role PLAYER se automaticky nevytváří CHILD; z COACH nelze odhadnout tým a z PARENT dítě.
7. **Implementovaná V4/P3:** přidat auth_revision a ochranné triggery bez nových tabulek; při upgradu zakázat všechny starší CHILD účty bez vymyšleného backfillu. Správce ověří vlastní hráčský profil a aktivního schváleného STANDARD rodiče před samostatnou aktivací. Ztráta posledního rodiče nebo jeho zákaz dítě zakáže a odvolá staré sessions i po reaktivaci; regrant/povolení rodiče dítě samo neaktivuje. Person-link a změny role/typu/přístupu mají důvod 1–500 znaků, nesmějí odebrat posledního povoleného dokončeného STANDARD ADMIN. Při vlastní P1 onboarding/setup operaci se zachová pouze očekávaná aktuální session, nikoli souběžně odvolaná revize.
8. **Pozdější zpřísnění:** `users.person_id NOT NULL` přidat samostatnou novou migrací až po ověřeném doplnění všech účtů. Nepřibalovat ji do prvního releasu, kde by staré neúplné účty znemožnily start. Po přechodu odstranit dočasnou migrační evidenci novou migrací. DBML popisuje konečný stav; tato dočasná výjimka je migrační postup.

Účet pro již evidovanou osobu vytváří ADMIN přímo na ověřenou osobu, která ještě účet nemá. První heslo se zadá přes HTTPS, uloží pouze BCryptem a oprávněné osobě předá soukromě mimo veřejný projekt; aplikace jej nevrací ani neloguje. Pro CHILD jej předá ověřenému rodiči. CHILD vzniká zakázaný a aktivace samostatně ověří všechny podmínky. Tato varianta je implementovaná v P3 a nevyžaduje nepřipravený systém pozvánek; pokud jej tým později zvolí, musí doplnit model hashovaných expiračních tokenů a jeho testy.

## Pořadí databázových změn

**V1–V6 jsou implementované; navazující migrační řezy níže jsou plán bez přidělených čísel a bez prázdných SQL souborů.** Číslo další migrace se přidělí při práci na konkrétní změně po V6. Aplikovaná migrace se nikdy nepřepisuje. Jeden vlastník koordinuje migrační soubory před sloučením větví.

| Návrh | Přidávané tabulky / změny | Součet tabulek |
| --- | --- | --- |
| V1 — existuje | `users`; zachovat soubor a jeho checksum | 1 |
| V2 — implementovaná identita a setup | `persons`, `club`, `audit_log`, `sports`; rozšíření a řízený převod `users` výše | 5 |
| V3 — implementovaní členové a týmy | `seasons`, `teams`, `team_seasons`, `player_profiles`, `guardian_links`, `player_team_memberships`, `coach_team_memberships` | 12 |
| V4 — implementované účty a rodiče | `users.auth_revision`, guardian/user guardy, zákaz neplatného CHILD; žádná nová tabulka | 12 |
| V5 — implementované formáty | `match_formats` | 13 |
| V6 — implementovaná soutěžní konfigurace | `competitions`, `competition_seasons`, `competition_point_rules`, `competition_forfeit_rules`, `competition_ranking_criteria`, `competition_entries` | 19 |
| Další řez P5 — události, výsledkový základ a schránka | `events`, `event_teams`, `event_participants`, `matches`, `match_result_revisions`, `match_periods`, `match_result_players`, `fee_assessments`, `fee_payments`, `notifications`, `notification_deliveries` | 30 |
| Další řez P6 — statistiky | `statistic_definitions`, `statistic_definition_versions`, `player_match_statistics` | 33 |
| Další řez P6 — úplné řešení shod | `competition_tie_decisions`, `competition_tie_decision_entries` | 35 |
| Další řez P8 — komunikace | `conversations`, `conversation_participants`, `messages` | 38 |

Důležité závislosti:

- Formáty a soutěžní ročníky musí existovat před soutěžním zápasem. Z E3 se proto malý balíček konfigurace posune **před dokončení zápasové části E2**. Tréninky a jiné události lze rozpracovat dříve; E2 se neuzavře bez MATCH scénáře.
- V migračním řezu P5 vytvořit předpisy a platby **před** notifikacemi: `notifications.fee_assessment_id` pak od počátku má skutečný FK. Jsou to pouze databázové základy; finanční service/API/UI a splatnostní scheduler vzniknou až v E4. Samotné prázdné tabulky nejsou splněním R07/R08.
- Nejprve vytvořit `matches` a `match_result_revisions`, teprve pak cyklický FK aktivní revize. Nový zápas má aktivní revizi NULL; revize vznikne a po validaci se aktivuje v transakci. Obě `supersedes_*` vazby musejí odmítnout vlastní odkaz i řetězec s cyklem.
- Společný zámek klubu se bere před kontrolou oprávnění a zápisem podle [databáze](database.md). Optimistická verze navíc brání přepsání rozepsaným formulářem; nenahrazuje transakční zámek ani historickou revizi.
- Povinné jmenné omezení, typy, indexy a FK se ověří na skutečném PostgreSQL. DBML není automatická náhrada implementace service invariantů.

Upgrade testy pokryjí čistou databázi i V1 fixture se starými účty a později minulou sezónou. Obnova znamená návrat zálohy celé kompatibilní sestavy; Flyway zde nemá automatické destruktivní down migrace.

## Pracovní balíčky a akceptace

P0 má dokumentační podklady; P1–P4 mají implementovaný rozsah níže. P5–P9 jsou **plánované**. Provedené kontroly uvádí `verification.md`, nikoli tento plán. Úplná akceptace E1/E3 ani celý projekt nejsou uzavřené. Backend a frontend balíčku se dodávají nad stejným skutečným kontraktem. Prázdný controller, mock v produkční stránce ani odkaz v menu nejsou hotová funkce.

| Balíček | Závisí na | Konkrétní společný výstup | Rozhodující důkaz |
| --- | --- | --- | --- |
| P0 — společný základ | E0 | Tento plán, API kontrakt, migrační scénář; při začátku kódu obnovit technický baseline a připravit testovací identity/čas | Zachovaný auth tok; existující testy; žádné tajné údaje ani prod seed účty |
| P1 — implementovaná identita a bezpečný setup | P0 | V2, onboarding osoby, čerstvé autorizační kontroly, audit, setup a admin správa sportů; nový účet/context DTO; obecné frontend chyby a CSRF mutace | Upgrade všech starých rolí bez ztráty účtů; A01, A17, A19, A27; dva souběžné setupy nejvýše jeden úspěch |
| P2 — implementované týmy a soupiska | P1 | V3 základ, sezóny, oba sporty, vlastní/externí týmy, hráči bez účtů, intervalová členství, vlastní trenérské přiřazení; admin a scoped UI | R01/R12/R16, T04/T08, U11; jediný ADMIN/trenér vede fotbal i basketbal bez dalších účtů; přestup zachová historii |
| P3 — implementované účty, rodič a CHILD | P2 | V4 auth_revision/guardy; správa schválených vazeb, účet na existující osobu, person-link, role/typ/přístup, omezený dětský účet, Moje děti, odvolání sessions i po reaktivaci, ochrana posledního ADMIN; finance deny politika před finančními endpointy | A04, A08, A16, A20, A23–A26; dvě děti a dva rodiče; ztráta jedné versus poslední platné vazby |
| P4 — dokončený základ soutěží | P2 | V5/V6; skutečné admin formuláře formátů, soutěží, ročníků, přihlášek, bodování, kontumací, kritérií pořadí a H2H politik | Lokální akceptace obou sportů z UI, cizího sportu/sezóny, překryvů a neúplnosti je zaznamenaná ve verification.md; výsledkové výpočty až P5/P6 |
| P5 — kalendář, účast a notifikace | P3, pro MATCH také P4 | Nová migrace po V6; TRAINING/MATCH/OTHER, publikace/změny/rušení, dostupnost, nominace, docházka, nastoupení a vlastní schránka | R03–R06/R10; A03, A06–A09, A12–A13; nominace není přítomnost; změna a doručení ve stejné transakci, opakování bez duplicity |
| P6 — výsledky, statistiky a pořadí | P4–P5 | Pozdější migrace statistik a shod; návrh/potvrzení/oprava/anulování s historií, dynamické definice a hodnoty, serverový výpočet tabulek a agregací | R13–R16, T05; A15/A18; oba sportovní fixtures, H2H a restart, historické názvy a hodnoty |
| P7 — finance a reminders | P3+P5 | Service/API/UI nad fee tabulkami plánovaného řezu P5; předpis, několik úhrad, storno, vlastní/rodičovské čtení, výzvy a audit | R07–R09; A05, A09–A10, A14, A21–A22; 1000→400→600→storno, souběh a idempotence, CHILD bez obsahu i počtů |
| P8 — komunikace | P3; integračně P5 | Pozdější migrace komunikace; seznam konverzací, aktivní účastníci, odeslání a stránkování zpráv, oprávnění při každém čtení/zápisu | R11, A11; ADMIN bez účasti odmítnut, zprávy až od připojení, zánik poslední vazby ihned zakáže přístup |
| P9 — kompletní předání | P6–P8 | E6: celá matice, skutečná příručka čtyř klubových kontextů, CHILD i solo provoz, mobil, HTTPS a obnova | Všechny R/T/U mají konkrétní důkaz; reálné TLS/cookie ověření a obnovení zálohy do odděleného prostředí |

P4 lze připravovat po P2 souběžně s P3. Po P5 mohou běžet P6, P7 a P8 souběžně, pokud mají samostatné soubory a dohodnuté kontrakty. Schéma a společnou autorizaci koordinuje jeden vlastník; neslučují se soupeřící kopie identity nebo finančních pravidel. Výsledná integrace musí znovu projít společné scénáře.

První malá dodávka je **P1: přihlášení → doplnění osoby → bezpečný ADMIN setup → založení a zobrazení sportu**. P2 již přidává první použitelnou týmovou dodávku: sezónu, dva sporty/týmy, vlastní trenérské přiřazení a hráče bez účtů. První provozní průchod přidá publikovaný trénink a docházku z P5. Ani jeden z těchto mezikroků se neoznačuje za dokončený projekt.

## Hranice implementovaných P1–P3 a navazující práce

Backend P1 zachovává `cz.jpmad.springprojecttemplate`, vrstvy api/service/repository/model, constructor injection, record DTO, UserService/BCrypt a JSON session. Centrální kontrola čte aktuální účet z DB i pro `/auth/me`; starý session snapshot neobnoví odebranou roli, enabled nebo povolení CHILD. Vlastní onboarding, setup a změny sportu používají klubový zámek a audit. P2 přidává scoped týmové dotazy a P3 rodičovské policy, revize sessions, správu účtů a omezený CHILD login.

Frontend P1 zachovává Next App Router, český shell, Axios credentials/CSRF a ochranu před opožděnou auth odpovědí. Typovaný transport a obecné chyby podporují onboarding, setup a sporty; formuláře nezaměňují libovolnou validaci za chybu přihlášení ani každý 409 za obsazené username. P2 UI přidává týmové funkce a historii, P3 account a rodičovské formuláře i Moje děti nad aktuálním `/me/context`. CHILD má jen vlastní povolené sportovní stránky; finanční funkce zatím neexistují. Backend každou operaci ověřuje samostatně.

Navržená struktura frontendu: `src/app/(club)/layout.tsx` a `src/features/{members,teams,events,competitions,statistics,finance,notifications,communication}`. Vznikají až s příslušnými funkcemi, ne jako prázdné soubory. Po odhlášení/401 se vyčistí citlivý stav; výpadek sítě se nevydává za logout. Seznamy mají načítání/prázdno/chybu, formuláře validaci a 409 se zachováním rozepsaných hodnot. Dítě vůbec nespouští finanční dotazy.

## Připravené testovací scénáře

Fixtures budou výhradně testovací, bez produkčních hesel či osobních údajů. UUID se nespoléhají na pořadí generování; čas používá injektovaný `Clock` a explicitní zónu.

| Sada | Data a ověřovaný výsledek |
| --- | --- |
| Identity a přístup | USER bez vazeb, jediný ADMIN/trenér, trenér jiného týmu, dospělý hráč, rodič dvou dětí, dítě se dvěma rodiči, CHILD a offline profil. Ověřit pozitivní oprávnění i všechny A01–A27 v etapě, kde příslušné API existuje. |
| Upgrade | V1 s pěti starými rolemi; po migraci stejné UUID/hashes, žádná domyšlená osoba/tým, zachovaný ADMIN a uzavřený setup. Souběžný onboarding/setup, později zachovaná minulá sezóna. |
| Události | Tři typy, oba sporty, koncept/publikace/zrušení, účastník z první publikace, pozdější doplnění, uzávěrka, dostupnost sourozenců nezávisle, nominovaný a nepřítomný hráč; rollback i deduplikace notifikací. |
| Fotbal | A–B 2:0, A–C 0:1, B–C 1:1 → C 4, A 3, B 1. Návrh nic nepřičte, opakování potvrzení nepřičte podruhé, oprava/anulování změní jen oficiální stav a zachová minulý. |
| Basketbal | A–B 80:70, B–C 90:80, C–A 85:75 → všichni 3 body, při konfiguraci z roadmapy pořadí C/B/A podle skóre 165/160/155. Remíza 80:80 neplatná. Běžná prohra 0:20 má 1 bod, kontumační prohra 0. |
| Shody | Tři/čtyři týmy, minitabulka, dva režimy chybějících vzájemných zápasů, restart menší podskupiny, úplná shoda bez nekonečné rekurze, ruční auditované rozhodnutí a jeho zneplatnění novým výsledkem. |
| Statistiky | INTEGER+SUM, DECIMAL+AVG, BOOLEAN+COUNT_TRUE, TEXT+NONE; chybějící hodnota není nula. Neplatný typ, meze/přesnost, TEXT+SUM, cizí sport a hráč bez nastoupení se odmítnou. Nové pojmenování nepřepíše historii. |
| Finance | 1000 CZK → platba 400 → 600 → storno 600 → nové doplacení. Duplicitní UUID nevytvoří druhou platbu; souběh nepřekročí zůstatek. Před/po splatnosti správní adresáti, změna splatnosti, odstávka, plná úhrada a dva souběžné schedulery. |
| CHILD | Přímé HTTP na vlastní/cizí předpis, platbu, součet, export, finanční notifikaci a její počet nic neprozradí. Stejnou hranici ověřit v dashboardu, obecném profilu i UI. Poslední rodič odvolaný během session → další požadavek odmítnut. |
| Konverzace | Trenér/hráč/rodič oprávněného týmu; cizí UUID a ADMIN bez účasti odmítnuty; pozdější účastník nečte starší zprávy. Odchod nebo zánik posledního vztahu zakáže další čtení/odeslání. |
| Nasazení a historie | Desktop, Pixel 7 i úzký viewport; ovládání klávesnicí, vnitřní scroll tabulky místo přetékání stránky. Reálné HTTPS, secure cookies, proxy, záloha a skutečná obnova se sportovní historií. |

Backend unit testy mají ověřovat výpočty, pravidla a hraniční hodnoty; integrační testy skutečný PostgreSQL, Flyway, autorizaci, transakce a souběh. Frontend unit testy pokryjí transport/chyby/formátování, integrační testy formuláře a providers s HTTP mockem. Playwright ověří společné průchody proti skutečnému backendu, nikoli mockovanému doménovému serveru.

## Historická technická příprava 27. 10. 2026 a příkazy

Lokální kontrola 27. 10. 2026: Java 17.0.15 je dostupná, výchozí shell ale používá Java 25.0.2; Maven Wrapper 3.9.12 pod Java 17 funguje. Node je 24.14.0, npm 11.15.0 a Python 3.12.5. Docker Desktop daemon 28.5.1 běží. Image `postgres:16-alpine` nebyla v době kontroly v lokální cache; první integrační běh ji musí stáhnout. Z toho neplyne, že běží vývojová DB nebo aplikace.

Z kořene projektu na macOS:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
cd backend
./mvnw -B -ntp verify
cd ../frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
cd ..
python3 scripts/check_public_content.py
docker compose --env-file .env.example config --quiet
```

`DOCKER_HOST` výše platí pro tento Docker Desktop na macOS; v CI/Linuxu ponechat odpovídající socket. Před změnou kódu zopakovat baseline, po změně relevantní kontroly. Browserové testy vyžadují běžící sestavu podle [ověření](verification.md), lokální/env a Compose postup je v [README](../README.md).

Při výše uvedené historické dokumentační přípravě před P1 **nebyly znovu spuštěny backendové ani frontendové testy**. Výsledky E0 i následných P1 běhů jsou odděleně datované v `verification.md`; historická příprava není novým testem. Provedena byla kontrola veřejného obsahu a kontrola prostředí. Důkazy nové implementace se doplní až po skutečném běhu; technický baseline nenahrazuje doménovou akceptaci.

## Podmínky uzavření balíčku a celého projektu

Každý balíček zaznamená ID požadavků, dodané API/UI, migrační dopad, konkrétní testy s výsledkem a známá omezení. Aktualizuje [příručku](user-guide.md), stavovou matici a OpenAPI. Pro aplikované DDL doloží upgrade a postup obnovy. Relevantní CI musí projít před merge; dokumentační příprava nevytváří falešné zelené záznamy.

Před P9 zbývá doplnit provozní doménu, odpovědnou osobu, ACME kontakt a cílové prostředí. Jde o vstupy pro skutečné nasazení, ne překážku P1. ODT orientačně uvádí 2 týdny návrhu, 5 implementace a 2 testování/nasazení; konkrétní start a kapacita týmu nejsou stanovené. Testování probíhá v každé dodávce.

Hotový projekt splní celou matici včetně obou sportů, dynamických statistik, tabulek, účasti, rodičů a CHILD, financí, notifikací, samostatných zpráv, historie a příručky všech čtyř klubových kontextů. Skutečné HTTPS nasazení a obnova musí mít uložený výsledek. Do té doby je projekt rozpracovaný, i kdyby první týmový průchod už fungoval.
