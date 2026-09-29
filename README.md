# Cobblemon Spawner

A [Cobblemon](https://www.curseforge.com/minecraft/mc-mods/cobblemon) addon that adds a configurable **Pokémon Spawner** block, in the spirit of Pixelmon's spawner, built for adventure maps.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green?logo=minecraft)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-supported-dbb37d?logo=fabric)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-e04e14)](https://neoforged.net)

## Getting the block

`/give @s cobblemonspawner:pokemon_spawner`, or the *Operator Utilities* creative tab (enable it in Controls > "Operator Items Tab").
Like a command block, it can only be placed and configured by an operator in creative, and is unbreakable in survival.

## Configuration

Right-click the block. The pool takes one Pokémon per line, using the **Cobblemon properties syntax** (the same as `/pokespawn`), with an optional weight prefix:

```
10: pikachu
5: eevee level=20
1: pikachu shiny=yes
```

A `level=` in the line overrides the spawner's level range.

| Option | Description |
|---|---|
| Min / Max level | Random level range |
| Radius | Horizontal spawn radius (0 = on top of the block) |
| Max active | Max Pokémon from this spawner alive at once |
| Per cycle | Pokémon spawned per cycle |
| Activation | A player must be within this range |
| Min / Max delay | Seconds between spawns (also the respawn delay after a Pokémon is defeated or caught) |
| Time | Any / Day / Night |
| Redstone | Ignored / only when powered / only when unpowered |
| Once | Spawns a single time until reset (bosses, legendaries) |
| Uncatchable | Poké Balls bounce off |
| Persistent | No natural despawn |
| Static | NoAI |

Breaking the spawner removes the wild Pokémon it created (except those in battle).

## Project layout

Architectury multiloader (Fabric + NeoForge, Minecraft 1.21.1):

- `common` — block, block entity, spawn logic, GUI, payloads
- `fabric` / `neoforge` — registration, networking and entry points only
