# Pokyny pro práci v projektu

## Rozsah a zdroje pravdy

Pracujte pouze v tomto samostatném `sport-club-system`. Původní privátní `petrsafrata/Spring-project-template` neupravujte, necommitujte/nepushujte do něj a neměňte jeho nastavení. Zachovejte LICENSE, NOTICE a původ implementací.

Před doménovou prací čtěte [požadavky](docs/requirements.md), [pracovní plán](docs/implementation-plan.md), [API kontrakt](docs/api-contract.md) a relevantní části [databáze](docs/database.md), [DBML](docs/sport-club.dbml) a [oprávnění](docs/authorization.md). Roadmapa E0–E6 určuje celý povinný rozsah; pracovní balíčky P0–P9 určují jeho pořadí. Dokumentace rozlišuje návrh, implementaci a skutečně provedenou akceptaci.

Historický stav před doménovou prací byl E0 s V1 users. P1–P3 dodávají identitu/setup/sporty, osoby/hráče/sezóny/týmy/členství a správu účtů/rodičů/CHILD. V1–V4 mají 12 tabulek, V4 přidává auth_revision a ochranné triggery. P4 nyní dodává celou soutěžní konfiguraci popsanou níže; P5–P9 zůstávají plánované. Úplná akceptace E1 ani celý projekt nejsou uzavřené. Výsledky běhů patří pouze do datovaného docs/verification.md; implementace nebo existence testu není důkaz jeho spuštění. Aplikované migrace se nepřepisují a další čísla potvrďte až při práci. Před prací ověřte aktuální soubory a Git stav.

## Zachovávaná architektura a pravidla

P4 ve větvi `feat/p4-match-formats` realizuje ADMIN správu formátů, soutěží, ročníků, úplného bodování, obou soutěžních kontumací, kritérií pořadí/H2H politik a přihlášek sezónních týmů přes skutečné API/UI. V5 přidává match_formats, V6 dalších šest soutěžních tabulek: celkem 19, dalších 19 cílových tabulek zůstává plánovaných. V1–V5 se při doplnění V6 nemění; další migraci přidělte až při práci po V6. Pravidla a přihlášky mají společnou verzi ročníku, klubový lock, fresh ADMIN auth a audit. Server odmítá cizí sport/sezónu, překryvy i neúplné pokrytí bodování a změny použitých/archivovaných konfigurací; přejmenování nemění pravidla. Výsledky, jejich první uzamčení a výpočet pořadí patří do P5/P6. Uživatel 6. 11. 2026 povolil izolované ověření; běžná klubová DB se nekontroluje ani nemění. Konkrétní výsledky běhů patří pouze do datovaného `docs/verification.md`.

