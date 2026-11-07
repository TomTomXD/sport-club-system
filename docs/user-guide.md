# Uživatelská příručka

**Dostupné E0 + P1–P4:** účty/osoby, bezpečný setup, sporty, sezóny, týmy, hráči, členství, rodiče, Moje děti, omezený CHILD a ADMIN formáty, soutěže, ročníky, pravidla a přihlášky týmů. Kalendář, dostupnost/nominace/docházka, výsledky/statistiky a výpočet pořadí, finance, notifikace a konverzace zůstávají **plán** v P5–P9. Celý projekt ani úplná akceptace E1/E3 nejsou uzavřené; rozsah sledují [požadavky](requirements.md), provedené kontroly [ověření](verification.md).

## Formáty zápasu — P4

Stránka **Formáty zápasu** (`/admin/match-formats`) je dostupná dokončenému aktivnímu STANDARD ADMIN přes správní navigaci. Vyžaduje schéma po Flyway V5. Běžný uživatel, rodič, trenér bez ADMIN ani CHILD k této správě přístup nemají. Výsledky lokálních kontrol uvádí [ověření](verification.md).

Vyberte existující aktivní sport, název formátu, vyhodnocení a označení částí. **Součet skóre** dovoluje například dva poločasy s remízou nebo čtyři čtvrtiny bez remízy s prodloužením. **Počet vyhraných částí** potřebuje povinnou evidenci částí a počet potřebných vítězství; nepovoluje remízu, prodloužení, nájezdy ani pevný počet částí. Prázdný počet částí znamená neomezený počet. Skóre přátelské kontumace musí určit vítěze; soutěžní body se zde nenastavují.

**Upravit formát** otevře konfiguraci. Použitý formát lze pouze přejmenovat. Změna pravidel nesmí zneplatnit připojený ročník; archivovaný nebo použitý ročník takovou změnu blokuje. Při konfliktu se rozepsaná data zachovají; **Zahodit úpravy a načíst aktuální stav** je výslovně nahradí serverovým stavem. Zápasy a výsledky patří do P5/P6.

## Soutěže, ročníky a přihlášky — P4

Dokončený aktivní STANDARD ADMIN otevře **Soutěže a ročníky** (`/admin/competitions`). Ostatní účty k této správě přístup nemají.

1. Připravte aktivní sport, jeho formát zápasu a otevřenou sezónu. V části **Nová soutěž** vyberte sport a název; identita sportu se později nemění. Název soutěže lze opravit samostatným formulářem.
2. V **Nový soutěžní ročník** vyberte soutěž, sezónu a formát stejného sportu. Jedna soutěž má v sezóně nejvýše jeden ročník; jeho soutěž/sezónu/formát později nepřepojíte. Při výběru formátu se připraví editovatelná bodovací pole, žádná sportovní šablona se sama neukládá.
3. Zadejte **Přidělené body** pro každý povolený výsledek a ukončení. U fotbalu lze nastavit například výhra/remíza/prohra 3/1/0, u basketbalu výhra/prohra 2/1 pro základní dobu i povolená prodloužení. Pomocí **Přidat bodovací pravidlo** vytvořte další rozsahy. Hranice skóre jsou včetně krajních hodnot; prázdná hranice je neomezená. Server odmítne překryv, chybějící povolený výsledek nebo mezeru v rozsahu. Pro vyhrané části se vyplňují jen dosažitelné konečné počty částí.
4. Vyplňte skóre a body domácích/hostů pro **Kontumace domácích** i **Kontumace hostů**. Provinivší se strana musí prohrát; její soutěžní body mohou být jiné než u běžné prohry, například basketbalová kontumace 0:20 s body 0:2 oproti běžné porážce za 1 bod. Tyto údaje nenahrazují přátelskou kontumaci z formátu.
5. V **Kritéria pořadí** přidejte metriky a směr, pomocí **Posunout výš/níž** nastavte pořadí. Metriky se nesmějí opakovat. Vzájemné metriky musí být pohromadě; skóre částí vyžaduje povinné části ve formátu. Zvolte práci s neúplnými vzájemnými zápasy, počet zápasů dvojice a případné opakování vzájemného bloku v menší shodné podskupině.
6. **Vytvořit soutěžní ročník** uloží vše v jedné transakci. Chyba nezanechá neúplný ročník. Otevře se detail **Pravidla a přihlášky**, kde **Zobrazit pravidla ročníku** ukazuje uložené hodnoty a **Upravit pravidla ročníku** otevře celé editovatelné nastavení.
7. V administraci týmů připravte klubové týmy i externí soupeře ve stejné sezóně a sportu. V detailu ročníku zvolte **Tým do soutěže → Přihlásit tým**. Každá přihláška používá sezónní název; duplicitní přihlášku ani cizí sport/sezónu backend nepřijme. **Odebrat přihlášku** je dostupné jen u nepoužitého otevřeného ročníku.

