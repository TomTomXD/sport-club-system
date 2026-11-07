# Databáze sportovního klubu

Tento dokument a [DBML](sport-club.dbml) popisují **cílový model 38 tabulek pro PostgreSQL 16**. V1–V4 mají 12 tabulek, V5 přidává match_formats a V6 šest soutěžních tabulek: celkem 19, dalších 19 je plán. P1–P4 dodávají členský základ a soutěžní konfiguraci, nikoli hotovou klubovou aplikaci nebo uzavřenou úplnou akceptaci E1/E3. Jedna instalace má jeden klub a více databázových sportů; každý tým právě jeden sport. [Požadavky](requirements.md) a [ověření](verification.md) oddělují rozsah a skutečné důkazy.

## P4 — V5 a V6

V5 realizuje navržené match_formats, V6 competitions, competition_seasons, competition_point_rules, competition_forfeit_rules, competition_ranking_criteria a competition_entries. Přidává FK/indexy, UUID, bigint verze, jmenný EXCLUDE guard proti překryvům inkluzivních rozsahů a triggery pro stejný sport/sezónu, neměnnou identitu, archivaci a historické uzamčení rodiče i pravidel/přihlášek. Bodovací pravidla jsou úplně validovaná v service a všechny části konfigurace se zapisují v jedné transakci. Žádný ručně spuštěný SQL seed není potřebný pro přidání soutěže nebo sportu. V1–V5 se při V6 nepřepisují. Uzamčení při prvním výsledku dodá až P5/P6; jeho guardy jsou již přítomné. Čistá instalace a upgrade se ověřují pouze na izolovaných testovacích DB, viz [ověření](verification.md); běžná uživatelova DB nebyla kontrolována ani měněna. Při neřízeném ručním DDL je před nasazením nutné individuálně porovnat schéma a Flyway historii; migrace nic nepřebírá pomocí baseline.

## Implementované V1–V4

V1 users a V2 persons/club/audit_log/sports se nepřepisují. V2 zachovává účty, hashe a časy, systémové role USER/ADMIN, typ STANDARD/CHILD a dočasně nullable UNIQUE users.person_id. PENDING má jen povolený vlastní onboarding/auth a prázdný kontext. V3 přidává seasons, teams, team_seasons, player_profiles, guardian_links, player_team_memberships a coach_team_memberships. V4 doplňuje neveřejný users.auth_revision a guard triggery pro identitu, CHILD a rodičovské vazby. P3 nad nimi poskytuje account/person-link, schvalování/odvolání rodičů, Moje děti a omezené CHILD sportovní čtení; nepřidává tabulku.

P2 poskytuje ADMIN správu sezón, týmů, osob, hráčů a členství a scoped soupisky/historii. Hráč má povinné datum narození, neměnnou osobu a nepotřebuje účet ani rodiče. Osobě s hráčem nejde datum narození vymazat. TeamSeason drží samostatný název; přejmenování základního týmu jej nemění. Použité hranice sezóny, sport a typ týmu jsou zamčené; archivace zachová data a zablokuje běžné změny.

V3 instaluje `btree_gist`. EXCLUDE omezuje překryvy stejného hráče/trenéra ve stejném team_season pomocí `[valid_from,valid_to)`; sousední intervaly a současná členství v jiných týmech jsou přípustné. NULL valid_to znamená při čtení a validaci konec sezóny (v EXCLUDE otevřený rozsah, bezpečný díky hranicím a samostatnému team_season). Interval musí celý ležet v sezóně. Externí soupeř nemá interní členství. Trenér musí být enabled STANDARD účet s osobou, včetně vlastního ADMIN.

ADMIN čte historii i soupisku ke zvolenému dni; běžný účet pouze aktuálně oprávněný přesný sezónní tým. Roster ani základní player detail nevracejí kontakty či datum narození. Aktuální scope omezuje také počty; vlastní hráč a schválený STANDARD rodič dítěte čtou celou příslušnou členskou historii, trenér pouze aktuálně svěřené team_seasons. Rodič získá dnešní tým přes dnešní členství schváleného dítěte, CHILD pouze přes vlastní hráčské členství. Budoucí historické výsledkové pohledy a opravy účastí událostí doplní P5/P6.

NOT NULL pro users.person_id vyžaduje pozdější backfill a novou migraci. Formáty a soutěžní konfiguraci realizují V5/V6; výsledky, statistiky, finance a komunikace níže jsou **cílový návrh**. Další sport, formáty i ročníky a jejich pravidla se spravují v UI bez sportovních větví, ručního SQL nebo seed skriptu.

## Přehled tabulek

| Tabulka                        | Účel                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| `persons`                      | Osobní a kontaktní údaje.                                    |
| `users`                        | Účet, role `USER`/`ADMIN` a typ `STANDARD`/`CHILD`.           |
| `player_profiles`              | Hráčský profil osoby, i bez přihlašovacího účtu.             |
| `guardian_links`               | Schválené vazby zástupců na hráče.                           |
| `club`                         | Jediný klub instalace a společný transakční zámek.           |
| `sports`                       | Databázově spravované sporty klubu.                          |
| `seasons`                      | Sezóny a jejich archivace.                                   |
| `teams`                        | Klubové týmy a externí soupeři.                              |
| `team_seasons`                 | Tým v konkrétní sezóně s tehdejším názvem.                   |
| `player_team_memberships`      | Časově platná členství hráčů v týmech.                       |
| `coach_team_memberships`       | Časově platná přiřazení trenérů k týmům.                     |
| `events`                       | Tréninky, zápasy a další akce.                               |
| `event_teams`                  | Týmy přiřazené k akci.                                       |
| `event_participants`           | Účastníci akcí, dostupnost, nominace, docházka a nastoupení. |
| `competitions`                 | Soutěže.                                                     |
| `competition_seasons`          | Ročníky soutěží a jejich pravidla.                           |
| `match_formats`                | Formát zápasu a způsob vyhodnocení výsledku.                 |
| `competition_point_rules`      | Přidělování bodů do soutěžní tabulky.                        |
| `competition_forfeit_rules`    | Skóre a body obou stran při explicitní kontumaci.            |
| `competition_ranking_criteria` | Kritéria pořadí v soutěžní tabulce.                          |
| `competition_tie_decisions`    | Auditovaná ruční rozhodnutí úplné shody při dané revizi výsledků. |
| `competition_tie_decision_entries` | Pořadí týmů dotčené ručním rozhodnutím.                 |
| `competition_entries`          | Týmy přihlášené do ročníku soutěže.                          |
| `matches`                      | Strany zápasu, formát a odkaz na aktuální výsledkovou revizi. |
| `match_result_revisions`       | Historie výsledků; potvrzené revize jsou neměnné.            |
| `match_periods`                | Části konkrétní revize, po potvrzení neměnné.                 |
| `match_result_players`         | Historický snímek týmu, docházky a nastoupení hráče pro revizi. |
| `statistic_definitions`        | Stabilní identity individuálních statistik.                 |
| `statistic_definition_versions` | Neměnné verze názvu a významu statistiky.                  |
| `player_match_statistics`      | Hodnoty hráčů v konkrétní revizi, po potvrzení neměnné.      |
| `fee_assessments`              | Předepsané členské příspěvky a splatnosti.                   |
| `fee_payments`                 | Ručně evidované úhrady příspěvků.                            |
| `conversations`                | Konverzace navázané na sezónní tým.                          |
| `conversation_participants`    | Účastníci konverzací.                                        |
| `messages`                     | Zprávy v konverzacích.                                       |
| `notifications`                | Oznámení vytvořená při změnách nebo blížící se splatnosti.   |
| `notification_deliveries`      | Doručení a přečtení oznámení jednotlivými uživateli.         |
| `audit_log`                    | Záznam důležitých změn.                                      |

