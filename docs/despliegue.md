# Cómo montar Ocumare Food en internet

Esta guía lleva el proyecto desde tu PC hasta una dirección pública con HTTPS, por ejemplo
`https://ocumarefood.duckdns.org`. Ahí van a vivir las dos caras del sistema:

| Dirección | Quién la usa |
|---|---|
| `https://tu-dominio/` | Los clientes: ven la carta y piden por delivery o para retirar |
| `https://tu-dominio/pos/` | El personal: mesas, pedidos, caja, cierre de turno y gestión |

Todo cuesta **0 $ al mes**. Se usan tres servicios gratuitos:

- **Aiven**: la base de datos MySQL. Plan Free: 1 GB de disco y 1 GB de RAM. Alcanza para años de
  pedidos y para las fotos de la carta, que se guardan achicadas a ~150 KB.
- **Oracle Cloud Always Free**: el servidor donde corre el programa.
- **DuckDNS**: un nombre fácil de recordar para el servidor. Con ese nombre se saca el certificado HTTPS.

> Tiempo estimado la primera vez: 1 a 2 horas. Después, cada actualización toma unos 5 minutos
> (sección 9).

Las partes marcadas con 💻 se hacen en tu PC con Windows. Las marcadas con 🖥️ se hacen dentro del
servidor, conectado por SSH. Cada comando se copia y se pega tal cual. Donde aparezca algo
`EN_MAYÚSCULAS`, reemplázalo por tu dato.

---

## Índice