Pravidla i přihlášky sdílejí verzi ročníku. Při souběhu se rozepsané hodnoty zachovají a **Zahodit úpravy a načíst aktuální ročník** je výslovně nahradí serverovým stavem. Během úpravy pravidel jsou mutace přihlášek pozastavené, aby reload neztratil rozpracovaná pravidla. Po archivaci sezóny nebo historickém uzamčení zůstane čtení a přihlášené týmy; pravidla/přihlášky se nepřepisují. Nová odlišná použitá pravidla vyžadují nový formát/ročník. P4 konfiguruje budoucí výpočty; samotné zápasy, výsledky a tabulky dodají P5/P6.

## Přihlášení a vlastní osoba

Po spuštění podle [README](../README.md) otevřete `http://localhost:3000`. Veřejnou HTTPS adresu poskytne provozovatel až po skutečném nasazení.

1. Na `/register` vyplňte **Uživatelské jméno** (3–50 znaků), **Heslo** a **Heslo znovu**. Heslo má 6–72 znaků a nejvýše 72 UTF-8 bajtů; diakritika může spotřebovat více bajtů. Registrace vytvoří USER/STANDARD/PENDING, sama nepřihlašuje a nepřiděluje správu klubu.
2. Přihlaste se na `/login`. **Můj účet** zobrazuje skutečně ověřený účet. Neúplný účet nabídne **Doplnit osobní údaje**.
3. Pokud vás klub již eviduje, před vytvořením další osoby požádejte ADMIN o ověřené propojení účtu. Shodné jméno nebo e-mail osoby automaticky nespojí. Pokud záznam nemáte, na `/profile` vyplňte **Jméno**, **Příjmení**, případně datum narození, e-mail a telefon; potvrďte **Uložit osobní údaje**. Jméno i příjmení mají nejvýše 100 znaků, datum nesmí být v budoucnosti.
4. Po potvrzení **Váš profil je dokončený.** se zobrazí vlastní údaje ke čtení. Jejich opravu provádí ADMIN po ověření. Dokončení osoby samo nevytváří hráčský profil, týmové členství ani rodičovskou vazbu.
5. **Odhlásit se** na dashboardu ukončí serverovou session. Při vypršení nebo administrátorské bezpečnostní změně se přihlaste znovu. Výpadek API sám není potvrzené odhlášení; použijte nabídnuté **Zkusit znovu**.

Máte-li účet již vytvořený správcem na ověřenou osobu, po přihlášení vlastní onboarding neopakujete. Rodič i dítě mají oddělené účty, hesla a přihlášení. Cookie a CSRF spravuje aplikace automaticky; nesdílejte je.

## První nastavení klubu — dostupné

Provozovatel nastaví soukromý `CLUB_SETUP_TOKEN` pouze v backendu podle [README](../README.md). Prázdná hodnota setup vypíná; neexistuje výchozí token nebo účet. Token nepatří do URL, veřejné dokumentace ani NEXT_PUBLIC proměnné.

1. S dokončeným STANDARD účtem otevřete `/setup` — **Nastavení klubu**.
2. Je-li převzetí dostupné, zadejte **Instalační token** a potvrďte **Převzít správu klubu**. První registrant nemá automatickou přednost ani ADMIN.
3. Po úspěchu má tentýž účet ADMIN a zobrazí se **Správa klubu je již nastavená.** Setup se už neopakuje; samotná pozdější změna role jej neotevře.
4. Pokud je setup nedostupný, provozovatel ověří konfiguraci nebo již nastavený klub. Neotevírejte setup ruční změnou databáze. CHILD jej použít nemůže.

