---
name: desktop-app-ui-design
description: Diseña y construye UI de aplicaciones de escritorio y dashboards (Electron, Tauri, web desktop) en modo claro, con sidebar oscuro, acento verde #3beb7c, componentes bien estructurados, efectos en botones y animaciones. Úsala siempre que pidan diseñar, maquetar, prototipar o programar pantallas de app de escritorio, paneles de administración, dashboards o componentes de interfaz, aunque no mencionen el estilo ni los colores.
---

# Diseño UI de apps de escritorio — Estilo "Statistics" (modo claro)

Esta skill produce interfaces de escritorio limpias y orientadas a datos: lienzo blanco, sidebar
carbón, tarjetas con borde fino, etiquetas de sección en mayúsculas, y **un único acento verde**
que dirige la mirada. Sobre esa base añade lo que una app real necesita: estados de interacción
completos, animaciones con propósito, arquitectura de componentes escalable, accesibilidad y
patrones propios de escritorio (atajos de teclado, menús contextuales, paleta de comandos).

**Modo claro al 100 %.** No diseñes ni implementes tema oscuro, toggles de tema ni variantes
`dark:`. Fija `color-scheme: light` para que el sistema operativo no oscurezca controles nativos.
Las únicas superficies oscuras permitidas son el **sidebar**, los **tooltips** y los **toasts**:
son acentos dentro de una interfaz clara, no un tema.

Archivos de esta skill (léelos cuando los necesites, no todos de entrada):

| Archivo | Cuándo leerlo |
|---|---|
| `assets/tokens.css` | Siempre que construyas algo: es la fuente única de colores, tipografía, espaciado, radios, sombras y movimiento. |
| `assets/ejemplo-dashboard.html` | Al hacer un mockup o pantalla nueva: ejemplo completo y probado (sidebar con indicador animado, búsqueda con Ctrl+K, botones con ripple y carga, tabs con píldora deslizante, barra segmentada, tabla ordenable, gauge, toast). Parte de él en vez de empezar de cero. |
| `references/componentes.md` | Al crear o especificar componentes: anatomía, tamaños, estados y API de cada uno, estructura de carpetas y ejemplo en React + TypeScript. |
| `references/animaciones.md` | Al implementar efectos y animaciones: catálogo de snippets CSS/JS listos (ripple, press, contadores, barras, gauge, modales, skeleton, etc.). |

---

## 1. Flujo de trabajo

1. **Entender la pantalla.** ¿Qué decisión o tarea resuelve? ¿Qué dato es el protagonista?
   Una pantalla de escritorio puede mostrar mucho, pero debe tener **una jerarquía clara**:
   1 KPI o acción dominante, 2–4 bloques de apoyo.
2. **Elegir el layout** (sección 6) y el **patrón de pantalla** (sección 12).
3. **Componer con componentes existentes** (`references/componentes.md`) antes de inventar
   nuevos. Si hace falta uno nuevo, defínelo con la misma API (variante, tamaño, estado).
4. **Aplicar tokens** de `assets/tokens.css`. Nunca hex, px de color o duraciones sueltas
   en los componentes: si falta un valor, añádelo como token.
5. **Definir todos los estados** (default, hover, active, focus, disabled, loading, vacío, error).
6. **Añadir movimiento** solo donde explique algo (sección 9).
7. **Entregar** en el formato pedido (sección 14) y **revisar con la checklist** (sección 15).
   Si puedes renderizar (Playwright/Chrome), haz captura y mírala antes de entregar.

Si el pedido es ambiguo, toma la interpretación más razonable, dila en una línea y avanza.

---

## 2. ADN visual (lo que hace reconocible este estilo)

- **Lienzo blanco** `#ffffff` sobre fondo de app gris claro `#e4e4e4`, ventana con radio 24 px.
- **Sidebar compacto carbón** `#292929`, 72 px de ancho, radio 16 px, separado del borde
  (flotante dentro de la ventana), solo iconos + tooltip. Ítem activo en verde.
