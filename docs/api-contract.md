# REST kontrakt: implementované P1–P4 a navazující plán

**E0 + P1–P4:** identita/setup/sporty, ADMIN osoby/sezóny/týmy/hráči/členství, účty/rodiče, scoped soupisky/historie a omezené CHILD, formáty, soutěže, ročníky, datová pravidla a přihlášky mají implementaci. Události, výsledky a výpočet pořadí/statistik, finance, notifikace a komunikace jsou plánované v P5–P9. [Ověření](verification.md) zaznamenává provedené běhy; [DBML](sport-club.dbml) popisuje celý cílový model.

## Implementované adresování a autentizace

Všechny cesty jsou relativní k `/api`: `/auth/me` znamená `/api/auth/me`. Browser používá `NEXT_PUBLIC_API_URL=http://localhost:8080/api`; Spring controller mapping nepřidává další servlet prefix.

| Metoda a cesta | Odpověď / chování |
| --- | --- |
| `GET /auth/csrf` | 200 `{token,headerName,parameterName}`; session CSRF, hlavička X-CSRF-TOKEN |
| `POST /auth/register` | `{username,password}` → 201 UserResponse; USER/STANDARD/PENDING, bez přihlášení a dalších vazeb |
| `POST /auth/login` | `{username,password}` → 200 UserResponse, změna session ID |
| `GET /auth/me` | 200 UserResponse aktuálního povoleného STANDARD nebo platného CHILD účtu; jinak 401 |
| `POST /auth/logout` | 200 bez těla; invalidace session a odstranění cookie |
| `GET /info/version` | 200 `{version}` |
| `GET /info/release-notes`, `/info/terms-of-service`, `/info/privacy-policy` | 200 `{content}` |
| `GET /actuator/health` | Minimální veřejný health |

Username má 3–50 znaků, heslo 6–72 znaků a současně nejvýše 72 UTF-8 bajtů. Request neuděluje role, typ účtu ani person ID. P1 UserResponse obsahuje explicitně nullable personId a nikdy hash, legacy_role nebo setup token:

```json
{"id":"00000000-0000-0000-0000-000000000001","username":"clen","role":"USER","accountType":"STANDARD","personId":null,"profileState":"PENDING","createdAt":"2026-10-27T10:00:00Z"}
```

`profileState` je odvozené PENDING/COMPLETE, nikoli DB role. V2 převádí staré role COACH/PLAYER/PARENT na USER a zachovává ADMIN. UUID, BCrypt a časy zůstávají. Dočasně nullable users.person_id dovolí onboarding bez vymyšlených jmen; NOT NULL přijde po pozdějším ověřeném backfillu. Registrace zatím nevytváří Person, účet ji doplní následným vlastním POSTem. P3 připouští pouze povolený CHILD s vlastním hráčským profilem a aktivním schváleným STANDARD rodičem. Deaktivovaný nebo neplatný dětský účet odmítá při loginu i každém požadavku.

## Implementované P1: vlastní osoba, kontext, setup a sporty

| UI / metoda a cesta | Payload a výsledek |
| --- | --- |
| `/profile`: `GET /me/person` | 200 vlastní PersonResponse; 404, pokud osoba zatím není doplněná |
| `/profile`: `POST /me/person` | `{firstName,lastName,birthDate?,email?,phone?}` → 201 PersonResponse; pouze vlastní STANDARD PENDING účet, vytvoření a propojení pod zámkem |
| `GET /me/context` | 200 AccountContext; i PENDING má prázdné capabilities, ownPlayerId=null a prázdné teamContexts/children |
| `/setup`: `GET /club/setup-status` | COMPLETE STANDARD → 200 `{completed,available,clubName}`; PENDING → 403 PROFILE_INCOMPLETE |
| `/setup`: `POST /club/setup` | `{token}` → 200 nový UserResponse s ADMIN; dokončený enabled STANDARD, nakonfigurovaný správný token, nedokončený setup a žádný dosavadní ADMIN |
| `/admin/sports`: `GET /sports?page=0&size=20` | ADMIN → 200 stránka SportResponse; page >= 0, size 1–100, stabilní řazení code a id |
| `POST /sports` | ADMIN `{code,name}` → 201 SportResponse; active=true |
| `PATCH /sports/{id}` | ADMIN `{name?,active?}` → 200 SportResponse; alespoň jedna změna, kód se nemění |

PersonResponse je `{id,firstName,lastName,birthDate,email,phone}`; poslední tři pole jsou explicitně nullable. Jméno a příjmení jsou povinné, každé nejvýše 100 znaků; birthDate není v budoucnosti, email je volitelný platný text nejvýše 254 znaků a telefon nejvýše 40. Self-service nemá následnou editaci; P2 přidává ADMIN PUT /persons/{id}. P3 ADMIN ověřeně propojí dosud neúplný účet s existující osobou, nikdy nepřepojí použitou identitu. Opakovaný POST po propojení vrací 409 PROFILE_ALREADY_LINKED.

AccountContext neobsahuje budoucí finanční ani událostní oprávnění. P3 vrací ADMIN osm správních schopností; teamContexts vzniká pouze ze skutečných aktuálních vazeb. Příklad ADMIN bez členství nebo vlastních dětí:

