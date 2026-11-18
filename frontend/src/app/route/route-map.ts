import { afterNextRender, Component, computed, DestroyRef, effect, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import type * as Leaflet from 'leaflet';
import { Stop } from '../core/stops-api';

@Component({
  selector: 'app-route-map',
  template: `
    <div class="map-shell">
      <div class="map-heading"><span class="map-label">YOUR COMMUTE</span><span class="map-mode">Stop overview</span></div>
      <div class="map-canvas" #canvas role="region" aria-label="Interactive map of your selected stops. Use arrow keys to pan, plus and minus to zoom."></div>
      <div class="map-caption"><span class="caption-mark" aria-hidden="true">↗</span><div><strong>{{ home() && school() ? 'Two stops. One less thing to worry about.' : 'Your morning, mapped out.' }}</strong><p>{{ home() && school() ? mapped() ? 'The dashed line connects your stops. It is not the bus route.' : 'Stop positions are not available yet. You can still save this route.' : 'Select your stops to see them on the map.' }}</p></div></div>
    </div>
    @if (unavailable()) { <p class="map-notice" role="status">The map is unavailable right now. You can still choose stops and save your route.</p> }
    <p class="map-help">Map tiles from OpenStreetMap. Stop locations from Linz AG. No location tracking.</p>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .map-shell { position: relative; overflow: hidden; border-radius: 1rem; border: 1px solid var(--line); background: #e7ede8; }
    .map-canvas { width: 100%; height: 31rem; z-index: 0; }
    .map-heading { position: absolute; z-index: 400; top: 1rem; left: 1rem; right: 1rem; display: flex; justify-content: space-between; align-items: center; gap: .5rem; pointer-events: none; }
    .map-label, .map-mode { background: #ffffffed; box-shadow: 0 2px 8px #102b4710; border: 1px solid #d3d9e1; border-radius: .4rem; padding: .45rem .65rem; color: var(--text); }
    .map-label { font-size: .65rem; letter-spacing: .1em; font-weight: 750; }
    .map-mode { font-size: .7rem; }
    .map-caption { display: flex; gap: .8rem; position: absolute; z-index: 400; left: 1rem; right: 1rem; bottom: 2rem; padding: 1rem; background: #0e2a47f5; color: var(--on-night); border-radius: .7rem; box-shadow: 0 4px 18px #102b4720; pointer-events: none; }
    .caption-mark { display: grid; place-items: center; width: 2rem; height: 2rem; flex: none; background: var(--amber); color: var(--night); border-radius: .45rem; font-size: 1.3rem; }
    .map-caption strong { font-family: var(--display); font-size: 1rem; }
    .map-caption p { font-size: .7rem; color: var(--on-night-muted); margin-top: .25rem; }
    .map-help { color: var(--muted); font-size: .65rem; padding: .6rem .2rem 0; }
    .map-notice { margin-top: .5rem; color: #704800; background: #fff1cf; padding: .7rem; border-radius: .4rem; font-size: .8rem; }
    @media(max-width: 700px) { .map-canvas { height: 23rem; } .map-caption { padding: .8rem; left: .75rem; right: .75rem; } .map-caption strong { font-size: .9rem; } }
  `,
})
export class RouteMap {
  readonly home = input<Stop | null>(null);
  readonly school = input<Stop | null>(null);
  private readonly canvas = viewChild.required<ElementRef<HTMLElement>>('canvas');
  private readonly destroyRef = inject(DestroyRef);
  protected readonly unavailable = signal(false);
  private readonly ready = signal(false);
  protected readonly mapped = computed(() => [this.home(), this.school()].every(stop => typeof stop?.latitude === 'number' && typeof stop.longitude === 'number'));
  private map: Leaflet.Map | undefined;
  private layer: Leaflet.LayerGroup | undefined;
  private leaflet: typeof Leaflet | undefined;
  private destroyed = false;

  constructor() {
    effect(() => { const home = this.home(); const school = this.school(); if (this.ready()) this.draw(home, school); });
    afterNextRender(() => void this.mount());
    this.destroyRef.onDestroy(() => { this.destroyed = true; this.map?.remove(); });
  }

  private async mount() {
    try {
      const L = await import('leaflet');
      if (this.destroyed) return;
      this.leaflet = L;
      this.map = L.map(this.canvas().nativeElement, { scrollWheelZoom: false, zoomControl: false }).setView([48.27, 14.28], 13);
      L.control.zoom({ position: 'topright' }).addTo(this.map);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).on('tileerror', () => this.unavailable.set(true)).addTo(this.map);
      this.layer = L.layerGroup().addTo(this.map);
      const observer = new ResizeObserver(() => { this.map?.invalidateSize(); this.draw(this.home(), this.school()); });
      observer.observe(this.canvas().nativeElement);
      this.destroyRef.onDestroy(() => observer.disconnect());
      this.ready.set(true);
    } catch { if (!this.destroyed) this.unavailable.set(true); }
  }

  private draw(home: Stop | null, school: Stop | null) {
    const L = this.leaflet;
    if (!L || !this.layer || !this.map) return;
    this.layer.clearLayers();
    const points: Leaflet.LatLngTuple[] = [];
    for (const [stop, label] of [[home, 'A'], [school, 'B']] as const) {
      if (typeof stop?.latitude !== 'number' || typeof stop.longitude !== 'number') continue;
      const point: Leaflet.LatLngTuple = [stop.latitude, stop.longitude];
      points.push(point);
      const text = document.createElement('span');
      text.textContent = `${label}: ${stop.name}`;
      L.marker(point, { title: `${label}: ${stop.name}`, alt: stop.name,
        icon: L.divIcon({ className: `commute-pin commute-pin--${label.toLowerCase()}`, html: `<span><b>${label}</b></span>`, iconSize: [34, 42], iconAnchor: [17, 40] }),
      }).bindPopup(text).addTo(this.layer);
    }
    if (points.length === 2) L.polyline(points, { color: '#0e2a47', weight: 4, dashArray: '6 10', opacity: .75 }).addTo(this.layer);
    if (points.length) this.map.fitBounds(L.latLngBounds(points), { paddingTopLeft: [55, 85], paddingBottomRight: [55, 130], maxZoom: 15, animate: false });
  }
}