## Osoby, účty a oprávnění

`persons` obsahuje osobní údaje. Jedna osoba může mít nejvýše jeden účet (`users`) a jeden hráčský profil (`player_profiles`), propojené jedinečným `person_id`. Hráč, například dítě, nemusí mít účet ani evidovaného rodiče. Musí však mít datum narození. Použitý účet ani profil nelze přiřadit jiné osobě.

`users.account_type` rozlišuje běžný účet `STANDARD` a omezený dětský účet `CHILD`; `users.enabled` určuje povolení přihlášení. Evidovaný nezletilý hráč není automaticky dětský účet: profil bez přihlášení zůstává použitelný pro správu trenérem a nevyžaduje rodičovský účet. CHILD vzniká s `enabled = false`. Před jeho aktivací musí být účet propojen se stejnou osobou jako jeho vlastní hráčský profil a dítě musí mít alespoň jednu schválenou neodvolanou rodičovskou vazbu na osobu s povoleným účtem `STANDARD`. Rodič může mít více dětí a dítě více oprávněných rodičů; potomkem je samostatný účet, nikoli kopie rodičovských oprávnění nebo společná session.

Účet `CHILD` má systémovou roli `USER`; v P3 čte vlastní osobu, vlastní profil a členskou historii a aktuální vlastní týmy/soupisky. Dostupnost a oprávněná komunikace jsou navazující plán. Nemůže být ADMIN, získat trenérská či zástupcovská oprávnění ani spravovat ostatní osoby. **Žádná finanční operace není pro CHILD přípustná**, včetně čtení vlastních příspěvků, plateb, seznamů, detailů, souhrnů, exportů a finančních notifikací. Kontrola typu účtu předchází vyhodnocení role a vztahů; znalost ID ani rodičovská session nesmí tento zákaz obejít.

Aktivace dětského účtu, změna jeho propojení a odvolání rodičovské vazby se kontrolují na serveru v jedné transakci. Odebrání poslední kvalifikované vazby okamžitě nastaví dítěti `enabled = false` a invaliduje jeho session; nesmí ponechat aktivní účet bez odpovědného rodiče. Totéž platí při deaktivaci posledního rodičovského účtu. Každý další request ověřuje enabled i aktuální platnost vazby. Po doplnění vazby se účet znovu aktivuje až samostatným administrátorským požadavkem a serverovou kontrolou všech podmínek. Regrant ani opětovné povolení rodiče dítě automaticky neaktivují. Přepnutí `CHILD` na `STANDARD` je samostatná odůvodněná administrátorská operace, nikoli self-service pole profilu nebo registračního formuláře. Rodič i dítě používají oddělená hesla a session a nepřebírají navzájem svá oprávnění.

Aplikace rozlišuje čtyři klubové role ze zadání: **administrátor, trenér, hráč a rodič nebo zákonný zástupce**. V databázi jsou vyjádřené takto:

- **Administrátor** má v `users.role` hodnotu `ADMIN`.
- **Trenér** má platné přiřazení v `coach_team_memberships`.
- **Hráč** je evidován hráčským profilem a členstvím v týmu. Přihlášený hráč jedná za profil propojený se svou osobou; profil bez účtu spravuje oprávněný trenér nebo administrátor.
- **Rodič nebo zástupce** se eviduje jako osoba se schválenou vazbou `guardian_links.guardian_person_id` na hráče. Účet mít nemusí; přístup do aplikace získá teprve účtem se stejným `person_id`. Rodičovské účty i vazby jsou volitelné; nevznikají povinně při založení hráče.

Sloupec `users.role` rozlišuje systémové role `USER` a `ADMIN`. Trenérská, hráčská a rodičovská oprávnění účtů STANDARD se vyhodnocují z uvedených vazeb pro konkrétní tým nebo dítě; účtu CHILD z nich vzniká pouze omezený vlastní hráčský přístup. Administrátor role a vazby spravuje v administračním rozhraní.

Vztahy se u účtu STANDARD mohou kombinovat: trenér může být také rodičem nebo administrátorem na **jediném účtu a pod jedinou osobou**. Samostatný administrátorský účet ani druhá identita nejsou podmínkou trenérské správy. Jeden provozovatel může jako ADMIN zakládat a spravovat své týmy, jejich hráčské profily a vlastní trenérská přiřazení. Ke správě hráče nepotřebuje vztah rodič–dítě. Běžnému trenérovi samotné přiřazení nedává přístup k financím svěřence; k rodičovskému přístupu potřebuje schválenou vazbu. ADMIN má finanční správu z administrátorského oprávnění. Uživatel STANDARD bez klubových vztahů nevidí týmové údaje, ale může číst vlastní příspěvky. Omezení CHILD má vždy přednost.