```json
{"accountType":"STANDARD","capabilities":["MANAGE_CLUB","MANAGE_SPORTS","MANAGE_SEASONS","MANAGE_TEAMS","MANAGE_PLAYERS","MANAGE_MEMBERSHIPS","MANAGE_ACCOUNTS","MANAGE_GUARDIANS"],"ownPlayerId":null,"teamContexts":[],"children":[]}
```

STANDARD/USER a PENDING dostávají globální capabilities `[]`; dokončení osoby samo klubová práva nezaloží. Server znovu čte aktuální roli, typ a enabled i při staré session. Žádné klientem zaslané capabilities nemají autorizační význam.

`CLUB_SETUP_TOKEN` patří pouze backendu. Prázdná konfigurace znamená available=false a POST 503 SETUP_UNAVAILABLE; chybný token 403 FORBIDDEN, dokončený setup 409 SETUP_ALREADY_COMPLETED. Token jde jen v těle CSRF chráněného POSTu, nikoli v URL, odpovědi, logu, auditu či NEXT_PUBLIC proměnné. V2 s existujícím ADMIN uzavře setup už při migraci; před správou sportů si i tento účet doplní osobu. Nový platný setup atomicky změní roli, dokončení klubu a audit; nesmí uspět dvakrát.

SportResponse je `{id,code,name,active}`. Kód odpovídá `[A-Z][A-Z0-9_]{0,39}`, název je neprázdný a nejvýše 100 znaků. Duplicitní kód vrací 409 DUPLICATE_RESOURCE. Sporty se pouze deaktivují, DELETE ani samostatný GET detail P1 neposkytuje. Seznam vrací všechny aktivní i deaktivované sporty; UI nevymýšlí nepodporované filtrování/sort.

```json
{"items":[],"page":0,"size":20,"totalElements":"0","totalPages":0}
```

P2/P3 níže rozšiřují osoby, hráče, týmy, účty a rodičovské vazby. Finanční a ostatní navazující API ještě neexistují.

## Společná pravidla a rozšíření navazujících kontraktů

- Zachovat JSON + `JSESSIONID`, Axios `withCredentials`, HttpOnly cookie a Secure v HTTPS produkci. Nevzniká JWT, NextAuth ani druhá autorizace v Next.js.
- Před každým POST/PUT/PATCH/DELETE klient získá nový `/auth/csrf` a pošle token v `X-CSRF-TOKEN`; token necacheuje přes login/logout. Změnové požadavky se při timeoutu ani 403 automaticky neopakují.
- Backend před každým čtením/zápisem ověří účet, typ, aktuální vazby, tým, sezónu a stav. UI capabilities pomáhají navigaci, nepředstavují oprávnění poslané klientem.
- Chybějící/neplatná session → 401; zakázaná změna přístupného objektu/CSRF → 403; cizí nepřístupný objekt → obecná 404 bez potvrzení existence. Výpadek není anonymní session.
- DTO používají camelCase, UUID jako řetězce, datum `YYYY-MM-DD`, okamžik ISO 8601 s offsetem/UTC. Sezóny a lokální časy se vyhodnocují v Europe/Prague.
- JSON `bigint`/BigInteger, všechny `version` a `resultsRevision` jsou desetinné **řetězce**, například `"3"`; JavaScript je nesmí převádět přes nepřesný `number`. Java používá typ odpovídající rozsahu DB, TypeScript wire typ `string`.
- Peníze `numeric(14,2)` a decimal statistiky `numeric(20,6)` jsou řetězce bez exponentu, NaN či Infinity; například `"400.00"`, `"12.500000"`. `integerValue` pro PostgreSQL bigint je také řetězec. Skóre a pořadová čísla typu integer mohou být JSON čísla v rozsahu DB.
- Backend ověří přesnost a meze, nic tiše nezaokrouhlí. Prázdná statistika není nula. Finanční zůstatky, agregace a oficiální pořadí vypočítá server.

### Seznamy a souběh

`GET` seznamy používají `page=0`, `size=20`, nejvýše `size=100`; záporná stránka či neplatná velikost vrátí 400. Filtry a sort mají whitelist; řazení doplňuje stabilní ID. `items` i celkové počty jsou autorizované již v dotazu.

```json
{"items":[],"page":0,"size":20,"totalElements":"0","totalPages":0}
```

Mutace verzovaného agregátu posílá jeho očekávanou `version`; odpověď vrátí novou. DBML má verzi u events, event_participants, matches, competition_seasons, match_formats, statistic_definitions a fee_assessments. Výsledková revize vlastní optimistic version nemá: její změny kontrolují `matches.version`. U tabulek bez sloupce version se verze nevymýšlí; použije se společný zámek klubu a kontrola stavu. Další optimistic sloupce by vyžadovaly výslovnou migraci.

### Chybová odpověď

P1 zachovává `timestamp`, `code`, `message` a volitelné `details: string` a přidává volitelné strukturované `fieldErrors`; původní klient nemusí parsovat text details. Zprávy jsou bezpečné, bez SQL, stack trace, `rejectedValue`, hesel, tokenů, cookies a cizích osobních údajů.

```json
{
  "timestamp":"2026-10-27T10:00:00Z",
  "code":"VALIDATION_ERROR",
  "message":"Zkontrolujte vyplněné údaje.",
  "details":"Některá pole nejsou platná.",
  "fieldErrors":[{"field":"birthDate","code":"INVALID_VALUE","message":"Datum narození nesmí být v budoucnosti."}]
}
```

