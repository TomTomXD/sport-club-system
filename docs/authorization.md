# Autentizace a návrh oprávnění

**Stav E0 + P1–P3:** vedle identitního a týmového základu jsou implementované ADMIN účty/person-link/role/type/enabled/activate, guardian grant/revoke, Moje děti a omezený CHILD login se sportovním čtením. V1–V4 mají stále 12 tabulek, V4 přidává neveřejné auth_revision a guardy. Události, výsledky, finance, notifikace a komunikace jsou plánované. Provedené běhy jsou v [ověření](verification.md).

PENDING má pouze povolený auth/onboarding rozsah a prázdný kontext. Dokončený STANDARD ADMIN má MANAGE_CLUB, MANAGE_SPORTS, MANAGE_SEASONS, MANAGE_TEAMS, MANAGE_PLAYERS, MANAGE_MEMBERSHIPS, MANAGE_ACCOUNTS a MANAGE_GUARDIANS. ADMIN není automaticky trenér ani rodič všech dětí. USER/CHILD nemají globální správní schopnosti. OwnPlayerId spojuje stejnou osobu; teamContexts sjednotí dnešní vlastní hráčské, STANDARD trenérské a STANDARD rodičovské vztahy bez duplicit. Children obsahuje pouze vlastní neodvolané vazby s READ_CHILD; CHILD má children vždy [].

P2/P3 členství platí `[validFrom,validTo)`, NULL končí sezónou. Pro běžný seznam/detail/soupisku se ověří dnešní vztah k přesnému team_season v Europe/Prague; minulé či budoucí členství nestačí. Parent potřebuje neodvolanou vazbu a dnešní členství dítěte. Bez týmového vztahu seznam 403, cizí detail 404. ADMIN smí historické datum soupisky; ostatní jen dnešek. Own player a schválený rodič mají vlastní hráčovu členskou historii; trenér pouze historii aktuálně svěřených sezónních týmů. Items i počty používají stejný SQL scope. Minimální roster ani pro ADMIN neobsahuje personId, datum narození nebo kontakty. Soukromé osoby a kompletní seznam hráčů jsou ADMIN API.

CHILD vyžaduje USER, vlastní osobu/hráče a alespoň jednoho schváleného enabled STANDARD rodiče při aktivaci, loginu i každém requestu. Osoba rodiče bez účtu nebo disabled rodič nestačí. Jedna odvolaná vazba při jiné kvalifikované vazbě účet neblokuje; ztráta poslední nastaví enabled=false a zvýší auth_revision. Regrant a obnovení rodiče samy dítě nezapnou. Session porovnává původní revizi; ani nikdy nepoužitá stará cookie nesmí ožít po opětovné aktivaci. Vlastní onboarding/setup obnoví pouze očekávanou vlastní revizi. V4 zakazuje dosavadní unsupported CHILD z P2 bez domýšlení údajů.

Server má pro CHILD allowlist metody a cesty jen pro dostupné vlastní a scoped sportovní GET + auth/veřejné informace. Všechny mutace osoby, účtu, rolí/vazeb a veškeré finanční/notifikační cesty včetně součtů, exportů a read/count jsou 403. P3 ještě nemá finance/notifikace: jde o předběžnou ochranu, nikoli jejich funkční akceptaci. UI brána toto pravidlo nenahrazuje.

Person-link, změny role/typu/přístupu, aktivace a guardian grant/revoke vyžadují odůvodnění. Správní mutace používají klubový výhradní zámek před fresh auth; běžná čtení sdílený zámek. Posledního enabled STANDARD COMPLETE ADMIN nelze odebrat. STANDARD→CHILD odmítá ADMIN, jakoukoli zachovanou trenérskou historii a neodvolané vazby jako rodič; výsledná změna typu je disabled. Použitá osoba účtu se nepřepojuje. Vlastní zástupce, CHILD zástupce a cyklický řetězec se odmítají. Následující úplná matice dále obsahuje dosud neimplementované operace.

## P4: formáty a soutěžní konfigurace

