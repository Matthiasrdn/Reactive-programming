# 🚀 Offworld Trading Bot – Spring WebFlux Client

Client Spring Boot WebFlux pour interagir avec le serveur Rust offworld-trading-manager.

Ce projet permet de :
- automatiser le trading (buy / sell simple)
- automatiser la logistique (transport)
- afficher un dashboard temps réel
- tester avec des requêtes curl

---

# 📦 Installation

## 1. Cloner le client

git clone https://github.com/Matthiasrdn/Reactive-programming.git
cd Reactive-programming/offworld

## 2. Cloner le serveur

git clone https://github.com/arendsyl/offworld-trading-manager.git
cd offworld-trading-manager

---

# ⚙️ Lancement

## Lancer le serveur Rust

cargo run

API :
http://localhost:3000

## Lancer le client Spring

./mvnw spring-boot:run

UI :
http://localhost:8081

STATE JSON :
http://localhost:8081/state

---

# 🧠 Architecture

SimulationStateService  
→ stocke tout l’état (stations, ships, orders, market, trades)

StateSyncService  
→ synchronise périodiquement avec le serveur

ShipAutomationService  
→ auto dock / undock (ignore les ships non possédés)

SimpleLogisticsAutomationService  
→ transporte les ressources automatiquement

TradingScanService  
→ stratégie simple buy/sell

PlayerSyncService  
→ récupère les crédits

---

# 🖥️ Dashboard

http://localhost:8081

Affiche :

- 💰 argent
- 📦 stock surface
- 🌍 ressources globales
- 🚀 ships interplanétaires
- 📈 orders actifs
- 🔄 trades récents
- 🛗 ascenseur local

---

# 🛗 Ascenseur local

⚠️ IMPORTANT

Ce n’est PAS un endpoint serveur.

C’est une projection :

surface → orbital

Conditions :

- stock disponible
- logistics activé
- ressource existante

---

# ⚙️ Configuration (application.yml)

server:
  port: 8081

offworld:
  base-url: "http://localhost:3000"
  player-id: "beta-corp"
  api-key: "beta-secret-key-002"

  trading:
    enabled: true
    station-system-name: "Proxima Centauri"
    station-planet-id: "Proxima Centauri-1"
    default-quantity: 10
    min-spread: 5
    watched-goods:
      - "food"
      - "water"
      - "iron_ore"

  logistics:
    enabled: true
    origin-system-name: "Proxima Centauri"
    origin-planet-id: "Proxima Centauri-1"
    destination-system-name: "Sol"
    destination-planet-id: "Sol-3"
    good-name: "food"
    quantity: 10

  order-management:
    enabled: true
    cancel-after-seconds: 120

---

# 🔐 Préparer le token

export TOKEN="Authorization: Bearer beta-secret-key-002"

---

# 🧪 TEST COMPLET (copier-coller)

## 1. Voir argent

curl -H "$TOKEN" http://localhost:3000/players/beta-corp | jq

---

## 2. Voir station

curl -H "$TOKEN" \
"http://localhost:3000/settlements/Proxima%20Centauri/Proxima%20Centauri-1/station" | jq

---

## 3. Voir marché

curl http://localhost:3000/market/book/food | jq

---

## 4. Créer SELL

curl -X POST http://localhost:3000/market/orders \
-H "$TOKEN" \
-H "Content-Type: application/json" \
-d '{
  "good_name":"food",
  "side":"sell",
  "order_type":"limit",
  "price":8,
  "quantity":10,
  "station_planet_id":"Proxima Centauri-1"
}'

---

## 5. Créer BUY (match)

curl -X POST http://localhost:3000/market/orders \
-H "$TOKEN" \
-H "Content-Type: application/json" \
-d '{
  "good_name":"food",
  "side":"buy",
  "order_type":"limit",
  "price":20,
  "quantity":10,
  "station_planet_id":"Proxima Centauri-1"
}'

---

## 6. Voir orders

curl -H "$TOKEN" http://localhost:3000/market/orders | jq

---

## 7. Voir trades live

curl -N http://localhost:3000/market/trades

---

## 8. Voir ships

curl -H "$TOKEN" http://localhost:3000/ships | jq

---

## 9. Voir state UI

curl -s http://localhost:8081/state | jq

---

# 🔁 Reset state

curl -X POST http://localhost:8081/debug/state/reset \
-H "$TOKEN"

---

# 🧪 Séquence rapide

export TOKEN="Authorization: Bearer beta-secret-key-002"

curl -H "$TOKEN" http://localhost:3000/players/beta-corp | jq
curl http://localhost:3000/market/book/food | jq

curl -X POST http://localhost:3000/market/orders \
-H "$TOKEN" -H "Content-Type: application/json" \
-d '{"good_name":"food","side":"sell","order_type":"limit","price":8,"quantity":10,"station_planet_id":"Proxima Centauri-1"}'

curl -X POST http://localhost:3000/market/orders \
-H "$TOKEN" -H "Content-Type: application/json" \
-d '{"good_name":"food","side":"buy","order_type":"limit","price":20,"quantity":10,"station_planet_id":"Proxima Centauri-1"}'

---

# ⚠️ Notes importantes

- un trade apparaît uniquement si BUY >= SELL
- certaines stations sont interdites (403)
- l’ascenseur est simulé côté client
- les ships non possédés sont ignorés

---

# 🎯 Résultat attendu

Après les curls :

- trades visibles
- orders visibles
- argent mis à jour
- dashboard dynamique
- ascenseur actif si stock OK

# Architecture du projet


---

                        +----------------------+
                        |   Front Dashboard    |
                        |   DebugController    |
                        +----------+-----------+
                                   |
                                   v
                        +----------------------+
                        |  State / Read Model  |
                        | SimulationStateSvc   |
                        +--+---------+---------+
                           |         |        
          +----------------+         +------------------+
          v                                         v
+----------------------+                 +----------------------+
| Sync Domain          |                 | Automation Domain    |
| - PlayerSyncService  |                 | - TradingScanService |
| - StateSyncService   |                 | - ShipAutomationSvc  |
+----------+-----------+                 | - LogisticsSvc       |
           |                             +----------+-----------+
           v                                        |
+---------------------------------------------------------------+
|                  HTTP Clients / Integration                   |
| PlayerClient | StationClient | ShippingClient | MarketClient  |
+-------------------------------+-------------------------------+
                                |
                                v
+---------------------------------------------------------------+
|              Rust Game Server / Market Engine                 |
|              offworld-trading-manager                         |
+---------------------------------------------------------------+


---