P1–P3 používají PROFILE_ALREADY_LINKED, DUPLICATE_RESOURCE, SETUP_ALREADY_COMPLETED, MEMBERSHIP_OVERLAP, CONFIGURATION_LOCKED, STATE_CONFLICT, CHILD_ACTIVATION_CONFLICT a LAST_ADMIN_REQUIRED; ostatní doménové 409 kódy v tabulce jsou plánované. Obecný CONFLICT zůstává pro kompatibilitu a neznamená vždy obsazené jméno. P1 dále vrací 403 PROFILE_INCOMPLETE a 503 SETUP_UNAVAILABLE pro vypnutý setup.

| HTTP 409 code | Význam / reakce UI |
| --- | --- |
| `OPTIMISTIC_LOCK_CONFLICT` | Zastaralá verze; zachovat rozepsaná data a nabídnout nové načtení |
| `DUPLICATE_RESOURCE` | Duplicitní jméno/kód či jedinečná dvojice; případné fieldErrors |
| `PROFILE_ALREADY_LINKED` | Účet/osoba/profil již propojen; žádné automatické připojení jiné identity |
| `MEMBERSHIP_OVERLAP` | Překryv stejného hráče/trenéra a sezónního týmu |
| `HISTORY_CONFLICT` | Změna by zneplatnila historickou nebo nevyřešenou budoucí účast |
| `CONFIGURATION_LOCKED` | Použitý formát, pravidla či význam statistiky nelze přepsat |
| `RESULT_BASE_CONFLICT` | Oprava nenavazuje na aktuální finální výsledkovou revizi |
| `PAYMENT_ID_CONFLICT` | Stejné UUID úhrady použité pro jiný obchodní payload |
| `PAYMENT_EXCEEDS_BALANCE` | Úhrada by překročila aktuální zůstatek |
| `SETUP_ALREADY_COMPLETED` | Jednorázové převzetí správy již proběhlo |
| `LAST_ADMIN_REQUIRED` | Změna by odstranila posledního enabled STANDARD ADMIN s dokončenou osobou |
| `CHILD_ACTIVATION_CONFLICT`, `STATE_CONFLICT` | Chybí aktuální podmínka aktivace, případně jiný nepovolený přechod stavu |

Neplatný vstup/typ/rozsah → 400 `VALIDATION_ERROR` nebo `BAD_REQUEST`; nepovolená činnost → 403 `FORBIDDEN`. Kódy konfliktů ani jejich detaily nesmějí předcházet kontrole přístupu.

## Implementovaný kontext P3

Dokončený STANDARD ADMIN má osm správních capabilities výše. USER a CHILD nemají globální správní schopnosti. ownPlayerId se odvozuje ze stejné osoby i bez aktuálního členství. teamContexts je union dnešního vlastního hráčského, trenérského a schváleného dětského členství bez duplicit, každý `{teamSeasonId,capabilities:["READ_TEAM"]}`. ADMIN není automaticky trenérem všech týmů.

STANDARD children obsahuje jen vlastní neodvolané vazby `{playerId,displayName,capabilities:["READ_CHILD"]}`, nezávisle na účtu nebo aktuálním členství dítěte. CHILD má children `[]` a týmové kontexty pouze vlastního hráče; nikdy trenérské nebo rodičovské. PENDING má stále null/[] a jen povolený vlastní onboarding. READ_OWN_FEES, event/message capabilities ještě neexistují. Nullable users.person_id zůstává přechodně kvůli skutečným neúplným účtům; NOT NULL vyžaduje pozdější ověřený backfill a samostatnou migraci.

## Implementované P2: sezóny, týmy, osoby a členství

Čtení vyžaduje dokončený enabled účet s povoleným rozsahem; P3 povoluje CHILD pouze vlastní sportovní čtení. Všechny změny a soukromé seznamy vyžadují STANDARD ADMIN. Žádné DELETE. PageResponse je vždy `{items,page,size,totalElements:string,totalPages:number}`, page od 0, size 20, max 100. Seznamy mají stabilní řazení a stejně omezené counts jako items.

