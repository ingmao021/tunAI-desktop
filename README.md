# TunAI Desktop

App de escritorio (JavaFX) de TunAI: el usuario toma una foto de su carro, el backend genera un modelo 3D con Tripo API v3, y la app lo muestra, le cambia el color y lo guarda en un garaje local.

La especificación está en [`docs/HANDOFF-Frontend-UI.md`](docs/HANDOFF-Frontend-UI.md).

## Requisitos

- JDK 21 o superior (probado con 25). Maven no hace falta: el proyecto trae el Maven Wrapper `mvnw`.

## Ejecutar

```powershell
.\mvnw.cmd javafx:run
```

Por defecto la app usa el backend de producción (`https://tunai-api-qd5x.onrender.com`). Para usar un backend local:

```powershell
$env:TUNAI_API_URL = "http://localhost:8080"; .\mvnw.cmd javafx:run
```

Prioridad de la base URL: propiedad `-Dtunai.api.url` (pruebas) → variable `TUNAI_API_URL` → `apiBaseUrl` en `settings.json` → producción.

El backend ya no tiene proveedor `fake`: **cada generación exitosa gasta créditos reales de Tripo**. Las pruebas automáticas usan un servidor HTTP falso que solo existe en `src/test`.

## Prueba real con Tripo

La app no guarda ninguna clave: la de Tripo vive solo en el backend (`TRIPO_API_KEY`, en Render o en el entorno local). Antes de gastar créditos:

1. **Saldo de Tripo.** Cada modelo cuesta ~40 créditos de Tripo, y el backend exige `TUNAI_TRIPO_MIN_BALANCE` (40) disponibles antes de crear nada. Se consulta gratis:
   `curl -s -H "Authorization: Bearer $TRIPO_API_KEY" https://openapi.tripo3d.ai/v3/account/balance`.
   Con saldo insuficiente la app muestra «El servicio no está disponible en este momento» (`503 SERVICE_UNAVAILABLE`) y no descuenta créditos.
2. **Ensayo sin gasto.** Con el backend local y `TUNAI_TRIPO_MIN_BALANCE=100000`, una generación se detiene en la consulta del saldo: valida la clave, la red y el manejo de errores sin subir la foto ni crear tareas.
3. **Límites del backend.** En Render, `TUNAI_QUOTA_PER_IP` y `TUNAI_QUOTA_GLOBAL_DAILY` están en 2 (valores de la demo): el tercer modelo del día devuelve `429` aunque queden créditos.
4. **Primer modelo real.** El plazo del backend es 85 s (`TUNAI_TRIPO_MAX_WAIT`, por el proxy de ~100 s de Render). Si Tripo tarda más, el backend responde `504`, Tripo cobra igual y el GLB se pierde (PRD del backend, P-BE-10). El visor solo acepta GLB de 1 malla, 1 primitiva y 1 material: si el de Tripo no cumple, la app lo guarda en `logs\unsupported-*.glb` para ajustar el visor.
5. **Después de generar.** Comprobar que el vehículo aparece en el garaje, abre en el visor, se recolorea, se guarda y se exporta, y que el contador bajó lo que informa el backend (`creditsPerModel`).

## Instaladores

Cada instalador incluye Java (no hace falta instalarlo) y se arma con `jpackage` **en su propio sistema operativo**.

### Publicar una Release (Windows, macOS y Linux)

Corre las pruebas en local (`.\mvnw.cmd verify`) y crea un tag:

```powershell
git tag v1.0.1; git push origin v1.0.1
```

El workflow [`release.yml`](.github/workflows/release.yml) arma `TunAI-<versión>.exe`, `TunAI-<versión>.dmg` (Apple Silicon, sin firmar) y `tunai_<versión>_amd64.deb`, y los publica en **Releases**. La versión sale del tag (`v1.0.1` → `1.0.1`, solo números). Si cambia el workflow en `develop`, arma los instaladores como artefactos sin publicar.

### `.exe` en local (Windows)

Requiere el JDK 21 completo (el runtime de IntelliJ no trae `jpackage`) y los binarios de [WiX 3.14](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm) en el `PATH` (`wix314-binaries.zip`, sin instalar):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;C:\ruta\a\wix314;$env:PATH"
.\mvnw.cmd clean package -DskipTests
jpackage --type exe --name TunAI --app-version 1.0.0 --vendor TunAI `
  --input target\app --main-jar tunai-desktop.jar --main-class com.tunai.desktop.Launcher `
  --icon src\main\resources\icon.ico --dest target\installer `
  --win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid f31c8be9-4220-4bca-8eaf-1ee788b073d9
```

