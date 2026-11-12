# Catálogo de animaciones y efectos

Snippets listos para copiar. Todos usan los tokens de movimiento de `assets/tokens.css`
(`--dur-*`, `--ease-*`), que ya se anulan con `prefers-reduced-motion`. Anima solo
`transform`, `opacity` y, en visualizaciones, `width` / `stroke-dashoffset`.

## Índice
1. Botones: hover-lift, press, ripple, loading, éxito
2. Entrada escalonada de contenido
3. Contador numérico
4. Barras y progreso
5. Gauge
6. Indicador deslizante (sidebar y tabs)
7. Tooltip
8. Menú / popover
9. Modal y drawer
10. Toast
11. Skeleton
12. Switch
13. Filas: hover, inserción y eliminación
14. Reducción de movimiento en JS

---

## 1. Botones

```css
.btn { transition: background var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast) var(--ease-standard),
                    transform var(--dur-instant) var(--ease-standard); }
/* Hover-lift: solo primarios y secundarios */
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(59,235,124,.35); }
/* Press */
.btn:active:not(:disabled) { transform: scale(.97); }
.btn-icon:active:not(:disabled) { transform: scale(.92); }
/* Ripple */
.ripple { position:absolute; border-radius:50%; transform:scale(0); background:currentColor; opacity:.22;
          pointer-events:none; animation: ripple 520ms var(--ease-out) forwards; }
@keyframes ripple { to { transform: scale(1); opacity: 0; } }
/* Loading */
.btn .spinner { display:none; width:16px; height:16px; border-radius:50%; border:2px solid currentColor;
                border-right-color:transparent; animation: spin .7s linear infinite; }
.btn[data-loading] .spinner { display:block; }
.btn[data-loading] .label-ico { display:none; }
@keyframes spin { to { transform: rotate(360deg); } }
/* Éxito transitorio: el icono se convierte en check con un pequeño rebote */
.btn[data-success] .check { animation: pop var(--dur-slow) var(--ease-spring); }
@keyframes pop { from { transform: scale(.4); opacity: 0; } }
```

```js
// Ripple global para cualquier .btn (no ghost)
document.addEventListener('pointerdown', e => {
  const btn = e.target.closest('.btn:not(.btn-ghost)');
  if (!btn || btn.disabled || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const r = btn.getBoundingClientRect(), size = Math.max(r.width, r.height) * 2;
  const s = Object.assign(document.createElement('span'), { className: 'ripple' });
  Object.assign(s.style, { width: size+'px', height: size+'px',
    left: e.clientX - r.left - size/2 + 'px', top: e.clientY - r.top - size/2 + 'px' });
  btn.appendChild(s); s.addEventListener('animationend', () => s.remove());
});

// Loading con ancho estable → toast
async function runWithLoading(btn, task, { busyText = 'Procesando…' } = {}) {
  if (btn.dataset.loading !== undefined) return;
  const lbl = btn.querySelector('.lbl'), prev = lbl.textContent;
  btn.style.minWidth = btn.offsetWidth + 'px';
  btn.dataset.loading = ''; btn.setAttribute('aria-busy', 'true'); lbl.textContent = busyText;
  try { await task(); } finally {
    delete btn.dataset.loading; btn.removeAttribute('aria-busy'); lbl.textContent = prev; btn.style.minWidth = '';
  }
}
```

## 2. Entrada escalonada

```css
.reveal { opacity:0; transform: translateY(8px);
          animation: reveal var(--dur-slow) var(--ease-out) forwards;
          animation-delay: calc(var(--i, 0) * 70ms); }
@keyframes reveal { to { opacity:1; transform:none; } }
```
```html
<header class="reveal" style="--i:0">…</header>
<div class="overline reveal" style="--i:1">…</div>
<section class="card reveal" style="--i:2">…</section>
```
Máximo ~8 elementos escalonados; más allá, anima el grupo entero.