**Implementovaný setup P1:** veřejná registrace vždy vytvoří USER a první registrant se automaticky nestává administrátorem. Provozovatel se přihlásí povoleným účtem STANDARD a provede jednorázový setup s instalačním tokenem ze soukromého prostředí nasazení. `CLUB_SETUP_TOKEN` je soukromá konfigurace backendu; prázdná jej vypíná. Token nemá výchozí hodnotu a neukládá se do DB ani logů. Pod zámkem `club FOR UPDATE` server ověří token, dosud nedokončený setup a absenci prvního administrátora; atomicky přidělí tomuto účtu ADMIN, zapíše `club.setup_completed_at`, `setup_completed_by` a audit. Obě setup pole jsou buď prázdná, nebo vyplněná společně. Souběžný či opakovaný setup nesmí založit dalšího admina. CHILD setup provést nemůže.

Jediný provozovatel následně pod tímto účtem založí sport, sezónu a tým a přiřadí sebe jako trenéra; nepotřebuje druhého uživatele, samostatnou osobu administrátora ani rodičovský vztah ke svěřencům. Změna role se projeví v aktuálním serverovém vyhodnocení session. P1/P2 tento průchod poskytují až po tým, hráče a vlastní trenérské přiřazení; události a další sportovní agenda jsou navazující práce.

Rodičovské vazby schvaluje administrátor a lze je odvolat. Odvolání ukončí oprávnění k dítěti, pokud neexistuje jiný platný důvod přístupu.

Původní E0 používala pět hodnot rolí. P1 je převedla na USER/ADMIN a přidala STANDARD/CHILD, P2 dodala týmové vazby a P3 správu účtů, rodiče a omezený CHILD login. V4 při upgradu nejprve zakáže všechny dosavadní CHILD účty bez domýšlení osob či rodičů. Podmínky přístupu podle aktuální role, typu účtu, vztahů a stavu záznamu popisuje [autorizace](authorization.md).

### Autorizační revize a správa účtů v P3

`users.auth_revision` je neveřejný nezáporný bigint, nikoli verze formuláře. Trigger ji zvýší při změně enabled/role/account_type/person_id/password_hash. Session nese původní revizi a filtr ji ověří proti aktuálnímu účtu před nahrazením principalu. Stará cookie zůstane neplatná i po opětovném povolení účtu. Vlastní onboarding a setup obnoví session jen pro očekávanou revizi své právě dokončené operace; konkurenční změnu nepřebírají.

V4 hlídá neměnnost již propojené osoby, CHILD s vlastním hráčem/rolí USER bez trenérství či rodičovství, povolení dítěte jen s aktivním STANDARD rodičem, zákaz sebevazby a rodičovských cyklů. Změna rodičovské vazby nebo rodičovského účtu automaticky zakáže osiřelý aktivní CHILD a zvýší jeho revizi. Zachované profily a historie se nemažou. Transakční service navíc chrání posledního povoleného dokončeného STANDARD ADMIN a převody typu; jakékoli zachované trenérské členství blokuje převod na CHILD.

Account/person-link a guardian security mutace vyžadují odůvodnění 1–500 znaků. Audit neobsahuje heslo ani kopii requestu; do důvodu správce nevkládá tajné nebo osobní údaje. Rodiče bez účtu lze evidovat, ale nejsou kvalifikovaným aktivním rodičem pro CHILD login. Přímý CHILD allowlist zákaz finančních a notifikačních cest je předběžná hranice, finanční tabulky/API ani schránka dosud nejsou dodané.

## Týmy a sezóny

`club` má jediný řádek s `id = 1`; identifikuje klub a slouží pro společný zámek, neurčuje jediný sport. `sports` obsahuje stabilní kód a název každého sportu. Klubové i externí týmy jsou v `teams` a každý má právě jeden `sport_id`. Soutěže, formáty a definice statistik patří rovněž konkrétnímu sportu. Přidání basketbalu vedle fotbalu vytváří další databázové záznamy v téže instalaci; uživatelé, klub i sezóny mohou zůstat společné.

Výpočty používají uložený formát zápasu a pravidla ročníku, nikoli podmínky podle názvu sportu. Obě strany zápasu, soutěž, formát a použité definice statistik musejí být stejného sportu; náhodný fotbalový tým se nesmí stát účastníkem basketbalové soutěže. Obecná jiná klubová akce může zahrnovat více sportů, zápas jen jeden. Historicky použitý sport ani příslušnost použitého týmu se nepřepisují.

`team_seasons.display_name` uchovává název týmu v dané sezóně, takže pozdější přejmenování týmu nepřepíše staré výsledky. Dvojice `(team_id, season_id)` je jedinečná. Zařazení do sezóny a intervaly členství určují, které týmy a hráči do ní patří; samostatný příznak `active` u týmu a hráče se nevede.

Hráčská i trenérská členství mají interval `[valid_from, valid_to)`. Neuvedené `valid_to` znamená konec sezóny. Překrývající se intervaly stejné dvojice hráč/tým nebo trenér/tým zakáže omezení `EXCLUDE` v PostgreSQL (`btree_gist`). Členství různých týmů téhož hráče se překrývat mohou.

Sezóna používá interval `[starts_on, ends_on)` v časové zóně `Europe/Prague`. Jakmile je použita v sezónním týmu, soutěžním ročníku, akci nebo příspěvku, její hranice se už nemění. Změny členství a dalších historických vztahů nesmějí zpětně zneplatnit uskutečněné akce nebo statistiky.

## Akce a účast hráčů

`events` obsahuje společné údaje pro trénink, zápas i jinou akci. Sezónní týmy akce jsou v `event_teams`. Tabulka `event_participants` má primární klíč `(event_id, player_id)`, takže hráč je na jedné akci veden nejvýše za jeden tým. Tým účastníka musí být přiřazený k akci.

Dostupnost, nominace, docházka a nastoupení mají v `event_participants` vlastní stav, autora a čas změny. `CHECK` vyžaduje všechny tři hodnoty společně, nebo žádnou. `NULL` znamená nezapsaný údaj; `UNKNOWN`, `NOT_SELECTED` a `played = false` jsou výslovné odpovědi.

Pravidla zápisu:

