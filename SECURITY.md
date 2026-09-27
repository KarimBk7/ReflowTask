# Security

## Supported use

ReflowTask is built for a **private network**: a home server reached directly or over a VPN such as
Tailscale or WireGuard. It is not hardened for exposure to the public internet. Please do not
port-forward it. The household login keeps the people who share one instance apart from each other.

What is in place:

- Passwords are stored as bcrypt hashes, never in clear text, and are at least 8 characters.
- Sessions are random 256-bit tokens in an `HttpOnly`, `SameSite=Lax` cookie, valid for 30 days,
  ended by logout, a password reset or removing the account.
- Five wrong passwords lock that username for a minute.
- Every task, block, replan record and setting is scoped to its owner, and another user's id
  answers `404`. This is covered by tests that try every id-taking endpoint as a second user.
- Recovery needs a shell on the machine. There is no reset link that could be intercepted.
- A calendar subscribe link carries a random 256-bit token and is the one endpoint that answers
  without a session. It shows that one person's blocks, read-only; making a new link or turning it
  off invalidates the old token at once.
- Adding another calendar makes the server fetch a URL the member typed. Only `http`, `https` and
  `webcal` are accepted, with a 10-second connect and 20-second overall timeout and a 5 MB limit,
  and the response is only ever parsed as a calendar: its content and headers are never shown
  back, only its HTTP status or that it was not a calendar. Private addresses are allowed on purpose, because a calendar on the same home
  network (a Nextcloud, say) is the main use. The server can therefore be asked to send GET
  requests into its own network by any logged-in member, which is acceptable for a household and
  another reason not to expose ReflowTask publicly.

Known limits, on purpose: no `Secure` cookie flag (plain HTTP is the supported private deployment),
no CSRF tokens (`SameSite=Lax` covers a same-origin single-page app), no per-address rate limiting,
no two-factor login and no audit log of logins.

## Before you deploy

- Set `REFLOWTASK_INITIAL_ADMIN_PASSWORD` in `.env` before the first start. Otherwise the admin's
  password is the well-known `changeme` until someone logs in.
- Use a long random `POSTGRES_PASSWORD`, and do not publish the PostgreSQL port (the Compose file
  does not).
- Keep backups off the machine.

## Supported versions

Security fixes go into the newest release, which is listed in [CHANGELOG.md](CHANGELOG.md) and on the
repository's Releases page. Updating is `git fetch --tags`, checking out the new tag and
`docker compose up -d --build` (see [docs/SETUP.md](docs/SETUP.md#updating)); older releases are not
patched separately.

## Reporting a vulnerability

Please report it privately using GitHub's "Report a vulnerability" on the repository's Security
tab, or contact the maintainer through the address on their GitHub profile. Do not open a public
issue. Include what you found, how to reproduce it and what it lets an attacker do. You can expect
an acknowledgement within a week.
