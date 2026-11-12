# Catálogo de componentes

Especificación de cada componente: anatomía, medidas, estados y API. Todo valor viene de
`assets/tokens.css`. Modo claro únicamente.

## Índice
1. Primitivos: Button · IconButton · Input / SearchField · Select · Checkbox / Radio / Switch · Badge / Chip · Tooltip · Kbd · Spinner
2. Componentes: Card · Tabs (segmentado) · Menu / ContextMenu · Modal · Drawer · Toast · Table · ProgressBar · SegmentedBar · Gauge · Skeleton · EmptyState · Pagination · Breadcrumbs · Avatar
3. Layout: AppShell · Sidebar · Topbar / PageHeader · SectionLabel
4. Patrones: KpiCard · DataTableCard · FilterBar · CommandPalette
5. Matriz de estados
6. Ejemplo React + TypeScript (Button y Card)

---

## 1. Primitivos

### Button
- **Anatomía:** [icono opcional] + etiqueta + [icono derecho opcional]; spinner reemplaza al icono en carga.
- **Tamaños:** sm 32 px · md 40 px (defecto) · lg 48 px; radio `--radius-md`; padding X 12/16/20; gap 8.
- **Variantes:** primary · secondary · outline · ghost · danger (ver tabla en SKILL.md §8).
- **Props:** `variant`, `size`, `icon`, `iconRight`, `loading`, `disabled`, `fullWidth`, `type`.
- **Texto:** verbo claro ("Exportar", "Guardar cambios"), máximo 3 palabras, sin mayúsculas sostenidas.
- **Agrupación:** acciones a la derecha del encabezado, orden secundario → primario (primario al final).

### IconButton
- Cuadrado 32/40/48 px, variantes outline o ghost; radio `--radius-md` (o full si está dentro de una píldora).
- `aria-label` obligatorio + tooltip con el mismo texto y, si existe, el atajo (`Buscar · Ctrl K`).
- Badge de notificación: punto 7 px verde con anillo del color del fondo, esquina superior derecha.

### Input / SearchField
- Alto 40 px; radio `--radius-sm` (formularios) o full (búsqueda global); borde `--color-border-strong`; fondo blanco.
- Label encima (caption 12, `--color-text-secondary`), ayuda debajo (12, muted), error debajo en `--color-danger` con icono.
- Estados: hover borde `#858585` · focus borde verde + `--shadow-focus` · error borde danger + anillo rojo suave · disabled fondo `--color-bg-subtle`.
- SearchField: icono `search` a la derecha (o izquierda), `kbd` "Ctrl K", se ensancha 240→300 px al enfocar, `Esc` limpia.

### Select / Combobox
- Igual que Input + chevron-down 16 px que rota 180° al abrir (`--dur-base`).
- Popover: `--radius-sm`, `--shadow-md`, borde, padding 4; opciones de 36 px con hover `--color-bg-subtle`, seleccionada con check verde (`--color-primary-text`).
- Teclado: flechas, Enter, Esc, escribir para saltar.

### Checkbox / Radio / Switch
- Checkbox 18 px radio 4; marcado = fondo verde + check `#0b2e17`; indeterminado = guion.
- Radio 18 px; marcado = anillo verde + punto interior.
- Switch 36 × 20, pista `--color-border-strong` → verde al activar; perilla blanca 16 px con `--shadow-xs` que se desliza (`--dur-base`, `--ease-spring`).
- Área clicable incluye la etiqueta.

### Badge / Chip
- Alto 22 px, radio full, caption 12/500, padding X 8.
- Tonos: success (`--color-success-bg` + `--color-success`), danger, warning, info, neutral (`--color-bg-subtle` + `--color-text-secondary`).
- Variación numérica: icono `trending-up/down` 14 px + signo + valor ("+4,33 %").
- Chip de filtro: alto 32, borde, con `x` para quitar; seleccionado = `--color-primary-subtle` + borde verde.

### Tooltip
- Fondo `#141414`, texto blanco caption, radio 8, padding 6×10, `--shadow-md`, máx. 240 px.
- Aparece con 300 ms de retardo (hover o foco), desaparece al instante; desplazamiento 4 px hacia su posición final.

### Kbd
- Caption 12, borde `--color-border`, fondo `--color-bg-subtle`, radio 4, padding 0×5.

### Spinner
- 16 px (en botones) / 24 px (en paneles); borde 2 px `currentColor` con un lado transparente; 700 ms lineal.

---

## 2. Componentes

