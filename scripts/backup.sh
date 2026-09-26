#!/usr/bin/env sh
# Back up the ReflowTask database: every account, task, block and setting.
#
#   ./scripts/backup.sh [target-directory] [days-to-keep]
#
# Run it from the directory that holds your docker-compose.yml. Defaults: ~/backups/reflowtask,
# keeping 14 days. Prints the path of the new backup. Safe while the app is running.
#
# Nightly, via cron (crontab -e):
#   0 3 * * *  cd /path/to/ReflowTask && ./scripts/backup.sh >> ~/backups/reflowtask/backup.log 2>&1
#
# Restore: see docs/SETUP.md ("Backing up and restoring").
set -eu

dir="${1:-$HOME/backups/reflowtask}"
keep="${2:-14}"
mkdir -p "$dir"

file="$dir/reflowtask-$(date +%F-%H%M).sql.gz"
partial="$file.partial"

# --clean --if-exists: the dump drops what it recreates, so restoring into an existing database works.
docker compose exec -T postgres pg_dump -U reflowtask --clean --if-exists reflowtask | gzip > "$partial"

# POSIX sh has no pipefail, so a failed pg_dump would still leave a (short) file behind. pg_dump
# writes a closing line only when it finished; without it, the backup is incomplete.
if ! gzip -t "$partial" || ! gunzip -c "$partial" | tail -n 5 | grep -q "PostgreSQL database dump complete"; then
  rm -f "$partial"
  echo "$(date '+%F %T') backup FAILED: the dump is incomplete" >&2
  exit 1
fi

mv "$partial" "$file"
find "$dir" -name 'reflowtask-*.sql.gz' -type f -mtime +"$keep" -delete
echo "$(date '+%F %T') backup ok: $file"