- **Título grande y pesado** (28 px / 700) alineado a la izquierda; búsqueda en píldora a la derecha.
- **Etiquetas de sección** en mayúsculas, 11 px / 700, tracking 0.08em, gris, encima de cada tarjeta.
- **Tarjetas planas**: borde 1 px `#e2e2e2`, radio 12 px, sin sombra en reposo (sombra suave solo en hover).
- **Datos en verde + escala de grises**: el verde marca la serie o el valor principal; el
  carbón y los grises, el resto. Nada de paletas multicolor.
- **Aire generoso**: padding de tarjeta 24 px, separación entre bloques 32 px.
- **Formas suaves**: barras y progresos con extremos redondeados, píldoras para búsqueda, chips y tabs.

---

## 3. Color

Tokens semánticos (los primitivos están en `assets/tokens.css`):

| Token | Valor | Uso | Contenido encima | Contraste |
|---|---|---|---|---|
| `--color-primary` | `#3beb7c` | Botón principal, ítem activo, serie principal, foco | `--color-on-primary` `#0b2e17` | 9.4:1 |
| `--color-primary-hover` | `#22c863` | Hover del primario | `#0b2e17` | — |
| `--color-primary-text` | `#117a3a` | Verde **como texto** o icono sobre blanco | — | 5.4:1 |
| `--color-primary-subtle` | `#ebfdf1` | Fondos de chips positivos, selección suave | `#117a3a` | 5:1 |
| `--color-secondary` | `#292929` | Sidebar, botón secundario, serie 2 de datos | `#ffffff` | 14.6:1 |
| `--color-bg-app` | `#e4e4e4` | Fondo exterior de la ventana | — | — |
| `--color-bg-canvas` / `--color-bg-surface` | `#ffffff` | Contenido y tarjetas | `--color-text` `#1f1f1f` | 16.5:1 |
| `--color-bg-subtle` | `#f6f6f6` | Hover de filas, tabs, inputs | — | — |
| `--color-text-secondary` | `#6b6b6b` | Texto de apoyo, fechas | — | 5.3:1 |
| `--color-text-muted` | `#737373` | Etiquetas en mayúsculas, placeholders, encabezados de tabla | — | 4.7:1 |
| `--color-border` | `#e2e2e2` | Bordes de tarjeta y divisores | — | — |
| `--color-border-strong` | `#bdbdbd` | Bordes de inputs y botones outline | — | — |
| `--color-track` | `#ededed` | Pistas de progreso y gauges | — | — |
| `--chart-1…4` | `#3beb7c` `#292929` `#858585` `#bdbdbd` | Series de datos, en ese orden | — | — |
| Estados | success `#117a3a`, danger `#b42318`, warning `#b54708`, info `#2563eb` | Siempre con su fondo `*-bg` claro | — | ≥4.8:1 |

**Reglas y por qué:**
- **Nunca texto blanco sobre el verde** (1.57:1, ilegible). Sobre `#3beb7c` usa `#0b2e17`.
- **Nunca verde `#3beb7c` como texto sobre blanco** (1.57:1). Para texto o iconos verdes usa
  `--color-primary-text` `#117a3a`.
- La referencia usa grises muy claros (`#c9c9c9`) para etiquetas: **no pasan contraste**.
  Conserva la estética con `#737373` (sigue viéndose ligera en 11 px mayúsculas).
- `#858585` y `#bdbdbd` son colores **de datos o decoración**, no de texto pequeño.
- **Un acento por vista:** el verde aparece en 1 acción principal + datos clave + ítem activo.
  Si todo es verde, nada destaca.
- El color nunca es el único indicador: acompaña estados con icono, texto o forma
  (p. ej. chips de variación con flecha y signo).

**Cambiar los colores de marca:** si el usuario da otros primario/secundario, cambia solo los
primitivos y recalcula el contenido encima (`on-primary`) y la variante de texto
(`primary-text`) para que cumplan ≥4.5:1. Mantén el resto de la arquitectura.

---

## 4. Tipografía

Familia: **Inter** (fallback: Segoe UI Variable en Windows, SF Pro en macOS, `system-ui`).
Números con `font-variant-numeric: tabular-nums` para que las cifras no se muevan al animarse.