Existující ADMIN z V1 se zachová a V2 uzavře bootstrap. Tento správce nejprve doplní vlastní osobu; další tokenové převzetí nepotřebuje. Vlastní onboarding a setup zachovávají aktuální session, běžné bezpečnostní změny účtů ji mohou odvolat.

## Administrátor: sporty, sezóny, týmy a hráči — dostupné

Dokončený STANDARD ADMIN otevře **Administrace** (`/admin`). Běžné trenérské nebo rodičovské přiřazení tuto správu samo neposkytuje.

1. Ve **Sporty** (`/admin/sports`) vyplňte **Kód sportu** a **Název sportu**, poté **Přidat sport**. Kód je stabilní: 1–40 znaků, první písmeno A–Z, dále velká písmena, číslice nebo podtržítko. UI jej převádí na velká písmena. Název má nejvýše 100 znaků. Založte například FOOTBALL/Fotbal a BASKETBALL/Basketbal ve stejné instalaci. **Upravit**, **Deaktivovat** a **Aktivovat** mění název nebo použitelnost, nemažou historii.
2. V **Sezóny** (`/admin/seasons`) zadejte název, začátek a konec. Začátek patří do sezóny, konec již ne: 1. 7. 2026 až 1. 7. 2027 zahrnuje 30. 6. 2027. Použité datumové hranice se nemění. **Archivovat sezónu** vyžaduje potvrzení; zachová čtení a zablokuje běžné změny sezónních týmů a členství. Vrácení archivace není dostupné.
3. V **Týmy a členství** (`/admin/teams`) založte základní tým s jedním aktivním sportem. **Klubový tým** má vlastní soupisku; nezaškrtnutý typ je externí soupeř bez interních členství. Ve formuláři **Zařadit tým do sezóny** vyberte tým, otevřenou sezónu a sezónní název. Ten se pozdějším přejmenováním základního týmu nepřepíše. Jednu dvojici nelze přidat dvakrát; po použití nelze měnit sport nebo typ týmu.
4. V **Hráči a osoby** (`/admin/players`) zvolte **Nová osoba a hráč společně**, nebo **Již evidovaná osoba** s datem narození a bez hráčského profilu. Jméno, příjmení a datum narození hráče jsou povinné. Osoba a hráč vzniknou atomicky, žádný účet ani rodič se tím nevytvoří. V přehledu osob lze údaje upravit; hráči nelze datum narození vymazat nebo profil přepojit na jinou osobu.
5. U sezónního týmu otevřete **Spravovat členství**. Vyberte hráče a interval a potvrďte **Přidat hráče do týmu**. V trenérském formuláři lze vybrat vlastní ADMIN účet; další uživatel není nutný. Nabídka obsahuje jen povolené STANDARD účty s dokončenou osobou.

Interval má zahrnutý začátek a vyloučený konec. Prázdný konec znamená konec sezóny. Překryv stejného hráče/trenéra ve stejném sezónním týmu se odmítne; sousední intervaly a souběžné členství v jiném týmu jsou přípustné. **Upravit interval** ukládá oba datumové údaje. Při přestupu ukončete starý interval a založte nový, historické řádky nemažte. Při konfliktu zůstávají rozepsané údaje pro opravu.

## Administrátor: ověřené účty a přístup — dostupné P3

Otevřete **Přístupové účty** (`/admin/accounts`). Účet a osoba jsou různé záznamy. Nepotvrzujte propojení pouze podle podobného jména.

