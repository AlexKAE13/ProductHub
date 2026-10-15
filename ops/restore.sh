#!/usr/bin/env bash
# Восстановление Product HUB из копии, сделанной ops/backup.sh.
# ВНИМАНИЕ: текущие данные базы и файлов будут заменены данными из копии.
# Запуск из папки с docker-compose.yml:  ./ops/restore.sh ./backups/20261014-120000
set -euo pipefail
# Команда docker compose; при нестандартном запуске задайте переменную COMPOSE (например "docker compose -p hub -f docker-compose.yml")
COMPOSE="${COMPOSE:-docker compose}"

SRC="${1:?Укажите папку с копией, например ./backups/20261014-120000}"
[ -f "$SRC/db.dump" ] && [ -f "$SRC/files.tar.gz" ] || { echo "В $SRC нет db.dump или files.tar.gz"; exit 1; }

echo "Проверка контрольных сумм..."
( cd "$SRC" && shasum -a 256 -c SHA256SUMS )

echo "Останавливаю приложение (база остается запущенной)..."
$COMPOSE stop app

echo "Восстанавливаю базу..."
$COMPOSE exec -T db psql -U producthub -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS producthub WITH (FORCE)" -c "CREATE DATABASE producthub OWNER producthub"
$COMPOSE exec -T db pg_restore -U producthub -d producthub --no-owner --exit-on-error < "$SRC/db.dump"

echo "Восстанавливаю файлы..."
$COMPOSE run --rm --no-deps --user root -T --entrypoint sh app -c "rm -rf /data/files /data/index && mkdir -p /data"
$COMPOSE run --rm --no-deps --user root -T --entrypoint sh app -c "tar -C /data -xzf - && chown -R hub /data/files" < "$SRC/files.tar.gz"

echo "Запускаю приложение..."
$COMPOSE start app
echo "Готово. Индекс поиска перестроится сам при старте."