| Metoda/cesta | Payload, výsledek a hranice |
| --- | --- |
| ADMIN GET/POST `/seasons`; PATCH `/seasons/{id}` | POST `{name,startsOn,endsOn}`, PATCH změněná pole; response `{id,name,startsOn,endsOn,archived}`. Název 1–100 unikátní, interval platný. Použité hranice se nemění. |
| ADMIN POST `/seasons/{id}/archive` | `{}` → 200 SeasonResponse, opakování bezpečně zachová archivaci. Archivovaná sezóna nepovoluje běžné změny. |
| ADMIN GET/POST `/teams`; PATCH `/teams/{id}` | GET filtr sportId; POST `{sportId,name,isClubTeam}`, PATCH změněná pole. Response navíc id/sportName. Nový sport musí být aktivní, název 1–120; po prvním TeamSeason sport/typ neměnné. Základní rename nemění sezónní displayName. |
| GET `/team-seasons?seasonId&sportId`; GET `/team-seasons/{id}` | ADMIN všechny, ostatní pouze dnešní vlastní/trenérské vztahy nebo členství schválených dětí k přesnému team_season. Bez vztahu seznam 403; cizí detail 404. |
| ADMIN POST `/team-seasons`; PATCH `/team-seasons/{id}` | POST `{teamId,seasonId,displayName}` → 201, PATCH `{displayName}` → 200. Jedinečná dvojice, otevřená sezóna a aktivní sport; identity neměnné. |
| GET `/team-seasons/{id}/players?on=YYYY-MM-DD` | Minimální roster `{playerId,firstName,lastName,membershipId,validFrom,validTo}`. Žádné personId, kontakty či datum narození ani pro ADMIN. on výchozí dnešek Europe/Prague, jiné datum jen ADMIN. |
| ADMIN GET/POST `/persons`; GET/PUT `/persons/{id}` | P1 PersonRequest/Response. PUT nahrazuje všechna osobní pole; datum narození nelze vymazat u osoby s hráčem. Identity se nepřepojují. |
| ADMIN GET/POST `/players` | Response seznamu/vytvoření `{id,personId,firstName,lastName,birthDate}`. POST právě jedna varianta `{personId}` nebo `{person:{firstName,lastName,birthDate,email?,phone?}}`; druhá atomicky založí osobu i hráče. DOB povinné, nebudoucí; jedna osoba nejvýše jeden profil. Žádný povinný účet nebo rodič. |
| GET `/players/{id}` | Pouze `{id,firstName,lastName}`. ADMIN, vlastní profil přes osobu, schválený STANDARD rodič nebo aktuální trenér stejného aktuálního hráčského týmu; ostatní 404. |
| GET `/players/{id}/memberships` | Page `{membershipId,teamSeasonId,displayName,seasonName,sportName,validFrom,validTo}`. ADMIN/vlastní hráč/schválený rodič celá oprávněná hráčova historie; trenér jen jeho aktuálně svěřené team_seasons. |
| ADMIN GET/POST `/team-seasons/{id}/player-memberships` | POST `{playerId,validFrom,validTo}` → 201 `{id,teamSeasonId,playerId,firstName,lastName,validFrom,validTo}`; GET stránka stejných DTO. |
| ADMIN GET/POST `/team-seasons/{id}/coach-memberships` | POST `{coachUserId,validFrom,validTo}` → 201 `{id,teamSeasonId,coachUserId,username,firstName,lastName,validFrom,validTo}`; GET stránka. Trenér musí být enabled STANDARD s osobou; ADMIN může vybrat sebe. |
| ADMIN PATCH `/player-memberships/{id}`, `/coach-memberships/{id}` | Úplný interval `{validFrom,validTo}` včetně explicitního null konce; dvojice a ID se nemění. |
| ADMIN GET `/admin/coach-candidates` | Page `{id,username,firstName,lastName}` jen způsobilých účtů, žádné heslo nebo správa rolí. |

TeamSeasonResponse je `{id,teamId,seasonId,sportId,sportName,teamName,displayName,isClubTeam,seasonName,startsOn,endsOn,archived}`. Vlastní i externí týmy sdílejí API, soupeř však nemá interní členství. Intervaly jsou `[validFrom,validTo)`, NULL končí sezónou. Musí celé ležet v sezóně, stejná dvojice se nesmí překrývat; sousední intervaly a souběh v jiném týmu jsou dovolené. Archivace uchová read při existujícím vztahu a zamkne změny. Žádné automatické událostní přestupy ještě neexistují: P5 doplní transakční kontrolu tehdy vznikajících účastí.

Členství PATCH je výslovná výjimka z partial PATCH: oba datumové klíče jsou povinné, aby vymazání konce bylo záměrné. Seasons/teams partial PATCH rozlišuje absent a explicitní null; null se odmítne a mění se pouze zaslaná pole. Tabulky P2 nemají optimistic version; souběh řeší klubový zámek, stav a DB constraints. Všechna data/oprávnění jedné operace používají stejný kalendářní den.

Frontend `/admin`, `/admin/seasons`, `/admin/teams`, `/admin/players` a `/admin/teams/[teamSeasonId]` spravuje skutečná data; `/teams`, `/teams/[teamSeasonId]` a `/players/[playerId]` mají pouze povolené čtení. NonADMIN historie soupisek starých team_seasons není P2 oprávnění; vlastní členská historie zůstává přístupná. Rozšíření o historické výsledkové pohledy přijde s P5/P6.

## Implementované P3: účty, rodiče a CHILD

Všechny cesty v této tabulce kromě GET `/me/children` a GET `/me/children/{playerId}` vyžadují aktuální STANDARD ADMIN s dokončenou osobou; každý zápis je CSRF chráněný. AccountResponse je `{id,username,role,accountType,enabled,personId,profileState,firstName,lastName,createdAt}`. PersonId a jméno/příjmení mohou být null u starého neúplného účtu. Hash, heslo, legacyRole a authRevision se nikdy nevracejí.

