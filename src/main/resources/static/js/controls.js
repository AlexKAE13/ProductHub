// Живые элементы современного оформления: сегментные переключатели и тумблеры на пружинах (как в макете).
// Физика по Apple "Designing Fluid Interfaces": нажатие отзывается сразу, элемент идет за курсором, у края сопротивление,
// при отпускании учитывается скорость. Работает поверх обычных radio и checkbox, поэтому формы и клавиатура не ломаются.
(function () {
// ---------- Физика: пружины, как в Apple (WWDC18 "Designing Fluid Interfaces") ----------
// Нажатие отзывается сразу, элемент идет за курсором один в один, у края сопротивление, при отпускании учитывается скорость,
// остановка на пружине: без отскока для обычного щелчка и с легким отскоком после броска.
const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;
const activeSprings = new Set();
let rafId = 0, lastT = 0;
class Spring {
  constructor(x) { this.x = x; this.v = 0; this.t = x; this.w = 2 * Math.PI / 0.32; this.z = 1; this.onTick = null; }
  to(t, { response = 0.32, damping = 1, velocity } = {}) {
    this.t = t; this.w = 2 * Math.PI / response; this.z = damping;
    if (velocity !== undefined) this.v = velocity;
    if (REDUCED) { this.x = t; this.v = 0; this.onTick && this.onTick(); return; }
    activeSprings.add(this); if (!rafId) { lastT = performance.now(); rafId = requestAnimationFrame(frame); }
  }
  jump(x) { this.x = x; this.t = x; this.v = 0; activeSprings.delete(this); this.onTick && this.onTick(); }
  get settled() { return Math.abs(this.x - this.t) < 0.03 && Math.abs(this.v) < 0.05; }
}
function frame(now) {
  const dt = Math.min(0.05, (now - lastT) / 1000); lastT = now;
  for (const sp of [...activeSprings]) {
    const n = Math.max(1, Math.ceil(dt / 0.004)), h = dt / n;
    for (let i = 0; i < n; i++) { const a = -sp.w * sp.w * (sp.x - sp.t) - 2 * sp.z * sp.w * sp.v; sp.v += a * h; sp.x += sp.v * h; }
    if (sp.settled) { sp.x = sp.t; sp.v = 0; activeSprings.delete(sp); }
    sp.onTick && sp.onTick();
  }
  rafId = activeSprings.size ? requestAnimationFrame(frame) : 0;
}
// резиновый ограничитель: за край можно слегка оттянуть, но не дальше lim пикселей (как у Apple: чем дальше тянешь, тем сильнее сопротивление)
const rubber = (d, lim) => lim * (1 - 1 / (d / lim * 0.6 + 1));
const clamped = (x, min, max, lim) => x < min ? min - rubber(min - x, lim) : x > max ? max + rubber(x - max, lim) : x;
const clearSelection = () => { try { getSelection().removeAllRanges(); } catch (e) { } };
const tick = () => { try { navigator.vibrate && navigator.vibrate(6); } catch (e) { } };
// скорость по последним точкам (px/сек)
function velocityOf(samples) {
  const now = performance.now(), recent = samples.filter(p => now - p.t < 90);
  if (recent.length < 2) return 0;
  const a = recent[0], b = recent[recent.length - 1];
  return b.t === a.t ? 0 : (b.x - a.x) / ((b.t - a.t) / 1000);
}


// ---------- Сегментные переключатели: .seg-row > label.seg-opt > input[type=radio] + span ----------
function initSeg(seg) {
  if (seg._ready) return; seg._ready = true;
  const thumb = document.createElement('i'); thumb.className = 'thumb'; seg.prepend(thumb);
  const sx = new Spring(0), sw = new Spring(0), ss = new Spring(1);
  const render = () => { thumb.style.width = sw.x + 'px'; thumb.style.transform = 'translateX(' + sx.x + 'px) scale(' + ss.x + ')'; };
  sx.onTick = sw.onTick = ss.onTick = render;
  const spans = () => [...seg.querySelectorAll(':scope > .seg-opt')];
  const onSpan = () => seg.querySelector(':scope > .seg-opt.on');
  const isOn = s => { const i = s.querySelector('input'); return i ? i.checked : s.classList.contains('active'); };
  const sync = () => spans().forEach(s => s.classList.toggle('on', isOn(s)));
  seg.addEventListener('change', sync);

  seg._place = (opts) => {
    const on = onSpan();
    if (!on || !on.offsetWidth) { thumb.style.opacity = on ? 1 : 0; return; }
    thumb.style.opacity = 1;
    const first = sw.x === 0;
    const o = Object.assign({ response: 0.34, damping: 0.9 }, seg._release || {}, opts || {});
    seg._release = null;
    if (first) { sx.jump(on.offsetLeft); sw.jump(on.offsetWidth); return; }
    sx.to(on.offsetLeft, o); sw.to(on.offsetWidth, { response: o.response, damping: 1 });
  };
  new MutationObserver(() => { if (!seg.classList.contains('dragging')) seg._place(); })
    .observe(seg, { subtree: true, attributes: true, attributeFilter: ['class'] });
  new ResizeObserver(() => { if (!seg.classList.contains('dragging') && sw.settled) { const on = onSpan(); if (on && on.offsetWidth) { sx.jump(on.offsetLeft); sw.jump(on.offsetWidth); } } }).observe(seg);
  seg.addEventListener('selectstart', e => e.preventDefault());
  seg.addEventListener('dragstart', e => e.preventDefault());
  seg.addEventListener('click', e => { if (e.isTrusted) { e.stopPropagation(); e.preventDefault(); } }, true);

  seg.addEventListener('pointerdown', e => {
    if (e.button) return;
    e.preventDefault(); clearSelection();
    const list = spans();
    const downSpan = list.find(sp => sp.contains(e.target))
      || list.reduce((best, sp) => { const r = sp.getBoundingClientRect(); const d = Math.abs(e.clientX - (r.left + r.width / 2)); return d < best.d ? { sp, d } : best; }, { sp: list[0], d: 1e9 }).sp;
    try { seg.setPointerCapture(e.pointerId); } catch (err) { }
    const rect = seg.getBoundingClientRect();
    const th = { left: rect.left + sx.x, right: rect.left + sx.x + sw.x };
    const onThumb = e.clientX >= th.left - 4 && e.clientX <= th.right + 4;
    const grab = e.clientX - th.left;
    const min = 3, max = rect.width - sw.x - 3;
    const samples = [{ x: e.clientX, t: performance.now() }];
    let dragging = false, hot = onSpan();
    if (onThumb) { seg.classList.add('pressed'); ss.to(1.06, { response: 0.22, damping: 0.9 }); }
    else { downSpan.style.opacity = 0.6; }

    const move = ev => {
      samples.push({ x: ev.clientX, t: performance.now() }); if (samples.length > 12) samples.shift();
      if (!dragging) {
        if (!onThumb || Math.abs(ev.clientX - e.clientX) < 4) return;
        dragging = true; seg.classList.add('dragging'); document.documentElement.classList.add('ui-dragging'); clearSelection();
      }
      let x = ev.clientX - rect.left - grab;
      x = clamped(x, min, max, 10);
      sx.jump(x);
      const c = rect.left + x + sw.x / 2;
      const h = list.find(sp => { const r = sp.getBoundingClientRect(); return c >= r.left && c < r.right; }) || (c < rect.left ? list[0] : list[list.length - 1]);
      if (h !== hot) { hot.classList.remove('hot'); h.classList.add('hot'); hot = h; tick(); }
    };
    const up = ev => {
      seg.removeEventListener('pointermove', move); seg.removeEventListener('pointerup', up); seg.removeEventListener('pointercancel', up);
      try { seg.releasePointerCapture(ev.pointerId); } catch (err) { }
      downSpan.style.opacity = '';
      const was = onSpan();
      let target;
      if (dragging) {
        const v = velocityOf(samples);
        const projected = sx.x + sw.x / 2 + Math.max(-70, Math.min(70, v * 0.14));
        const cx = rect.left + projected;
        target = list.reduce((best, sp) => { const r = sp.getBoundingClientRect(); const d = Math.abs(cx - (r.left + r.width / 2)); return d < best.d ? { sp, d } : best; }, { sp: list[0], d: 1e9 }).sp;
        seg._release = { response: 0.36, damping: 0.78, velocity: v };
      } else {
        const r = downSpan.getBoundingClientRect();
        target = ev.clientX >= r.left && ev.clientX <= r.right ? downSpan : was;
        seg._release = { response: 0.32, damping: 0.88 };
      }
      list.forEach(sp => sp.classList.remove('hot'));
      seg.classList.remove('dragging', 'pressed'); document.documentElement.classList.remove('ui-dragging');
      ss.to(1, { response: 0.3, damping: 0.55 });
      if (target !== was) { tick(); (target.querySelector('input') || target).click(); } else seg._place();
    };
    seg.addEventListener('pointermove', move); seg.addEventListener('pointerup', up); seg.addEventListener('pointercancel', up);
  });
  sync(); seg._place();
}

// ---------- Тумблеры: сам checkbox служит дорожкой, кружок рисует ::after ----------
function initSwitch(sw) {
  if (sw._ready) return; sw._ready = true;
  sw.addEventListener('click', e => { if (e.isTrusted) { e.stopPropagation(); e.preventDefault(); } }, true);
  sw.addEventListener('selectstart', e => e.preventDefault());
  sw.addEventListener('keydown', e => { if (e.key === ' ') { e.preventDefault(); sw.click(); } });
  sw.addEventListener('pointerdown', e => {
    if (e.button) return;
    e.preventDefault(); clearSelection();
    try { sw.setPointerCapture(e.pointerId); } catch (err) { }
    const rect = sw.getBoundingClientRect(), W = rect.width, REST = 18, PRESS = 24;
    const left = (kw) => W - 2 - kw;
    const isOn = sw.checked;
    const kx = new Spring(isOn ? left(REST) : 2), kw = new Spring(REST);
    const render = () => { sw.style.setProperty('--kx', kx.x + 'px'); sw.style.setProperty('--kw', kw.x + 'px'); };
    kx.onTick = kw.onTick = render;
    sw.classList.add('driving', 'pressed'); render();
    kw.to(PRESS, { response: 0.2, damping: 0.9 });
    kx.to(isOn ? left(PRESS) : 2, { response: 0.2, damping: 0.9 });
    const samples = [{ x: e.clientX, t: performance.now() }];
    const grabOffset = e.clientX - (rect.left + kx.x);
    let moved = false;
    const move = ev => {
      samples.push({ x: ev.clientX, t: performance.now() }); if (samples.length > 12) samples.shift();
      if (!moved && Math.abs(ev.clientX - e.clientX) < 4) return;
      if (!moved) { moved = true; document.documentElement.classList.add('ui-dragging'); clearSelection(); }
      let x = ev.clientX - rect.left - grabOffset;
      x = clamped(x, 2, left(kw.x), 5);
      kx.jump(x);
    };
    const up = ev => {
      sw.removeEventListener('pointermove', move); sw.removeEventListener('pointerup', up); sw.removeEventListener('pointercancel', up);
      try { sw.releasePointerCapture(ev.pointerId); } catch (err) { }
      document.documentElement.classList.remove('ui-dragging');
      let want;
      if (moved) { const v = velocityOf(samples); const proj = Math.max(-9, Math.min(9, v * 0.12)); want = kx.x + kw.x / 2 + proj > W / 2; }
      else want = !isOn;
      const v0 = moved ? velocityOf(samples) : 0;
      sw.classList.remove('pressed');
      if (want !== isOn) { tick(); sw.click(); }
      kw.to(REST, { response: 0.3, damping: 0.85 });
      kx.to(want ? left(REST) : 2, { response: 0.36, damping: moved ? 0.78 : 0.9, velocity: moved ? v0 : 0 });
      const done = () => { if (kx.settled && kw.settled) { sw.classList.remove('driving'); sw.style.removeProperty('--kx'); sw.style.removeProperty('--kw'); } else requestAnimationFrame(done); };
      requestAnimationFrame(done);
    };
    sw.addEventListener('pointermove', move); sw.addEventListener('pointerup', up); sw.addEventListener('pointercancel', up);
  });
}

function initAll(root) {
  (root || document).querySelectorAll('.seg-row').forEach(initSeg);
  (root || document).querySelectorAll('.check-line input[type=checkbox], .filters .check input[type=checkbox]').forEach(initSwitch);
}
initAll();
document.body.addEventListener('htmx:afterSwap', () => initAll(document));
window.addEventListener('load', () => document.querySelectorAll('.seg-row').forEach(seg => seg._place && seg._place({ response: 0.01, damping: 1 })));
})();