- Hráč nebo jeho aktuální zástupce může potvrdit dostupnost nejpozději do uzávěrky nebo začátku akce. Odpovědi přijímá pouze publikovaná akce. Výjimečnou opravu provede ADMIN s důvodem a auditem; samotné trenérské přiřazení k přepsání odpovědi neopravňuje.
- Nominaci zapisuje trenér nebo administrátor. Hráči a rodiče ji vidí až po `nomination_published_at`.
- Docházku a nastoupení zapisuje trenér nebo administrátor po začátku publikované akce. Opravy dokončené akce vyžadují odpovídající oprávnění.
- `played` se používá jen u zápasů. Hodnota `true` vyžaduje docházku `PRESENT` nebo `LATE` a tým účastníka musí být jednou ze stran zápasu.
- Zrušené akce nepřijímají běžné sportovní zápisy. Koncepty nevidí hráči ani rodiče.

Požadavek smí měnit jen povolenou skupinu polí. Autora a čas doplní server; `version` chrání řádek před přepsáním zastaralým formulářem. Zveřejnění nominace a její pozdější změny vytvoří oznámení.

### Soupisky, přestupy a změny termínů

Při první publikaci akce se účastníci vytvoří ze soupisky platné k jejímu začátku. Pozdější příchod hráče do týmu je automaticky nepřidá; doplnění je výslovná operace s validací členství a oprávnění. Hráči s více členstvími se pro akci vybere jeden tým. Toto upřesnění navazující implementace je zachyceno v [pracovním plánu](implementation-plan.md).

Budoucího účastníka lze před začátkem akce vyřadit pomocí `withdrawn_at` a důvodu. Nesmí už mít zapsanou docházku, nastoupení ani statistiky. Odpověď a nominace zůstanou uložené kvůli historii, ale účastník se dále nepovažuje za aktivního. Pokud už byla nominace zveřejněná, změna se oznámí. Prázdný záznam budoucí účasti lze také odstranit. Vrácení vyřazeného účastníka je možné jen jako odůvodněná administrátorská oprava.

Přestup ukončí původní členství a založí nové. Neplatné budoucí účasti se musí vyřadit nebo odstranit ve stejné transakci; jinak se přestup odmítne. Historická účast zůstává přiřazená původnímu týmu. Podobně při posunu akce musí aplikace znovu zkontrolovat členství, uzávěrku a ostatní navázané údaje. Existující dostupnost a nominace se automaticky nemažou; změna termínu vyvolá oznámení a případně nové potvrzení účasti.

Celá akce se musí vejít do hranic sezóny. Konec akce může být přesně na jejím konci. Událost nelze přesunout do jiné sezóny; dosud neodehranou akci lze zrušit a vytvořit novou. `events.season_id` a `events.type` lze měnit pouze u konceptu bez týmů, účastníků, zápasu a oznámení.

Zápas má právě jeden záznam v `matches`, ostatní akce žádný. Strany zápasu, akce a případný ročník soutěže musejí patřit do stejné sezóny. Soutěžní strany musejí být přihlášené do daného ročníku; klubové strany odpovídají týmům přiřazeným akci. Zápas dvou externích týmů klubové účastníky nepotřebuje. Strany zápasu se po prvním výsledku, části, nastoupení nebo statistice běžně nemění.

Primární klíče se nepřepisují a historie se nemaže kaskádou. Oprava starých údajů vyžaduje administrátorské oprávnění, důvod a kontrolu všech navázaných záznamů.

## Zápasy a soutěžní tabulky

### Formát zápasu

`match_formats` popisuje způsob vyhodnocení zápasu nezávisle na bodování soutěže. Podporované režimy jsou:

- `SUM_SCORE`: konečné skóre je součet částí zápasu, například gólů po třetinách nebo bodů po čtvrtinách.
- `WON_PARTS`: konečné skóre vyjadřuje počet vyhraných částí, například setů ve volejbalu.

Formát určuje povolení remízy, případnou povinnost evidovat části, jejich označení a počet, počet částí potřebných k vítězství a možnost prodloužení či nájezdů. `regulation_period_count = NULL` znamená neomezený počet částí v základní době. Jeden formát může používat více soutěžních ročníků i přátelských zápasů.

Bodování a řazení tabulky patří konkrétnímu `competition_seasons`, nikoli formátu. Soutěžní zápas musí použít stejný formát jako jeho ročník. Přátelský zápas si formát určuje přímo a nepotřebuje bodovací pravidla soutěže.

První část nebo úplný výsledek nastaví `match_formats.locked_at` a u soutěžního zápasu také `competition_seasons.locked_at`. Formát, bodování, kontumační pravidla a kritéria pořadí včetně politiky minitabulek pak nelze změnit ani po opravě či anulování výsledku. Jiná pravidla vyžadují nový formát nebo ročník. Přejmenování je povolené, výpočty nemění. Auditované rozhodnutí úplné shody je samostatný záznam pro konkrétní sadu výsledků, nikoli změna zamčených pravidel.

### Výsledek a části zápasu

`matches` uchovává identitu a strany utkání a odkaz `active_result_revision_id` na aktuální potvrzený výsledek stejného zápasu. Skóre a jeho opravy neukládá přepisem tohoto řádku. `match_result_revisions` obsahuje číslo revize v rámci zápasu, stav `DRAFT`, `CONFIRMED` nebo `ANNULLED`, oba výsledkové součty, rozhodnutí o kontumaci, způsob ukončení běžného zápasu a případného vítěze nájezdů, autora/čas a u opravy také důvod a odkaz `supersedes_revision_id`. Identifikátor nahrazované revize musí patřit témuž zápasu.

Návrh se připravuje po konci publikované akce. Potvrzený výsledek, jeho části, hráčské snímky i hodnoty statistik jsou **neměnný celek**; běžný update ani delete jej nesmějí přepsat. Oprava vytvoří novou návrhovou revizi, vědomě převezme zachovávané části a statistiky, upraví potřebné údaje a znovu provede validaci. Potvrzení změnu přepne atomicky: nová revize je potvrzená, `matches.active_result_revision_id` se změní a `competition_seasons.results_revision` se zvýší. Původní potvrzená revize zůstane dohledatelná včetně svého skóre, autorství, částí a statistik.

