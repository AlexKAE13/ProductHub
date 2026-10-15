#!/usr/bin/env bash
# Резервная копия Product HUB: дамп PostgreSQL и архив файлов (материалы, прошивки).
# Индекс поиска в копию не входит: его можно перестроить кнопкой "Переиндексировать" в "Справочниках".
# Запуск из папки с docker-compose.yml:  ./ops/backup.sh [папка_для_копий] [сколько_дней_хранить]
set -euo pipefail
# Команда docker compose; при нестандартном запуске задайте переменную COMPOSE (например "docker compose -p hub -f docker-compose.yml")
COMPOSE="${COMPOSE:-docker compose}"

DEST="${1:-./backups}"
KEEP_DAYS="${2:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$DEST/$STAMP"
mkdir -p "$TARGET"

echo "Дамп базы данных..."
$COMPOSE exec -T db pg_dump -U producthub -d producthub --format=custom > "$TARGET/db.dump"

echo "Архив файлов..."
$COMPOSE exec -T app tar -C /data -czf - files > "$TARGET/files.tar.gz"

# Контрольные суммы, чтобы при восстановлении убедиться, что копия не повреждена
( cd "$TARGET" && shasum -a 256 db.dump files.tar.gz > SHA256SUMS )

# Удаляем копии старше KEEP_DAYS дней
find "$DEST" -mindepth 1 -maxdepth 1 -type d -mtime "+$KEEP_DAYS" -exec rm -rf {} + 2>/dev/null || true

echo "Готово: $TARGET"
ls -lh "$TARGET"
