#!/usr/bin/env sh
# Reset a ReflowTask account's password from the machine it runs on.
#
#   ./scripts/reset-password.sh <username> [temporary-password]
#
# Run it from the directory that holds your docker-compose.yml. With no temporary password, a
# random one is generated and printed. The person must choose their own at next login, and all
# their sessions are ended. Having a shell on the device is the credential: no login is needed.
#
# Docker Compose is expected; for a plain jar run, see docs/SETUP.md ("Without Docker").
set -eu

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <username> [temporary-password]" >&2
  exit 64
fi

username="$1"
set -- --spring.main.web-application-type=none --spring.main.banner-mode=off \
  --logging.level.root=WARN "--reflowtask.reset-password.username=$username" \
  ${2:+"--reflowtask.reset-password.password=$2"}

# -T: no terminal, so it also works over a non-interactive ssh command.
exec docker compose exec -T backend java -jar app.jar "$@"