| Token | Spec | Uso |
|---|---|---|
| `--text-display` | 600 32/40 | Cifras héroe (gauge, KPI grande) |
| `--text-h1` | 700 28/36, tracking −0.01em | Título de página ("Estadísticas") |
| `--text-h2` | 700 20/28 | Título de modal o panel |
| `--text-h3` | 600 16/24 | Título de tarjeta |
| `--text-body` / `-strong` | 400·600 14/20 | Texto base, celdas, botones |
| `--text-small` | 400 13/18 | Inputs, metadatos |
| `--text-caption` | 500 12/16 | Captions, chips, tooltips, encabezados de tabla |
| `--text-overline` | 700 11/16 + uppercase + 0.08em | Etiquetas de sección |

Máximo 4–5 estilos por pantalla. Formatea números y fechas con la configuración regional del
usuario (`Intl.NumberFormat` / `toLocaleString`, p. ej. `es-CO`: `4.725,05`).

---

## 5. Espaciado, radios y elevación

- **Escala base 4:** 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64 (`--space-*`).
- **Ritmo:** 8 dentro de componentes, 16 entre elementos relacionados, 24 padding de tarjeta,
  32 entre bloques/secciones.
- **Radios:** 4 (chips de tabla) · 8 (menús, inputs pequeños) · 12 (tarjetas, botones)
  · 16 (sidebar, modales) · 24 (ventana) · full (búsqueda, tabs, chips, barras).
- **Elevación:** el estilo es plano. Reposo = borde, sin sombra. `--shadow-sm` en hover de
  tarjeta, `--shadow-md` en menús/popovers, `--shadow-lg` en modales, toasts y la ventana.

---

## 6. Layout de app de escritorio

```
┌───────────────────────── ventana (radio 24, bg canvas) ─────────────────────────┐
│ ┌──┐  Título h1                         [Buscar  Ctrl K 🔍] [Filtros] [Exportar] │
│ │  │  ETIQUETA DE SECCIÓN                                                       │
│ │S │  ┌──────────────────────── tarjeta KPI ────────────────────────────────┐   │
│ │I │  └──────────────────────────────────────────────────────────────────────┘  │
│ │D │  ETIQUETA                                    ETIQUETA                      │
│ │E │  ┌────────── tarjeta (tabla) ────────────┐  ┌──── tarjeta (gauge) ────┐    │
│ └──┘  └────────────────────────────────────────┘  └─────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- **Shell:** sidebar 72 px (expandible a 240 px con etiquetas) + área principal flexible.
  Topbar de 64 px dentro del área principal (título + acciones), no a ancho completo.
- **Grid de contenido:** 12 columnas, gutter 24 px, `max-width` 1200 px. Combinaciones típicas:
  12 · 8+4 · 6+6 · 4+4+4. La fila inferior de la referencia es ~8+4.
- **Tamaños de ventana:** diseña a **1280 × 800**, verifica a 1024 × 680 (mínimo) y 1920 × 1080.
  Por debajo de 1200 px colapsa grids de 3 a 2 columnas; nunca scroll horizontal del layout.
- **Barra de título (Electron/Tauri sin marco):** zona arrastrable de 32–40 px
  (`-webkit-app-region: drag`, botones con `no-drag`). En macOS reserva ~80 px a la izquierda
  para los semáforos; en Windows, ~138 px a la derecha para minimizar/maximizar/cerrar.
- **Scroll:** solo el área de contenido hace scroll; sidebar y topbar fijos. Scrollbar fina
  (8 px, thumb `#bdbdbd`, radio full).
- **Densidad:** "cómoda" por defecto (filas de tabla 48 px). Ofrece "compacta" (36 px) en vistas
  con muchos datos.

---

## 7. Arquitectura de componentes

Organiza en capas, de lo atómico a lo compuesto; cada capa solo usa las de abajo:

```
src/
├── styles/        tokens.css · base.css (reset, tipografía, :focus-visible)
├── primitives/    Button · IconButton · Input · Checkbox · Switch · Badge · Tooltip · Spinner · Icon
├── components/    Card · Tabs · Select · Menu · Table · Modal · Toast · ProgressBar · Gauge · SegmentedBar · EmptyState · Skeleton
├── patterns/      KpiCard · StatLegend · DataTableCard · FilterBar · CommandPalette · SearchField
├── layout/        AppShell · Sidebar · Topbar · PageHeader · SectionLabel · Grid
├── features/      (pantallas: statistics/, settings/…) — componen patterns, no estilos propios
└── hooks/         useCountUp · useHotkey · useReducedMotion · useToast
```

