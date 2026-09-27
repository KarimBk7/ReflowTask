# Changelog

All notable changes to ReflowTask are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/): a new major version would mean an upgrade needs more
than `git pull` and `docker compose up -d --build`.

## [1.0.0] - 2026-09-28

The first release: a self-hosted planner that places tasks into your week as time blocks and
replans on its own when work is missed.

### Planning

- Tasks with a title, notes, estimated duration, priority and an optional deadline (a date, with
  an optional time).
- Automatic placement into free working time, earliest deadline first, then priority. A task stays
  in one piece when a gap holds it and is split only when it must, never below a minimum length.
- Missed blocks are found by an hourly job and the work is planned again; the missed block stays as
  a record and can still be marked done.
- Work that cannot finish before its deadline is still scheduled and flagged at risk.
- Parts of split work can be marked done one by one; only what is left is planned.
- Repeating tasks (daily on working days, weekly, every two weeks, monthly), one occurrence at a
  time; periods that went by are skipped rather than owed.
- Earliest start: a task can be held back until a chosen day.
- A task can wait for another; an urgent task pulls the one it waits for forward.
- Work and personal time: personal hours beside working hours, and tasks marked Personal are
  planned only there.
- A freeze window keeps work that is about to start in place when something urgent comes in.
- Breaks, a buffer between tasks, the planning horizon and the smallest piece are configurable per
  person. Days off are a switch per weekday, so shift and weekend work are covered.

### The calendar

- Week view with drag to move, drag to resize, pinning and keyboard moves (Alt + arrows).
- Every replan is recorded: moved blocks carry an amber ring and a "from" note, their old slot is
  outlined, and the Activity list says what moved where and why.
- Month view as an overview, and a list view of every task with search and filters.
- A workload meter per day.
- Undo for moves, resizes and marking done, from the message that follows or with Ctrl+Z.
- A top bar with only what planning needs; settings, sync, the household, help and logging out sit
  in a menu behind your initial.
- A phone layout: one day at a time under a strip of the week, tabs at the bottom and a floating
  New task button. Long press drags a block on touch screens. It can be added to the home screen
  and opens full screen with its own icon.
- English and German, chosen by the browser or per person.
- Light and dark themes that follow the operating system.

### Calendar sync

- A secret subscribe link publishes your plan as an `.ics` feed for Apple Calendar, Android
  (ICSx⁵), Thunderbird and Outlook, with reminders.
- Other calendars (Nextcloud, Google, Outlook, iCloud, anything with an `.ics` address) are read
  every hour; their appointments block time and show on the week view.

### Households

- One login per person, each with their own tasks, hours, calendars and history.
- An admin adds and removes accounts and can reset a password; whoever has a shell on the device
  can reset any account with `scripts/reset-password.sh`.
- Passwords stored as bcrypt hashes, `HttpOnly` session cookies, and a lockout after five wrong
  passwords.
- Everyone can export their own data as JSON, or their tasks as CSV.

### Running it

- Docker Compose with PostgreSQL, the Spring Boot backend and nginx; runs on a Raspberry Pi.
- The backend's tests run inside its image build, so a broken image never starts.
- `scripts/backup.sh` writes verified, rotating database backups.
- Documentation: a setup guide with screenshots, security notes and a contributing guide.

[1.0.0]: https://github.com/KarimBk7/ReflowTask/releases/tag/v1.0.0