`/match-formats`, `/competitions`, `/competition-seasons` a všechny jejich podzdroje vyžadují dokončeného enabled STANDARD ADMIN. UI používá existující MANAGE_CLUB, backend účet ověří sám. Detaily, seznamy, konfigurace i přihlášky včetně počtů jsou správní údaje; CHILD allowlist se nerozšiřuje. Čtení bere sdílený klubový zámek, mutace výhradní před fresh auth; CSRF a audit ve stejné transakci jsou povinné. Verze ročníku chrání společně pravidla i přihlášky. Archivovaný nebo historicky uzamčený ročník je pouze ke čtení; administrátorské přejmenování samotné soutěže/formátu nemění pravidla. Nejde o budoucí sportovní čtení výsledků/tabulek ostatních kontextů z P5/P6.

## Přihlašovací základ

Zachovává se `User`, `UserRepository`, `UserService` jako `UserDetailsService`, BCrypt a JSON login ze šablony. Spring udržuje session `JSESSIONID`; Next.js používá credentials a CSRF token při měnících požadavcích. Přihlášení rotuje identifikátor session, odhlášení ji invaliduje a odstraní cookie. Produkční cookie je HttpOnly, Secure přes HTTPS a s vhodným SameSite. Nezavádí se HTTP Basic, JWT ani druhá autentizace na frontendu.

Veřejná registrace nikdy nepřiděluje `ADMIN`, trenérské přiřazení, hráčský profil ani rodičovskou vazbu. Server nebere `role`, `accountType`, `personId`, `playerId`, `teamId` ani `guardianLinks` z requestu jako oprávnění. V cílové aplikaci vzniká běžný účet `USER`/`STANDARD`; omezený účet dítěte vyžaduje samostatnou administrátorsky řízenou aktivaci. Připojení existující osoby a profilu se ověřuje, nikdy se neděje jen podle shodného jména nebo klientem dodaného UUID.

Chybějící/neplatná session vrací JSON 401, nedovolená operace JSON 403 ve formátu centrálního handleru. Čtení cizího nepřístupného objektu vrací 404, aby neprozradilo jeho existenci. Přístupné objekty s nepovolenou změnou vracejí 403. Neplatný CSRF token vrací 403 i pro jinak přístupnou změnu. CORS pouze vymezuje origins, není autorizační pravidlo.

## Systémová role, typ účtu a klubové vztahy

Následující matice popisuje cílové operace po navazujících balíčcích; P1–P3 z ní implementují pouze výše vymezený rozsah. Účet má jednu systémovou roli `USER` nebo `ADMIN`. Běžný `STANDARD`/`USER` smí pracovat s vlastním účtem a povolenými vlastními údaji, číst své příspěvky a vlastní notifikace. Bez dalších vazeb nevidí týmové seznamy, kalendáře, jiné hráče ani konverzace. Registrace z uživatele automaticky nedělá hráče.

Klubové činnosti mají tyto podmínky:

- **Administrátor:** účet `STANDARD` s `users.role = ADMIN`; má správní oprávnění pro tento klub.
- **Trenér:** účet `STANDARD` a platné `coach_team_memberships.coach_user_id = currentUser.id` pro požadovaný tým a období. Samostatná tabulka trenérských profilů se nepoužívá.
- **Hráč:** účet i `player_profiles` odkazují přes `person_id` na tutéž osobu. Pro týmové operace se ověřuje také `player_team_memberships`. Účet může být `STANDARD` nebo omezený `CHILD`.
- **Rodič nebo zákonný zástupce:** účet `STANDARD`, jehož `users.person_id` odpovídá `guardian_links.guardian_person_id` u schválené, neodvolané vazby ke konkrétnímu hráči. Pro týmové operace se ověřuje i členství dítěte.

Trenér, hráč a rodič nejsou další hodnoty `users.role`. Jeden účet `STANDARD` může splňovat více podmínek současně; pro danou operaci se uplatní jeho skutečné vazby a systémová role. Trenér s rodičovskou vazbou může číst příspěvky svého dítěte díky této vazbě, nikoli díky trenérskému přiřazení. Zánik jedné vazby neodebere oprávnění, které stále plyne z jiné platné vazby.

Hráčský profil, včetně dítěte, může existovat bez účtu i bez rodičovské vazby. Dítě je evidovaná osoba a hráčský profil, nikoli další systémová role. Rodičovskou vazbu lze evidovat také k osobě bez přihlášení; samotná existence osoby nebo vazby nezakládá session, přístup ani doručení. Pro přihlášené rodičovské činnosti je nutný odpovídající účet `STANDARD`.

### Omezený účet dítěte