### Card
- Fondo blanco, borde 1 px `--color-border`, radio `--radius-md`, padding 24 (16 en tarjetas compactas).
- Hover (si es interactiva o contiene datos): `--shadow-sm`. Clicable completa: además cursor pointer y foco visible.
- Subpartes: `Card.Header` (caption o h3 + acciones a la derecha) · `Card.Body` · `Card.Footer` (borde superior, acciones).
- Siempre precedida por un `SectionLabel` cuando forma parte de un dashboard.

### Tabs (control segmentado)
- Contenedor `--color-bg-subtle`, radio full, padding 4; tabs de 32 px, caption 12.
- Seleccionada: píldora blanca con `--shadow-xs` que **se desliza** (transform + width, `--dur-base`, `--ease-out`); texto 600 `--color-text`.
- Teclado: flechas izquierda/derecha cambian; `role="tablist"`, `aria-selected`.
- Variante subrayada (para navegación de página): línea verde de 2 px que se desliza bajo la pestaña activa.

### Menu / ContextMenu
- Popover 200–280 px, radio 8, `--shadow-md`, borde, padding 4.
- Ítems 36 px: icono 16 + texto + atajo (`kbd`) a la derecha. Hover `--color-bg-subtle`. Ítem destructivo en `--color-danger`, separado por divisor.
- Entrada: escala 0.96→1 + opacidad, origen en el punto de apertura, `--dur-base`.

### Modal / Dialog
- Ancho 480 (confirmación) · 640 (formulario) · 880 (contenido); radio 16; padding 24; `--shadow-lg`.
- Overlay `rgba(20,20,20,.32)` con `backdrop-filter: blur(2px)`.
- Header: h2 + botón cerrar (IconButton ghost `x`). Footer: acciones alineadas a la derecha (secundaria, primaria).
- Entrada: overlay fade + modal escala 0.96→1 y sube 8 px (`--dur-slow`, `--ease-out`). Salida más rápida (`--dur-base`, `--ease-in`).
- Trap de foco, `Esc` cierra, foco vuelve al disparador.

### Drawer (panel lateral)
- Derecha, 400–480 px, alto completo, `--shadow-lg`, entra deslizando 100 %→0 (`--dur-slow`).
- Para detalle de un registro sin perder el contexto de la lista.

### Toast
- Fondo `#1f1f1f`, texto blanco, radio 12, padding 12×14, mín. 300 px, `--shadow-lg`, abajo a la derecha.
- Icono de estado en círculo de 28 px (éxito: verde con check oscuro), título 600 + detalle caption gris claro, acción opcional ("Deshacer") como botón ghost blanco.
- Entra con `--ease-spring` desde abajo; sale deslizando a la derecha. 4 s; pausa al pasar el ratón. `aria-live="polite"`.

### Table
- Encabezados caption 12 `--color-text-muted`, ordenables con chevron (gira 180° en ascendente; `aria-sort`).
- Filas 48 px (cómoda) / 36 px (compacta), divisor superior 1 px `--color-border`.
- Celda principal en 600; secundarias en `--color-text-secondary`; números tabulares.
- Hover de fila `--color-bg-subtle` con esquinas redondeadas 8; fila seleccionada `--color-primary-subtle` + checkbox.
- Acciones de fila: IconButton `ellipsis` visible en hover y foco, mismo contenido que el menú contextual.
- Encabezado sticky al hacer scroll; paginación o scroll virtual a partir de ~100 filas.

### ProgressBar
- Alto 8 px (mini) / 12 px; pista `--color-track`; relleno con color de la serie; radio full.
- Crece desde 0 con `--dur-data`; con valor indeterminado, barra de 30 % que se desplaza en bucle.

### SegmentedBar
- Alto 48 px, radio 12, segmentos contiguos con colores `--chart-1…4`; el primero y el último heredan el radio.
- Ancho animado (`--dur-data`, `--ease-out`); hover aclara el segmento y muestra tooltip "Etiqueta · 35 %".
- Leyenda: grid de 2 columnas; fila = % en 600 (44 px) + mini barra (64 px) + etiqueta secundaria.

### Gauge
- Arco de 180° (SVG path `A`), trazo 14 px, linecap round, pista `--color-track`, valor `--color-primary`.
- Valor centrado abajo: 40 px/600 + "%" a 16 px/500. Anima `stroke-dashoffset` + contador en paralelo.
- `role="img"` con `aria-label` ("75 por ciento").

### Skeleton
- Bloques `--color-bg-subtle` con la forma exacta del contenido final (radio igual), brillo que recorre de izquierda a derecha cada 1.4 s.
- Mostrar solo si la carga supera ~300 ms (evita parpadeos).