| Metoda/cesta | Payload, výsledek a podmínky |
| --- | --- |
| GET `/admin/users`, GET `/admin/users/{id}` | Page AccountResponse nebo detail; žádná hesla ani libovolné přepínání cílové osoby |
| POST `/admin/users` | `{personId,username,password,accountType}` → 201 AccountResponse. Existující ověřená osoba bez účtu, username 3–50, heslo 6–72 znaků a nejvýše 72 UTF-8 bajtů. Vždy USER; STANDARD enabled=true, CHILD enabled=false s vlastním hráčským profilem. |
| POST `/admin/users/{id}/person-link` | `{personId,reason}` → 200 AccountResponse. Jen dosud personId=null a osoba bez jiného účtu; zachová UUID, hash a createdAt. Žádné automatické matching jmen nebo přepojení existující identity. |
| PATCH `/admin/users/{id}/role` | `{role,reason}` → 200; ADMIN jen STANDARD, jeho správní činnosti vyžadují dokončenou osobu; CHILD vždy USER |
| PATCH `/admin/users/{id}/account-type` | `{accountType,reason}` → 200. STANDARD→CHILD vyžaduje vlastní hráče/DOB a žádné ADMIN, žádné historické trenérství ani aktivní vazby jako zástupce; výsledek je disabled. CHILD→STANDARD také zůstane disabled do výslovného povolení. |
| PATCH `/admin/users/{id}/enabled` | `{enabled,reason}` → 200. CHILD enabled=true se zde odmítá; použije se activate. Deaktivace posledního rodiče automaticky zakáže závislé CHILD. |
| POST `/admin/users/{id}/activate` | `{reason}` → 200. Jen CHILD s vlastním hráčem a alespoň jednou neodvolanou vazbou na enabled STANDARD rodičovský účet; jinak 409 CHILD_ACTIVATION_CONFLICT |
| GET `/players/{playerId}/guardians` | Page GuardianResponse včetně odvolaných vazeb |
| PUT `/players/{playerId}/guardians/{guardianPersonId}` | `{reason}` → 200 GuardianResponse. Výslovné ověření správcem. Již aktivní grant zachová první čas, regrant odvolané vazby aktualizuje grantedBy/grantedAt a vyčistí revokedAt. |
| POST `/players/{playerId}/guardians/{guardianPersonId}/revoke` | `{reason}` → 200 GuardianResponse. Opakování zachová první revokedAt; audit složeného klíče. Poslední kvalifikovaný rodič zakáže CHILD a odvolá dosavadní sessions. |
| GET `/me/children` | Jen dokončený STANDARD: Page ChildResponse přes person session, bez vazeb prázdná stránka 200. ADMIN zde nemá automaticky všechny děti. CHILD 403. |
| GET `/me/children/{playerId}` | ChildResponse jen vlastní schválené dítě, cizí 404; žádný klientský guardianId scope |

Reason je u person-link, změn role/typu/přístupu, aktivace a guardian grant/revoke povinné neprázdné odůvodnění 1–500 znaků, ukládané do soukromého auditu; nevkládají se do něj hesla ani osobní údaje. GuardianResponse je `{guardianPersonId,playerId,firstName,lastName,grantedBy,grantedAt,revokedAt}`, bez vymyšleného guardianLinkId. ChildResponse je pouze `{playerId,displayName,firstName,lastName}`, bez data narození, kontaktů nebo finančních souhrnů.

Zástupcem může být evidovaná osoba bez účtu, její vazba však nestačí k přístupu ani CHILD aktivaci. Nesmí jít o osobu dítěte samotného, CHILD účet nebo cyklický řetězec rodičovských vazeb. Rodičovské a trenérské vazby se z CHILD nikdy neodvozují ani při chybném uloženém vztahu. Parent čte vlastní schválený základní profil a celou členskou historii; tým/soupisku jen při dnešním členství dítěte v přesném team_season. Stejný SQL scope omezuje counts. Žádný obecný DELETE, reset hesla ani pozvánkový mechanismus není implementovaný.

Posledního enabled STANDARD COMPLETE ADMIN nelze demotovat, zakázat ani konvertovat (409 LAST_ADMIN_REQUIRED). V4 přidává neveřejné users.auth_revision a bezpečnostní guardy bez nové tabulky; V1–V3 se nemění. Původní unsupported CHILD řádky jsou při upgradu zakázány bez vytváření falešných osob/vztahů. Bezpečnostní změna nebo automatické zablokování dítěte zvýší revizi; session se při dalším requestu porovná s původním login snapshotem. Stará cookie zůstane neplatná i po reaktivaci. Vlastní P1 onboarding/setup výslovně obnoví jen očekávanou právě vzniklou revizi; nepřevezmou pozdější souběžnou správní změnu.

Child login a každý další request ověří enabled, USER/CHILD, vlastní hráče a aktuálně aktivního rodiče. Ztráta jedné z více kvalifikovaných vazeb dítě neblokuje; ztráta poslední ano. Regrant ani opětovné povolení rodiče dítě automaticky nezapnou: vždy je nutná samostatná ADMIN aktivace. Sportovní profil, členství a historie se nemažou. První heslo se předává soukromě mimo veřejný projekt; server jej nevrací a frontend jej maže při odeslání i po chybě, bez storage/URL.

CHILD allowlist dovoluje jen implementované vlastní a scoped sportovní GET (auth/me, CSRF, me/person, me/context, team-seasons/detail/roster, players/detail/memberships), veřejné info/health/dokumentaci a auth operace. Ostatní metody/cesty jsou 403: změna osoby, setup, správní API, děti a zejména všechny fee/payment/export/notifikační cesty včetně count/read. Toto je předběžná finanční bezpečnostní hranice, **nikoli implementace financí nebo notifikací**. Budoucí sportovní zápisy a schránka vyžadují rozšíření allowlistu a odpovídající policy/testy v příslušném balíčku.