Anulování vytvoří novou konečnou revizi `ANNULLED` s důvodem a vazbou na nahrazenou revizi, bez skóre, částí, hráčských snímků a statistik, a vyprázdní aktivní odkaz; původní data nemaže. Oprava/anulování zároveň zneplatní odvozenou tabulku, agregace a případné ruční rozhodnutí pro starou revizi výsledků. V jedné transakci se zapíše i audit. Návrhy a nahrazené revize se do oficiálních součtů nezapočítají. První dokončení akce lze provést až po jejím konci a zápas při něm vyžaduje úplný platný potvrzený výsledek. Pozdější anulování zachová historickou dokončenou akci, ale odstraní její aktivní výsledek a vyřadí ji z výsledkových součtů. Oficiální statistiky a tabulky používají jen aktivní revize `CONFIRMED` dokončených nezrušených zápasů, nikdy součet všech uložených revizí.

Jednotlivé části ukládá `match_periods` pro konkrétní `result_revision_id` s pořadovým číslem a fází `REGULATION`, `OVERTIME` nebo `SHOOTOUT`. Klíčem je `(result_revision_id, period_number)`, nikoli pouze zápas a číslo části. Při potvrzení musí být číslování souvislé od jedničky a fáze musejí být ve správném pořadí. Oprava části již potvrzeného výsledku vytváří novou výsledkovou revizi; původní rozpis zůstane zachovaný.

V režimu `SUM_SCORE` se sčítají základní části a prodloužení. Nájezdy se do běžného součtu nezahrnují. Pokud formát části nevyžaduje a žádné nejsou zapsané, lze zadat přímo finální skóre. Jakmile se části používají, musí popisovat celý zápas; pevně stanovený počet základních částí se musí dodržet.

Režim `WON_PARTS` vyžaduje části bez remíz. Zápas končí ve chvíli, kdy jeden tým dosáhne `wins_required`; po rozhodující části už nesmí následovat další. Prodloužení ani nájezdy se v tomto režimu nepoužívají.

Prodloužení lze použít jen při nerozhodném stavu po základní době a jen pokud ho formát dovoluje. Další prodloužení může následovat pouze při trvající remíze. Nájezdy smějí tvořit nejvýše jednu závěrečnou část, nesmějí skončit remízou a musejí následovat po nerozhodném skóre. `shootout_winner_home` výslovně určuje vítěze nájezdů i tehdy, když se kvůli nulovému bonusu finální skóre nezmění. Pokud je bonus nenulový, přičte se pouze vítězi.

Jsou-li uložené části běžného výsledku, konečné skóre vypočítá backend v téže transakci; klient neposílá nezávislý součet. Rozpracované části bez finálního výsledku jsou povolené jen v návrhu. Potvrzení opravené revize přepočítá výsledek, nebo se odmítne, pokud by rozpis či navázané statistiky přestaly být platné. Kontumační skóre se řídí samostatným pravidlem níže a nevyžaduje vymyšlené odehrané části.

### Bodování a pořadí

`competition_point_rules` přiřazuje body běžného zápasu podle výsledku (`WIN`, `DRAW`, `LOSS`), způsobu ukončení a případně rozsahu vlastního a soupeřova konečného skóre. Chybějící hranice znamená neomezený rozsah. Pravidla pro stejný výsledek a ukončení se nesmějí překrývat. Při dokončení běžného zápasu musí pro každou stranu existovat právě jedno platné bodovací pravidlo; jinak se zápis odmítne. U formátu bez remíz nelze použít pravidlo `DRAW`.

Kontumaci určuje samostatné rozhodnutí `FORFEIT_HOME` (provinili se domácí, vítězí hosté) nebo `FORFEIT_AWAY` (provinili se hosté, vítězí domácí); `REGULAR` znamená běžně odehraný výsledek. Není odvozena ze skóre ani zaměněna za prodloužení/nájezdy; u kontumace je `ending` prázdné. `competition_forfeit_rules` pro každý ročník a rozhodnutí explicitně ukládá domácí/hostující skóre a body obou stran. Konfigurace musí určit správného vítěze. Při kontumaci se použije právě toto skóre a bodování, ne pravidlo běžné prohry. Kontumační revize nemá části; případné rozehrané části zůstanou v dřívější REGULAR revizi či návrhu jako historické údaje. Nevynucuje se fiktivní kompletní průběh zápasu. Kontumační revize potřebuje autora a důvod. Přátelská kontumace nemá soutěžní body; skóre vítěze a poraženého určí `match_formats.friendly_forfeit_winner_score` a `friendly_forfeit_loser_score` podle stejného rozhodnutí.

`competition_ranking_criteria` stanoví pořadí metrik a směr jejich řazení. K dispozici jsou `POINTS`, `WINS`, `SCORE_DIFFERENCE`, `SCORE_FOR`, `SCORE_RATIO`, `PART_SCORE_DIFFERENCE`, `PART_SCORE_RATIO`, `HEAD_TO_HEAD_POINTS`, `HEAD_TO_HEAD_SCORE_DIFFERENCE` a `HEAD_TO_HEAD_SCORE_FOR`. Vzájemná kritéria musejí tvořit jeden souvislý blok; konfigurace s vloženým celkovým kritériem uvnitř tohoto bloku se odmítne. Běžné metriky pracují s finálním skóre, metriky `PART_SCORE` se součtem započítávaných částí a vyžadují jejich povinnou evidenci. Kontumace používá konfigurované konečné skóre; nezapočítá statistiky hráčů za neodehranou část zápasu.

Výpočet začíná u všech přihlášených týmů a používá aktuální potvrzené výsledkové revize dokončených nezrušených zápasů. Kritéria se vyhodnocují postupně uvnitř dosud shodných skupin. `HEAD_TO_HEAD_*` vytvoří minitabulku výhradně ze vzájemných výsledků členů právě vyhodnocované skupiny. `head_to_head_missing_policy` určuje zacházení s chybějícími zápasy: `PLAYED_ONLY` použije dosud započitatelné vzájemné výsledky; `REQUIRE_COMPLETE` přeskočí celý vzájemný blok, pokud některá dvojice nemá alespoň `head_to_head_meetings_per_pair` započitatelných vzájemných výsledků. Kladný počet střetnutí se konfiguruje v ročníku (výchozí 1, například 2 pro vzájemný zápas doma i venku) a není natvrdo odvozený ze sportu. Úplnost se po rozdělení skupiny posuzuje znovu jen pro její aktuální členy.