### EmptyState
- Centrado en la tarjeta: icono Lucide 24 px en círculo de 48 `--color-bg-subtle`, título h3, texto de 1–2 líneas en secundario, botón primario o outline.
- Tono útil, no culpable: "Aún no hay ventas este mes" + "Importar datos".

### Pagination
- Botones ghost 32 px con números; página actual en `--color-secondary` con texto blanco; "Anterior/Siguiente" con chevrons; texto "1–20 de 248".

### Breadcrumbs
- Caption 13, separador `chevron-right` 14 px gris; último elemento en `--color-text` y no clicable.

### Avatar
- 32 px (listas) / 40 px (encabezados), radio full; iniciales 600 sobre `--color-primary-subtle` + `--color-primary-text`, o foto. Grupo apilado con borde blanco de 2 px.

---

## 3. Layout

### AppShell
- `display:flex`, gap 32 px, padding 12 px (lado del sidebar) y 32 px (resto); fondo canvas; ventana radio 24.
- El área principal es la única con scroll.

### Sidebar
- 72 px, fondo `--color-bg-sidebar`, radio 16, padding vertical 20.
- Arriba: botón menú (expande a 240 px con etiquetas, `--dur-slow`). Centro: navegación. Abajo: ajustes / perfil.
- Ítems 44 × 44, icono 20, color `--color-text-on-sidebar-muted`; hover blanco + fondo blanco 6 %.
- Activo: icono verde + fondo verde 14 % + barra verde de 4 px pegada al borde izquierdo; el indicador **se desliza** entre ítems.
- Tooltip a la derecha con el nombre de la sección (solo en modo colapsado).

### Topbar / PageHeader
- Alto 64 px: h1 a la izquierda; búsqueda, filtros y acción primaria a la derecha, gap 12.
- Opcional: breadcrumbs encima del título, tabs de página debajo.

### SectionLabel
- Overline 11/700, uppercase, tracking 0.08em, `--color-text-muted`; margen 32 arriba / 12 abajo.

---

## 4. Patrones

### KpiCard
Caption ("Analítica") → valor 28/700 tabular + chip de variación → control de periodo (Tabs) a la
derecha → visualización (SegmentedBar, sparkline o progreso) → leyenda. Al cambiar periodo,
todo transiciona desde los valores actuales (no reinicia desde 0).

### DataTableCard
SectionLabel + Card con: barra superior opcional (búsqueda local, filtros, acciones masivas) →
Table → Pagination. Estados de carga (skeleton de 5 filas), vacío y error dentro de la tarjeta.

### FilterBar
Fila de chips de filtro + botón "Filtros" (outline con icono `funnel` y contador de filtros
activos) + "Limpiar" ghost. Los filtros activos se reflejan en la URL o se persisten.

### CommandPalette
Modal 560 px arriba-centro (20 % desde arriba); input grande sin borde con icono; resultados
agrupados con SectionLabel ("Navegación", "Acciones", "Recientes"); ítem activo con fondo
`--color-bg-subtle` y barra verde; atajos a la derecha. Se abre con Ctrl/⌘ + K.

---

## 5. Matriz de estados (todo componente interactivo)

| Estado | Señal visual | Duración |
|---|---|---|
| Default | Según variante | — |
| Hover | Fondo/borde un paso más oscuro; primarios suben 1 px | `--dur-fast` |
| Active / pressed | `scale(.97)` (iconos `.92`); ripple en botones sólidos | `--dur-instant` |
| Focus-visible | Anillo `--shadow-focus` | inmediato |
| Selected | Verde (fondo, borde o indicador) + cambio de peso tipográfico | `--dur-base` |
| Disabled | Opacidad .45, sin hover, `cursor:not-allowed`, tooltip con motivo | — |
| Loading | Spinner/skeleton, `aria-busy`, interacción bloqueada | — |
| Error | Borde y texto danger + icono + mensaje que explica cómo resolver | — |
| Success | Toast o check verde transitorio (1.5 s) | `--ease-spring` |

---

## 6. Ejemplo React + TypeScript