Frontend `/admin/accounts`, `/admin/players/[playerId]/guardians`, `/my-children` a `/my-children/[playerId]` používá skutečné API. Dětská navigace nabízí vlastní profil/sportovní čtení; osobní údaje jsou pouze ke čtení, datum narození a role se samoobslužně nemění. Finance ani rodičovské/správní stránky nenabízí.

## E2 — události, účast a notifikace

| UI | Plánované API |
| --- | --- |
| `/calendar`, `/events/[eventId]` | Scoped `GET /events?teamSeasonId&from&to&type`, `GET /events/{id}`; typ TRAINING/MATCH/OTHER, stav DRAFT/PUBLISHED/CANCELLED/COMPLETED |
| Vytvoření/editace akce | ADMIN nebo trenér všech dotčených týmů: POST/PATCH `/events`, POST `/events/{id}/publish`, `/cancel`; skutečná pole title,description,startsAt,endsAt,location,availabilityDeadline,seasonId,type a teamSeasonIds |
| Dostupnost na detailu | Vlastní hráč nebo STANDARD rodič: `PUT /events/{id}/participants/{playerId}/availability {version,availability,availabilityComment}`; UNKNOWN/AVAILABLE/UNAVAILABLE |
| Nominace | ADMIN/trenér: `PUT /events/{id}/nomination` s verzovanými účastníky, `POST .../nomination/publish` s verzí akce; NOT_SELECTED/SELECTED/RESERVE |
| Docházka | ADMIN/trenér: `PUT /events/{id}/participants/{playerId}/attendance {version,attendance,attendanceComment,played}`; PRESENT/ABSENT/EXCUSED/LATE, played jen MATCH |
| `/notifications` | `GET /notifications`, `/notifications/unread-count`, `GET /notifications/{notificationId}`, `POST /notifications/{notificationId}/read` → 204 |

Účast má PK `(event_id,player_id)`; každá změna řádku kontroluje jeho version a smí měnit jen příslušná pole. Nezapsaná hodnota je null, nikoli UNKNOWN/NOT_SELECTED/false. Docházka se neodvozuje z dostupnosti ani nominace; played=true vyžaduje PRESENT/LATE. Koncepty a nepublikovanou nominaci hráč/rodič nevidí.

`notification_deliveries` má PK `(notification_id,recipient_id)`, nemá deliveryId. Recipient přečtení se bere výhradně ze session; klient jej neposílá. Opakované read je bezpečně 204 a zachová první readAt. CHILD nedostane FEE_DUE_SOON/FEE_OVERDUE ani jejich počty; otevření cíle starého oznámení nepřeskakuje aktuální autorizaci.

## P4 — formáty zápasu

P4 poskytuje `/admin/match-formats` nad Flyway V5. Navazující soutěžní konfiguraci realizuje V6 a rozhraní níže. Výsledky lokálních kontrol uvádí [ověření](verification.md).

| Metoda a cesta | Kontrakt |
| --- | --- |
| `GET /match-formats?page=0&size=20&sportId=UUID` | ADMIN stránka; volitelný sport omezuje items i count. Stabilní řazení name/id. |
| `GET /match-formats/{id}` | ADMIN detail včetně configuration, řetězcové version a nullable lockedAt. |
| `POST /match-formats` | ADMIN + CSRF; `{sportId,name,configuration}`. Aktivní sport, odpověď 201 s verzí `"0"`. |
| `PUT /match-formats/{id}` | ADMIN + CSRF; úplný `{version,name,configuration}`. Neměnný sport, stará verze → 409 OPTIMISTIC_LOCK_CONFLICT. |

Configuration obsahuje scoreMode (SUM_SCORE/WON_PARTS), allowDraw, periodsRequired, periodLabel, nullable regulationPeriodCount a winsRequired, allowOvertime, allowShootout, shootoutWinnerBonus, friendlyForfeitLoserScore, friendlyForfeitWinnerScore. Ostatní pole jsou povinná. Čísla mají rozsah PostgreSQL integer; počty jsou kladné, skóre/bonus nezáporné, vítěz kontumace má vyšší skóre. Bez nájezdů je bonus 0. SUM_SCORE nepřijímá winsRequired. WON_PARTS vyžaduje povinné části a winsRequired, bez remízy/prodloužení/nájezdů/pevného počtu částí.

LockedAt nastaví až budoucí výsledkový průchod; klient jej nemění. Použitý formát dovolí jen přejmenování se stejnou konfigurací, jinak 409 CONFIGURATION_LOCKED. UI uchová rozepsané hodnoty a nabízí výslovné zahození a nové načtení. Správní navigace používá existující MANAGE_CLUB; backend samostatně vyžaduje aktuálního dokončeného enabled STANDARD ADMIN. USER, trenér bez ADMIN, rodič, CHILD a PENDING přístup nemají. Mutace berou klubový zámek před fresh auth a auditují ve stejné transakci. Delete endpoint ani výpočet výsledků zatím není.

## P4 — soutěže, ročníky a přihlášky

UI `/admin/competitions` vytváří a přejmenovává soutěže a zakládá celé ročníky. `/admin/competitions/{competitionSeasonId}` spravuje pravidla a přihlášky. Všechny následující endpointy vyžadují aktuálního dokončeného enabled STANDARD ADMIN, mutace také CSRF, klubový write lock a transakční audit. Čtení používá klubový read lock; USER/trenér/rodič bez ADMIN, CHILD a PENDING tento správní obsah ani counts nedostanou.