1. Pro rodiče nebo jiného člena bez hráčského profilu použijte formulář **Nová osoba pro účet** a **Vytvořit osobu**. Není nutné z něj vytvářet hráče. Pokud osoba již existuje, použijte její původní záznam.
2. V **Nový přístupový účet** vyberte **Ověřená osoba**, zadejte **Uživatelské jméno**, **První heslo** a **Typ nového účtu**. Potvrďte **Vytvořit přístupový účet**. Osoba nesmí mít jiný účet. Vznikne USER; běžný účet je povolený, dětský zakázaný. CHILD již musí mít vlastní hráčský profil.
3. První heslo předejte soukromě oprávněné osobě, u dítěte ověřenému rodiči, přes bezpečný kanál. Při veřejném provozu použijte HTTPS. Formulář heslo při odeslání vymaže i při chybě. Aplikace jej nevrací, neposílá automatický e-mail ani nenabízí pozvánkový/password reset mechanismus.
4. U již registrovaného účtu bez osoby vyberte **Osoba pro propojení**, vyplňte **Odůvodnění propojení** a použijte **Propojit ověřenou osobu**. UUID a heslo účtu zůstanou, vznikne vazba na vybranou osobu. Již použitý účet nelze přepojit na jiného člověka.
5. **Systémová role** + **Odůvodnění změny role** + **Uložit roli** mění USER/ADMIN. Trenéra vytváří samostatné týmové přiřazení, rodiče samostatná schválená vazba. CHILD nemůže dostat ADMIN.
6. **Typ účtu** + **Odůvodnění změny typu** + **Uložit typ účtu** otevře potvrzení dopadu. Až **Potvrdit změnu typu účtu** provede operaci. STANDARD→CHILD vyžaduje vlastního hráče a odmítne ADMIN, jakékoli zachované trenérské členství nebo aktivní rodičovskou vazbu. Účet po změně zůstane zakázaný; CHILD→STANDARD také vyžaduje následné samostatné povolení.
7. Vyplňte **Odůvodnění změny přístupu**. U běžného zakázaného účtu použijte **Povolit účet**. **Zakázat účet** vyžaduje ještě **Potvrdit zákaz účtu**: odvolá přihlášení a jako zákaz posledního aktivního rodiče může zakázat i jeho děti. U dětského účtu je dostupná pouze samostatná **Aktivovat dětský účet**, popsaná níže.

Důvod bezpečnostní změny má 1–500 znaků. Nevkládejte do něj hesla, tajné nebo osobní údaje; patří do auditu. Posledního povoleného STANDARD ADMIN s dokončenou osobou nelze demotovat, zakázat nebo převést na CHILD. Pokud změníte vlastní bezpečnostní stav, další ověření vás může odhlásit. Stará přihlášení nepoužívejte znovu ani po pozdějším povolení účtu.

## Administrátor: rodiče a aktivace dítěte — dostupné P3

1. V `/admin/players` u dítěte zvolte **Rodiče a zástupci**. Hráč může nadále zůstat bez účtu a bez rodiče; vazba není podmínkou vedení offline hráče.
2. Ve formuláři **Schválit rodičovskou vazbu** vyberte **Ověřený rodič nebo zástupce**, doplňte **Odůvodnění schválení** a potvrďte **Schválit rodičovskou vazbu**. Ověřte skutečnou identitu i oprávnění zastupovat konkrétního hráče. Odmítne se vztah k sobě samému, rodičovský cyklus a CHILD jako rodič. Jedno dítě může mít více zástupců a jeden rodič více dětí.
3. Osoba rodiče může existovat bez účtu. Přístup do **Moje děti** a způsobilost pro aktivaci CHILD však vyžadují vlastní povolený STANDARD účet na téže osobě. Samotný kontakt rodiče nestačí.
4. Dětský účet vytvořte na vlastní osobě hráče ve **Přístupové účty**, typ **Dětský účet**. Vznikne zakázaný s rolí USER. Po schválení alespoň jednoho aktivního rodiče vyplňte důvod přístupu a použijte **Aktivovat dětský účet**. Přidání rodiče samo dítě neaktivuje. Nesplněné podmínky nechají účet zakázaný.
5. Pro odvolání u konkrétní rodičovské karty vyplňte **Odůvodnění odvolání**, zvolte **Odvolat rodičovskou vazbu** a až po přečtení dopadu **Potvrdit odvolání vazby**. Seznam zachová odvolaný záznam. Opětovné schválení používá stejnou dvojici a má audit.