- Backend: Java 17, Spring Boot, `cz.jpmad.springprojecttemplate`, api/service/repository/model, constructor injection a record DTO. P2 relační dotazy používají NamedParameterJdbcTemplate ve stejné transakci jako P1 JPA; SQL scope pro items i counts musí být shodný. Zachovat BCrypt UserService, session `JSESSIONID`, CSRF a centrální JSON chyby. Nepřidávat paralelní JWT/NextAuth autentizaci.
- Frontend: Next.js App Router/TypeScript, skutečný Spring REST klient, `/api` právě jednou, credentials a CSRF. Zachovat ochranu před opožděnou auth odpovědí a rozlišení 401 od výpadku sítě. Doménové policy patří backendu.
- Flyway mění schéma, Hibernate pouze validuje. Aplikované migrace včetně V1 nepřepisovat. Čísla dalších migrací koordinovat podle skutečného stavu, nevytvářet předem prázdné SQL soubory.
- Při převodu účtů zachovat UUID a hashe, nevymýšlet osobní údaje ani vztahy ze staré role. `person_id` může být dočasně NULL s omezeným onboardingem; NOT NULL až po ověřeném backfillu samostatnou změnou.
- P1/P2 poskytují identitu/setup/sporty a týmový základ. P3 poskytuje ADMIN accounts/person-link/role/type/enabled/activate a guardian grant/revoke, rodičovské čtení a platný CHILD login. PENDING má jen povolený onboarding/auth rozsah a prázdný context. ADMIN má osm skutečných správních capabilities, READ_TEAM jen pro aktuální vlastní/trenérské/schválené dětské vazby a READ_CHILD jen pro schválené dítě. CHILD má jen vlastní hráčský kontext, nikdy ADMIN/coach/guardian nebo finanční oprávnění; allowlist povoluje pouze skutečně implementovaná sportovní čtení a vlastní osobu ke čtení.
- `CLUB_SETUP_TOKEN` je soukromé nastavení backendu bez výchozí hodnoty; prázdný setup vypíná. První registrant automatický ADMIN není. Setup vyžaduje aktuální enabled STANDARD účet s vlastní osobou, zámek a audit; po dokončení se neopakuje. Token nikdy do veřejné konfigurace nebo dokumentačního příkladu s použitelnou hodnotou.
- Jedna instalace/klub, více DB sportů současně, každý tým právě jeden sport. Fotbal i basketbal bez sportovních větví a pevných polí statistik. Definice, agregace a bodování jsou konfigurovaná data.
- USER/ADMIN a STANDARD/CHILD se kombinují s aktuálními týmovými/rodičovskými vazbami. Kontrolovat je i se starou session, v detailech, seznamech, exportech a počtech. Jeden ADMIN může být jediným uživatelem a trenérem.
- CHILD má vlastní profil a ověřeného aktivního STANDARD rodiče; žádné finance včetně vlastních částek či počtů oznámení. Ztráta posledního platného rodiče nebo jeho deaktivace blokuje dítě i dosud nepoužitou session pomocí auth_revision. Regrant ani re-enable rodiče dítě samy neaktivují. Security změny a person-link odvolají staré sessions i po následné reaktivaci; vlastní onboarding/setup obnoví pouze očekávanou revizi své operace. Posledního povoleného STANDARD ADMIN s osobou nelze odebrat. Security mutace vyžadují důvod 1–500 znaků bez tajných a osobních údajů. Hráčský profil bez účtu rodiče nevyžaduje.
- Dostupnost, nominace a skutečná účast jsou různé údaje. Historická členství, potvrzené výsledky/části/statistiky a použité konfigurace se nepřepisují; oprava vytváří revizi.
- Finance evidují skutečné adminem zaznamenané částečné platby a storna na osobu. Bez brány a kreditu. Notifikace jsou samostatné vedle konverzací; ADMIN nemá automatický přístup do soukromých zpráv.
- Nevracet entities či nepovolené osobní/finanční údaje v obecných DTO. Hesla, session, setup tokeny a CSRF nikdy do logů, auditu, URL ani veřejných env. Žádné výchozí produkční účty ani skutečné osobní údaje ve fixtures.

## Dodávka a ověření

Dodávejte malé funkční průchody backend → reálné UI → testy. Prázdné controllery, produkční mock data a pouhé tabulky nejsou dokončená funkce. Uveďte ID požadavků, změny schématu a přístupové hranice. Aktualizujte příručku a stav požadavků podle skutečnosti; neoznačujte plánované nebo neprovedené kontroly za hotové.

Příkazy z odpovídajícího adresáře:

```sh
# backend/; Java 17 a běžící Docker pro PostgreSQL Testcontainers
./mvnw -B -ntp verify

# frontend/; Node z .nvmrc, závislosti přes npm ci
npm run lint
npm run typecheck
npm test
npm run build

# kořen repozitáře
python3 scripts/check_public_content.py
docker compose --env-file .env.example config --quiet
git diff --check
```

Na macOS vyberte JDK pro daný shell přes `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`; při problému se socketem použijte `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`. Neměňte globální Java nastavení. Start a E2E postup je v README a `docs/verification.md`. Testy spouštějte podle dopadu změny; u čisté dokumentace stačí kontrola obsahu, odkazů a diffu. U doménových změn doplňujte smysluplné unit/integrační a přístupové testy, včetně přímého HTTP a souběhu tam, kde rozhoduje transakce.

Před zveřejněním kontrolujte veřejný obsah. `.env`, databázová data, soukromé dokumenty a generované adresáře se necommitují. Zachovávejte rozpracované změny ostatních a nemažte jejich data. Automatické aktualizace závislostí neslučujte bez relevantního ověření kompatibility.
