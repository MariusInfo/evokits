# ðŸ“¦ EvoKits

Flexible kit management and cooldown system for **Minecraft Forge 1.20.1**.

## Features
- **In-Game Kit Creation:** Create kits directly from your inventory with a single command.
- **Persistent SQL Cooldowns:** Cooldown timers are tracked per-player in the database.
- **Crate Key Rewards:** Link virtual crate keys directly to kits.
- **Preview & Claiming:** Commands for players to preview cooldowns and claim kits.

## Commands
| Command | Permission | Description |
| :--- | :--- | :--- |
| /kit <name> | kit.<name> | Claims a kit |
| /kits | Everyone | Lists all available kits and cooldown status |
| /evokit create <name> <hours> | OP | Creates a kit from your current inventory |
| /evokit delete <name> | OP | Deletes an existing kit |
| /evokit addkey <key> <kit> | OP | Links a crate key to a kit |

## Requirements
- Minecraft Forge **1.20.1** (47.4.18+)
- Java **17**
- **EvoCore** 1.0.0+

## License
All Rights Reserved - EvoCraft Network
