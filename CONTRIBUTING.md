# Contributing to ReflowTask

Thanks for wanting to change it. ReflowTask is MIT-licensed and meant to be forked: take it,
bend it to your household, add the feature you miss. If you think others would want it too, a
pull request is welcome.

## Run it for development

You need Java 25 and Node.js 22 or newer. Maven is included as a wrapper.

```bash
# terminal 1: backend on :8080, H2 file database in backend/data/
cd backend
./mvnw spring-boot:run

# terminal 2: frontend on :5173, proxies /api to the backend
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and log in as `admin` / `changeme` (you will be asked to choose a new
password). To skip that, start the backend with `REFLOWTASK_INITIAL_ADMIN_PASSWORD=some-password`.
To start over, stop the backend and delete `backend/data/`. H2 allows one process on its file, so
stop the app before running the [password-reset command](docs/SETUP.md#forgot-a-password) against
it.

## Before you open a pull request

```bash
cd backend && ./mvnw test           # all backend tests
cd frontend && npm test             # frontend unit tests
cd frontend && npm run build        # typecheck and production build
cd frontend && npx oxlint           # lint
```

CI runs the same. A change that touches behaviour needs a test that would fail without it. If you
fix a bug, write the failing test first and check it fails for the reason you think.

## Where things live

| To change... | Look in |
| --- | --- |
| How work is placed | `backend/.../schedule/SchedulePlanner.java`: a pure function, no database. Rules are tested in `SchedulePlannerTests`. |
| What survives a replan, and what is recorded | `backend/.../schedule/SchedulerService.java` |
| Working hours and planning settings | `backend/.../config/` |
| Accounts, sessions, recovery | `backend/.../user/` |
| The database | a **new** `backend/src/main/resources/db/migration/V<n>__name.sql`. Never edit a migration that has shipped. |
| An endpoint | its controller, plus a test in the matching `*ApiTests` |
| The week or month views | `frontend/src/week/`, `frontend/src/month/` |
| Text shown to people | `frontend/src/i18n/en.ts`, the one file for every string |
| Colours, spacing, type | `frontend/src/design/tokens.css` (see [DESIGN.md](DESIGN.md)) |

Read [PRODUCT.md](PRODUCT.md) first if you are unsure whether an idea fits: it records who this is
for and which trade-offs were made on purpose.

## Conventions worth knowing

- **Everything a user owns is scoped to the user.** Every repository query for tasks, blocks,
  events, hours or settings takes a user id, and `TimeBlock` is owned through its task. A new
  user-owned table gets a `user_id` column with `ON DELETE CASCADE`, and a case in
  `DataIsolationTests`. An id that belongs to someone else must answer `404`, exactly like one that
  does not exist.
- **Migrations must run on both H2 and PostgreSQL.** Tests run on H2, production on PostgreSQL. H2
  scopes constraint names to the whole schema, and unnamed constraints are named differently, so
  recreate a table rather than dropping an unnamed constraint.
- **Scheduling times are wall-clock.** `LocalDateTime` with no zone is deliberate: 09:00 means
  09:00. Tests use `SettableClock`, never the real one, when time matters.
- **Comments say why, not what.** Keep them short.
- **No silent schedule changes.** Anything that moves a block must be recorded as a reschedule event.
- **A test transaction hides some bugs.** `@Transactional` on a test class wraps every request in one
  Hibernate session, so an entity that would arrive detached in production stays attached in the
  test. If a bug depends on that, suspend the transaction for that test
  (see `UserApiTests.changingThePasswordActuallyPersistsAcrossSeparateRequests`).

## Forking and running your own version

Nothing ties the project to one host: the Compose file and Dockerfiles work for any fork. Change
`docker-compose.yml`'s build contexts if you rename directories, and see
[docs/SETUP.md](docs/SETUP.md) for deployment. If you keep the login, remember to keep
`AdminAccountRunner` so device owners can still recover accounts.

## Reporting bugs and vulnerabilities

Bugs: open an issue with what you did, what you expected, what happened, and the version (`git
rev-parse --short HEAD`). Security problems: see [SECURITY.md](SECURITY.md), not a public issue.
