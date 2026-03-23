package com.example.offworld.webhook;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.offworld.config.OffworldProperties;
import com.example.offworld.service.DebugShipService;
import com.example.offworld.service.DebugStateService;
import com.example.offworld.service.LogisticsService;

import reactor.core.publisher.Mono;

@RestController
public class DebugController {

    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final DebugShipService debugShipService;
    private final LogisticsService logisticsService;

    public DebugController(
            OffworldProperties props,
            DebugStateService debugStateService,
            DebugShipService debugShipService,
            LogisticsService logisticsService
    ) {
        this.props = props;
        this.debugStateService = debugStateService;
        this.debugShipService = debugShipService;
        this.logisticsService = logisticsService;
    }

    @GetMapping(value = "/debug", produces = MediaType.TEXT_HTML_VALUE)
    public String dashboard() {
        return """
                <html lang=\"fr\">
                <head>
                  <meta charset=\"UTF-8\">
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
                  <title>Offworld Reactive Demo</title>
                  <style>
                    :root {
                      --bg: #f4efe7;
                      --panel: #fffdf8;
                      --panel-strong: #f7f0e6;
                      --text: #1e1b18;
                      --muted: #6f655d;
                      --line: #dbcdbd;
                      --accent: #c65d2e;
                      --accent-soft: #f3d9cb;
                      --ok: #2f7d4a;
                      --warn: #b7791f;
                    }
                    * {
                      box-sizing: border-box;
                    }
                    body {
                      margin: 0;
                      font-family: Georgia, \"Times New Roman\", serif;
                      background:
                        radial-gradient(circle at top left, #f8dcc4 0, transparent 28%),
                        radial-gradient(circle at top right, #d9ead3 0, transparent 22%),
                        linear-gradient(180deg, #f8f3ec 0%, var(--bg) 100%);
                      color: var(--text);
                    }
                    .page {
                      max-width: 1200px;
                      margin: 0 auto;
                      padding: 28px 20px 40px;
                    }
                    .hero {
                      display: grid;
                      grid-template-columns: 1.4fr 0.8fr;
                      gap: 18px;
                      align-items: stretch;
                    }
                    .panel {
                      background: rgba(255, 253, 248, 0.92);
                      border: 1px solid var(--line);
                      border-radius: 20px;
                      box-shadow: 0 10px 30px rgba(70, 48, 30, 0.08);
                    }
                    .hero-main {
                      padding: 26px;
                    }
                    .eyebrow {
                      text-transform: uppercase;
                      letter-spacing: 0.18em;
                      font-size: 12px;
                      color: var(--accent);
                      margin-bottom: 10px;
                    }
                    h1 {
                      margin: 0;
                      font-size: clamp(34px, 5vw, 56px);
                      line-height: 0.95;
                    }
                    .subtitle {
                      margin-top: 14px;
                      color: var(--muted);
                      font-size: 18px;
                      line-height: 1.5;
                      max-width: 52ch;
                    }
                    .hero-side {
                      padding: 22px;
                      display: flex;
                      flex-direction: column;
                      gap: 14px;
                      justify-content: space-between;
                      background: linear-gradient(180deg, var(--panel) 0%, var(--panel-strong) 100%);
                    }
                    .stamp {
                      display: inline-flex;
                      align-items: center;
                      gap: 8px;
                      padding: 8px 12px;
                      border-radius: 999px;
                      background: var(--accent-soft);
                      color: var(--accent);
                      font-size: 13px;
                      width: fit-content;
                    }
                    .mini-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 14px;
                      margin-top: 18px;
                    }
                    .card {
                      padding: 18px;
                    }
                    .label {
                      font-size: 12px;
                      text-transform: uppercase;
                      letter-spacing: 0.12em;
                      color: var(--muted);
                      margin-bottom: 10px;
                    }
                    .value {
                      font-size: 20px;
                      line-height: 1.35;
                      word-break: break-word;
                    }
                    .chips {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 10px;
                      margin-top: 10px;
                    }
                    .chip {
                      padding: 8px 12px;
                      border-radius: 999px;
                      background: #efe4d7;
                      font-size: 13px;
                      color: #5e5146;
                    }
                    .chip.ok {
                      background: #dcefdc;
                      color: var(--ok);
                    }
                    .chip.off {
                      background: #efe3d4;
                      color: var(--warn);
                    }
                    .section {
                      margin-top: 18px;
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 18px;
                    }
                    .section .panel {
                      padding: 20px;
                    }
                    .section-title {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 10px;
                      margin-bottom: 16px;
                    }
                    h2 {
                      margin: 0;
                      font-size: 24px;
                    }
                    .muted {
                      color: var(--muted);
                      font-size: 14px;
                    }
                    .timeline {
                      display: flex;
                      flex-direction: column;
                      gap: 12px;
                      max-height: 520px;
                      overflow: auto;
                      padding-right: 6px;
                    }
                    .timeline-item {
                      border: 1px solid var(--line);
                      border-radius: 16px;
                      padding: 14px;
                      background: #fffaf3;
                    }
                    .timeline-meta {
                      display: flex;
                      justify-content: space-between;
                      gap: 12px;
                      font-size: 12px;
                      color: var(--muted);
                      margin-bottom: 8px;
                      text-transform: uppercase;
                      letter-spacing: 0.08em;
                    }
                    .timeline-text {
                      line-height: 1.45;
                      white-space: pre-wrap;
                      word-break: break-word;
                    }
                    .action-row {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                    }
                    button {
                      border: 0;
                      border-radius: 12px;
                      padding: 12px 16px;
                      background: var(--accent);
                      color: white;
                      font: inherit;
                      cursor: pointer;
                    }
                    button.secondary {
                      background: #706256;
                    }
                    .footer-note {
                      margin-top: 18px;
                      font-size: 13px;
                      color: var(--muted);
                    }
                    .live-grid {
                      margin-top: 18px;
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 18px;
                    }
                    .live-grid .panel {
                      padding: 20px;
                    }
                    .live-list {
                      display: flex;
                      flex-direction: column;
                      gap: 10px;
                      max-height: 360px;
                      overflow: auto;
                      padding-right: 6px;
                    }
                    .live-item {
                      border: 1px solid var(--line);
                      border-radius: 14px;
                      padding: 12px 14px;
                      background: #fffaf3;
                    }
                    .live-item strong {
                      display: inline-block;
                      margin-bottom: 6px;
                    }
                    .live-meta {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                      margin-top: 8px;
                    }
                    .live-pill {
                      border-radius: 999px;
                      background: #efe4d7;
                      color: #5e5146;
                      padding: 4px 9px;
                      font-size: 12px;
                    }
                    .summary-grid {
                      display: grid;
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .summary-box {
                      border: 1px solid var(--line);
                      border-radius: 14px;
                      padding: 14px;
                      background: #fffaf3;
                    }
                    .summary-number {
                      font-size: 28px;
                      line-height: 1;
                      margin-bottom: 6px;
                    }
                    .mono {
                      font-family: \"Courier New\", Courier, monospace;
                      font-size: 13px;
                    }
                    .empty-state {
                      border: 1px dashed var(--line);
                      border-radius: 14px;
                      padding: 16px;
                      color: var(--muted);
                      background: #fffcf7;
                    }
                    @media (max-width: 920px) {
                      .hero,
                      .section,
                      .live-grid {
                        grid-template-columns: 1fr;
                      }
                      .mini-grid {
                        grid-template-columns: 1fr;
                      }
                      .summary-grid {
                        grid-template-columns: 1fr 1fr;
                      }
                    }
                  </style>
                </head>
                <body>
                  <div class=\"page\">
                    <section class=\"hero\">
                      <div class=\"panel hero-main\">
                        <div class=\"eyebrow\">Reactive Trading Dashboard</div>
                        <h1>Vision claire de la démo Offworld</h1>
                        <div class=\"subtitle\">Cette page suit l'activité marché, trading, webhooks et logistique en quasi temps réel pour éviter de lire du JSON brut pendant la démonstration.</div>
                        <div class=\"mini-grid\">
                          <div class=\"panel card\">
                            <div class=\"label\">Dernière action trading</div>
                            <div id=\"lastTradeAction\" class=\"value\">Chargement...</div>
                          </div>
                          <div class=\"panel card\">
                            <div class=\"label\">Dernier webhook</div>
                            <div id=\"lastWebhook\" class=\"value\">Chargement...</div>
                          </div>
                          <div class=\"panel card\">
                            <div class=\"label\">Dernière action logistique</div>
                            <div id=\"lastLogisticsAction\" class=\"value\">Chargement...</div>
                          </div>
                          <div class=\"panel card\">
                            <div class=\"label\">Dernière activité réseau</div>
                            <div id=\"lastHttpRequest\" class=\"value\">Chargement...</div>
                          </div>
                        </div>
                      </div>
                      <aside class=\"panel hero-side\">
                        <div>
                          <div class=\"stamp\">Auto refresh toutes les 2 secondes</div>
                          <div style=\"margin-top:14px\" class=\"label\">Dernière mise à jour</div>
                          <div id=\"lastUpdateTime\" class=\"value\">Chargement...</div>
                          <div class=\"chips\" id=\"flags\"></div>
                        </div>
                        <div>
                          <div class=\"action-row\">
                            <button type=\"button\" id=\"refreshButton\">Rafraîchir maintenant</button>
                            <button type=\"button\" id=\"launchLogisticsButton\" class=\"secondary\">Lancer logistique</button>
                          </div>
                          <div id=\"actionMessage\" class=\"footer-note\"></div>
                        </div>
                      </aside>
                    </section>

                    <section class=\"section\">
                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Configuration active</h2>
                          <div class=\"muted\">Pour l'oral et la démo</div>
                        </div>
                        <div class=\"mini-grid\">
                          <div class=\"card\">
                            <div class=\"label\">Serveur Offworld</div>
                            <div id=\"baseUrl\" class=\"value\"></div>
                          </div>
                          <div class=\"card\">
                            <div class=\"label\">Player</div>
                            <div id=\"playerId\" class=\"value\"></div>
                          </div>
                          <div class=\"card\">
                            <div class=\"label\">Callback</div>
                            <div id=\"callbackUrl\" class=\"value\"></div>
                          </div>
                          <div class=\"card\">
                            <div class=\"label\">Dernière réponse HTTP</div>
                            <div id=\"lastHttpResponse\" class=\"value\"></div>
                          </div>
                          <div class=\"card\">
                            <div class=\"label\">Dernière action ship</div>
                            <div id=\"lastShipAction\" class=\"value\"></div>
                          </div>
                        </div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Timeline des événements</h2>
                          <div class=\"muted\">40 derniers événements en mémoire</div>
                        </div>
                        <div id=\"timeline\" class=\"timeline\"></div>
                      </div>
                    </section>

                    <section class=\"live-grid\">
                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Simulation live</h2>
                          <div class=\"muted\">Snapshot backend toutes les 2 secondes</div>
                        </div>
                        <div class=\"summary-grid\" id=\"simulationSummary\"></div>
                        <div class=\"footer-note mono\" id=\"simulationUpdatedAt\">Mise à jour simulation: -</div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Evenements live</h2>
                          <div class=\"muted\">Flux SSE avec messages simples</div>
                        </div>
                        <div id=\"eventsList\" class=\"live-list\"></div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Marché</h2>
                          <div class=\"muted\">Prix, spread, profondeur visible</div>
                        </div>
                        <div id=\"marketList\" class=\"live-list\"></div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Ordres actifs</h2>
                          <div class=\"muted\">Création, suivi, clôture</div>
                        </div>
                        <div id=\"ordersList\" class=\"live-list\"></div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Vaisseaux</h2>
                          <div class=\"muted\">Départ, trajet, arrivée</div>
                        </div>
                        <div id=\"shipsList\" class=\"live-list\"></div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Planètes et ressources</h2>
                          <div class=\"muted\">Inventaires observés côté station</div>
                        </div>
                        <div id=\"planetsList\" class=\"live-list\"></div>
                      </div>

                      <div class=\"panel\">
                        <div class=\"section-title\">
                          <h2>Trades récents</h2>
                          <div class=\"muted\">Derniers échanges captés en direct</div>
                        </div>
                        <div id=\"tradesList\" class=\"live-list\"></div>
                      </div>
                    </section>
                  </div>

                  <script>
                    const ids = [
                      "baseUrl",
                      "playerId",
                      "callbackUrl",
                      "lastHttpRequest",
                      "lastHttpResponse",
                      "lastWebhook",
                      "lastShipAction",
                      "lastLogisticsAction",
                      "lastTradeAction",
                      "lastUpdateTime"
                    ];
                    let simulationEventSource = null;
                    let simulationReconnectTimer = null;

                    function setText(id, value) {
                      const element = document.getElementById(id);
                      if (element == null) {
                        return;
                      }
                      element.textContent = value == null ? "-" : String(value);
                    }

                    function formatFlag(label, enabled) {
                      const status = enabled ? "ok" : "off";
                      const text = enabled ? "actif" : "off";
                      return '<div class="chip ' + status + '">' + label + ' · ' + text + '</div>';
                    }

                    function renderFlags(data) {
                      const flags = document.getElementById("flags");
                      if (flags == null) {
                        return;
                      }
                      flags.innerHTML = [
                        formatFlag("Market SSE", data.marketEnabled),
                        formatFlag("Trading", data.tradingEnabled),
                        formatFlag("Logistics", data.logisticsEnabled),
                        formatFlag("Orders", data.orderManagementEnabled)
                      ].join("");
                    }

                    function renderTimeline(state) {
                      const timeline = document.getElementById("timeline");
                      if (timeline == null) {
                        return;
                      }
                      const events = Array.isArray(state.recentEvents) ? state.recentEvents : [];
                      if (events.length === 0) {
                        timeline.innerHTML = '<div class="timeline-item"><div class="timeline-text">Aucun événement enregistré pour le moment.</div></div>';
                        return;
                      }
                      timeline.innerHTML = events.map(event => {
                        const time = event.time == null ? "-" : event.time;
                        const channel = event.channel == null ? "event" : event.channel;
                        const value = event.value == null ? "-" : event.value;
                        return '<div class="timeline-item">'
                          + '<div class="timeline-meta"><span>' + channel + '</span><span>' + time + '</span></div>'
                          + '<div class="timeline-text">' + value + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function escapeHtml(value) {
                      return String(value ?? "-")
                        .replaceAll("&", "&amp;")
                        .replaceAll("<", "&lt;")
                        .replaceAll(">", "&gt;")
                        .replaceAll('"', "&quot;")
                        .replaceAll("'", "&#39;");
                    }

                    function renderEmpty(id, text) {
                      const element = document.getElementById(id);
                      if (element == null) {
                        return;
                      }
                      element.innerHTML = '<div class="empty-state">' + escapeHtml(text) + '</div>';
                    }

                    function entriesFromMap(value) {
                      if (value == null || typeof value !== "object") {
                        return [];
                      }
                      return Object.entries(value);
                    }

                    function renderSimulationSummary(snapshot) {
                      const summary = document.getElementById("simulationSummary");
                      if (summary == null) {
                        return;
                      }
                      const counts = snapshot.counts == null ? {} : snapshot.counts;
                      const cards = [
                        ["Planètes", counts.planets],
                        ["Ordres", counts.activeOrders],
                        ["Vaisseaux", counts.ships],
                        ["Trades", counts.recentTrades]
                      ];
                      summary.innerHTML = cards.map(([label, value]) => {
                        return '<div class="summary-box">'
                          + '<div class="summary-number">' + escapeHtml(value ?? 0) + '</div>'
                          + '<div class="muted">' + escapeHtml(label) + '</div>'
                          + '</div>';
                      }).join("");
                      setText("simulationUpdatedAt", "Mise à jour simulation: " + (snapshot.updatedAt ?? "-"));
                    }

                    function renderEvents(snapshot) {
                      const eventsList = document.getElementById("eventsList");
                      if (eventsList == null) {
                        return;
                      }
                      const events = Array.isArray(snapshot.recentEvents) ? snapshot.recentEvents.slice(0, 12) : [];
                      if (events.length === 0) {
                        renderEmpty("eventsList", "Aucun événement live pour le moment.");
                        return;
                      }
                      eventsList.innerHTML = events.map(item => {
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(item.message) + '</strong>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">' + escapeHtml(item.type) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.recordedAt) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function renderSimulationSnapshot(snapshot) {
                      renderSimulationSummary(snapshot);
                      renderEvents(snapshot);
                      renderMarket(snapshot);
                      renderOrders(snapshot);
                      renderShips(snapshot);
                      renderPlanets(snapshot);
                      renderTrades(snapshot);
                    }

                    function renderMarket(snapshot) {
                      const entries = entriesFromMap(snapshot.marketPrices || snapshot.market)
                        .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
                        .slice(0, 8);
                      if (entries.length === 0) {
                        renderEmpty("marketList", "Aucune donnée marché pour le moment.");
                        return;
                      }
                      document.getElementById("marketList").innerHTML = entries.map(([goodName, item]) => {
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(goodName) + '</strong>'
                          + '<div class="mono">last=' + escapeHtml(item.lastPrice) + ' bid=' + escapeHtml(item.bestBid) + ' ask=' + escapeHtml(item.bestAsk) + ' spread=' + escapeHtml(item.spread) + '</div>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">source ' + escapeHtml(item.source) + '</span>'
                          + '<span class="live-pill">maj ' + escapeHtml(item.updatedAt) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function renderOrders(snapshot) {
                      const entries = entriesFromMap(snapshot.activeOrders || snapshot.orders)
                        .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
                        .slice(0, 8);
                      if (entries.length === 0) {
                        renderEmpty("ordersList", "Aucun ordre actif.");
                        return;
                      }
                      document.getElementById("ordersList").innerHTML = entries.map(([orderId, item]) => {
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(orderId) + '</strong>'
                          + '<div>' + escapeHtml(item.side) + ' ' + escapeHtml(item.goodName) + ' @ ' + escapeHtml(item.price) + '</div>'
                          + '<div class="mono">qty=' + escapeHtml(item.quantity) + ' filled=' + escapeHtml(item.filledQuantity) + ' status=' + escapeHtml(item.status) + '</div>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">' + escapeHtml(item.orderType) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.stationPlanetId) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.source) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function renderShips(snapshot) {
                      const entries = entriesFromMap(snapshot.ships)
                        .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
                        .slice(0, 8);
                      if (entries.length === 0) {
                        renderEmpty("shipsList", "Aucun vaisseau observé.");
                        return;
                      }
                      document.getElementById("shipsList").innerHTML = entries.map(([shipId, item]) => {
                        const cargo = entriesFromMap(item.cargo).map(([goodName, qty]) => goodName + ':' + qty).join(', ');
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(shipId) + '</strong>'
                          + '<div>' + escapeHtml(item.originPlanetId) + ' → ' + escapeHtml(item.destinationPlanetId) + '</div>'
                          + '<div class="mono">status=' + escapeHtml(item.status) + ' cargo=' + escapeHtml(cargo || '-') + '</div>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">' + escapeHtml(item.truckingId) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.source) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function renderPlanets(snapshot) {
                      const entries = entriesFromMap(snapshot.planets)
                        .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
                        .slice(0, 8);
                      if (entries.length === 0) {
                        renderEmpty("planetsList", "Aucune planète observée.");
                        return;
                      }
                      document.getElementById("planetsList").innerHTML = entries.map(([planetId, item]) => {
                        const inventory = entriesFromMap(item.inventory).slice(0, 4)
                          .map(([goodName, qty]) => goodName + ':' + qty)
                          .join(', ');
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(item.displayName || planetId) + '</strong>'
                          + '<div class="mono">' + escapeHtml(planetId) + ' · système=' + escapeHtml(item.systemName) + '</div>'
                          + '<div>Stock: ' + escapeHtml(inventory || 'vide') + '</div>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">' + escapeHtml(item.source) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.updatedAt) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    function renderTrades(snapshot) {
                      const trades = Array.isArray(snapshot.recentTrades) ? snapshot.recentTrades.slice(0, 10) : [];
                      if (trades.length === 0) {
                        renderEmpty("tradesList", "Aucun trade récent.");
                        return;
                      }
                      document.getElementById("tradesList").innerHTML = trades.map(item => {
                        return '<div class="live-item">'
                          + '<strong>' + escapeHtml(item.goodName) + '</strong>'
                          + '<div class="mono">price=' + escapeHtml(item.price) + ' qty=' + escapeHtml(item.quantity) + '</div>'
                          + '<div>' + escapeHtml(item.buyerStation) + ' ←→ ' + escapeHtml(item.sellerStation) + '</div>'
                          + '<div class="live-meta">'
                          + '<span class="live-pill">' + escapeHtml(item.source) + '</span>'
                          + '<span class="live-pill">' + escapeHtml(item.recordedAt || item.updatedAt) + '</span>'
                          + '</div>'
                          + '</div>';
                      }).join("");
                    }

                    async function loadSimulationState() {
                      const response = await fetch("/state", { cache: "no-store" });
                      if (response.ok == false) {
                        throw new Error("State HTTP " + response.status);
                      }
                      const snapshot = await response.json();
                      renderSimulationSnapshot(snapshot);
                    }

                    function connectSimulationStream() {
                      if (typeof EventSource === "undefined") {
                        return;
                      }
                      if (simulationEventSource != null) {
                        simulationEventSource.close();
                      }
                      simulationEventSource = new EventSource("/stream/state");
                      simulationEventSource.onmessage = event => {
                        try {
                          const snapshot = JSON.parse(event.data);
                          renderSimulationSnapshot(snapshot);
                        } catch (error) {
                          console.error("Erreur SSE simulation", error);
                        }
                      };
                      simulationEventSource.onerror = () => {
                        const message = document.getElementById("actionMessage");
                        if (message != null) {
                          message.textContent = "Flux live interrompu, reconnexion...";
                        }
                        simulationEventSource.close();
                        simulationEventSource = null;
                        if (simulationReconnectTimer != null) {
                          window.clearTimeout(simulationReconnectTimer);
                        }
                        simulationReconnectTimer = window.setTimeout(connectSimulationStream, 2000);
                      };
                    }

                    async function loadStatus() {
                      const response = await fetch("/debug/status", { cache: "no-store" });
                      if (response.ok == false) {
                        throw new Error("Status HTTP " + response.status);
                      }
                      const data = await response.json();
                      const state = data.state == null ? {} : data.state;

                      setText("baseUrl", data.baseUrl);
                      setText("playerId", data.playerId);
                      setText("callbackUrl", data.callbackUrl);
                      ids.slice(3).forEach(id => setText(id, state[id]));
                      renderFlags(data);
                      renderTimeline(state);
                    }

                    async function launchLogistics() {
                      const message = document.getElementById("actionMessage");
                      if (message != null) {
                        message.textContent = "Lancement logistique en cours...";
                      }
                      const response = await fetch("/debug/logistics/launch", { method: "POST" });
                      const text = await response.text();
                      if (message != null) {
                        message.textContent = text;
                      }
                      await refresh();
                    }

                    async function refresh() {
                      try {
                        await Promise.all([loadStatus(), loadSimulationState()]);
                      } catch (error) {
                        const message = document.getElementById("actionMessage");
                        if (message != null) {
                          message.textContent = "Erreur de chargement: " + error.message;
                        }
                      }
                    }

                    document.getElementById("refreshButton").addEventListener("click", refresh);
                    document.getElementById("launchLogisticsButton").addEventListener("click", () => {
                      launchLogistics().catch(error => {
                        const message = document.getElementById("actionMessage");
                        if (message != null) {
                          message.textContent = "Erreur logistique: " + error.message;
                        }
                      });
                    });

                    refresh();
                    connectSimulationStream();
                    window.setInterval(refresh, 5000);
                  </script>
                </body>
                </html>
                """;
    }

    @GetMapping("/debug/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", props.getBaseUrl());
        result.put("playerId", props.getPlayerId());
        result.put("callbackUrl", props.getCallbackUrl());
        result.put("marketEnabled", props.getMarket().isEnabled());
        result.put("tradingEnabled", props.getTrading().isEnabled());
        result.put("logisticsEnabled", props.getLogistics().isEnabled());
        result.put("orderManagementEnabled", props.getOrderManagement().isEnabled());
        result.put("state", debugStateService.snapshot());
        return result;
    }

    @PostMapping("/debug/logistics/launch")
    public Mono<ResponseEntity<String>> launchLogistics() {
        return logisticsService.forceLaunchNow()
                .thenReturn(ResponseEntity.ok("Trucking launch triggered"));
    }

    @GetMapping("/debug/ships")
    public Map<String, Object> allShips() {
        return debugShipService.getAllShips();
    }

    @GetMapping("/debug/ships/{shipId}")
    public Map<String, Object> oneShip(@PathVariable String shipId) {
        return debugShipService.getShip(shipId);
    }
}
