**Simulacija rada luke**

**Opis projekta**

**Port** je desktop aplikacija razvijena u programskom jeziku Java, u okviru predmeta Programski jezici 2 na Elektrotehničkom fakultetu Univerziteta u Banjoj Luci. Cilj projekta je razvoj aplikacije za upravljanje lukom i simulaciju pomorskog saobraćaja, uključujući kretanje plovila kroz plovne kanale, korišćenje vezova, upravljanje različitim tipovima plovila i obradu posebnih događaja tokom simulacije. Aplikacija se sastoji iz dva osnovna dijela: administratorskog dijela, namijenjenog upravljanju lukom i plovilima, i korisničkog dijela, namijenjenog simulaciji pomorskog saobraćaja.

**Funkcionalnosti**
Prema specifikaciji projektnog zadatka, aplikacija obuhvata sljedeće funkcionalnosti:

**Administratorski dio**
- Dodavanje novih plovila u luku.
- Pregled plovila raspoređenih po terminalima.
- Izmjena i brisanje postojećih plovila.
- Podrška za različite tipove plovila i njihove specifične karakteristike.
- Upravljanje početnim stanjem luke.
- Pokretanje korisničkog dijela aplikacije.

**Korisnički dio i simulacija**
- Simulacija kretanja plovila kroz plovne kanale.
- Raspoređivanje plovila na terminale i vezove.
- Poštovanje pravila pomorskog saobraćaja i prvenstva prolaza.
- Evidencija slobodnih vezova i kapaciteta terminala.
- Simulacija ulaska i izlaska plovila iz luke.
- Obrada mogućih pomorskih nezgoda i sprovođenje uviđaja.
- Podrška za specijalna plovila državnih službi.

Podržani tipovi plovila su kontejnerski brod, putnički kruzeri, tankeri, plovila obalske straže, carinska plovila i vatrogasna plovila. Pojedina plovila državnih službi imaju posebne prioritete u saobraćaju, u skladu sa pravilima definisanim projektnim zadatkom.

**Evidencija i čuvanje podataka**
- Serijalizacija i deserijalizacija stanja luke.
- Evidencija ulazaka i izlazaka plovila.
- Obračun lučkih taksi.
- Izvoz evidencije naplate u CSV format.
- Čuvanje podataka o pomorskim nezgodama u binarnim datotekama.
- Evidentiranje izuzetaka u log datoteci.