Účet `CHILD` má jen roli `USER` a vlastní hráčský profil přes shodné `person_id`. Nemůže být `ADMIN`, trenérem ani oprávněným zástupcem. Vzniká s `users.enabled = false`; administrátorská aktivace vyžaduje alespoň jednu schválenou, neodvolanou vazbu dítěte na osobu s účtem `STANDARD`, který má `enabled = true`. Typ účtu, datum narození, systémovou roli, identitu osoby a rodičovské vazby dítě samo nemění. Administrátorský převod typu účtu musí ověřit podmínky výsledného stavu; změna vlastního profilu tato omezení neobchází.

**Finanční přístup je pro CHILD vždy zakázaný**, včetně vlastních předpisů a úhrad, částek, zůstatků, součtů, exportů a finančních notifikací. Zákaz se kontroluje na backendu před zpřístupněním finančního rozsahu; shodné `person_id` jej nepřebíjí. `FEE_DUE_SOON` ani `FEE_OVERDUE` se dítěti nedoručují a jejich obsah ani počet se mu nevrací přes schránku nebo přímé API. Příspěvky dítěte spravuje a čte administrátor a jeho oprávněný rodič.

Po zániku poslední platné vazby nebo deaktivaci posledního odpovídajícího rodičovského účtu `STANDARD` dostane účet `CHILD` `enabled = false` a jeho session se invalidují. Platná rodičovská podmínka se kontroluje při přihlášení i každém autorizovaném požadavku; samotné `enabled = true` nebo stará session nestačí. Obnovení vyžaduje opět platnou schválenou vazbu a administrátorskou aktivaci. Profil, členství a sportovní historie dítěte se nemažou a oprávněný trenér je může dále spravovat.

| Operace finální aplikace | ADMIN (STANDARD) | Trenér podle přiřazení | Hráč s vlastním účtem | Rodič podle vazby (STANDARD) |
| --- | --- | --- | --- | --- |
| Správa klubu, sezón, týmů, osob a soutěží | Ano, klub | Jen povolené vlastní údaje | Jen povolené vlastní údaje; CHILD nemění chráněná pole | Jen povolené vlastní údaje, dítě dle vazby |
| Přidělení ADMIN, typ účtu, propojení osoby, rodičovské vazby a členství | Ano, s auditem a validací | Ne | Ne | Ne, může nejvýše požádat o ověření |
| Týmový kalendář | Ano | Přiřazené týmy/sezóny | Týmy vlastního členství | Týmy schválených dětí |
| Vytvoření a změna události | Ano | Všechny dotčené týmy musejí být přiřazené trenérovi | Ne | Ne |
| Dostupnost/omluva hráče | Oprava s důvodem | Přiřazení samo dostupnost hráče nepotvrzuje | Jen vlastní profil | Jen schválené dítě |
| Vytvoření a publikování nominace | Ano | Přiřazený tým a jeho platní účastníci | Ne | Ne |
| Zápis skutečné docházky a nastoupení | Ano | Přiřazený tým a platný účastník | Ne | Ne |
| Zápis výsledku a statistik | Ano | Zápas přiřazeného týmu v otevřené sezóně | Ne | Ne |
| Soutěžní tabulky a týmové statistiky | Ano | V kontextu přiřazených týmů | V kontextu vlastních týmů | V kontextu týmů dětí |
| Individuální historie hráče | Ano | Jen oprávněný tým/sezóna, bez cizích osobních údajů | Vlastní profil | Schválené dítě |
| Definice statistik, bodování a kritérií pořadí | Ano | Ne | Ne | Ne |
| Čtení příspěvků a plateb | Všichni členové klubu | Jen vlastní; dítě jen s rodičovskou vazbou | STANDARD vlastní; CHILD nikdy | Vlastní a předpisy schváleného dítěte |
| Zápis předpisu, částečné úhrady nebo storna | Ano s auditem | Ne | Ne | Ne |
| Čtení notifikace/označení přečtení | Vlastní schránka | Vlastní schránka | Vlastní schránka; CHILD bez finančních oznámení | Vlastní schránka |
| Čtení/odeslání zprávy | Aktivní účastník s oprávněným týmovým kontextem | Aktivní účastník v přiřazeném týmu | Aktivní účastník ve vlastním týmu | Aktivní účastník v týmu dítěte |
| Řízená oprava archivovaných sportovních údajů | Ano s důvodem, novou revizí výsledku/statistik a přepočtem | Ne | Ne | Ne |