**Convenciones de API (para que todo se use igual):**
- `variant` (primary · secondary · outline · ghost · danger), `size` (sm · md · lg),
  estados booleanos (`loading`, `disabled`, `selected`), `icon` / `iconRight`.
- Los componentes **no conocen el negocio**: reciben datos y callbacks (`onSelect`, `onSort`).
- Estilos solo por tokens; las clases describen rol (`.btn-primary`), no apariencia (`.green`).
- Composición sobre configuración: `<Card><Card.Header/><Card.Body/></Card>` en vez de 20 props.
- Todo componente interactivo expone `aria-*` correctos y funciona con teclado.
- Un archivo por componente + su historia/ejemplo; nombres en PascalCase, en inglés en código
  y textos visibles en el idioma del usuario.

Especificaciones completas de cada componente y un `Button` en React + TS:
`references/componentes.md`.

---

## 8. Botones y efectos de interacción

| Variante | Reposo | Hover | Active (press) | Uso |
|---|---|---|---|---|
| Primary | bg verde, texto `#0b2e17` | bg `#22c863`, sube 1 px, sombra verde suave | escala 0.97 + ripple | 1 por vista: la acción principal |
| Secondary | bg `#292929`, texto blanco | bg `#3a3a3a`, sube 1 px, `--shadow-sm` | escala 0.97 + ripple | Acción fuerte alternativa |
| Outline | bg blanco, borde `#bdbdbd` | bg `#f6f6f6`, borde `#858585` | escala 0.97 | Acciones secundarias (Filtros) |
| Ghost | transparente, texto gris | bg `#f6f6f6`, texto oscuro | escala 0.97 | Acciones terciarias, toolbars |
| Danger | bg `#b42318`, texto blanco | más oscuro | escala 0.97 | Destructivas (con confirmación) |
| Icon | cuadrado 40 px, outline o ghost | igual que su variante | escala 0.92 | Siempre con `aria-label` + tooltip |

- **Tamaños:** sm 32 px (texto 13) · md 40 px (14, por defecto) · lg 48 px (15). Padding
  horizontal 12 · 16 · 20. Icono 16 px (sm/md) o 20 px (lg), gap 8.
- **Focus:** anillo `--shadow-focus` (3 px verde translúcido) solo con `:focus-visible`.
- **Loading:** spinner sustituye al icono, texto cambia a gerundio ("Exportando…"), ancho
  estable, `aria-busy="true"`, ignora clics repetidos. Al terminar: toast de confirmación.
- **Disabled:** opacidad 0.45, `cursor: not-allowed`, sin hover; explica el porqué con tooltip.
- **Ripple:** origen en el punto del clic, `currentColor` al 22 %, 520 ms; no en ghost/links.
- Hover y press deben sentirse **inmediatos** (80–140 ms). Lento = la app parece pesada.

---

## 9. Animaciones

Principio: **el movimiento explica cambios** (de dónde viene algo, qué cambió, qué está pasando).
Si no explica nada, no se anima.

| Token | Duración | Para |
|---|---|---|
| `--dur-instant` | 80 ms | Press de botones |
| `--dur-fast` | 140 ms | Hover, cambios de color, tooltips |
| `--dur-base` | 220 ms | Menús, tabs, switches, popovers |
| `--dur-slow` | 360 ms | Modales, drawers, indicador del sidebar, entrada de tarjetas |
| `--dur-data` | 900 ms | Barras, gauges, contadores numéricos |

Curvas: `--ease-out` (entradas y datos), `--ease-in` (salidas), `--ease-standard` (cambios de
estado), `--ease-spring` (toasts y confirmaciones, con un leve rebote).

