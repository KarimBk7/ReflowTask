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

Known limits, on purpose: no `Secure` cookie flag (plain HTTP is the supported private deployment),
no CSRF tokens (`SameSite=Lax` covers a same-origin single-page app), no per-address rate limiting,
no two-factor login and no audit log of logins.

## Before you deploy

- Set `REFLOWTASK_INITIAL_ADMIN_PASSWORD` in `.env` before the first start. Otherwise the admin's
  password is the well-known `changeme` until someone logs in.
- Use a long random `POSTGRES_PASSWORD`, and do not publish the PostgreSQL port (the Compose file
  does not).
- Keep backups off the machine.

## Reporting a vulnerability

Please report it privately using GitHub's "Report a vulnerability" on the repository's Security
tab, or contact the maintainer through the address on their GitHub profile. Do not open a public
issue. Include what you found, how to reproduce it and what it lets an attacker do. You can expect
an acknowledgement within a week.