```tsx
// src/primitives/Button/Button.tsx
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import styles from "./Button.module.css";
import { useRipple } from "../../hooks/useRipple";

type Variant = "primary" | "secondary" | "outline" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: ReactNode;
  iconRight?: ReactNode;
  loading?: boolean;
  loadingText?: string;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", size = "md", icon, iconRight, loading = false, loadingText,
    disabled, children, className, onPointerDown, onClick, ...rest },
  ref,
) {
  const ripple = useRipple(variant !== "ghost");
  return (
    <button
      ref={ref}
      className={[styles.btn, styles[variant], styles[size], className].filter(Boolean).join(" ")}
      disabled={disabled}
      aria-busy={loading || undefined}
      data-loading={loading || undefined}
      onPointerDown={(e) => { ripple(e); onPointerDown?.(e); }}
      onClick={loading ? (e) => e.preventDefault() : onClick}
      {...rest}
    >
      {loading ? <span className={styles.spinner} aria-hidden /> : icon}
      <span>{loading && loadingText ? loadingText : children}</span>
      {!loading && iconRight}
    </button>
  );
});
```

```css
/* src/primitives/Button/Button.module.css — mismos valores que .btn del ejemplo */
.btn { position: relative; overflow: hidden; display: inline-flex; align-items: center; justify-content: center;
  gap: var(--space-2); height: 40px; padding: 0 var(--space-4); border: 1px solid transparent;
  border-radius: var(--radius-md); font: var(--text-body-strong); cursor: pointer; user-select: none;
  transition: background var(--dur-fast) var(--ease-standard), border-color var(--dur-fast),
              box-shadow var(--dur-fast) var(--ease-standard), transform var(--dur-instant) var(--ease-standard); }
.btn:focus-visible { outline: none; box-shadow: var(--shadow-focus); }
.btn:active:not(:disabled) { transform: scale(.97); }
.btn:disabled { opacity: .45; cursor: not-allowed; }
.sm { height: 32px; padding: 0 var(--space-3); font-size: 13px; }
.lg { height: 48px; padding: 0 var(--space-5); font-size: 15px; }
.primary { background: var(--color-primary); color: var(--color-on-primary); }
.primary:hover:not(:disabled) { background: var(--color-primary-hover); transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59,235,124,.35); }
.secondary { background: var(--color-secondary); color: var(--color-on-secondary); }
.secondary:hover:not(:disabled) { background: var(--color-secondary-hover); transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.outline { background: var(--color-bg-surface); border-color: var(--color-border-strong); color: var(--color-text); }
.outline:hover:not(:disabled) { background: var(--color-bg-subtle); border-color: var(--gray-500); }
.ghost { background: transparent; color: var(--color-text-secondary); }
.ghost:hover:not(:disabled) { background: var(--color-bg-subtle); color: var(--color-text); }
.danger { background: var(--color-danger); color: #fff; }
.danger:hover:not(:disabled) { background: #912018; }
.spinner { width: 16px; height: 16px; border-radius: 50%; border: 2px solid currentColor;
  border-right-color: transparent; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
```

```tsx
// src/hooks/useRipple.ts
import type { PointerEvent } from "react";
export function useRipple(enabled = true) {
  return (e: PointerEvent<HTMLElement>) => {
    if (!enabled || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const el = e.currentTarget, r = el.getBoundingClientRect(), size = Math.max(r.width, r.height) * 2;
    const s = document.createElement("span");
    Object.assign(s.style, { position: "absolute", width: `${size}px`, height: `${size}px`, borderRadius: "50%",
      left: `${e.clientX - r.left - size / 2}px`, top: `${e.clientY - r.top - size / 2}px`,
      background: "currentColor", opacity: ".22", transform: "scale(0)", pointerEvents: "none" });
    el.appendChild(s);
    s.animate([{ transform: "scale(0)", opacity: .22 }, { transform: "scale(1)", opacity: 0 }],
      { duration: 520, easing: "cubic-bezier(.16,1,.3,1)" }).onfinish = () => s.remove();
  };
}
```

```tsx
// src/components/Card/Card.tsx — composición en lugar de muchas props
import type { ReactNode } from "react";
import styles from "./Card.module.css";

export function Card({ children, interactive }: { children: ReactNode; interactive?: boolean }) {
  return <section className={`${styles.card} ${interactive ? styles.interactive : ""}`}>{children}</section>;
}
Card.Header = ({ title, actions }: { title: ReactNode; actions?: ReactNode }) => (
  <header className={styles.header}><span className={styles.caption}>{title}</span>{actions}</header>
);
Card.Body = ({ children }: { children: ReactNode }) => <div className={styles.body}>{children}</div>;

// Uso:
// <SectionLabel>Resumen de ventas</SectionLabel>
// <Card>
//   <Card.Header title="Analítica" actions={<Tabs items={["Día","Semana","Mes"]} />} />
//   <Card.Body><KpiValue value={4725.05} delta={12.4} /><SegmentedBar data={parts} /></Card.Body>
// </Card>
```