Catálogo mínimo de una pantalla bien animada (todos implementados en el ejemplo):
1. **Entrada escalonada** de header y tarjetas (fade + 8 px, 70 ms entre cada una).
2. **Contadores** que suben desde 0 (o desde el valor anterior) con easing.
3. **Barras y progresos** que crecen desde 0; al cambiar de periodo, transicionan al nuevo valor.
4. **Gauge** que dibuja el arco (`stroke-dashoffset`).
5. **Indicador deslizante** en sidebar y tabs (se mueve, no salta).
6. **Ripple + press** en botones; **spinner** de carga; **toast** con resorte.
7. **Tooltips** con retardo de 300 ms al entrar e inmediatos al salir.
8. **Hover de filas** y de tarjetas (fondo / sombra suave).

Reglas: anima solo `transform` y `opacity` (y `width`/`stroke` en datos); nunca `top/left`.
Respeta **`prefers-reduced-motion`** (los tokens ya lo hacen): los datos aparecen en su valor
final sin animar. Nada de animaciones en bucle salvo spinners y skeletons.
Snippets listos: `references/animaciones.md`.

---

## 10. Iconografía

**Set: Lucide** (licencia ISC; mismo estilo que la referencia: trazo limpio, geométrico).
Configuración fija: `stroke-width: 2`, `stroke-linecap/linejoin: round`, `fill: none`,
color `currentColor`.

- Tamaños: 20 px en sidebar y botones lg · 16 px en botones md/sm, inputs, chips · 24 px en
  estados vacíos e ilustraciones pequeñas.
- Instalación: `npm i lucide-react` (React) o `lucide` (vanilla). Offline: los símbolos SVG de
  `assets/ejemplo-dashboard.html` (menu, house, chart, star, layout, bell, hexagon, search,
  download, trending-up, chevron-down, check, filter), ya verificados.
- Mapeo frecuente: Inicio `house` · Estadísticas `chart-column` · Favoritos `star` ·
  Tableros `layout-dashboard` · Notificaciones `bell` · Ajustes `settings` · Buscar `search` ·
  Exportar `download` · Filtrar `funnel` · Añadir `plus` · Editar `pencil` · Eliminar `trash-2` ·
  Más `ellipsis` · Cerrar `x` · Subida/bajada `trending-up`/`trending-down` · Calendario
  `calendar` · Usuario `user` · Cerrar sesión `log-out` · Ayuda `circle-help`.
- Un solo set en toda la app. No mezcles con emojis ni con iconos rellenos de otra familia.
- Botón solo-icono = `aria-label` + tooltip. Icono decorativo junto a texto = `aria-hidden`.

---

## 11. Visualización de datos

- Orden de series: `--chart-1` (verde, lo que importa) → `--chart-2` (carbón) →
  `--chart-3` (gris) → `--chart-4` (gris claro). Máximo 4 categorías; agrupa el resto en "Otros".
- **Barra segmentada** (KPI de composición): 48 px de alto, radio 12, segmentos sin separación;
  leyenda en 2 columnas con % en negrita + mini barra (8 px) + etiqueta. Tooltip por segmento.
- **Gauge**: arco de 180°, trazo 14 px, extremos redondeados, pista `--color-track`, valor
  grande centrado con "%" más pequeño.
- **Tabla**: encabezados en caption gris, ordenables con chevron que rota; números alineados a
  la derecha si se comparan entre filas; variaciones como chip (verde/rojo suave + signo).
- Ejes y cuadrículas en `#ededed`, nunca negros. Etiquetas directas mejor que leyendas lejanas.
- Siempre muestra el valor exacto en tooltip y el estado vacío ("Sin datos para este periodo").
- Librerías: Recharts / ECharts / Chart.js aplicando estos tokens; para piezas simples (barra
  segmentada, gauge, sparkline) SVG/CSS a mano es más ligero.

---

## 12. Patrones de escritorio que una app profesional necesita

- **Paleta de comandos** (Ctrl/⌘ + K): modal centrado de 560 px, búsqueda + resultados
  agrupados, navegación con flechas, Enter ejecuta, Esc cierra.
- **Atajos de teclado** visibles: muéstralos en tooltips y menús (`kbd` gris). Usa Ctrl en
  Windows/Linux y ⌘ en macOS.
- **Menú contextual** (clic derecho) en filas y tarjetas, con las mismas acciones que el menú "…".
- **Selección múltiple** en tablas (checkbox + Shift-clic para rango) con barra de acciones
  flotante que aparece desde abajo.
