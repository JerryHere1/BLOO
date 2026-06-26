# SuperBot Plugin — Setup & Build Guide

## Requirements
- Java 21+
- Maven 3.6+
- Paper 1.21.1 server
- Citizens 2.x plugin installed on the server

## Build
```bash
cd SuperBot
mvn clean package -q
```
Output: `target/SuperBot-1.0.0.jar`

## Install
1. Drop `SuperBot-1.0.0.jar` into your server's `plugins/` folder
2. Make sure `Citizens.jar` is also in `plugins/`
3. Restart the server

## File Structure Generated on First Run
```
plugins/
  SuperBot/
    config.yml     ← all tunable settings
    bots.db        ← SQLite database (backpacks, modes)
```

## Commands
| Command | Permission | Description |
|---|---|---|
| `/spawnadvancedbot` | `superbot.spawn` | Spawn your bot at your location |
| `/removebot` | `superbot.spawn` | Remove your bot (backpack saved) |
| `/superbotreload` | `superbot.admin` | Hot-reload config.yml |

## Chat Commands (in-game)
- Type **`come here`** in chat → your bot teleports to you instantly

## Usage
1. `/spawnadvancedbot` — bot spawns at your feet
2. **Right-click the bot** → opens the 3-row Control Panel GUI
3. Click a mode button to activate it
4. Click **View Backpack** to drag items out

## GUI Slots
| Slot | Item | Action |
|---|---|---|
| 10 | Diamond Pickaxe | Mining Mode |
| 11 | Diamond Axe | Lumberjack Mode |
| 12 | Diamond Hoe | Farming Mode |
| 13 | Nether Star | Status display |
| 14 | Diamond Sword | Combat Mode |
| 16 | Chest | Open Backpack |
| 22 | Barrier | Set Idle |

## config.yml Highlights
- `max-bots-per-player` — limit per player
- `ai.tick-interval` — how often the AI brain runs (default 40 ticks = 2s)
- `ai.combat-scan-radius` — mob detection range
- `mining.ores` — add/remove any ore block + its drop
- `farming.crops` — add Potatoes, Carrots, Beetroots, etc.
- `lumber.logs` — add any log type

## Adding New Ores (example: Nether Quartz)
```yaml
mining:
  ores:
    NETHER_QUARTZ_ORE:
      drop: QUARTZ
      amount: 2
```

## AI Priority Order
1. **Combat** — always active, scans 6 blocks for monsters
2. **Gathering** — mines/chops/farms based on active mode
3. **Roaming** — wanders if nothing to gather; detects cliffs/lava before moving

## Hazard Detection
Before each roam step the bot checks:
- Block directly in front → lava or water? → push back
- Block *below* the front block → air/lava/water? → push back (cliff detection)

## Dependencies
Citizens is a soft required dependency for NPC rendering and the Navigator API.
If Citizens is not found, the plugin disables itself at startup with a clear error.