Odebrání jedné z více platných aktivních rodičovských vazeb dítě neblokuje. Ztráta poslední nebo zákaz posledního kvalifikovaného rodiče zakáže CHILD a odvolá jeho sessions. Profily a sportovní historie zůstanou. Opětovné schválení vazby nebo povolení rodiče dítě automaticky nezapne: podmínky ověřte a dítě znovu samostatně aktivujte. Stará cookie zůstane neplatná i po reaktivaci, včetně přihlášení nepoužitého během zákazu.

## Trenér: svěřené soupisky — dostupné

Trenér je STANDARD účet s platným přiřazením k týmu, nikoli zvláštní systémová role. Chybějící přiřazení řeší ADMIN. V **Týmy** (`/teams`) zvolte aktuálně svěřený sezónní tým a **Zobrazit soupisku**. Seznam i počty respektují dnešní interval. Minulé ani budoucí přiřazení samo aktuální přístup nedává.

Soupiska obsahuje jména a intervaly, bez kontaktů, data narození a financí. **Profil a členství** otevře minimální profil hráče a pouze aktuálně svěřené sezónní týmy. ADMIN má navíc historický filtr **Soupiska ke dni** a správu členství. Archivace data zachová, sama ale přístup nevytváří. Ukončení přiřazení se projeví i během přihlášené session.

## Hráč: vlastní profil a historie — dostupné

Přihlášený hráč je účet propojený přes stejnou osobu s vlastním hráčským profilem. Registrace samotná toto propojení nevytváří. Na dashboardu otevřete **Můj hráčský profil** nebo **Profil a členství**. Vlastní členská historie zůstává dostupná i po přestupu. **Týmy** nabízejí jen dnešní vlastní členství. Opravu údajů a intervalů řeší ADMIN; profil bez účtu lze nadále vést offline.

### Dítě s omezeným účtem — dostupné P3

Přihlaste se vlastním dětským loginem. **Osobní údaje** jsou jen ke čtení, vlastní hráčský profil obsahuje povolenou členskou historii a **Týmy** dnešní vlastní soupisky. U spoluhráčů se zobrazují pouze jména a intervaly; cizí profil nebo sourozenec tím dostupný není.

Dítě nespravuje účty, rodiče, vlastní typ/roli/osobu nebo datum narození. Nemá **Nastavení klubu**, **Administrace**, **Moje děti** ani finanční přehled. Zákaz předpisů, plateb, součtů, exportů a finančních oznámení včetně počtů platí i pro přímé API cesty. Jde o předběžnou bezpečnostní hranici; příspěvky a schránka ještě nejsou dostupné ani jako dokončená rodičovská agenda. Sportovní kalendář, dostupnost a komunikace jsou také plán.

Po zákazu účtu nebo ztrátě posledního aktivního rodiče se znovu nepřihlašujte cizími údaji. Rodič s ADMIN ověří vazbu a samostatnou aktivaci; po ní použijte nové přihlášení.

## Rodič / zákonný zástupce: moje děti — dostupné P3

Přihlaste se vlastním povoleným STANDARD účtem na osobě schváleného zástupce. Systémová role PARENT se neuděluje. Dítě vlastní účet mít nemusí.

1. V navigaci nebo dashboardu otevřete **Moje děti** (`/my-children`). Seznam i počty obsahují jen schválené děti této osoby; při přímém otevření bez vazeb je seznam prázdný. Chybějící dítě řešte s ADMIN, dosazení cizího ID právo nezaloží.
2. U konkrétního dítěte otevřete **Profil dítěte**. Uvidíte základní jméno a **Historie členství**, bez kontaktů, data narození nebo finančních údajů.
3. **Zobrazit tým** je dostupné pro dnešní oprávněný sezónní tým. V **Týmy** vidíte sjednocení dnešních týmů schválených dětí, případně vlastních a trenérských přiřazení. Minulá historie dítěte sama nedává dnešní týmový přístup. Obecný profil cizího spoluhráče může server odmítnout, i když vidíte jeho jméno na soupisce dítěte.
4. Odvolání vztahu odstraní další přístup k tomuto dítěti; jiné platné děti a oprávnění zůstanou. Dvěma rodičům se práva vyhodnocují samostatně. CHILD účet rodičovský přehled nemá.

## Jeden trenér jako jediný uživatel — dostupný základ