## 3. Contador numérico

```js
function countTo(el, to, { dur = 900, decimals = 0, suffix = '', locale = 'es-CO' } = {}) {
  const from = parseFloat(el.dataset.v || 0); el.dataset.v = to;
  const fmt = v => v.toLocaleString(locale, { minimumFractionDigits: decimals, maximumFractionDigits: decimals }) + suffix;
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) { el.textContent = fmt(to); return; }
  const t0 = performance.now(), ease = t => 1 - Math.pow(1 - t, 4);
  (function step(now) {
    const p = Math.min(1, (now - t0) / dur);
    el.textContent = fmt(from + (to - from) * ease(p));
    if (p < 1) requestAnimationFrame(step);
  })(t0);
}
```
Parte del valor anterior (no de 0) cuando cambian los datos. Usa `tabular-nums` en el elemento.

## 4. Barras y progreso

```css
.seg, .mini > i { width: 0; transition: width var(--dur-data) var(--ease-out); }
```
```js
// Doble rAF: garantiza que el navegador pintó el 0 antes de fijar el valor
requestAnimationFrame(() => requestAnimationFrame(() => {
  segments.forEach((el, i) => el.style.width = data[i] + '%');
}));
```
Progreso indeterminado:
```css
.progress.indeterminate > i { width: 30%; animation: indet 1.2s var(--ease-standard) infinite; }
@keyframes indet { from { transform: translateX(-100%); } to { transform: translateX(340%); } }
```

## 5. Gauge (arco 180°)

```html
<svg viewBox="0 0 200 120">
  <path d="M20 110 A80 80 0 0 1 180 110" fill="none" stroke="var(--color-track)" stroke-width="14" stroke-linecap="round"/>
  <path id="arc" d="M20 110 A80 80 0 0 1 180 110" fill="none" stroke="var(--color-primary)" stroke-width="14"
        stroke-linecap="round" stroke-dasharray="251.33" stroke-dashoffset="251.33"
        style="transition: stroke-dashoffset var(--dur-data) var(--ease-out)"/>
</svg>
```
```js
const L = 251.33; // π × 80
arc.style.strokeDashoffset = L * (1 - value / 100);
countTo(label, value);
```

## 6. Indicador deslizante

```css
.nav-indicator { position:absolute; left:0; top:0; width:44px; height:44px; border-radius:var(--radius-md);
                 background: rgba(59,235,124,.14); transition: transform var(--dur-slow) var(--ease-out); }
.tabs .pill { position:absolute; top:4px; left:4px; height:32px; border-radius:var(--radius-full);
              background:#fff; box-shadow: var(--shadow-xs);
              transition: transform var(--dur-base) var(--ease-out), width var(--dur-base) var(--ease-out); }
```
```js
indicator.style.transform = `translateY(${activeItem.offsetTop}px)`;
pill.style.width = activeTab.offsetWidth + 'px';
pill.style.transform = `translateX(${activeTab.offsetLeft - 4}px)`;
```
Recalcula al redimensionar (`ResizeObserver`) y al cargar fuentes (`document.fonts.ready`).

## 7. Tooltip

```css
[data-tip] { position: relative; }
[data-tip]::after { content: attr(data-tip); position:absolute; left: calc(100% + 14px); top:50%;
  transform: translate(-4px, -50%); opacity:0; pointer-events:none; white-space:nowrap;
  background:#141414; color:#fff; font: var(--text-caption); padding:6px 10px; border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md); transition: opacity var(--dur-fast), transform var(--dur-fast) var(--ease-out); }
[data-tip]:hover::after, [data-tip]:focus-visible::after { opacity:1; transform: translate(0,-50%); transition-delay: 300ms; }
```
Para tooltips que pueden salirse de la ventana usa Floating UI (`@floating-ui/dom`) con flip/shift.

## 8. Menú / popover

