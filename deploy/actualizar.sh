#!/usr/bin/env bash
# Instala una versión nueva: toma ~/ocumare.jar (el que subiste con WinSCP),
# guarda la anterior por si hay que volver atrás y reinicia el servicio.
set -euo pipefail
NUEVO=/home/ubuntu/ocumare.jar
[ -f "$NUEVO" ] || { echo "No encuentro $NUEVO. Súbelo primero."; exit 1; }

sudo cp /opt/ocumare/ocumare.jar /opt/ocumare/ocumare.anterior.jar 2>/dev/null || true
sudo mv "$NUEVO" /opt/ocumare/ocumare.jar
sudo chown ocumare:ocumare /opt/ocumare/ocumare.jar
sudo systemctl restart ocumare
echo "Reiniciando... espera 40 segundos."
sleep 40
if curl -fs http://127.0.0.1:8080/api/menu > /dev/null; then
  echo "Listo: la nueva versión está funcionando."
else
  echo "La aplicación no responde. Revisa: sudo journalctl -u ocumare -n 80"
  echo "Para volver a la versión anterior: sudo cp /opt/ocumare/ocumare.anterior.jar /opt/ocumare/ocumare.jar && sudo systemctl restart ocumare"
fi
