# ReflowTask — Selfhosted Task Manager mit Auto-Rescheduling

**Projektname:** ReflowTask
Der Name spielt auf die Kernfunktion an: Der Zeitplan "fließt" automatisch neu (reflow),
sobald eine geplante Aufgabe verpasst wurde.

## Kontext / Motivation

Idee ursprünglich aus der [Selfhosted-Wish-List](https://github.com/tony4212/Selfhosted-Wish-List):
Ein selbstgehosteter Task-Manager, der den eigenen Zeitplan automatisch anpasst, wenn eine
geplante Aufgabe nicht erledigt wurde — ähnlich dem kommerziellen SaaS-Tool
[Focuster](https://www.focuster.com), aber selbstgehostet und open source.

Die meisten Open-Source-Task-Manager (Vikunja, Wekan, etc.) sind statische Listen oder
Kanban-Boards ohne automatische Neuplanung. Diese Lücke soll das Projekt schließen.

**Ziel dieses Projekts:** Portfolio-Projekt, das auf eigenem Home-Lab (Raspberry Pi) selbst
gehostet werden kann und Backend-Schwerpunkt zeigt (Scheduling-Logik), mit einfachem Web-Frontend.

---

## Kernproblem

Normale To-Do-Apps zeigen Aufgaben nur mit Fälligkeitsdatum an. Wird eine Aufgabe verpasst,
bleibt sie einfach "überfällig" — der restliche Zeitplan passt sich nicht automatisch an.
Nutzer müssen manuell neu planen.

## Lösung

Aufgaben werden nicht nur mit Deadline, sondern mit einer **geschätzten Dauer** erfasst und
automatisch als Zeitblöcke in den Kalender/Zeitplan eingeplant. Wird ein Zeitblock verpasst,
plant das System die verbleibenden, noch offenen Aufgaben automatisch neu — unter
Berücksichtigung von Priorität, Deadline und bereits belegten Zeitfenstern.

---

## Funktionale Anforderungen (MVP)

### 1. Aufgabenverwaltung
- Aufgabe anlegen mit: Titel, Beschreibung (optional), geschätzte Dauer (Minuten/Stunden),
  Deadline (Datum + optional Uhrzeit), Priorität (z. B. niedrig/mittel/hoch), Status
  (offen/in Bearbeitung/erledigt)
- Aufgabe bearbeiten, löschen, als erledigt markieren

### 2. Automatische Einplanung (Scheduler)
- Beim Anlegen einer Aufgabe: System schlägt automatisch einen Zeitblock vor, basierend auf:
  - verfügbaren freien Slots im definierten Arbeitszeitfenster (z. B. Mo–Fr, 9–18 Uhr,
    konfigurierbar)
  - vorhandenen, bereits eingeplanten Zeitblöcken (keine Überlappung)
  - Priorität und Deadline (dringendere/wichtigere Aufgaben bekommen frühere Slots)
- Einfacher Algorithmus für MVP: Greedy-Ansatz — sortiere offene Aufgaben nach
  (Deadline aufsteigend, Priorität absteigend) und fülle freie Slots der Reihe nach auf.
  (Optimierung/komplexere Algorithmen sind spätere Ausbaustufe, kein MVP-Blocker.)

### 3. Automatisches Rescheduling
- Cronjob/Scheduled Task (z. B. täglich oder stündlich), der prüft:
  - Ist die aktuelle Zeit nach dem Ende eines Zeitblocks, dessen Aufgabe noch nicht
    "erledigt" markiert wurde?
  - Falls ja: Aufgabe gilt als "verpasst" → wird aus dem Zeitplan entfernt und der
    Scheduler berechnet für alle offenen/verpassten Aufgaben einen neuen Plan
- Nutzer bekommt sichtbares Feedback (z. B. Log/Historie oder Benachrichtigung im UI),
  wenn eine Neuplanung stattgefunden hat

### 4. Kalender-/Wochenansicht (Frontend)
- Wochenansicht mit eingeplanten Zeitblöcken (ähnlich Google Calendar, simpler)
- Liste aller offenen, unverplanten oder verpassten Aufgaben
- Aufgabe direkt aus der Kalenderansicht als erledigt markieren

### 5. Konfiguration
- Nutzer kann eigene Arbeitszeiten definieren (z. B. Wochentage + Start-/Endzeit)
- Optional: Pausen/blockierte Zeiten (z. B. Mittagspause), die der Scheduler nicht belegt

---

## Nicht im MVP (spätere Ausbaustufe)

- Mehrbenutzerbetrieb / Auth mit mehreren Accounts (MVP: Single-User reicht)
- Externe Kalender-Integration (Google Calendar, CalDAV/Nextcloud-Sync)
- Mobile App / Push-Benachrichtigungen
- Machine-Learning-basierte Dauerabschätzung ("Aufgaben wie diese dauern bei dir meist X")
- Team-/Kollaborationsfeatures

---

## Architektur-Entscheidung: Web statt Desktop

**Web-Anwendung, kein Desktop-Client.** Begründung:

- Das Kern-Feature (automatisches Rescheduling per Cronjob) braucht einen Server, der
  durchgehend läuft — unabhängig davon, ob ein bestimmtes Gerät gerade an ist. Das setzt
  ohnehin eine Client-Server-Architektur voraus, keinen reinen Desktop-Client.
- Zielumgebung ist selbst gehostet auf einem Raspberry Pi (Server-Use-Case).
- Web-Frontend ist von jedem Gerät im Netzwerk erreichbar, ohne Installation.

**API-first, um spätere Mobile-Erweiterung vorzubereiten:** Das Backend wird als saubere
REST-API gebaut, unabhängig vom Frontend. Die Web-Oberfläche ist ein Client dieser API.
Eine spätere Mobile-App wäre ein zweiter Client derselben API — kein Backend-Rewrite nötig.

## Tech-Stack (final)

| Teil | Technologie | Begründung |
|---|---|---|
| Backend | Java + Spring Boot | Kernstärke, REST-API, `@Scheduled`-Jobs für Rescheduling |
| Datenbank | PostgreSQL (Dev: H2) | siehe unten |
| Web-Frontend | React | Komponentenbasiert; Wissen überträgt sich später direkt auf React Native |
| Später (Ausbaustufe): Mobile | React Native | nutzt dieselbe API, gleiche Denkweise/Komponentenlogik wie React — kein Umstieg auf komplett neues Framework nötig |
| Deployment | Docker Compose | App + DB, ARM64-kompatibel für Raspberry Pi |



---

## Vorgeschlagene Projektstruktur

```
reflowtask/
├── backend/
│   ├── src/main/java/.../
│   │   ├── task/          # Task Entity, Repository, Controller, Service
│   │   ├── scheduler/     # Scheduling-Algorithmus, Rescheduling-Job
│   │   └── config/        # Arbeitszeiten-Konfiguration
│   ├── src/test/java/...  # Unit-Tests für Scheduler-Logik (wichtigster Testfokus)
│   └── pom.xml
├── frontend/
│   └── ...
├── docker-compose.yml
└── README.md
```

---

## Vorschlag für erste Implementierungsschritte (für Claude Code)

1. Spring Boot Projekt aufsetzen (Web, JPA, PostgreSQL/H2, Scheduling-Dependency)
2. `Task`-Entity + Repository + einfache CRUD-REST-Endpunkte
3. Scheduler-Service: Kernalgorithmus (freie Slots berechnen, Aufgaben nach
   Priorität/Deadline einordnen) — **mit Unit-Tests**, da das die fachliche Kernlogik ist
4. Rescheduling-Job (`@Scheduled`) inkl. Logik "verpasste Aufgabe erkennen"
5. Frontend mit React: Wochenansicht + Aufgabenliste, Anbindung an REST-API
6. Docker-Setup für Home-Lab-Deployment (Raspberry Pi kompatibel)
7. README mit Setup-Anleitung fürs Portfolio (Projektname: ReflowTask)

---

## Hinweis zur Weiterverwendung

Diese Datei ist als Ausgangs-Briefing für Claude Code gedacht. Claude Code kann direkt mit
Schritt 1 beginnen; Details (z. B. genaue Scheduler-Heuristik, UI-Framework) können iterativ
verfeinert werden.
