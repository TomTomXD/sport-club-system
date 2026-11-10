# Informační systém pro správu sportovního klubu

Samostatný týmový ročníkový projekt `sport-club-system`. **E0 a balíčky P1–P3 tvoří technický a členský základ E1; úplná akceptace E1 ani celá klubová aplikace nejsou uzavřené.** Cílový rozsah odpovídá `zadani_sportovni_klub_v2.odt`; finální akceptace zahrnuje fotbal i basketbal v jedné aplikaci bez změny kódu.

Základ zachovává Spring REST API, UUID účty a BCrypt, session `JSESSIONID`, CSRF, info API s verzí z JAR, samostatný Next.js frontend, Docker a CI. P1 přidává Flyway V2, doplnění vlastní osoby, aktuální autorizační kontext, bezpečné jednorázové převzetí správy a administrátorský seznam, založení a úpravu sportů. Veřejná registrace vytváří `USER`/`STANDARD` bez klubových práv; neúplný účet nejdříve dokončí osobní údaje.

P2 přidává V3, sezóny, vlastní i externí týmy, hráče bez účtů, trenérská přiřazení, intervalová členství, historii a oprávněné soupisky. P3 přidává V4, administrátorskou správu účtů na ověřené osoby, person-link, role/typ/přístup, schválené rodičovské vazby, Moje děti a omezené CHILD přihlášení ke sportovním údajům. Události, výsledky, statistiky, příspěvky, komunikace a notifikace zůstávají **plánované**. Předběžný zákaz finančních cest pro CHILD není implementovaná finanční agenda. Rozsah implementace popisuje [API kontrakt](docs/api-contract.md); skutečně provedené kontroly a jejich omezení uvádí [ověření](docs/verification.md).

P4 přidává **formáty zápasu** (`/admin/match-formats`) a **soutěže a ročníky** (`/admin/competitions`) pro dokončeného aktivního STANDARD ADMIN: vyhodnocení skóre/částí, úplné bodování podle výsledku/ukončení a rozsahů skóre, obě soutěžní kontumace, kritéria pořadí a H2H politiku, přihlášky klubových i externích sezónních týmů. V5/V6 rozšiřují schéma na 19 tabulek. Server odmítá cizí sport/sezónu, neúplná či překrývající se pravidla, staré verze a změny historických/archivovaných konfigurací. Výsledky, výpočet tabulek a statistik patří do P5/P6. Lokální ověření viz [záznam](docs/verification.md).

## Požadavky

- Java **17** a Maven Wrapper (v repozitáři; Maven není nutné instalovat).
- Node **24.14.0** a npm; `.nvmrc` a `.node-version` obsahují stejnou verzi jako Docker a CI.
- Docker Desktop / Docker Engine s Compose v2, běžící daemon pro PostgreSQL a Testcontainers.
- Python 3 pro týmové pomocné skripty.
- Volné porty 5432 (lokální DB), 8080 (API), 3000 (frontend).

Backend používá Spring Boot 4.0.3 a springdoc 3.0.3 podle [oficiální kompatibility](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot). Přesné verze frontendových závislostí jsou v `frontend/package.json` a `package-lock.json`. Běžné kontroly nevyžadují žádný token ani produkční secrets.

Verze testovacího Testcontainers vychází z dependency managementu Spring Boot (nyní 2.0.3); projekt ji samostatně nepřepisuje. JUnit a PostgreSQL moduly používají názvy `testcontainers-junit-jupiter` a `testcontainers-postgresql` řady 2.x. Kompatibilitu Docker API řeší knihovna, běžný testovací běh nepotřebuje ruční `api.version` ani `JAVA_TOOL_OPTIONS` workaround. Při změně Spring Boot ověřte celou backendovou sadu včetně PostgreSQL a migračních testů.

## Od čistého klonu: lokální vývoj

