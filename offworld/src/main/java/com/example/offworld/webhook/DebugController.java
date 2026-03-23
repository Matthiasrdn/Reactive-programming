package com.example.offworld.webhook;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugController {

    @GetMapping(value = {"/", "/debug"}, produces = MediaType.TEXT_HTML_VALUE)
    public String dashboard() {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Station Control</title>
              <style>
                :root {
                  --bg: #07101d;
                  --panel: #121b28;
                  --panel2: #162233;
                  --border: #27374d;
                  --text: #e7edf5;
                  --muted: #98a7ba;
                  --green: #39d353;
                  --blue: #58a6ff;
                  --gold: #f0b232;
                  --orange: #f59e0b;
                }

                * { box-sizing: border-box; }

                body {
                  margin: 0;
                  background: linear-gradient(180deg, #060c16 0%, #07101d 100%);
                  color: var(--text);
                  font-family: Arial, sans-serif;
                }

                .container {
                  padding: 18px;
                }

                .topbar {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  margin-bottom: 14px;
                  padding-bottom: 10px;
                  border-bottom: 1px solid var(--border);
                }

                .title {
                  font-size: 32px;
                  font-weight: bold;
                  color: var(--blue);
                  letter-spacing: 2px;
                }

                .subtitle {
                  margin-top: 4px;
                  color: var(--text);
                  font-size: 18px;
                }

                .credits {
                  font-size: 28px;
                  color: var(--gold);
                  font-weight: bold;
                }

                .grid {
                  display: grid;
                  grid-template-columns: repeat(12, 1fr);
                  gap: 16px;
                  align-items: start;
                }

                .card {
                  background: linear-gradient(180deg, rgba(22,34,51,.95), rgba(18,27,40,.95));
                  border: 1px solid var(--border);
                  border-radius: 14px;
                  padding: 18px;
                  box-shadow: 0 8px 24px rgba(0,0,0,.3);
                  min-height: 260px;
                  overflow: hidden;
                }

                .span-3 { grid-column: span 3; }
                .span-4 { grid-column: span 4; }
                .span-6 { grid-column: span 6; }
                .span-8 { grid-column: span 8; }
                .span-12 { grid-column: span 12; }

                @media (max-width: 1400px) {
                  .span-8, .span-6, .span-4, .span-3 { grid-column: span 6; }
                }

                @media (max-width: 900px) {
                  .span-8, .span-6, .span-4, .span-3, .span-12 { grid-column: span 12; }
                }

                .card h2 {
                  margin: 0 0 12px 0;
                  font-size: 22px;
                  color: var(--green);
                }

                .card-body-scroll {
                  max-height: 420px;
                  overflow-y: auto;
                  overflow-x: auto;
                  padding-right: 6px;
                }

                .row {
                  display: flex;
                  justify-content: space-between;
                  gap: 12px;
                  padding: 9px 0;
                  border-bottom: 1px solid rgba(255,255,255,0.06);
                }

                .muted { color: var(--muted); }

                .badge {
                  display: inline-block;
                  padding: 4px 8px;
                  border-radius: 999px;
                  background: #2b4f7a;
                  color: #d9ecff;
                  font-size: 12px;
                }

                .badge-orange {
                  background: rgba(245, 158, 11, 0.18);
                  color: #ffd89c;
                }

                .badge-green {
                  background: rgba(57, 211, 83, 0.18);
                  color: #aaf0b6;
                }

                .badge-red {
                  background: rgba(239, 68, 68, 0.18);
                  color: #ffb0b0;
                }

                table {
                  width: 100%;
                  border-collapse: collapse;
                  font-size: 14px;
                }

                th, td {
                  padding: 8px 6px;
                  text-align: left;
                  border-bottom: 1px solid rgba(255,255,255,0.06);
                  vertical-align: top;
                }

                th {
                  color: var(--muted);
                }

                .small { font-size: 13px; }
                .mono { font-family: monospace; }

                .section-note {
                  color: var(--muted);
                  font-size: 13px;
                  margin-top: -4px;
                  margin-bottom: 10px;
                }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="topbar">
                  <div>
                    <div class="title">🚀 STATION CONTROL</div>
                    <div id="connectedAs" class="subtitle">Connecté en tant que : -</div>
                  </div>
                  <div id="statusRight" class="credits">Dashboard</div>
                </div>

                <div class="grid">
                  <div class="card span-4">
                    <h2>📍 Où on se situe</h2>
                    <div id="position"></div>
                  </div>

                  <div class="card span-4">
                    <h2>🏭 Entrepôt de surface</h2>
                    <div class="section-note">Stock de la planète de surface configurée</div>
                    <div id="surfaceStock"></div>
                  </div>

                  <div class="card span-4">
                    <h2>🌍 Ressources globales</h2>
                    <div class="section-note">Total connu sur toutes les planètes suivies</div>
                    <div id="globalResources"></div>
                  </div>

                  <div class="card span-4">
                    <h2>🛗 Ascenseur local</h2>
                    <div class="section-note">Station orbitale ↔ planète, distinct des ships spatiaux</div>
                    <div id="elevator"></div>
                  </div>

                  <div class="card span-4">
                    <h2>📦 Inventaire orbital</h2>
                    <div class="section-note">Cargo actuellement vu dans le trafic spatial</div>
                    <div id="orbitalStock"></div>
                  </div>

                  <div class="card span-4">
                    <h2>🧠 IA Trading Bot</h2>
                    <div id="botStatus"></div>
                  </div>

                  <div class="card span-12">
                    <h2>🛸 Ships interplanétaires</h2>
                    <div class="section-note">Uniquement les transports spatiaux entre planètes</div>
                    <div class="card-body-scroll">
                      <div id="spaceShips"></div>
                    </div>
                  </div>

                  <div class="card span-6">
                    <h2>📄 Ordres actifs</h2>
                    <div class="card-body-scroll">
                      <div id="orders"></div>
                    </div>
                  </div>

                  <div class="card span-6">
                    <h2>💹 Trades récents</h2>
                    <div class="card-body-scroll">
                      <div id="trades"></div>
                    </div>
                  </div>

                  <div class="card span-12">
                    <h2>🗺️ Cartographie galactique</h2>
                    <div class="card-body-scroll">
                      <div id="planets"></div>
                    </div>
                  </div>
                </div>
              </div>

              <script>
                function esc(v) {
                  return String(v ?? "-")
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll('"', "&quot;")
                    .replaceAll("'", "&#39;");
                }

                function renderKeyValues(targetId, data) {
                  const el = document.getElementById(targetId);
                  const entries = Object.entries(data || {});
                  if (!entries.length) {
                    el.innerHTML = "<div class='muted'>Aucune donnée</div>";
                    return;
                  }

                  el.innerHTML = entries.map(([k, v]) => `
                    <div class="row">
                      <div>${esc(k).toUpperCase()}</div>
                      <div>${esc(v)}</div>
                    </div>
                  `).join("");
                }

                function badgeForStatus(status) {
                  const s = String(status || "").toLowerCase();
                  if (s.includes("ready")) return "badge badge-green";
                  if (s.includes("partial")) return "badge badge-orange";
                  if (s.includes("disabled") || s.includes("waiting")) return "badge badge-red";
                  return "badge";
                }

                function renderPosition(data) {
                  const el = document.getElementById("position");
                  el.innerHTML = `
                    <div class="row"><div>Player</div><div>${esc(data.playerId)}</div></div>
                    <div class="row"><div>Base URL</div><div class="mono">${esc(data.baseUrl)}</div></div>
                    <div class="row"><div>Station system</div><div>${esc(data.stationSystemName)}</div></div>
                    <div class="row"><div>Station trading</div><div>${esc(data.tradingStationPlanetId)}</div></div>
                    <div class="row"><div>Système origine</div><div>${esc(data.originSystemName)}</div></div>
                    <div class="row"><div>Planète origine</div><div>${esc(data.originPlanetId)}</div></div>
                    <div class="row"><div>Système destination</div><div>${esc(data.destinationSystemName)}</div></div>
                    <div class="row"><div>Planète destination</div><div>${esc(data.destinationPlanetId)}</div></div>
                    <div class="row"><div>Logistique</div><div>${esc(data.logisticsEnabled)}</div></div>
                  `;
                }

                function renderElevator(data) {
                  const el = document.getElementById("elevator");
                  el.innerHTML = `
                    <div class="row"><div>Type</div><div><span class="badge">${esc(data.type)}</span></div></div>
                    <div class="row"><div>Direction</div><div>${esc(data.direction)}</div></div>
                    <div class="row"><div>Planète surface</div><div>${esc(data.surfacePlanetId)}</div></div>
                    <div class="row"><div>Noeud orbital</div><div>${esc(data.orbitalNode)}</div></div>
                    <div class="row"><div>Ressource</div><div><span class="badge">${esc(data.goodName)}</span></div></div>
                    <div class="row"><div>Demandé</div><div>${esc(data.requestedQuantity)}</div></div>
                    <div class="row"><div>Disponible au sol</div><div>${esc(data.availableSurfaceStock)}</div></div>
                    <div class="row"><div>Chargeable maintenant</div><div>${esc(data.loadableNow)}</div></div>
                    <div class="row"><div>Statut</div><div><span class="${badgeForStatus(data.status)}">${esc(data.status)}</span></div></div>
                  `;
                }

                function renderBotStatus(data) {
                  const el = document.getElementById("botStatus");
                  el.innerHTML = `
                    <div class="row"><div>Dernière action</div><div>${esc(data.lastAction)}</div></div>
                    <div class="row"><div>Ordres actifs</div><div>${esc(data.activeOrders)}</div></div>
                    <div class="row"><div>Marchés suivis</div><div>${esc(data.trackedMarkets)}</div></div>
                    <div class="row"><div>Ships spatiaux</div><div>${esc(data.activeSpaceShips)}</div></div>
                    <div class="row"><div>Meilleure opportunité</div><div>${esc(data.bestOpportunity)}</div></div>
                  `;
                }

                function renderSpaceShips(data) {
                  const ships = Object.values(data || {});
                  const el = document.getElementById("spaceShips");

                  if (!ships.length) {
                    el.innerHTML = "<div class='muted'>Aucun ship interplanétaire observé.</div>";
                    return;
                  }

                  el.innerHTML = `
                    <table>
                      <thead>
                        <tr>
                          <th>Ship</th>
                          <th>De</th>
                          <th>Vers</th>
                          <th>Status</th>
                          <th>Cargo</th>
                          <th>Maj</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${ships.map(s => `
                          <tr>
                            <td class="mono">${esc(s.shipId)}</td>
                            <td>${esc(s.originPlanetId)}</td>
                            <td>${esc(s.destinationPlanetId)}</td>
                            <td>${esc(s.status)}</td>
                            <td class="small mono">${esc(JSON.stringify(s.cargo || {}))}</td>
                            <td class="small">${esc(s.updatedAt)}</td>
                          </tr>
                        `).join("")}
                      </tbody>
                    </table>
                  `;
                }

                function renderOrders(data) {
                  const orders = Object.values(data || {});
                  const el = document.getElementById("orders");

                  if (!orders.length) {
                    el.innerHTML = "<div class='muted'>Aucun ordre actif.</div>";
                    return;
                  }

                  el.innerHTML = `
                    <table>
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>Good</th>
                          <th>Side</th>
                          <th>Prix</th>
                          <th>Qté</th>
                          <th>Filled</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${orders.map(o => `
                          <tr>
                            <td class="mono">${esc(o.orderId)}</td>
                            <td>${esc(o.goodName)}</td>
                            <td>${esc(o.side)}</td>
                            <td>${esc(o.price)}</td>
                            <td>${esc(o.quantity)}</td>
                            <td>${esc(o.filledQuantity)}</td>
                            <td>${esc(o.status)}</td>
                          </tr>
                        `).join("")}
                      </tbody>
                    </table>
                  `;
                }

                function renderPlanets(data) {
                  const planets = Object.values(data || {});
                  const el = document.getElementById("planets");

                  if (!planets.length) {
                    el.innerHTML = "<div class='muted'>Aucune planète suivie.</div>";
                    return;
                  }

                  el.innerHTML = planets.map(p => `
                    <div class="row">
                      <div>
                        <strong>${esc(p.displayName)}</strong><br>
                        <span class="muted small">${esc(p.systemName || "-")} · ${esc(p.planetId)}</span>
                      </div>
                      <div>${Object.keys(p.inventory || {}).length} ressources</div>
                    </div>
                  `).join("");
                }

                function renderTrades(trades) {
                  const el = document.getElementById("trades");

                  if (!trades || !trades.length) {
                    el.innerHTML = "<div class='muted'>Aucun trade récent.</div>";
                    return;
                  }

                  el.innerHTML = `
                    <table>
                      <thead>
                        <tr>
                          <th>Ressource</th>
                          <th>Prix</th>
                          <th>Qté</th>
                          <th>Acheteur</th>
                          <th>Vendeur</th>
                          <th>Temps</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${trades.slice(0, 20).map(t => `
                          <tr>
                            <td>${esc(t.goodName)}</td>
                            <td>${esc(t.price)}</td>
                            <td>${esc(t.quantity)}</td>
                            <td>${esc(t.buyerStation || t.buyerId)}</td>
                            <td>${esc(t.sellerStation || t.sellerId)}</td>
                            <td class="small">${esc(t.recordedAt)}</td>
                          </tr>
                        `).join("")}
                      </tbody>
                    </table>
                  `;
                }

                async function refresh() {
                  const res = await fetch("/state");
                  const state = await res.json();

                  const position = state.position || {};
                  const player = state.player || {};

                  document.getElementById("connectedAs").textContent =
                    "Connecté en tant que : " + (player.name || position.playerId || "-");

                  document.getElementById("statusRight").textContent =
                    player.credits != null ? player.credits + " cr" : "Dashboard";

                  renderPosition(state.position || {});
                  renderKeyValues("surfaceStock", state.surfaceStock || {});
                  renderKeyValues("globalResources", state.globalResources || {});
                  renderKeyValues("orbitalStock", state.orbitalInventory || {});
                  renderElevator(state.elevatorOverview || {});
                  renderBotStatus(state.botStatus || {});
                  renderSpaceShips(state.spaceShips || {});
                  renderOrders(state.activeOrders || {});
                  renderTrades(state.recentTrades || []);
                  renderPlanets(state.planets || {});
                }

                refresh();
                setInterval(refresh, 2000);
              </script>
            </body>
            </html>
            """;
    }
}