Matice popisuje návrh, nikoli implementované endpointy. Rozhoduje **typ účtu + systémová role + vztah k objektu + čas + stav**. Omezení `CHILD` se uplatní před skládáním oprávnění. Správní role sama nezakládá účast v soukromé konverzaci.

### Provoz jediným trenérem

Není nutná samostatná osoba administrátora. Jediný provozovatel může používat jeden účet `STANDARD` s `ADMIN` a vlastním trenérským přiřazením. Založí klub, sezónu, vlastní tým a hráčské profily a spravuje sportovní i správní agendu. Hráči nemusejí mít účty a rodičovské vazby nemusí existovat. Účet dítěte se přidá teprve při splnění podmínek pro `CHILD`; nepřihlášený hráč takovou podmínku nemá.

**Prvotní nastavení P1:** přihlášený účet `STANDARD` s `enabled = true` a dokončenou vlastní osobou převezme správu pomocí jednorázového instalačního tokenu z prostředí provozovatele. Prázdný `CLUB_SETUP_TOKEN` setup vypíná. Token nemá výchozí hodnotu a veřejná registrace ani pořadí registrací nenahrazuje jeho ověření. Transakce pod `FOR UPDATE` nad klubem ověří token, nedokončené nastavení a absenci prvního administrátora, přidělí `ADMIN` a uloží `club.setup_completed_at` a `setup_completed_by`. Po dokončení není token znovu použitelný ani při pozdějším odebrání role; souběžné převzetí smí uspět jen jednou. `CHILD` nesmí správu převzít. Token se neukládá do DB, auditu ani logů.

P1–P3 poskytují setup, sporty, týmy, hráče, členství, účty, rodiče a omezené dětské přihlášení. Navazující událostní a finanční činnosti ještě nejsou dostupné. Existující ADMIN při V2 zachová práva a uzavře bootstrap; i on nejdříve doplní vlastní osobu. Veřejná registrace nadále vytváří `USER`/`STANDARD`. Samostatný provoz nepřidává práva ostatním účtům. Konverzace stále vyžaduje alespoň dva oprávněné účastníky; bez dalších účtů se tato funkce nepoužívá.

## Kontrola objektu na serveru