Pokud minitabulka rozdělí skupinu a `restart_head_to_head_on_subgroup` je zapnuté, menší dosud shodná podskupina začíná znovu prvním vzájemným kritériem. Změní se tedy i množina zápasů použitá k přepočtu. Pokud kritérium skupinu nerozdělí nebo se kvůli chybějícím zápasům přeskočí, pokračuje se dalším kritériem; restart nikdy neopakuje stejnou nerozdělenou skupinu. Výpočet musí skončit i při úplné shodě.

Poměry se při řazení nezaokrouhlují. Kladná hodnota dělená nulou se považuje za nekonečno, `0/0` za nulu. Při shodě všech kritérií mají týmy stejné pořadí; identifikátor slouží pouze ke stabilnímu zobrazení, neurčuje sportovní výsledek. Admin může pro zcela shodnou skupinu zaznamenat odůvodněné, auditované ruční rozhodnutí v `competition_tie_decisions` a seřazené položky v `competition_tie_decision_entries`. Rozhodnutí obsahuje autora/čas, důvod a konkrétní `results_revision` ročníku. Mění jen pořadí této skupiny, nikoli body či výsledky. Každý dotčený tým se v položkách objeví právě jednou a skupina musí obsahovat přesně všechny dosud shodné týmy. Oprava ručního rozhodnutí vytváří nový záznam s `supersedes_decision_id`; dřívější rozhodnutí zůstane dohledatelné. Platí jen poslední nenahrazené rozhodnutí téhož kontextu. Oprava, přidání či anulování výsledku zvýší výsledkovou revizi a vyžaduje nové vyhodnocení; staré rozhodnutí nesmí převážit aktuální pravidla. V tabulce jsou i přihlášené týmy bez odehraného zápasu, s nulovými součty.

Příklady konfigurace:

| Sport     | Formát                                                         | Bodování a pořadí                                                          |
| --------- | -------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Fotbal    | `SUM_SCORE`, možnost remízy, případně dva poločasy.            | Výhra/remíza/prohra 3/1/0; body, rozdíl skóre, vstřelené góly.             |
| Basketbal | `SUM_SCORE`, bez remíz, případně čtyři čtvrtiny a prodloužení. | Výhra 2, běžná prohra 1, kontumační prohra 0; body, vzájemné body, vzájemný rozdíl, celkový rozdíl, dosažené skóre. |
| Hokej     | `SUM_SCORE`, tři třetiny, prodloužení a nájezdy.               | V základní době 3/0, po prodloužení či nájezdech 2/1.                      |
| Volejbal  | `WON_PARTS`, vítězství po třech vyhraných setech.              | Výhra 3:0 nebo 3:1 za 3 body, 3:2 za 2; prohra 2:3 za 1 bod, ostatní za 0. |

Výpočty používají uložený formát a pravidla ročníku. Název sportu na ně nemá vliv. Například `FORFEIT_HOME` v basketbalu může přidělit skóre 0:20 a body 0:2; běžná porážka 0:20 stále přiděluje poraženému 1 bod. Fotbal i basketbal musejí být konfigurovatelné a akceptované současně v jedné aplikaci.

Mimo návrh zůstávají bodové tresty, kontrola skóre pro ukončení setu, časomíra a hráčské statistiky po částech. Kontumace a minitabulky jsou součástí povinného návrhu. U výsledku bez rozpisu částí nelze ověřit stav před prodloužením.

## Statistiky

Definice individuálních statistik spravuje administrátor. `statistic_definitions` je stabilní identita statistiky a jejího sportu; `statistic_definition_versions` ukládá neměnné verze názvu, typu (`INTEGER`, `DECIMAL`, `BOOLEAN`, `TEXT`), agregační funkce, případně jednotky a minimální nebo maximální hodnoty. Nejvyšší číslo revize je aktuální definice pro nový zápis. Přejmenování vytváří novou verzi a stará hodnota dál odkazuje na původní název. Optimistic-lock sloupec `version` identity tuto historii nenahrazuje.

Po prvním použití se nesmí změnit význam definice: typ, jednotka, meze ani agregace. Nová verze téže identity pak smí změnit jen název, nový význam vyžaduje novou definici. Deaktivace ponechá minulá data čitelná. Definici identifikuje její `id`; další ručně udržovaný kód nepotřebuje. Žádné góly, asistence nebo basketbalové body nejsou pevnými sloupci hráče.

`player_match_statistics` ukládá jednu hodnotu identity statistiky pro hráče a **výsledkovou revizi**, v jediném sloupci odpovídajícím typu. Obsahuje `statistic_definition_version_id` i vazbu na tutéž identitu, takže nelze zadat dvě různé verze stejné statistiky jednomu hráči v jedné revizi zápasu. Backend ověřuje typ, meze, sport a přesnost; hodnoty nesmí tiše zaokrouhlit. Nevyplněná statistika neznamená nulu.

`match_result_players` zachytí při přípravě revize historický tým hráče, docházku a skutečné nastoupení pro tuto revizi. Statistiku lze zapsat jen hráči, který podle tohoto snímku skutečně nastoupil (`played = true`), má docházku `PRESENT` nebo `LATE`, nebyl vyřazen a patří k jedné ze stran zápasu. Zápas musí být po skončení a revize mít úplný platný výsledek. Při potvrzení se snímek validuje proti skutečné evidenci účasti.

Potvrzený snímek a statistiky se později neověřují proti přepsané současné docházce: oprava nastoupení nebo hodnot vytváří a potvrzuje novou výsledkovou revizi se související opravou účasti. Stará revize tak stále prokáže, proč její statistika vznikla. Do současných agregací vstupují výhradně hodnoty aktivní potvrzené revize; historický přehled může zvolit starou revizi a zobrazit její části, účastníky i původní verze definic.

Funkce `SUM`, `AVG`, `MIN` a `MAX` agregují numerické hodnoty, `COUNT` počítá záznamy a `COUNT_TRUE` pravdivé booleany; `NONE` se neagreguje. Chybějící hodnota není nula a nevstupuje do jmenovatele AVG. Prázdná SUM vrací 0 s počtem vzorků 0; prázdné AVG/MIN/MAX vrací null. Numerické agregace nepoužívají plovoucí čárku. Týmové a sezónní souhrny se odvozují z aktivních potvrzených výsledků, jejich částí a historického týmu účastníka. Počet odehraných zápasů vychází z `played = true` ve snímku aktivní revize dokončených zápasů, nikoli z nominace nebo samotné přítomnosti statistiky.

## Členské příspěvky