| Metoda a cesta | Skutečný kontrakt |
| --- | --- |
| `GET /competitions?page=0&size=20&sportId=UUID` | Stránka `{items,page,size,totalElements,totalPages}`, items `{id,sportId,sportName,name}`; stabilní name/id. |
| `GET /competitions/{id}` | Stejný detail soutěže. |
| `POST /competitions` | `{sportId,name}` → 201; aktivní sport, name 1–160 znaků. Sport/identita se nemění. |
| `PUT /competitions/{id}` | `{name}` → 200; pouze přejmenování, nikoli změna sportu. |
| `GET /competition-seasons?page=0&size=20&sportId=UUID&competitionId=UUID&seasonId=UUID` | Volitelné filtry omezují items i counts. Items jsou SeasonSummary, řazení soutěž/sezóna od nejnovější/id. |
| `GET /competition-seasons/{id}` | `{season:SeasonSummary,configuration}`. |
| `POST /competition-seasons` | `{competitionId,seasonId,matchFormatId,configuration}` → 201; atomické vytvoření úplné konfigurace. Aktivní sport, otevřená sezóna, formát stejného sportu a jedinečná dvojice soutěž/sezóna. |
| `PUT /competition-seasons/{id}` | `{version,configuration}` → 200; atomická náhrada všech pravidel a H2H nastavení. Identita, sezóna a formát ročníku jsou neměnné. |
| `GET /competition-seasons/{id}/point-rules`, `/forfeit-rules`, `/ranking-criteria` | Oprávněný přímý read příslušné omezené kolekce; mutace pravidel probíhá výhradně atomickým PUT ročníku. |
| `GET /competition-seasons/{id}/entries?page=0&size=20` | Stránka `{teamSeasonId,displayName,isClubTeam}` se shodným count scope, historickými sezónními názvy, řazení displayName/id. |
| `POST /competition-seasons/{id}/entries` | `{version,teamSeasonId}` → 200 SeasonSummary s novou verzí; tým stejného sportu i sezóny, klubový nebo externí; duplicita → 409 DUPLICATE_RESOURCE. |
| `DELETE /competition-seasons/{id}/entries/{teamSeasonId}` | JSON `{version}` → 200 SeasonSummary; odebrání pouze nepoužitého otevřeného ročníku, chybějící přihláška → 404. |

SeasonSummary obsahuje `id,competitionId,competitionName,seasonId,seasonName,sportId,sportName,matchFormatId,matchFormatName,version,resultsRevision,lockedAt,archived`. Version, resultsRevision i totalElements jsou přesné řetězce. Mutace pravidel i přihlášek zvyšují tutéž version; stará verze → 409 OPTIMISTIC_LOCK_CONFLICT. Neplatná/nekanonická nebo přetékající verze → 400 VALIDATION_ERROR. Klient nemůže zapisovat resultsRevision, lockedAt ani identitu přes obecné DTO. První část/výsledek nastaví lockedAt až v P5/P6; P4 poskytuje a ověřuje ochranu použitých dat.

Configuration obsahuje `headToHeadMissingPolicy` (REQUIRE_COMPLETE/PLAYED_ONLY), kladné `headToHeadMeetingsPerPair`, boolean `restartHeadToHeadOnSubgroup`, `pointRules` (2–100), obě `forfeitRules` a `rankingCriteria` (1–10). Každé pointRule má `outcome` WIN/DRAW/LOSS, `ending` REGULATION/OVERTIME/SHOOTOUT, nullable inkluzivní `ownScoreMin,ownScoreMax,opponentScoreMin,opponentScoreMax` a nezáporné `points`; nullable hranice je neomezená. Čísla jsou PostgreSQL integer, priority a počty kladné. Ukončení musí povolit formát; DRAW je možné jen podle allowDraw a nikdy pro nájezdy. Pravidla pro stejné outcome/ending se nesmějí překrývat. Server vyžaduje úplné pokrytí povolených konečných skóre (0 až integer max): u WON_PARTS končí vítěz na winsRequired a poražený pod ním; u nájezdů je rozdíl konfigurovaný bonus, při nulovém bonusu je možné stejné skóre i s WIN/LOSS. Chybná nebo neúplná konfigurace → 400 VALIDATION_ERROR, bez částečného zápisu.

ForfeitRule má `decision` FORFEIT_HOME/FORFEIT_AWAY a `homeScore,awayScore,homePoints,awayPoints`; každé rozhodnutí právě jednou, nezáporná čísla a správný vítěz. Bodování kontumace není běžná prohra. RankingCriterion má souvislou `priority` od 1, jedinečnou `metric` z DBML a `direction` ASC/DESC; HEAD_TO_HEAD metriky tvoří souvislý blok, PART_SCORE metriky vyžadují povinné části. UI podporuje přidání/odebrání bodovacích pravidel, hranice skóre, obě kontumace i přesuny kritérií a jejich směr. Konfigurace je datová, žádné SQL nebo sportovní skripty.

Archivace a lockedAt odmítají změny pravidel, H2H politiky a přihlášek s 409 CONFIGURATION_LOCKED; čtení zůstává. Změna dosud nepoužitého sdíleného formátu musí zachovat platnost všech připojených ročníků, jinak stejný konflikt; archivovaný/použitý ročník blokuje změnu pravidel formátu. Změna hranic sezóny se odmítne i tehdy, má-li zatím pouze ročník a žádný tým. Při 409 UI zachová rozepsaná data a nabídne výslovné zahození a reload; nepokouší se mutaci automaticky opakovat. Audit přihlášky ukládá oba klíče vazby.

