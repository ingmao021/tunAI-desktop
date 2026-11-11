# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

> Nota: TunAI Desktop es una aplicación de escritorio nativa (JavaFX 21, FXML + CSS, empaquetada con `jpackage`), no una página web. El valor `web` indica únicamente que no aplica la guía nativa de iOS/Android. El estilado usa el CSS de JavaFX (`styles/app.css`), que no es CSS de navegador.

## Users

- **Usuario principal (confirmado): el jurado de la demo académica.** El producto existe para la presentación universitaria del **23 de noviembre de 2026** (fecha actualizada; antes era el 9). Quien evalúa la demo es el usuario real en la práctica.
- **Usuario representado en la demo:** el dueño de un carro que le toma una foto, lo ve en 3D, le prueba colores y lo guarda en su garaje local. La demo la opera el equipo en vivo.
- Qué pasa con el proyecto después de la presentación: **sin decidir**.

## Product Purpose

El usuario toma una foto de su carro; el backend (en Render) genera un modelo 3D GLB con **Tripo API v3**; la app lo muestra en un visor 3D nativo de JavaFX, permite **recolorearlo** (máscara de pintura sobre islas UV) y lo guarda en un **garaje local**. Éxito = una demo en vivo fluida el 23-nov, sin desperdiciar créditos reales de Tripo y sin pérdida de modelos ya generados.

## Positioning

De una sola foto a un modelo 3D recoloreable, **sin cuentas, sin login y sin subir el garaje a ningún servidor**: la foto solo viaja para generar; todo lo demás (modelo, texturas, miniaturas, recoloreado) vive y se procesa en el equipo del usuario. La verificación de "¿es un vehículo?" también es local (MobileNetV2 ONNX incluido en la app).

## Operating Context

- Demo en vivo ante jurado; el `.exe` de Windows es el entregable principal (hay instaladores `.dmg` Apple Silicon y `.deb`, sin firmar).
- Backend de producción en Render (`https://tunai-api-qd5x.onrender.com`) con límites de demo: `TUNAI_QUOTA_PER_IP` y `TUNAI_QUOTA_GLOBAL_DAILY` = 2; plazo de generación 85 s (proxy de Render ~100 s); cada modelo cuesta ~40 créditos reales de Tripo.
- **Presupuesto real de generaciones muy limitado** (créditos gratuitos): una de ensayo y una en vivo. Toda la UX alrededor de generar está diseñada para no gastar por accidente.
- Sin conexión, el garaje sigue funcionando: ver, recolorear y exportar son locales.
- Ventana mínima 1280×720, redimensionable, tema claro por defecto. **Todo el texto en español** (requisito del PRD).
- Datos locales en la carpeta de usuario (`%APPDATA%\TunAI\` en Windows; ver `AppPaths`): `installation-id`, `settings.json`, `garage\<id>\`, `logs\`.

## Capabilities and Constraints

- **La generación nunca se reintenta sola**; cada generación (incluso un reintento manual) pide confirmación y la cuota/créditos son visibles para el usuario.
- **El modelo se guarda en el garaje en cuanto llega**, antes de abrir el visor: una generación pagada no debe perderse si la app se cierra.
- El visor 3D solo acepta GLB de **1 malla, 1 primitiva, 1 material** con textura de color base y sin extensiones `KHR_*` obligatorias; un GLB no compatible se rechaza con error claro y se guarda en `logs\unsupported-*.glb`.
- Con una sola malla **no se pueden reemplazar rines como pieza aparte**; la función de rines está **PENDIENTE** (PRD frontend §17), no implementada.
- Clasificador local MobileNetV2 (ONNX) verifica que la foto sea un vehículo; redimensiona directo a 224×224 (sin recorte central). Salidas de emergencia para la demo vía `settings.json` (`classifier.enabled`, `classifier.threshold`), no visibles en la UI.
- Sin cuentas ni login: cada instalación se identifica con un UUID v4 local.
- Exportar GLB reescribe los chunks del archivo (JglTF no permitía reemplazar la imagen de forma fiable).
- La máscara de pintura está ajustada con SF3D; **pendiente** ajustarla con un GLB real de Tripo tras la primera generación pagada.
- Stack fijado por el handoff: Java 21 + Maven (wrapper incluido), OpenJFX 21 LTS, FXML + CSS, JglTF, `java.net.http`, Jackson, JUnit 5 + TestFX. Prohibido: WebView con WebGL, jMonkeyEngine, LWJGL.
- Terminología del producto: **garaje**, **vehículo**, **generación**, **créditos / cuota**, **visor**.

## Brand Commitments

- **Solo el nombre "TunAI" es fijo** (confirmado por el usuario). La tipografía actual (Rajdhani), el icono y los tokens de color en `styles/app.css` son **provisionales y abiertos a cambio**; no tratarlos como vinculantes.
- Voz confirmada: español, mensajes de error en lenguaje claro para el usuario (catálogo en handoff §7).

## Evidence on Hand

- `docs/HANDOFF-Frontend-UI.md` (especificación de UI, contrato de API, criterios de aceptación) y `docs/calibracion-clasificador.md`.
- `vista_principal.png` (captura de la pantalla principal) y capturas de pruebas TestFX en `target\ui-snapshots\`.
- **Ausencias que no se deben fabricar:** el texto legal de consentimiento (Ley 1581, Colombia) es un marcador **PENDIENTE** en `legal/consent-*.txt`; atribución/términos de Tripo AI y licencia de los pesos de MobileNetV2 **pendientes de validar**. No hay testimonios, clientes, métricas ni precios reales: `PricingDialog` y `Credits` reflejan la cuota/créditos del backend, no una oferta comercial.

## Product Principles

1. **Cada generación cuesta dinero real.** Confirmación explícita antes de gastar, cuota siempre visible, cero reintentos automáticos.
2. **Lo generado no se pierde.** Persistir en el garaje antes de mostrar; la app puede cerrarse en cualquier momento sin perder un modelo pagado.
3. **Privacidad por diseño.** Sin cuentas; la foto solo se usa para generar; el garaje nunca sale del equipo; la verificación de vehículo es local.
4. **La demo manda.** Fiabilidad en vivo (salidas de emergencia, errores en español claro, arranque estable) por encima de funciones nuevas hasta el 23-nov.
5. **Lo pendiente se marca, no se inventa.** Legal, rines, validación de marca y ajustes con GLB real quedan como PENDIENTE explícito.