```css
.menu { transform-origin: var(--origin, top left); opacity:0; transform: scale(.96) translateY(-4px);
        transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out); }
.menu[data-open] { opacity:1; transform:none; }
```
Para menú contextual, fija `--origin` al punto del clic.

## 9. Modal y drawer

```css
.overlay { position:fixed; inset:0; background: rgba(20,20,20,.32); backdrop-filter: blur(2px);
           animation: fade var(--dur-base) var(--ease-out); }
.modal { animation: modal-in var(--dur-slow) var(--ease-out); }
.modal[data-closing] { animation: modal-out var(--dur-base) var(--ease-in) forwards; }
@keyframes fade { from { opacity: 0; } }
@keyframes modal-in { from { opacity:0; transform: translateY(8px) scale(.96); } }
@keyframes modal-out { to { opacity:0; transform: scale(.98); } }

.drawer { position:fixed; top:0; right:0; height:100%; width:440px; background:#fff; box-shadow: var(--shadow-lg);
          transform: translateX(100%); transition: transform var(--dur-slow) var(--ease-out); }
.drawer[data-open] { transform: none; }
```
Usa `<dialog>` nativo (`showModal()`) para obtener trap de foco y `Esc` gratis.

## 10. Toast

```css
.toast { animation: toast-in var(--dur-slow) var(--ease-spring); }
.toast.out { animation: toast-out var(--dur-base) var(--ease-in) forwards; }
@keyframes toast-in { from { opacity:0; transform: translateY(12px) scale(.96); } }
@keyframes toast-out { to { opacity:0; transform: translateX(24px); } }
```
Pausa el temporizador en `mouseenter`; máximo 3 visibles (el más antiguo sale primero).

## 11. Skeleton

```css
.skeleton { position:relative; overflow:hidden; background: var(--color-bg-subtle); border-radius: var(--radius-sm); }
.skeleton::after { content:""; position:absolute; inset:0; transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.7), transparent);
  animation: shimmer 1.4s var(--ease-standard) infinite; }
@keyframes shimmer { to { transform: translateX(100%); } }
```
Transición skeleton → contenido: fade de 220 ms; el contenido real debe ocupar el mismo espacio.

## 12. Switch

```css
.switch { width:36px; height:20px; border-radius:999px; background: var(--color-border-strong);
          transition: background var(--dur-base) var(--ease-standard); }
.switch::before { content:""; display:block; width:16px; height:16px; margin:2px; border-radius:50%;
                  background:#fff; box-shadow: var(--shadow-xs);
                  transition: transform var(--dur-base) var(--ease-spring); }
.switch[aria-checked="true"] { background: var(--color-primary); }
.switch[aria-checked="true"]::before { transform: translateX(16px); }
```

## 13. Filas de tabla

```css
td { transition: background var(--dur-fast); }
tbody tr:hover td { background: var(--color-bg-subtle); }
tr.entering { animation: row-in var(--dur-slow) var(--ease-out); }
tr.leaving  { animation: row-out var(--dur-base) var(--ease-in) forwards; }
@keyframes row-in  { from { opacity:0; background: var(--color-primary-subtle); } }
@keyframes row-out { to { opacity:0; transform: translateX(-12px); } }
```
Al reordenar, para animar posiciones usa la técnica FLIP (o `@formkit/auto-animate` /
Framer Motion `layout` en React).

## 14. Reducción de movimiento en JS

```js
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
const animate = (el, keyframes, opts) =>
  reduceMotion.matches ? null : el.animate(keyframes, opts);
```
En React: hook `useReducedMotion()` (Framer Motion lo incluye) y valores finales sin transición.

---

### Librerías recomendadas (opcionales)
- **Framer Motion / Motion** (React): `layout`, `AnimatePresence`, springs.
- **@formkit/auto-animate**: listas y tablas sin configuración.
- **Floating UI**: posicionamiento de tooltips, menús y popovers.
Úsalas con las mismas duraciones y curvas de los tokens.