`fee_assessments` obsahuje osobu, sezónu, částku, měnu a jednu splatnost. Administrátor eviduje skutečně přijaté úhrady v `fee_payments`. Více úhrad jednoho předpisu je možné; samostatný splátkový kalendář se nevede.

Stav příspěvku se vypočítá ze součtu nezrušených úhrad:

- `0` – nezaplaceno;
- více než `0`, ale méně než předepsaná částka – částečně zaplaceno;
- součet rovný částce – zaplaceno.

Přeplacení se odmítne. Částku předpisu nelze snížit pod součet platných úhrad. Po první úhradě se nemění osoba, sezóna ani měna, ani když je platba později stornovaná.

Chybná platba se stornuje s autorem, časem a důvodem; případně se zapíše nová. Storno nevrací peníze. Opakovaný požadavek se stejným UUID a obsahem nic nezmění, jiný obsah je konflikt. Zrušený předpis nesmí mít aktivní úhrady; historie zůstává.

Úloha pro notifikace hlídá nedoplatky a blížící se i překročené splatnosti. Upozorňuje vlastní účet člena, pokud je STANDARD, účty všech jeho oprávněných zástupců a administrátory. Zástupci dostávají upozornění i tehdy, když má dítě vlastní účet. **CHILD nedostává žádné finanční notifikace ani finanční text v obecné schránce**; jeho příspěvky vidí oprávnění rodiče a administrátor. Účty zástupců se získají přes jejich `persons.id` a musejí být aktivní STANDARD; samotný kontakt bez loginu není doručením do schránky. Pokud člen ani žádný oprávněný zástupce způsobilý účet nemají, admin dostane upozornění na nedoručitelnou schránku člena; systém nesmí člena označit za obeslaného. Existence účtu nebo rodičovské vazby přitom není podmínkou evidence hráče ani předpisu. Plně zaplacený nebo zrušený příspěvek už další výzvu nevytváří. Platby za staré sezóny lze doplňovat i po archivaci. Součty různých měn se nesčítají; databáze kontroluje formát třípísmenného kódu měny, podporované měny aplikace.

Platební brána, bankovní integrace, vratky, slevy, odměny a kredit nejsou součástí návrhu.

## Komunikace a notifikace

Konverzace v `conversations` patří konkrétnímu sezónnímu týmu a má alespoň dva oprávněné účastníky včetně zakladatele. Při čtení i odesílání zprávy musí mít uživatel aktivní záznam v `conversation_participants` a platný vztah k týmu. U starší sezóny se kontroluje také příslušný historický vztah. Administrátor nemá automatický přístup do všech konverzací; musí být jejich účastníkem.

Účastník přidaný později vidí zprávy až od `joined_at`. Odchod nebo ztráta posledního oprávnění nastaví `left_at`; záznam se nemaže kvůli autorství starších zpráv. Jednou ukončená účast se znovu neotevírá. Po obnovení oprávnění se pro další komunikaci založí nová konverzace. Zánik jedné vazby přístup neukončí, pokud existuje jiná platná vazba ke stejnému týmu.

`notifications` ukládá společný obsah oznámení, zatímco `notification_deliveries` eviduje doručení a přečtení pro jednotlivé adresáty. Oznámení a doručení se zapisují ve stejné transakci jako související změna. Jedinečný deduplikační klíč brání opakovanému vytvoření stejného oznámení a při shodném klíči musí souhlasit i obsah. U platebních výzev klíč rozlišuje předpis, splatnost a druh výzvy.

Oznámení neobsahují soukromé zprávy, omluvenky ani citlivé informace o dítěti. Při otevření cíle aplikace znovu zkontroluje oprávnění; stará notifikace sama přístup neuděluje. Externí doručování není součástí návrhu.

## Integrita a souběžné operace

Kontroly při zápisu:

| Vrstva                                     | Co kontroluje                                                                                                                                  |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL (`PK`, `FK`, `UNIQUE`, `CHECK`) | Existenci vazeb, jedinečnost, platnost lokálních hodnot, úplnost skupin sloupců a základní omezení částek či skóre.                            |
| PostgreSQL (`EXCLUDE`)                     | Nepřekrývání intervalů členství stejné dvojice hráč/tým a trenér/tým je implementované V3 s btree_gist.                                 |
| Backend                                    | Shodu sezón, členství účastníka, termíny zápisů, sportovní pravidla, finanční součty, návaznost historie a ostatní podmínky přes více tabulek. |
| Autorizace                                 | Aktuální role a vztahy, oprávnění pro konkrétní operaci, povolená pole a přístup k soukromým údajům.                                           |

Pro souběh změn se používá společný zámek řádku `club.id = 1`. Transakce čtoucí klubová data získá `FOR SHARE`, každá doménová změna a plánovaná úloha `FOR UPDATE`. Po získání zámku služba znovu načte aktuální oprávnění a závislá data v režimu `READ COMMITTED`, zkontroluje výsledný stav a zapíše změnu, audit a případná oznámení. Zámek drží až do potvrzení transakce; během transakce se nečeká na uživatele ani na síťové služby.

Zámek řadí všechny zápisy za sebe, což omezuje propustnost. Pro malý klub tím chrání souběžné úhrady, odvolání oprávnění i změny výsledků a pravidel. Po potvrzení odvolání pracují další operace s novým stavem. Postup platí také pro administrátorské opravy a plánované úlohy. Přímé SQL mimo aplikaci mezitabulkové kontroly nezajistí.

Sloupce `version` chrání uživatele před přepsáním dat zastaralým formulářem. Úprava návrhové části či potvrzení nové výsledkové revize zvyšuje verzi zápasu; změna dosud nepoužitého bodování nebo řazení zvyšuje verzi soutěžního ročníku. `results_revision` navíc identifikuje konkrétní sadu oficiálních výsledků pro výpočet pořadí a ruční rozhodnutí. Optimistic-lock `version` nenahrazuje historické výsledkové/definiční revize ani databázový zámek.

Archivace uchovává soupisky, výsledky, jejich revize a statistiky. Oprava archivovaných sportovních údajů vyžaduje administrátora, důvod a společnou kontrolu navázaných dat; použitá pravidla ani definice statistik se tím neodemknou. Archivované potvrzené části, snímky hráčů a statistiky se fyzicky nepřepisují ani nemažou: nová revize opraví současný oficiální stav a předchozí stav zůstane dohledatelný.