- **Paneles redimensionables** (divisor de 4 px con cursor `col-resize`) y recordar su tamaño.
- **Drag & drop** con placeholder punteado verde y elemento arrastrado con `--shadow-md`.
- **Estados de pantalla:** carga (skeleton con la forma real), vacío (icono 24 + título +
  explicación + acción primaria), error (mensaje claro + "Reintentar"), sin conexión (banner).
- **Notificaciones:** toast abajo a la derecha, 4 s, máximo 3 apilados, con acción "Deshacer"
  para operaciones destructivas.
- **Confirmaciones** solo para acciones irreversibles; para el resto, deshacer.
- **Persistencia:** recordar filtros, orden de tablas, sidebar expandido y tamaño de ventana.

Pantallas tipo para las que la skill está preparada: dashboard de estadísticas (referencia),
listado con tabla y filtros, detalle con panel lateral, formulario de ajustes por secciones,
onboarding de 3 pasos, inicio de sesión centrado.

---

## 13. Accesibilidad

- Contraste ≥ 4.5:1 en texto normal, ≥ 3:1 en texto grande, iconos y bordes de controles
  (respeta los pares de la sección 3).
- Todo es alcanzable con **Tab** en orden lógico; `:focus-visible` siempre visible.
- Objetivos de clic ≥ 32 × 32 px (botones sm), ideal 40 px.
- Roles ARIA: `nav` + `aria-current="page"` en sidebar, `role="tablist"/"tab"` +
  `aria-selected` en tabs, `aria-sort` en columnas ordenadas, `aria-live="polite"` en toasts,
  `aria-busy` en carga, trap de foco y `aria-modal` en modales.
- Nada depende solo del hover: los tooltips también aparecen con foco y la información
  crítica no vive solo en tooltips.
- Respeta `prefers-reduced-motion` y el escalado de texto del sistema (usa rem en código real).

---

## 14. Formatos de entrega

**Mockup / prototipo HTML** (por defecto si piden "diseña", "muéstrame", "maqueta"):
un único archivo HTML autocontenido. Copia `assets/tokens.css` dentro de `<style>` y parte de
`assets/ejemplo-dashboard.html` (estructura, iconos, JS de interacciones). Contenido realista
en el idioma del usuario (nombres, cifras y fechas creíbles, nunca "Lorem ipsum").

**React + TypeScript** (si piden código de producción): estructura de la sección 7,
tokens como CSS variables (o mapeados en `tailwind.config` `theme.extend`), componentes con la
API descrita, `lucide-react` para iconos. Ejemplo de `Button` en `references/componentes.md`.

**Electron / Tauri:** lo anterior + ventana sin marco con barra de título personalizada
(sección 6), `minWidth: 1024, minHeight: 680`, atajos globales de la app registrados en el
proceso principal solo si deben funcionar fuera de foco.

**Especificación para Figma:** variables de color/espaciado con los nombres de los tokens,
estilos de texto de la sección 4, componentes con variantes (`variant`, `size`, `state`) y
frames 1280 × 800 y 1024 × 680.

---

## 15. Checklist antes de entregar

- [ ] Modo claro únicamente; sin toggles ni estilos de tema oscuro.
- [ ] Un solo acento verde dominante por vista; datos en verde + grises.
- [ ] Ningún texto blanco sobre verde ni texto verde claro sobre blanco.
- [ ] Todo valor visual sale de un token.
- [ ] Etiquetas de sección en mayúsculas sobre cada bloque; tarjetas con borde fino, radio 12.
- [ ] Botones con hover, press, focus, disabled y loading; botones solo-icono con label y tooltip.
- [ ] Animaciones con duración/curva de tokens y `prefers-reduced-motion` respetado.
- [ ] Estados de carga, vacío y error contemplados.
- [ ] Navegable con teclado; Ctrl/⌘ + K si hay búsqueda.
- [ ] Iconos Lucide coherentes (trazo 2, tamaños 16/20).
- [ ] Números tabulares y formateados según la región; contenido realista.
- [ ] Verificado a 1280 × 800 y 1024 × 680 (renderizado y revisado si es posible).