Veřejný týmový repozitář je [Dasky02/sport-club-system](https://github.com/Dasky02/sport-club-system). Klonujte jej a přejděte do jeho kořene:

```sh
git clone https://github.com/Dasky02/sport-club-system.git
cd sport-club-system
cp .env.example .env
cp frontend/.env.example frontend/.env.local
```

V `.env` nahraďte vzor `POSTGRES_PASSWORD` vlastním lokálním heslem. Tento postup načítá env soubor i shellem: pro heslo použijte například náhodnou hex hodnotu nebo jej uzavřete do jednoduchých uvozovek. Pro převzetí nové instalace nastavte vlastní náhodný soukromý `CLUB_SETUP_TOKEN`; v příkladu je prázdný a bez něj je setup vypnutý. Nemá výchozí heslo ani známý testovací token. Ostatní výchozí hodnoty jsou připravené pro localhost. Soubor frontend `.env.local` obsahuje `NEXT_PUBLIC_API_URL=http://localhost:8080/api`; databázové heslo ani setup token nikdy nepatří do `NEXT_PUBLIC_*`, URL či frontendu.

```sh
docker compose -f docker-compose.dev.yml up -d --wait
```

Backend v prvním terminálu (z kořene; `.env` se exportuje pouze pro tento shell):

```sh
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Java musí být verze 17 (`java -version`; při více instalacích nastavte `JAVA_HOME`). Na macOS např. `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Dev profil nepřidává žádné výchozí účty. Registraci, doplnění osoby a bezpečný setup projděte přes skutečné UI níže; další účty a role spravuje dokončený ADMIN v `/admin/accounts` po ověření identity. Posledního povoleného správce nelze odebrat.

Frontend ve druhém terminálu:

```sh
cd frontend
nvm use                 # pokud používáte nvm
npm ci
npm run dev
```

Otevřete [frontend](http://localhost:3000). [Verze API](http://localhost:8080/api/info/version), [health](http://localhost:8080/api/actuator/health) a [OpenAPI](http://localhost:8080/api/v3/api-docs) ověřují skutečný běžící backend. API prefix `/api` se přidává právě jednou. Databázové změny provádí Flyway; Hibernate pouze validuje schéma.

## První průchod P1–P3

1. Zaregistrujte se a přihlaste. Registrace sama nepřihlašuje ani nepřiděluje ADMIN.
2. Na `/profile` doplňte skutečné jméno a příjmení, případně datum narození a kontakty. PENDING účet nemá klubová oprávnění. Onboarding vytváří jen vlastní osobu; nepřipojuje cizí existující profil podle jména či ID.
3. Pro novou instalaci otevřete `/setup` a použijte soukromý instalační token nastavený v backendu. Dokončený STANDARD účet získá ADMIN pouze při prvním platném převzetí. Prázdný token setup nepovolí a dokončený setup nelze opakovat.
4. Jako ADMIN otevřete `/admin/sports`, založte například fotbal a basketbal a upravte jejich název či aktivní stav. Kód sportu je stabilní a sport se nemaže. Běžný USER tuto správu nemá.
5. V `/admin/seasons` vytvořte sezónu, v `/admin/teams` týmy a jejich sezónní záznamy. Každý tým vyberte právě k jednomu sportu. Externí soupeř nemá interní členství.
6. V `/admin/players` založte hráče s datem narození; účet ani rodič nejsou povinné. Přes **Spravovat členství** přidejte hráče a vlastní účet jako trenéra. Interval je `[od,do)`, prázdný konec znamená konec sezóny. Překryv stejné dvojice je zakázaný.
7. V `/teams` otevřete soupisku a historii hráče. ADMIN vidí všechny týmy; ostatní jen aktuální vlastní či trenérské členství nebo členství schváleného dítěte. CHILD má pouze vlastní aktuální týmy. Přestup ukončí starý interval a přidá nový, archivace zachová čtení a blokuje změny. Dashboard a navigace používají skutečný `/me/context`; finanční schopnosti zatím nevznikají.
8. Potřebujete-li rodiče nebo hráčské přihlášení, v `/admin/accounts` založte účet na ověřené osobě. Osobu rodiče lze vytvořit samostatně bez hráčského profilu. Neúplný již registrovaný účet propojte přes **Propojit ověřenou osobu**; použitou identitu nelze přepojit. První heslo předejte soukromě, u dítěte ověřenému rodiči.
9. U hráče otevřete **Rodiče a zástupci**, vyberte ověřenou osobu a odůvodněte schválení vazby. Rodič se STANDARD účtem uvidí `/my-children`. Nový CHILD účet vzniká zakázaný a vyžaduje vlastní hráčský profil a nejméně jednoho schváleného rodiče s povoleným STANDARD účtem; teprve pak použijte **Aktivovat dětský účet**. Role, typ, přístup, person-link a rodičovské změny vyžadují důvod bez tajných či osobních údajů. Zákaz účtu, změna typu a odvolání vazby mají potvrzení dopadu.

Při upgradu původní databáze se V1 nepřepisuje: V2 zachová UUID, BCrypt hashe a časy. ADMIN zůstává ADMIN, staré COACH/PLAYER/PARENT se převádějí na USER bez vymyšlených vztahů. Účty mohou mít dočasně `person_id = NULL`, po přihlášení dokončí vlastní onboarding. Existující ADMIN při migraci uzavře prvotní setup a po doplnění osoby může spravovat sporty. V4 při upgradu zakáže všechny dřívější CHILD účty bez domýšlení osoby nebo rodiče. Současný ADMIN po ověření může doplnit vazby a provést samostatnou aktivaci. Ztráta posledního platného aktivního rodiče dítě zakáže a odvolá jeho přihlášení; opětovné schválení rodiče či jeho povolení dítě samo neaktivuje. Stará cookie se neobnoví ani po reaktivaci účtu. Podrobný postup a plán čtyř klubových rolí obsahuje [příručka](docs/user-guide.md).

## Celá sestava v Dockeru

Lokální běh má HTTP a cookie `Secure=false`; HTTPS produkce používá samostatný override.

```sh
docker compose up --build -d --wait
docker compose ps
docker compose logs backend
docker compose down
```

Před plnou sestavou zastavte lokální backend/frontend na stejných portech. Vývojový Compose a plná sestava mají oddělená pojmenovaná DB volumes. Plná sestava nevystavuje port DB; frontend a backend se vážou na loopback hostitele. V kontejnerech backend používá `db:5432`; v lokálním backendu `localhost:5432`. Prohlížeč vždy používá veřejné `http://localhost:8080/api`. `NEXT_PUBLIC_API_URL` je build argument; při změně URL frontend znovu sestavte.

`docker compose down` zachová data. `down -v` je smaže; používejte jej jen pro vlastní zahoditelnou vývojovou databázi. Nasazení s Caddy, HTTPS, zálohou a obnovou je v [docs/deployment.md](docs/deployment.md).

## Kontroly

```sh
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

Na macOS může Testcontainers potřebovat `export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`; v Linux CI používá běžný Docker socket. Integrační testy vytvářejí vlastní čistý PostgreSQL 16 container, zapínají Flyway a Hibernate validate. Nezávisí na vašem vývojovém volume. Browserové E2E instrukce a skutečné výsledky kontrol jsou v [docs/verification.md](docs/verification.md). Neprovedené kontroly jsou označeny jako neprovedené.

## Dokumentace a týmová práce

- [Architektura a bezpečnost skeletonu](docs/architecture.md), [výchozí problémy šablony](docs/baseline.md).
- [Požadavky a akceptace](docs/requirements.md), [doménový model](docs/domain-model.md), [návrh databáze](docs/database.md), [DBML](docs/sport-club.dbml) a [ER diagram](docs/ERD%20diagram.svg).
- [Oprávnění](docs/authorization.md), [etapy implementace](docs/roadmap.md), [uživatelská příručka](docs/user-guide.md).
- [Pracovní plán celé implementace](docs/implementation-plan.md), [implementované a plánované API](docs/api-contract.md), [pokyny pro navazující práci](AGENTS.md).
- [Nasazení](docs/deployment.md), [výsledky ověření](docs/verification.md), [CONTRIBUTING](CONTRIBUTING.md).

Doporučený postup: krátké větve, PR, alespoň jedno schválení kolegou a povinné úspěšné CI před merge do `main`. Nastavení těchto GitHub pravidel je samostatný týmový krok. Release je ruční workflow nad `main`, po testech a s verzí z `VERSION`; běžné PR kontroly nic nepublikují. Aktualizujte verzi přes `python3 scripts/bump_version.py X.Y.Z` a release notes v backend resources.

## Původ a licence

Backend a API klient vycházejí z `petrsafrata/Spring-project-template`, commit `32dded5e33b83f038655199eb81044fa3d4c66a8`. Zachovány jsou vrstvy a Java package šablony, User/UserRepository/UserService, DTO records, handler chyb, InfoController/InfoService/OpenApiConfig, Maven Wrapper, PostgreSQL a Testcontainers. Přehled cílených úprav je v [NOTICE](NOTICE) a dokumentaci. [LICENSE](LICENSE) včetně původního oznámení Petr Šafrata je zachována.

Nový veřejný repozitář má vlastní Git historii a neobsahuje privátní `.env` soubory šablony. Publikování proběhlo po kontrole připraveného obsahu. Původní privátní repozitář nebyl upraven. Výsledky automatických kontrol jsou v [GitHub Actions](https://github.com/Dasky02/sport-club-system/actions/workflows/ci.yml).
