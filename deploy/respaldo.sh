#!/usr/bin/env bash
# Respaldo diario de la base de datos de Ocumare Food (incluye las fotos de los productos).
# Guarda un .sql.gz por día en /home/ubuntu/respaldos y borra los de más de 14 días.
set -euo pipefail

source /etc/ocumare/ocumare.env
DESTINO=/home/ubuntu/respaldos
mkdir -p "$DESTINO"
ARCHIVO="$DESTINO/ocumare-$(TZ=America/Caracas date +%Y-%m-%d_%H%M).sql.gz"

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --ssl-mode=REQUIRED --single-transaction --set-gtid-purged=OFF \
  --no-tablespaces --routines --hex-blob \
  "$DB_NAME" | gzip > "$ARCHIVO"

find "$DESTINO" -name 'ocumare-*.sql.gz' -mtime +14 -delete
echo "Respaldo listo: $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"