## Pokrytí zadání

| Požadavek                                          | Model                                                                                      |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Hráči, trenéři, rodiče, administrátor              | `persons`, `users`, `player_profiles`, `guardian_links`, `coach_team_memberships`          |
| Týmy a soupisky                                    | `teams`, `team_seasons`, `player_team_memberships`, `seasons`                              |
| Tréninky, zápasy, další akce                       | `events`, `event_teams`, `event_participants`, `matches`                                   |
| Dostupnost, nominace a docházka                    | Samostatné skupiny polí v `event_participants`                                             |
| Členské příspěvky a částečné úhrady                | `fee_assessments`, `fee_payments`                                                          |
| Notifikace                                         | `notifications`, `notification_deliveries`                                                 |
| Interní komunikace                                 | `conversations`, `conversation_participants`, `messages`                                   |
| Soutěže a sezóny                                   | `competitions`, `competition_seasons`, `competition_entries`, `seasons`                    |
| Výsledky a tabulky                                 | `matches`, výsledkové revize/části, formáty, bodovací a kontumační pravidla, vzájemná kritéria a auditovaná rozhodnutí |
| Individuální statistiky spravované administrátorem | Identity a neměnné verze definic, hráčské hodnoty v konkrétní výsledkové revizi           |
| Týmové statistiky a historie                       | Agregace aktivních potvrzených revizí; uchované předchozí výsledky, části, snímky hráčů i názvy definic |
| Minimálně dva sporty bez změny kódu                | `sports` a povinné sportovní vazby týmů/soutěží/formátů/statistik; fotbal i basketbal současně v jedné aplikaci |
| Jeden účet administrátora i trenéra               | STANDARD účet s rolí ADMIN a vlastními trenérskými přiřazeními; bez druhé identity        |
| Volitelní rodiče a omezené dětské účty             | Hráčský profil bez loginu nevyžaduje rodiče; CHILD vyžaduje schváleného rodiče STANDARD a nemá finanční přístup |

Stav implementace a akceptační podmínky původního ODT sledují [požadavky](requirements.md). Tento přehled dokládá pokrytí **návrhem**, ne hotové funkce skeletonu.

### Povinné ověření navazující implementace

- Veřejná registrace neposkytne ADMIN. Z prvního povoleného STANDARD účtu vznikne první administrátor pouze úspěšným jednorázovým setupem s neveřejným instalačním tokenem; dva souběžné požadavky nevytvoří dva adminy. Tentýž účet poté spravuje vlastní trenérská přiřazení, fotbalový i basketbalový tým a hráče bez účtů či rodičů. Oba sporty vzniknou z administračního UI bez úpravy kódu; API odmítne zařazení týmu nebo statistiky cizího sportu.
- Rodič STANDARD zastupuje více dětí; jedno má pouze profil, jiné účet CHILD. Dítě se samostatně přihlásí do omezeného rozhraní. Pokus o vlastní i cizí finanční seznam/detail/export/notifikaci se odmítne bez úniku údajů. Rodič vidí příspěvky oprávněných dětí; odebrání poslední platné vazby nenechá dětskou session aktivní. CHILD se nepovýší odesláním role či account_type v requestu.
- Při shodě soutěžních bodů A a B může vzájemná výhra A rozhodnout před lepším celkovým skóre B. Samostatné fixtures ověří tří- a čtyřčlenné minitabulky, restart menší podskupiny, oba režimy chybějících zápasů, úplnou shodu a ruční rozhodnutí zneplatněné změnou výsledků.
- Basketbalová běžná porážka 0:20 přidělí poraženému 1 bod, zatímco `FORFEIT_HOME` se stejným skóre přidělí domácím 0 a hostům 2. Kontumace nevyžaduje fiktivní odehrané čtvrtiny.
- Oprava archivovaného výsledku 2:1 na 1:2 vytvoří novou revizi a přepočítá tabulku i statistiky právě z ní. Původní skóre, části, účast a hodnoty zůstanou čitelné. Přejmenování definice nezmění původní název v historickém výsledku; anulování nevstoupí do oficiálních součtů.

Jde o úplné scénáře cílové domény. P1–P3 implementují identitu, týmové a rodičovské části; výsledky konkrétních běhů jsou pouze v [ověření](verification.md). Soutěžní, finanční a další dosud nedodané části zůstávají plánované.

## Audit a provozní logy

`audit_log` zachycuje změny rolí a vztahů, příspěvků, úhrad a historické opravy. Obsahuje autora, čas, typ změny, identifikaci dotčeného záznamu a případný důvod. Zápis auditu probíhá ve stejné transakci jako změna. Audit neukládá hesla, obsah soukromých zpráv ani kopie osobních údajů; přístup k němu má administrátor.

Cíl určuje název tabulky v `entity_type` a JSONB objekt se všemi sloupci primárního klíče v `entity_key`. Například `{"id":"<uuid>"}` u osoby nebo `{"guardian_person_id":"<uuid>","player_id":"<uuid>"}` u rodičovské vazby. Databáze odmítne prázdný objekt; backend ověří typy a úplnost klíče. Cizí klíč se nepoužívá, aby audit zůstal i po odstranění cíle. Oprava více objektů vytvoří záznam pro každý z nich. Audit eviduje provedenou operaci; historické skóre, části, statistické hodnoty a názvy uchovávají jejich vlastní neměnné revize, nikoli pouze auditní metadata.

Provozní logy slouží k řešení chyb a sledování běhu aplikace. Nenahrazují audit změn. Jejich konkrétní obsah a dobu uchování určí implementace.

## Zálohování a obnova

Pro PostgreSQL je navržen **pgBackRest**. Zálohy se ukládají mimo datový adresář databáze, ideálně na jiný disk.

- Fyzická záloha každých 12 hodin: jednou týdně plná, ostatní diferenciální.
- Průběžná archivace WAL pro obnovu k vybranému okamžiku.
- Uchování tří týdenních plných záloh, souvisejících diferenciálních záloh a potřebných WAL souborů.
- Pravidelná zkušební obnova.

Pro obnovu k určitému okamžiku je potřeba fyzická záloha a souvislá řada WAL; na logický dump nelze WAL přehrát. Lokální zálohy nechrání před ztrátou celého serveru. Vzdálená kopie ani detailní provozní postup nejsou součástí tohoto návrhu.