1. [Crear la base de datos en Aiven](#1-crear-la-base-de-datos-en-aiven)
2. [Crear el servidor en Oracle Cloud](#2-crear-el-servidor-en-oracle-cloud)
3. [Abrir los puertos 80 y 443](#3-abrir-los-puertos-80-y-443)
4. [Conseguir el dominio en DuckDNS](#4-conseguir-el-dominio-en-duckdns)
5. [Preparar el servidor](#5-preparar-el-servidor)
6. [Armar el paquete en tu PC y subirlo](#6-armar-el-paquete-en-tu-pc-y-subirlo)
7. [Configurar y arrancar Ocumare Food](#7-configurar-y-arrancar-ocumare-food)
8. [Activar HTTPS con Caddy](#8-activar-https-con-caddy)
9. [Primer ingreso y puesta a punto](#9-primer-ingreso-y-puesta-a-punto)
10. [Respaldos automáticos](#10-respaldos-automáticos)
11. [Publicar una versión nueva](#11-publicar-una-versión-nueva)
12. [Pasar a los clientes a la web nueva](#12-pasar-a-los-clientes-a-la-web-nueva)
13. [Si algo falla](#13-si-algo-falla)

---

## 1. Crear la base de datos en Aiven

1. Entra a <https://console.aiven.io/signup> y crea la cuenta. No pide tarjeta.
2. Pulsa **Create service** → **MySQL**.
3. En el plan elige **Free**. En la región elige la más cercana a Venezuela que ofrezca el plan
   gratis, normalmente una de Norteamérica.
4. Ponle de nombre `mysql-ocumare` y pulsa **Create service**. Tarda unos minutos en quedar
   en estado **Running**.
5. Abre el servicio. En **Overview → Connection information** vas a ver estos datos. Cópialos en un
   bloc de notas, porque los usarás en el paso 7:

   | Dato en Aiven | Ejemplo |
   |---|---|
   | Host | `mysql-ocumare-carlos.aivencloud.com` |
   | Port | `12345` |
   | User | `avnadmin` |
   | Password | (pulsa el ojo para verla) |
   | Database name | `defaultdb` |

No hace falta crear tablas. El programa las crea solo la primera vez que arranca.

> ⚠️ Aiven puede apagar un servicio gratuito si pasa mucho tiempo sin uso. Con el restaurante
> trabajando eso no debería pasar, y siempre avisan por correo antes. Si llega a apagarse, se
> enciende de nuevo desde la consola con **Power on**.

---

## 2. Crear el servidor en Oracle Cloud

1. Crea la cuenta en <https://signup.cloud.oracle.com>. Pide una tarjeta solo para verificar
   la identidad; las cuentas gratuitas no cobran.
   - La **región de origen (home region)** no se puede cambiar después. Elige una con buena
     conexión a Venezuela, por ejemplo **US East (Ashburn)** o **Brazil East (São Paulo)**.
2. En el menú ☰ ve a **Compute → Instances → Create instance**.
3. Llena el formulario así:
   - **Name**: `ocumare`
   - **Image**: pulsa *Change image* → **Canonical Ubuntu 24.04**.
   - **Shape**: pulsa *Change shape* → **Ampere** → `VM.Standard.A1.Flex` con
     **1 OCPU y 6 GB** de memoria. Es más que suficiente y entra en lo gratuito. Desde junio de
     2026 el límite gratuito es de 2 OCPU y 12 GB en total.
     - Si sale *Out of capacity*, inténtalo más tarde o en otro *Availability domain*. Como
       plan B puedes usar `VM.Standard.E2.1.Micro` (1 GB de RAM; mira la nota de la sección 7).
   - **Networking**: deja la red que propone y verifica que esté marcado
     **Assign a public IPv4 address**.
   - **Add SSH keys**: elige **Generate a key pair for me** y pulsa
     **Save private key**. Guarda el archivo `.key` en una carpeta segura, por ejemplo
     `C:\Users\Carlo\.ssh\ocumare.key`. **Sin este archivo no podrás entrar al servidor.**
4. Pulsa **Create**. Cuando el estado diga **Running**, copia la **Public IP address**.
   La llamaremos `IP_DEL_SERVIDOR`.

> ℹ️ Oracle puede recuperar las máquinas gratuitas que pasan 7 días casi sin uso: CPU, red y
> memoria por debajo del 20 %. Con 6 GB de memoria y Java usando unos 800 MB podría verse
> "ociosa". Si algún día te llega un correo de Oracle sobre una instancia inactiva, la solución
> recomendada es pasar la cuenta a **Pay As You Go**. Lo que ya es gratis sigue costando 0 $ y
> Oracle deja de recuperar la máquina; solo se cobraría si creas recursos de pago a propósito.

### Probar la conexión 💻

Abre **PowerShell** y escribe:

```powershell
ssh -i C:\Users\Carlo\.ssh\ocumare.key ubuntu@IP_DEL_SERVIDOR
```

La primera vez pregunta *Are you sure you want to continue connecting?*. Escribe `yes`.
Si ves algo como `ubuntu@ocumare:~$`, ya estás dentro del servidor. Para salir escribe `exit`.

---

## 3. Abrir los puertos 80 y 443

Oracle tiene **dos** cortafuegos y hay que abrir los dos. Si falta uno, la página no cargará.

### 3a. En la consola de Oracle (lista de seguridad)

1. Abre la instancia → enlace de la **Subnet** → **Security Lists** →
   `Default Security List for…`.
2. **Add Ingress Rules** y agrega estas dos reglas:

   | Source CIDR | IP Protocol | Destination Port Range |
   |---|---|---|
   | `0.0.0.0/0` | TCP | `80` |
   | `0.0.0.0/0` | TCP | `443` |

### 3b. Dentro del servidor 🖥️

Las imágenes Ubuntu de Oracle traen reglas `iptables` que bloquean todo menos SSH. Ábrelas y deja
el cambio guardado:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

---

## 4. Conseguir el dominio en DuckDNS

1. Entra a <https://www.duckdns.org> e inicia sesión con Google o GitHub.
2. En **sub domain** escribe el nombre que quieras, por ejemplo `ocumarefood`, y pulsa **add domain**.
3. En el campo **current ip** de ese dominio pega `IP_DEL_SERVIDOR` y pulsa **update ip**.

Tu dirección será `ocumarefood.duckdns.org`. La IP de Oracle no cambia mientras la instancia
exista, así que esto se hace una sola vez.

> Si más adelante compran un dominio propio, como `ocumarefoodhot.com`, solo hay que apuntarlo a
> la misma IP y cambiar el nombre en el Caddyfile (paso 8).

---

## 5. Preparar el servidor 🖥️

Conéctate por SSH como en el paso 2 y ejecuta estos bloques en orden.

**Hora de Venezuela y actualizaciones:**

```bash
sudo timedatectl set-timezone America/Caracas
sudo apt update && sudo apt upgrade -y
```

**Java 21, Caddy (para HTTPS) y el cliente de MySQL (para los respaldos):**

```bash
sudo apt install -y openjdk-21-jre-headless caddy mysql-client
java -version
```

La última línea debe mostrar `openjdk version "21…"`.

**Usuario y carpetas del programa:**

```bash
sudo useradd --system --home /opt/ocumare --shell /usr/sbin/nologin ocumare
sudo mkdir -p /opt/ocumare /etc/ocumare
sudo chown ocumare:ocumare /opt/ocumare
```

El programa corre con su propio usuario sin permisos de administrador. Así, si algo saliera mal,
no puede tocar el resto del servidor.

---

## 6. Armar el paquete en tu PC y subirlo 💻

1. En la carpeta del proyecto (`C:\Users\Carlo\IdeaProjects\ocumare_food`), haz doble clic en
   **`empaquetar.bat`**. Corre todas las pruebas y arma el paquete. Al final debe decir:

   ```
   Paquete listo: C:\Users\Carlo\IdeaProjects\ocumare_food\build\libs\ocumare.jar
   ```

   Si dice *Algo falló*, abre `build-log.txt`; el error aparece al final.

2. Sube el paquete y la carpeta `deploy` al servidor. Abre PowerShell **en la carpeta del
   proyecto** y ejecuta:

   ```powershell
   scp -i C:\Users\Carlo\.ssh\ocumare.key build\libs\ocumare.jar ubuntu@IP_DEL_SERVIDOR:~/
   scp -i C:\Users\Carlo\.ssh\ocumare.key -r deploy ubuntu@IP_DEL_SERVIDOR:~/
   ```

   > ¿Prefieres arrastrar archivos? Usa **WinSCP** (<https://winscp.net>): protocolo SFTP,
   > host `IP_DEL_SERVIDOR`, usuario `ubuntu`. En *Avanzado → SSH → Autenticación* elige el
   > archivo `ocumare.key`; WinSCP ofrece convertirlo, acepta. Arrastra `ocumare.jar` y la
   > carpeta `deploy` a `/home/ubuntu`.

---

## 7. Configurar y arrancar Ocumare Food 🖥️

**7.1 Colocar los archivos en su lugar:**

```bash
sed -i 's/\r$//' ~/deploy/*            # quita finales de línea de Windows, por si acaso
sudo mv ~/ocumare.jar /opt/ocumare/ocumare.jar
sudo chown ocumare:ocumare /opt/ocumare/ocumare.jar
sudo cp ~/deploy/ocumare.service /etc/systemd/system/ocumare.service
sudo cp ~/deploy/ocumare.env.ejemplo /etc/ocumare/ocumare.env
chmod +x ~/deploy/*.sh
```

**7.2 Generar la clave secreta de las sesiones.** Copia el resultado; lo usarás enseguida:

```bash
openssl rand -base64 48
```

**7.3 Llenar las variables:**

```bash
sudo nano /etc/ocumare/ocumare.env
```

Reemplaza cada valor de ejemplo:

- `DB_URL`: `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED` con el Host y el Port de Aiven
  (paso 1).
- `DB_USER` y `DB_PASSWORD`: usuario y contraseña de Aiven.
- `JWT_SECRET`: el resultado del paso 7.2.
- `ADMIN_USERNAME` y `ADMIN_PASSWORD`: el usuario administrador con el que entrarás al POS. Usa
  una contraseña larga que no uses en otro sitio.
- `TELEGRAM_TOKEN` y `TELEGRAM_CHAT_ID`: para recibir un mensaje por cada pedido web. Puedes
  dejarlos vacíos y llenarlos después.
- `DB_HOST`, `DB_PORT` y `DB_NAME`: los mismos datos de Aiven, separados. Los usa el respaldo.

Guarda con **Ctrl+O**, **Enter**, y sal con **Ctrl+X**. Después protege el archivo para que solo
el administrador pueda leerlo:

```bash
sudo chmod 600 /etc/ocumare/ocumare.env
```

> Si usaste la máquina **E2.1.Micro** (1 GB de RAM), haz además estos dos cambios para que Java
> quepa:
> ```bash
> sudo sed -i 's/-Xms256m -Xmx768m/-Xms128m -Xmx400m/' /etc/systemd/system/ocumare.service
> sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
> echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
> ```

**7.4 Arrancar y dejarlo encendido para siempre.** Así también arranca solo si el servidor se
reinicia:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ocumare
sudo journalctl -u ocumare -f
```

El último comando muestra el registro en vivo. Espera hasta ver una línea como
`Started OcumareFoodApplication in 25.3 seconds` y sal con **Ctrl+C**.

La primera vez el programa hace tres cosas solo:

- Crea las tablas en Aiven.
- Crea el usuario administrador.
- Carga la carta inicial: 9 categorías, 50 platos con sus fotos y 5 destacados.

**Comprobación rápida:**

```bash
curl -s http://127.0.0.1:8080/api/menu | head -c 300
```

Si aparece texto con nombres de platos, el programa funciona.

---

## 8. Activar HTTPS con Caddy 🖥️

```bash
sudo cp ~/deploy/Caddyfile /etc/caddy/Caddyfile
sudo nano /etc/caddy/Caddyfile        # cambia ocumarefood.duckdns.org por tu dominio si es otro
sudo systemctl reload caddy
```

Caddy pide el certificado HTTPS gratuito (Let's Encrypt) y lo renueva solo. Tarda menos de un
minuto. Abre en el navegador:

- `https://TU_DOMINIO/`: debe verse la web de los clientes con la carta y las fotos.
- `https://TU_DOMINIO/pos/`: debe verse el inicio de sesión del POS.

Si no carga, mira la sección 13.

---

## 9. Primer ingreso y puesta a punto

En `https://TU_DOMINIO/pos/` entra con el `ADMIN_USERNAME` y el `ADMIN_PASSWORD` del paso 7.3, y
haz esto una sola vez:

1. **Gestión → Personal**: crea un usuario **Gerente** para cada encargado. Los meseros puede
   crearlos el administrador o cualquier gerente. Cada persona puede cambiar luego su propia
   contraseña desde el menú.
   - Crea también un **segundo administrador** de respaldo, por ejemplo para el dueño. Solo un
     administrador puede cambiar la clave de otro administrador o de un gerente.
2. **Gestión → Salón**: crea las mesas. Puedes ponerles nombre, zona y capacidad, y ordenarlas
   arrastrándolas.
3. **Gestión → Carta**: revisa precios y disponibilidad. Para la foto de un plato nuevo pulsa
   **Subir foto**; desde el celular puedes tomarla con la cámara. El sistema la achica sola.
4. **Caja → Abrir turno**: registra el fondo inicial.
5. **Tasa del día**: no hay que hacer nada. El servidor consulta la tasa oficial del **BCV** cada
   hora y la registra sola cuando el BCV publica una nueva. Si hace falta otra, por ejemplo la del
   paralelo, el gerente toca la tasa en la barra superior y la pone a mano. La manual se respeta
   hasta que el BCV publique la siguiente.

Mientras no haya un turno abierto, la web de clientes avisa que el restaurante no está recibiendo
pedidos.

---

## 10. Respaldos automáticos 🖥️

El script `respaldo.sh` guarda toda la base cada día: pedidos, pagos, turnos, carta **y fotos**.
Los archivos quedan en `/home/ubuntu/respaldos` y se conservan 14 días.

**Probarlo una vez a mano:**

```bash
sudo ~/deploy/respaldo.sh
```

Debe terminar con `Respaldo listo: /home/ubuntu/respaldos/ocumare-AAAA-MM-DD_HHMM.sql.gz`.

**Programarlo todos los días a las 4:30 a. m. (hora de Venezuela):**

```bash
(sudo crontab -l 2>/dev/null; echo '30 4 * * * /home/ubuntu/deploy/respaldo.sh >> /var/log/ocumare-respaldo.log 2>&1') | sudo crontab -
```

El respaldo no detiene el sistema, así que puede correr aunque el turno de noche siga abierto.

**Bajar una copia a tu PC de vez en cuando** 💻. Es recomendable, por si se pierde el servidor
completo:

```powershell
scp -i C:\Users\Carlo\.ssh\ocumare.key "ubuntu@IP_DEL_SERVIDOR:~/respaldos/*.sql.gz" C:\Users\Carlo\Respaldos\
```

**Restaurar un respaldo.** Esto reemplaza los datos actuales, así que úsalo solo en una
emergencia:

```bash
sudo systemctl stop ocumare
source /etc/ocumare/ocumare.env
gunzip -c /home/ubuntu/respaldos/ARCHIVO.sql.gz | MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --ssl-mode=REQUIRED "$DB_NAME"
sudo systemctl start ocumare
```

> Aiven guarda sus propias copias internas, pero en el plan Free son limitadas. Esta es la copia que
> controlas tú.

---

## 11. Publicar una versión nueva

Cada vez que haya cambios en el programa:

1. 💻 Doble clic en **`empaquetar.bat`** y espera *Paquete listo*.
2. 💻 Sube el paquete:

   ```powershell
   scp -i C:\Users\Carlo\.ssh\ocumare.key build\libs\ocumare.jar ubuntu@IP_DEL_SERVIDOR:~/
   ```

3. 🖥️ Instálalo:

   ```bash
   ~/deploy/actualizar.sh
   ```

   El script guarda la versión anterior, instala la nueva y comprueba que responda. Si algo sale
   mal, te dice cómo volver atrás.

> El POS se cierra unos 30 segundos mientras reinicia. Hazlo en un momento tranquilo; los pedidos
> y la caja quedan guardados en la base.

---

## 12. Pasar a los clientes a la web nueva

La web antigua en GitHub Pages enviaba los pedidos a Google Apps Script. La nueva vive en
`https://TU_DOMINIO/` y manda los pedidos directo al POS. Para no perder clientes:

1. Cambia el enlace de la biografía de Instagram y el mensaje automático de WhatsApp por
   `https://TU_DOMINIO/`.
2. Reemplaza el `index.html` del repositorio de GitHub Pages por esta redirección, para que quien
   tenga guardado el enlace viejo llegue a la web nueva:

   ```html
   <!doctype html>
   <meta charset="utf-8">
   <meta http-equiv="refresh" content="0; url=https://TU_DOMINIO/">
   <title>Ocumare Food Hot</title>
   <p>Nos mudamos: <a href="https://TU_DOMINIO/">pide aquí</a>.</p>
   ```

3. Cuando confirmes que los pedidos llegan al POS, puedes desactivar el Apps Script.

---

## 13. Si algo falla

| Síntoma | Qué revisar |
|---|---|
| La página no abre (se queda cargando) | Los **dos** cortafuegos del paso 3. Comprueba que DuckDNS apunta a la IP correcta. |
| Error del certificado o "conexión no privada" | `sudo journalctl -u caddy -n 50`. Casi siempre es el puerto 80 cerrado o un dominio mal escrito en el Caddyfile. |
| **502 Bad Gateway** | El programa está apagado o arrancando. Mira `sudo systemctl status ocumare` y `sudo journalctl -u ocumare -n 80`. |
| `Communications link failure` en el registro | Datos de Aiven mal copiados en `ocumare.env`, o el servicio de Aiven está apagado. |
| `Access denied for user` | La contraseña de Aiven en `DB_PASSWORD` está mal. |
| `JWT_SECRET` o `Could not resolve placeholder` | Falta una variable en `ocumare.env`. Revisa que no tenga espacios alrededor del `=`. |
| No llegan avisos por Telegram | Verifica `TELEGRAM_TOKEN` y `TELEGRAM_CHAT_ID` y escríbele primero al bot desde ese chat. |
| La web dice que no está recibiendo pedidos | No hay turno abierto en **Caja**. |
| La tasa no se actualiza sola | `sudo journalctl -u ocumare \| grep "Tasa BCV"`. Si el servicio de la tasa falla, ponla a mano mientras tanto. |
| Un mesero olvidó su contraseña | Un gerente o administrador le pone una nueva desde **Personal**. |
| Un gerente o administrador olvidó su contraseña | Otro **administrador** se la cambia desde **Personal**. Por eso conviene tener dos (paso 9). |

**Comandos útiles:**

```bash
sudo systemctl status ocumare          # ¿está corriendo?
sudo systemctl restart ocumare         # reiniciarlo
sudo journalctl -u ocumare -n 100      # últimas 100 líneas del registro
sudo journalctl -u ocumare --since "1 hour ago"
df -h /                                # espacio en disco del servidor
```

---

## Resumen de seguridad

- Las contraseñas viven **solo** en `/etc/ocumare/ocumare.env` del servidor, con permisos `600`.
  Nunca van en el código ni en GitHub.
- Si alguna vez una contraseña de Aiven quedó publicada, por ejemplo en un chat o en un archivo
  compartido, cámbiala en Aiven (**Users → Reset password**) y actualízala en `ocumare.env`.
- El programa solo escucha dentro del servidor (`127.0.0.1:8080`). Desde internet únicamente se
  llega a través de Caddy con HTTPS.
- El inicio de sesión del POS se bloquea 5 minutos después de 5 intentos fallidos.