## E3 — navazující výsledkové revize a dynamické statistiky

| UI | Plánované API |
| --- | --- |
| `/competitions/[competitionSeasonId]` | Scoped `/competition-seasons/{id}/matches`, `/standings`; rows, resultsRevision a vysvětlení použitých kritérií vrací backend |
| `/matches/[eventId]/result`, `/history` | `/matches/{eventId}/result-revisions`, `/.../{revisionId}/confirm`, `/annul`; očekávaná matchVersion, supersedesRevisionId a correctionReason při opravě |
| `/admin/statistic-definitions` | ADMIN `/statistic-definitions`, `/.../{id}/versions`; name,valueType,aggregation,unit,minValue,maxValue podle DBML |
| Hráčské/týmové statistiky | Scoped `/players/{id}/statistics`, `/team-seasons/{id}/statistics`, filtry sezóny; hodnoty jsou součástí návrhu výsledkové revize |

Zápas má PK `matches.event_id`, nikoli nové matchId. DRAFT revize je upravitelná; CONFIRMED/ANNULLED a jejich historická data se nepřepisují. Při částech backend odvodí skóre; nebere druhý nezávislý součet. Potvrzení ověří sport, formát, stav akce, snímek účasti a pravidla, atomicky přepne aktivní revizi a resultsRevision. Archivované opravy smí jen ADMIN s důvodem.

Statistika obsahuje `{playerId,statisticDefinitionId,statisticDefinitionVersionId}` a právě jednu hodnotu integerValue/decimalValue/booleanValue/textValue. Platí INTEGER/DECIMAL + SUM/AVG/MIN/MAX/COUNT/NONE, BOOLEAN + COUNT_TRUE/COUNT/NONE, TEXT + COUNT/NONE. Hodnota patří skutečně nastoupivšímu hráči dané revize. Použitý význam definice/pravidel je zamčený; nový název vzniká novou verzí, nový význam novou identitou. Fotbal i basketbal používají stejné kontrakty a konfiguraci v DB.

## E4 — příspěvky, platby a přesná idempotence

STANDARD `/fees` používá `GET /me/fees`, detail `/fees/{id}`; rodič `/my-children/[playerId]/fees` používá `/me/children/{playerId}/fees`. ADMIN `/admin/fees` spravuje `/fees` a skutečné platby. CHILD má backendový zákaz seznamů, detailů, exportů, částek, zůstatků i oznámení, včetně vlastních.

`POST /fees/{feeAssessmentId}/payments` přijme `{id,amount,paidOn,reference,assessmentVersion}`. Klient vygeneruje UUID `id` jednou pro zamýšlený zápis; je přímo `fee_payments.id`, nikoli klíč nové idempotency tabulky. Osoba i měna vyplývají z předpisu, recordedBy/recordedAt doplní server.

Pod zámkem a až po autorizaci: nové UUID + platný zůstatek/verze → 201; již existující UUID + shodný obchodní payload `(feeAssessmentId, normalizovaná amount, paidOn, reference)` → 200 se stávající platbou, bez druhého zápisu. Jiný payload → 409 PAYMENT_ID_CONFLICT. Opakování se vyhodnotí před odmítnutím staré assessmentVersion, která není součást obchodního payloadu; nepřepíše ani již stornovanou platbu.

Timeout neznamená neprovedenou platbu: UI ověří `GET /payments/{id}` nebo nabídne vědomé opakování se stejným UUID/payloadem, nikdy automatické nové UUID. `POST /payments/{id}/cancel {cancellationReason,assessmentVersion}` zaznamená cancelledAt/cancelledBy/cancellationReason na existující platbě. Storno nemaže historii a nevrací peníze; nejde o bránu. Součet platných úhrad nepřekročí předpis ani v souběhu.

## E5–E6 — samostatná komunikace a ověření

`/messages` a `/messages/[conversationId]` používají `/conversations`, `/conversations/{id}/messages` a `/participants`. Konverzace má teamSeasonId, title a nejméně dva oprávněné účastníky. Zpráva přijímá pouze `{body}` (1–5000 neprázdných znaků); authorId/sentAt jsou serverové. Aktivní účast i vztah k týmu se ověřují při každém požadavku; ADMIN není automatický čtenář. Čte se až od joinedAt, ukončená účast se znovu neotevírá.

**DBML nemá chat read receipts, message readAt ani lastReadMessageId.** Kontrakt proto nemá `/conversations/.../read`, počty nepřečtených chatových zpráv ani stav „přečteno“. Taková funkce vyžaduje výslovné rozšíření doménového návrhu a novou migraci; readAt existuje pouze u notifikačních doručení.

Akceptace každé etapy ověří skutečné DTO/OpenAPI, CSRF, scoped seznamy/počty, 401/403/404/409, chybové/prázdné/loading stavy a mobilní UI. Povinné jsou solo ADMIN/trenér bez rodičů, STANDARD rodič více dětí, CHILD bez úniku financí a oba sporty bez změny kódu. E3 navíc ověří potvrzení/opravu/anulování a historii; E4 souběh i opakování plateb se stejným UUID. Tento dokument sám žádnou kontrolu neoznačuje za provedenou.