Service získá účet z `SecurityContext`, potom společný zámek klubu podle [databázového modelu](database.md#integrita-a-souběžné-operace): změna `FOR UPDATE`, čtení `FOR SHARE`. Znovu načte typ účtu, `enabled`, systémovou roli, vztahy a závislá data v téže transakci; nepovolený účet dál do klubového rozsahu nepustí. Klientem zaslané ID určuje cíl, nikdy identitu aktéra. Autora a čas doplňuje server; DTO nepřijímá pole jako `recordedBy`, která by měnila vlastnictví.

1. **Účet dítěte:** nejdříve ověř platnou vazbu na aktivní rodičovský účet `STANDARD` a povolenou operaci. Blokovaný `CHILD` nepokračuje do klubového rozsahu. Všechny finanční cesty odmítni i při vlastním `person_id`.
2. **Hráč:** spoj `users.person_id` s `player_profiles.person_id`. Pro týmovou operaci ověř členství k datu události a uzávěrku. Cizí `playerId` odmítni i ve stejném týmu, pokud účet nemá další platné oprávnění. Po přestupu zůstává vlastní historie přístupná, nové soukromé informace původního týmu nikoli.
3. **Rodič:** spoj `users.person_id = guardian_links.guardian_person_id`, ověř `STANDARD`, schválení a neodvolaný vztah ke konkrétnímu dítěti. Žádná hodnota `PARENT` v systémové roli není potřebná. Ověř členství dítěte v období cílové události. Přehled Moje děti i jeho počty používají tutéž vazbu. Odvolaný vztah sám nedává přístup ani k historii dítěte.
4. **Trenér:** spoj účet s `coach_team_memberships.coach_user_id` a ověř aktuální přiřazení k relevantnímu týmu. Změna společné akce vyžaduje všechny dotčené týmy, nebo správní oprávnění. Starší sezóna vyžaduje aktuální oprávnění k týmu a odpovídající historické přiřazení; nově přijatý trenér nezíská automaticky osobní historii minulých sezón. Ukončené přiřazení samo další přístup nedává.
5. **Finance:** nejdříve odmítni `CHILD`. U vlastního předpisu porovnej `fee_assessments.person_id = currentUser.person_id`. Předpis dítěte zpřístupni rodiči přes jeho platnou vazbu k hráčskému profilu příslušné osoby, nebo `ADMIN`. Úhradu autorizuj přes `fee_assessment_id`; samotné UUID přístup nezakládá. Zápis a storno smí jen `ADMIN`; součet platných úhrad nesmí překročit předpis.
6. **Administrátor:** ověř `STANDARD`, `ADMIN` i přípustný stav operace. Správní rozsah neruší validaci, CSRF, historii, finanční limity ani pravidla konverzací. Změny typu účtu, role, rodičovské vazby, platby a archivované opravy mají povinný audit. Administrátor může spravovat i vlastní trenérské přiřazení.
7. **Komunikace:** každý list/read/send vyžaduje aktivní `conversation_participants` a oprávnění k týmovému kontextu. Zprávy zpřístupni až od `joined_at`. UUID ani historická účast nestačí po zániku posledního oprávnění. Rodič bez vazby na dítě v týmu se nepřidá. Odchod nastaví `left_at`; ukončená účast se znovu neotevírá, další komunikace používá novou konverzaci.
8. **Notifikace:** dotaz na `notification_deliveries` vždy omezuje `recipient_id = currentUser.id`; u `CHILD` navíc vyloučí finanční typy i souhrnné počty. Finanční oznámení se dítěti nevytvářejí ani nepřidělují při pozdějším přepočtu adresátů. Otevření cíle se autorizuje znovu. Oznámení neobsahuje soukromé zprávy ani citlivé údaje dítěte; doručení neobnovuje odvolaná práva.

Listy, hledání, exporty, počty a detaily používají stejné filtrování. Nefiltruje se až po načtení všech dat do frontendu. DTO vrací jen povolená pole: kalendář nepotřebuje datum narození či telefon všech hráčů a finanční stav nepatří do obecného hráčského seznamu. Dashboard ani agregace nesmějí dítěti nepřímo prozradit finanční částky nebo počty oznámení.

Session obsahuje identitu, ale typ účtu, role a citlivé vazby se ověřují čerstvě. Odebrání `ADMIN` musí invalidovat session nebo se aktuální role ověří z DB při každém privilegovaném požadavku. Zánik trenérského přiřazení a rodičovské vazby platí při dalším požadavku i se starou session; jiné platné oprávnění se posoudí samostatně. Zánik poslední potřebné rodičovské vazby u `CHILD` navíc blokuje účet a invaliduje jeho session. Kontrola `version` brání přepsání zastaralým formulářem, nenahrazuje autorizaci.

## Negativní akceptační scénáře navazujících etap

Jde o **celou cílovou testovací matici**, nikoli prohlášení, že je kompletně provedena. P1–P3 pokrývají dostupné identity/týmy/rodiče/CHILD hranice, podrobnosti a počty běhů jsou v verification.md. Finance/notifikace jsou v P3 pouze deny testy před implementací; událostní, výsledkové a komunikační scénáře vzniknou s jejich funkcemi. Pokud scénář uvádí jen trenéra, hráče či rodiče, účet nemá další oprávnění, které by pokus dovolovalo.

| ID | Pokus | Očekávaný výsledek |
| --- | --- | --- |
| A01 | Registrovat `role: ADMIN`, vlastní `accountType`, cizí person/player ID nebo rodičovskou vazbu | Běžný USER bez přidělení klubových práv a bez připojení cizí osoby |
| A02 | USER bez klubových vztahů otevře týmový seznam nebo cizí osobu | 403, případně 404 pro cizí objekt; vlastní povolené údaje zůstávají přístupné |
| A03 | Hráč A změní dostupnost hráče B změnou UUID | 404/403 dle viditelnosti, nulový zápis a žádné oznámení |
| A04 | Účet bez schválené nebo s odvolanou vazbou otevře dítě | 404; žádný profil, příspěvky, historie ani počty dítěte |
| A05 | Rodič dítěte A získá UUID předpisu či úhrady dítěte B | 404, žádné cizí finanční údaje |
| A06 | Trenér A publikuje nominaci týmu B | 404/403, nulový zápis; přiřazení k A neopravňuje spravovat B |
| A07 | Trenér A změní společnou akci A+B bez přiřazení k B | 403, žádná částečná změna |
| A08 | Účet po ukončení posledního trenérského přiřazení použije platnou session | Další trenérský přístup odmítnut; vztah se kontroluje na serveru |
| A09 | Účet pouze s hráčskou nebo rodičovskou vazbou zapíše platbu, výsledek, docházku či nominaci | 403, finanční a sportovní stav beze změny |
| A10 | Trenér čte příspěvky svěřence bez rodičovské vazby | 404; samotné trenérské přiřazení dovoluje jen vlastní finance |
| A11 | Účastník čte cizí konverzaci nebo přidá neoprávněného rodiče; ADMIN bez účasti čte soukromé zprávy | 404/403; správní role automatický vstup nedává |
| A12 | Uživatel označí cizí oznámení jako přečtené | 404; cizí `read_at` beze změny |
| A13 | Rodiči zanikne poslední vazba po doručení oznámení a otevře detail dítěte | 404; historické doručení neobchází autorizaci |
| A14 | Dva ADMIN požadavky souběžně připíšou úhrady překračující zůstatek | Zámek operace seřadí; překračující úhrada se odmítne, platný součet nepřekročí předpis |
| A15 | Trenér opraví archivovaná sportovní data nebo odešle zastaralou verzi nominace | 403, respektive 409; stav se nezmění |
| A16 | ADMIN odebere trenérské přiřazení nebo ADMIN jinému účtu; tento použije starou session | Privilegovaný požadavek odmítnut, pokud jej nedovoluje jiné aktuální oprávnění |
| A17 | Chybí CSRF, session nebo je origin nepovolený | 403 pro CSRF, 401 pro autentizaci; nepovolený origin nedostane credentialed CORS a změna se neprovede |
| A18 | Zápis statistiky cizího sportu, neplatné definice pro zápas nebo hodnoty hráče bez skutečného nastoupení | 400/409; žádná hodnota ani přepočet |
| A19 | USER si přidělí ADMIN nebo se sám přiřadí jako trenér | 403; správa vlastního účtu klubová práva nevytvoří |
| A20 | Rodič dosadí cizí `playerId` při vytvoření vazby nebo v Moje děti | Žádná automatická vazba; cizí detail/počty 404 a nepatří do seznamu dětí |
| A21 | CHILD načte vlastní předpis, platbu, zůstatek, součet nebo finanční export přímým API | 403/404 bez finančních údajů; shodné vlastní `person_id` není výjimka |
| A22 | CHILD načte FEE oznámení přímo přes UUID, schránku nebo její počet; označí je jako přečtené | Žádný finanční obsah, doručení ani počet; přímý přístup odmítnut a nic se nezmění |
| A23 | CHILD změní typ účtu, datum narození, systémovou roli, osobu nebo rodičovské vazby | 403; žádná eskalace ani obcházení omezeného účtu |
| A24 | Aktivovat CHILD bez vlastního hráčského profilu nebo bez schválené vazby na aktivní STANDARD rodičovský účet | Aktivace odmítnuta; pouhá osoba rodiče bez účtu nestačí |
| A25 | Poslední potřebná rodičovská vazba CHILD zanikne nebo se poslední STANDARD rodič deaktivuje; dítě použije starou session či nové přihlášení | `enabled = false`, session invalidovaná; žádný další klubový přístup ani při zastaralé session autoritě |
| A26 | CHILD se nastaví jako ADMIN, trenér nebo oprávněný zástupce kteréhokoli hráče | Neplatný stav odmítnut i při administrátorském zápisu |
| A27 | CHILD převezme prvotní správu; STANDARD použije chybný token nebo opakuje převzetí po dokončení | Zamítnuto; role a stav nastavení se nezmění |

Pozitivní protějšky ověří rodiče se dvěma dětmi, dítě s více zástupci, hráče bez účtu se zástupcem i bez něj, trenéra více týmů a kombinaci trenérské a rodičovské vazby. Samostatný scénář převezme prvotní správu platným tokenem a použije jediný STANDARD/ADMIN účet s vlastním trenérským přiřazením a profily hráčů bez dalších účtů či rodičů. Dva souběžné pokusy o převzetí pod zámkem musejí dát právě jeden úspěch. Běžný USER přitom vidí jen vlastní povolená data. Platný CHILD čte vlastní sportovní údaje a zadává dostupnost, zatímco finance vidí rodič a ADMIN. Ztráta jedné z více platných rodičovských vazeb dítě neblokuje; ztráta poslední ano. Po 401 UI nabídne přihlášení, po 403/404 srozumitelnou chybu a výpadek API nezamění za odhlášení.