El instalador queda en `target\installer\`. El `--win-upgrade-uuid` no debe cambiar: con él una versión nueva reemplaza a la anterior.

## Datos locales

Todo vive en la carpeta de datos de usuario de cada sistema (`AppPaths`):

| Sistema | Carpeta |
|---|---|
| Windows | `%APPDATA%\TunAI\` |
| macOS | `~/Library/Application Support/TunAI/` |
| Linux | `$XDG_DATA_HOME/TunAI/` (por defecto `~/.local/share/TunAI/`) |

La 1.0.0 usaba `~/AppData/Roaming/TunAI` también en macOS y Linux; al abrir una versión nueva esa carpeta se mueve sola a la nueva ubicación.

| Archivo | Contenido |
|---|---|
| `installation-id` | UUID v4 de esta instalación |
| `settings.json` | consentimiento, última carpeta, `apiBaseUrl`, `classifier` |
| `garage\<id>\` | `original.glb`, `texture-edited.png`, `thumbnail.png`, `vehicle.json` |
| `logs\tunai0.log` | log de la app; los GLB no compatibles se guardan aquí como `unsupported-*.glb` |
| `vehicle-classes.txt` | (opcional) reemplaza la lista de clases de vehículo del clasificador |

### Salidas de emergencia para la demo (no visibles en la UI)

```json
{ "classifier": { "enabled": false, "threshold": 0.45 } }
```

- `classifier.enabled=false` desactiva la verificación de vehículo si rechaza una foto válida.
- `classifier.threshold` ajusta el umbral sin recompilar (ver [`docs/calibracion-clasificador.md`](docs/calibracion-clasificador.md)).

## Pruebas

```powershell
.\mvnw.cmd test
```

Incluyen pruebas de UI con TestFX que abren ventanas: no muevas el mouse mientras corren. Las capturas de cada pantalla quedan en `target\ui-snapshots\`.

En GitHub, [`ci.yml`](.github/workflows/ci.yml) corre `mvnw verify` (con las pruebas de UI) en Windows en cada push a `main` o `develop` y en cada PR. Antes de crear el instalador, `release.yml` abre la app empaquetada en Windows, macOS y Linux y exige en su log el arranque y la carga del clasificador.

## Decisiones que conviene conocer

- **La generación nunca se reintenta sola.** Cada generación (también un reintento manual) pide confirmación.
- **El modelo se guarda en el garaje en cuanto llega**, antes de abrir el visor: cada generación cuesta créditos y no debe perderse si la app se cierra.
- **Validación del GLB (§3):** se exige 1 malla, 1 primitiva, 1 material con textura de color base y ninguna extensión obligatoria. Si el GLB trae otras texturas (por ejemplo un mapa de normales), se ignoran en lugar de rechazar el modelo.
- **Recoloreado:** la máscara de pintura (`PaintMaskStrategy`) solo considera los píxeles que usa el modelo (islas UV) y busca el color dominante. Hay que ajustarla con el GLB del ensayo real.
- **Exportar GLB** reemplaza la imagen de color base reescribiendo los chunks del GLB (JglTF reescribe la imagen desde el `bufferView` original y no permitía reemplazarla de forma fiable).
- **Clasificador:** MobileNetV2 (ONNX Model Zoo); las fotos se redimensionan directamente a 224×224, como pide el documento (sin recorte central), para no cortar el carro.

## Pendientes

| Tema | Estado |
|---|---|
| Texto legal de consentimiento (Ley 1581) | PENDIENTE; reemplazar `src/main/resources/com/tunai/desktop/legal/consent-*.txt` |
| Atribución o términos exigidos por Tripo AI | PENDIENTE DE VALIDAR con sus términos; `legal/attribution.txt` y `consent-*.txt` ya nombran solo a Tripo AI |
| Licencia de los pesos de MobileNetV2 | PENDIENTE DE VALIDAR (el Model Zoo indica Apache-2.0) |
| Rines | PENDIENTE (PRD frontend §17); no implementado |
| Logo, colores de marca y tipografía | Rediseño aplicado (icono, Rajdhani, tokens en `styles/app.css`); falta la validación final de marca |
| Ajuste de la máscara de pintura y del visor con un GLB real de Tripo | Tras la primera generación pagada (la máscara está ajustada con SF3D) |
| Calibración con fotos propias del equipo | Antes del ensayo |
| Firma de los instaladores | Sin firmar: Windows SmartScreen y macOS Gatekeeper avisan la primera vez. Requiere certificado de firma de código (Windows) y cuenta de Apple Developer (macOS) |
| macOS Intel | El `.dmg` es solo para Apple Silicon |