Stačí jeden STANDARD ADMIN, který si přiřadí trenérství, založí současně fotbalový i basketbalový tým a vede offline hráče. Druhý administrátor, hráčská přihlášení ani rodiče nejsou podmínkou. Další účty a vztahy přidá až podle skutečné potřeby přes P3 správu. Založení dítěte bez loginu nevyžaduje rodiče; jeho CHILD aktivace ano. Události, docházku a výsledky tento základ dosud neposkytuje.

## Navazující pracovní postupy — dosud plánované

| Klubový kontext | Povinná agenda navazujících balíčků |
| --- | --- |
| Administrátor | P5 události/schránka; P6 dynamické statistiky, výsledky a výpočet pořadí; P7 předpisy, skutečné částečné úhrady/storna a splatnostní výzvy; P8 konverzace; P9 úplné HTTPS předání a obnova. |
| Trenér | Publikovat TRAINING/MATCH/OTHER, číst dostupnost, připravit a publikovat nominaci, zvlášť zapsat skutečnou docházku/nastoupení, výsledek a statistiky; povolené konverzace a historie. Nominace nebude znamenat přítomnost. Samotné trenérství neudělí finance nebo správu rodičů. |
| Hráč | Vlastní dostupnost/omluva, čtení nominace, docházky, výsledků a statistik, vlastní schránka a oprávněná komunikace. STANDARD navíc vlastní příspěvky; CHILD žádné finance ani finanční notifikace/počty. |
| Rodič | Za schválené dítě dostupnost a omluva, čtení nominace, docházky, výsledků/statistik a příspěvků, výzvy před i po splatnosti a oprávněné konverzace. Platbu provede mimo aplikaci a skutečnou úhradu zaznamená ADMIN. |

Notifikace a konverzace budou dvě samostatné funkce. ADMIN nebude automaticky číst cizí soukromé rozhovory. Tabulky se vypočtou z potvrzených výsledků podle konfigurovatelných pravidel pro oba sporty; roadmapa nepředstavuje dokončenou implementaci.

## Upgrade a obvyklé situace

V2 zachovává UUID, BCrypt a časy, převádí staré COACH/PLAYER/PARENT na USER bez domýšlení vazeb. Neúplný účet má vlastní onboarding nebo ověřený admin person-link. V4 při upgradu zakáže všechny dřívější CHILD účty; osoba a vztahy se nevyrábějí odhadem. Správce je po ověření připraví a samostatně aktivuje.

| Situace | Postup |
| --- | --- |
| Vypršela nebo byla odvolána session | Nově se přihlásit. Změnu s neověřeným výsledkem neopakovat automaticky. Stará cookie se po povolení účtu neobnoví. |
| API je nedostupné | Ověřit spojení a službu, pak zkusit znovu; výpadek neznamená potvrzené odhlášení. |
| Zamítnutý přístup / nenalezený záznam | Ověřit typ účtu, roli a konkrétní tým/dítě. Znalost UUID ani nová registrace práva nepřidají. |
| CHILD nelze aktivovat | Ověřit vlastní osobu/hráče a alespoň jednoho schváleného rodiče s povoleným STANDARD účtem. Osoba rodiče bez účtu nestačí. |
| Rodič byl znovu povolen, dítě zůstalo zakázané | ADMIN znovu ověří podmínky a samostatně použije Aktivovat dětský účet. |
| Nelze odebrat posledního správce | Nejprve zajistit jiný povolený STANDARD ADMIN s dokončenou osobou; neobcházet kontrolu ručním SQL. |
| Konflikt formuláře | Načíst aktuální stav a změnu znovu posoudit. Heslo po chybě zadat znovu; neodesílá se automaticky. |
| Hráč nemá účet ani rodiče | Sportovní evidenci vede ADMIN; povolenou soupisku čte přiřazený trenér. Nevytvářet fiktivní účty nebo doručení. |

Technické problémy zapisujte do issue nového repozitáře s reprodukcí, verzí API a chybou. Nevkládejte hesla, `.env`, cookies, osobní údaje dětí ani dumpy. Provozní kontakty a produkční URL se doplní po skutečném nasazení podle [postupu](deployment.md).
