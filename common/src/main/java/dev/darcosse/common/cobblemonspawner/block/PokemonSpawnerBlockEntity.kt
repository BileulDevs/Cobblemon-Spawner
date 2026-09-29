package dev.darcosse.common.cobblemonspawner.block

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import dev.darcosse.common.cobblemonspawner.CobblemonSpawner
import dev.darcosse.common.cobblemonspawner.CobblemonSpawnerRegistry
import dev.darcosse.common.cobblemonspawner.network.OpenSpawnerScreenPayload
import dev.darcosse.common.cobblemonspawner.network.SpawnerNetwork
import dev.darcosse.common.cobblemonspawner.spawner.NaturalPlacementResolver
import dev.darcosse.common.cobblemonspawner.spawner.RedstoneMode
import dev.darcosse.common.cobblemonspawner.spawner.SpawnEntry
import dev.darcosse.common.cobblemonspawner.spawner.SpawnPlacement
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerConfig
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerTags
import dev.darcosse.common.cobblemonspawner.spawner.TimeCondition
import dev.darcosse.common.cobblemonspawner.spawner.WeatherCondition
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import kotlin.math.max
import kotlin.math.min

/**
 * Stores a spawner configuration and runs the spawn loop server-side.
 *
 * Spawned Pokémon are not tracked by UUID: each one carries the spawner position in its
 * `persistentData`, and the spawner counts them by scanning the area. That survives
 * world reloads and chunk unloads without duplicating Pokémon.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
class PokemonSpawnerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonSpawnerRegistry.spawnerBlockEntity, pos, state) {

    var config = SpawnerConfig()
        private set

    /** True once at least one Pokémon was spawned. Blocks further spawns when `onceOnly` is set. */
    var hasSpawned = false
        private set

    private var cooldown = 0
    private var wasFull = false

    // ---------------------------------------------------------------- tick

    /**
     * Server tick. Almost always a single decrement: the real work (conditions, entity scan,
     * spawn) only runs when the cooldown reaches zero, i.e. at most once per [RECHECK_TICKS].
     */
    fun serverTick(level: ServerLevel) {
        if (cooldown > 0) {
            cooldown--
            return
        }

        // Entities load asynchronously: counting before they are loaded would make the spawner
        // believe its Pokémon are gone and spawn duplicates after a reload.
        if (!level.areEntitiesLoaded(ChunkPos.asLong(blockPos)) || !conditionsMet(level)) {
            cooldown = RECHECK_TICKS
            return
        }

        val active = countActive(level)
        if (active >= config.maxActive) {
            wasFull = true
            cooldown = RECHECK_TICKS
            return
        }

        // A slot just freed up (Pokémon defeated / caught): wait a full delay before respawning.
        if (wasFull) {
            wasFull = false
            cooldown = randomDelayTicks(level)
            return
        }

        val toSpawn = min(config.spawnsPerCycle, config.maxActive - active)
        var spawned = 0
        repeat(toSpawn) { if (spawnOne(level)) spawned++ }

        if (spawned > 0) {
            hasSpawned = true
            setChanged()
            cooldown = randomDelayTicks(level)
        } else {
            cooldown = RETRY_TICKS
        }
    }

    /**
     * Checks every spawner-wide condition except the active-count limit.
     * Time of day and weather are per entry and checked in [pickEntry].
     */
    private fun conditionsMet(level: ServerLevel): Boolean {
        if (config.entries.isEmpty()) return false
        if (config.onceOnly && hasSpawned) return false

        val powered = level.hasNeighborSignal(blockPos)
        when (config.redstone) {
            RedstoneMode.IGNORED -> Unit
            RedstoneMode.POWERED -> if (!powered) return false
            RedstoneMode.UNPOWERED -> if (powered) return false
        }

        return level.hasNearbyAlivePlayer(
            blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5,
            config.activationRange.toDouble()
        )
    }

    // ---------------------------------------------------------------- spawning

    /**
     * Picks one entry, builds its Pokémon and tries to place it around the spawner.
     * @return true if a Pokémon was actually added to the world
     */
    private fun spawnOne(level: ServerLevel): Boolean {
        val entry = pickEntry(level) ?: return false
        val propertiesString = entry.toPropertiesString()

        val properties = try {
            PokemonProperties.parse(propertiesString)
        } catch (e: Exception) {
            CobblemonSpawner.LOGGER.warn("Spawner at {}: invalid properties '{}'", blockPos, propertiesString, e)
            return false
        }
        if (properties.species == null) {
            CobblemonSpawner.LOGGER.warn("Spawner at {}: no species in '{}'", blockPos, propertiesString)
            return false
        }

        // A level written in the extra properties wins over the entry range.
        if (properties.level == null) {
            // min/max re-ordered defensively: nextInt() throws on a negative bound, which would crash the tick.
            val low = minOf(entry.minLevel, entry.maxLevel)
            val high = maxOf(entry.minLevel, entry.maxLevel)
            properties.level = low + level.random.nextInt(high - low + 1)
        }

        val entity = try {
            properties.createEntity(level)
        } catch (e: Exception) {
            CobblemonSpawner.LOGGER.warn("Spawner at {}: failed to create '{}'", blockPos, propertiesString, e)
            return false
        }

        // AUTO is resolved from the final species (the extra properties may override it).
        val placement = if (entry.placement == SpawnPlacement.AUTO) {
            NaturalPlacementResolver.resolve(properties.species ?: entry.species)
        } else {
            entry.placement
        }
        if (!placeEntity(level, entity, placement)) return false

        val data = entity.pokemon.persistentData
        data.putLong(SpawnerTags.SPAWNER_POS, blockPos.asLong())
        if (config.uncatchable) data.putBoolean(SpawnerTags.UNCATCHABLE, true)

        if (config.persistent) entity.setPersistenceRequired()
        if (config.noAi) entity.isNoAi = true

        if (!level.addFreshEntity(entity)) return false

        level.sendParticles(
            ParticleTypes.POOF, entity.x, entity.y + entity.bbHeight / 2, entity.z,
            SPAWN_PARTICLE_COUNT, SPAWN_PARTICLE_SPREAD, SPAWN_PARTICLE_SPREAD, SPAWN_PARTICLE_SPREAD, SPAWN_PARTICLE_SPEED
        )
        return true
    }

    /**
     * Weighted random pick among the entries allowed by the current time of day and weather.
     * Returns null when none is, e.g. a pool of night Pokémon during the day.
     */
    private fun pickEntry(level: ServerLevel): SpawnEntry? {
        val entries = config.entries.filter { isTimeAllowed(it.time, level) && isWeatherAllowed(it.weather, level) }
        if (entries.isEmpty()) return null
        var roll = level.random.nextInt(entries.sumOf { it.weight })
        return entries.first { roll -= it.weight; roll < 0 }
    }

    /**
     * True when [time] allows spawning at the current time of day of [level].
     */
    private fun isTimeAllowed(time: TimeCondition, level: ServerLevel): Boolean = when (time) {
        TimeCondition.ANY -> true
        TimeCondition.DAY -> level.isDay
        TimeCondition.NIGHT -> level.isNight
    }

    /**
     * True when [weather] allows spawning with the current weather of [level].
     */
    private fun isWeatherAllowed(weather: WeatherCondition, level: ServerLevel): Boolean = when (weather) {
        WeatherCondition.ANY -> true
        WeatherCondition.CLEAR -> !level.isRaining
        WeatherCondition.RAIN -> level.isRaining
        WeatherCondition.THUNDER -> level.isThundering
    }

    /**
     * Looks for a spot inside the spawn radius that matches [placement] and where the entity
     * fits without colliding. Radius 0 means "right above / around the spawner column".
     */
    private fun placeEntity(level: ServerLevel, entity: PokemonEntity, placement: SpawnPlacement): Boolean {
        val radius = config.radius
        val verticalRange = max(radius, MIN_VERTICAL_RANGE)
        val random = level.random
        val offsets = (verticalRange downTo -verticalRange).toMutableList()

        repeat(PLACEMENT_ATTEMPTS) {
            val x = blockPos.x + if (radius == 0) 0 else random.nextInt(radius * 2 + 1) - radius
            val z = blockPos.z + if (radius == 0) 0 else random.nextInt(radius * 2 + 1) - radius

            if (placement.spreadsVertically) shuffle(offsets, level)

            for (dy in offsets) {
                val feet = BlockPos(x, blockPos.y + dy, z)
                if (!placement.fits(level, feet)) continue

                val yaw = random.nextFloat() * FULL_TURN_DEGREES
                entity.moveTo(x + 0.5, feet.y.toDouble(), z + 0.5, yaw, 0f)
                entity.yHeadRot = yaw
                if (!level.noCollision(entity)) continue
                if (!placement.allowsLiquid && level.containsAnyLiquid(entity.boundingBox)) continue
                return true
            }
        }
        return false
    }

    /**
     * In-place Fisher-Yates shuffle driven by the level random, so results stay tied to the
     * world seed like the rest of the spawn rolls (kotlin.random would not be).
     */
    private fun shuffle(list: MutableList<Int>, level: ServerLevel) {
        for (i in list.size - 1 downTo 1) {
            val j = level.random.nextInt(i + 1)
            list[i] = list[j].also { list[j] = list[i] }
        }
    }

    /**
     * Rolls the next delay, in ticks, inside the configured [SpawnerConfig.minDelay]..[SpawnerConfig.maxDelay].
     */
    private fun randomDelayTicks(level: ServerLevel): Int {
        val seconds = config.minDelay + level.random.nextInt(config.maxDelay - config.minDelay + 1)
        return max(1, seconds * TICKS_PER_SECOND)
    }

    // ---------------------------------------------------------------- tracking

    /**
     * Area in which this spawner looks for the Pokémon it created. Wider than the spawn radius
     * because Pokémon wander; one that leaves it is no longer counted and may be replaced.
     */
    private fun scanArea(): AABB =
        AABB(blockPos).inflate(max(config.radius * 2 + SCAN_MARGIN, MIN_SCAN_RANGE).toDouble())

    /**
     * True for a still-wild Pokémon tagged with this spawner position.
     */
    private fun isFromThisSpawner(entity: PokemonEntity): Boolean {
        val data = entity.pokemon.persistentData
        return entity.pokemon.isWild() &&
            data.contains(SpawnerTags.SPAWNER_POS) &&
            data.getLong(SpawnerTags.SPAWNER_POS) == blockPos.asLong()
    }

    /**
     * Number of living wild Pokémon created by this spawner inside [scanArea].
     */
    fun countActive(level: ServerLevel): Int =
        level.getEntitiesOfClass(PokemonEntity::class.java, scanArea()) { it.isAlive && isFromThisSpawner(it) }.size

    /** Removes the wild Pokémon created by this spawner (except those currently in battle). */
    fun despawnSpawned(level: ServerLevel) {
        level.getEntitiesOfClass(PokemonEntity::class.java, scanArea()) { it.isAlive && isFromThisSpawner(it) && !it.isBattling }
            .forEach { it.discard() }
    }

    // ---------------------------------------------------------------- GUI

    /**
     * Sends the current config to [player] so the client opens the configuration screen.
     */
    fun openScreen(player: ServerPlayer, level: ServerLevel) {
        SpawnerNetwork.sendToPlayer(
            player,
            OpenSpawnerScreenPayload(blockPos, config.toTag(), countActive(level), hasSpawned)
        )
    }

    /**
     * Applies a sanitized config coming from the GUI.
     * @param reset also clears the "already spawned" state and removes the current Pokémon.
     */
    fun applyConfig(newConfig: SpawnerConfig, reset: Boolean, level: ServerLevel) {
        config = newConfig
        if (reset) {
            despawnSpawned(level)
            hasSpawned = false
            wasFull = false
        }
        cooldown = 0
        setChanged()
    }

    // ---------------------------------------------------------------- save / load

    /**
     * Persists the config and the runtime state (cooldown, once-only flag).
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.put("Config", config.toTag())
        tag.putBoolean("HasSpawned", hasSpawned)
        tag.putInt("Cooldown", cooldown)
    }

    /**
     * Restores what [saveAdditional] wrote. The config goes through its own migration.
     */
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        // Sanitized on load too, not only on GUI save: a hand-edited or corrupted NBT
        // (weight 0, min > max) would otherwise make nextInt() throw on every tick.
        config = SpawnerConfig.fromTag(tag.getCompound("Config")).also { it.sanitize() }
        hasSpawned = tag.getBoolean("HasSpawned")
        cooldown = tag.getInt("Cooldown")
    }

    companion object {
        /** Delay between two condition checks while idle. Lower = more reactive, more scans. */
        private const val RECHECK_TICKS = 20

        /** Delay after a cycle where no spot was found. Too low spams placement searches in cramped rooms. */
        private const val RETRY_TICKS = 40

        /** Random positions tried per spawn. Each one scans the full vertical range. */
        private const val PLACEMENT_ATTEMPTS = 16

        private const val TICKS_PER_SECOND = 20

        /**
         * Minimum half-height of the vertical search, in blocks. With radius 0 it still lets a
         * Pokémon land on top of the spawner or in a pool right next to it. Raising it makes
         * each placement attempt scan more blocks.
         */
        private const val MIN_VERTICAL_RANGE = 3

        private const val FULL_TURN_DEGREES = 360f

        /** Extra blocks scanned around the spawn radius to still count Pokémon that wandered off. */
        private const val SCAN_MARGIN = 16

        /** Minimum half-size of the scan box, so tiny radii still track wandering Pokémon. */
        private const val MIN_SCAN_RANGE = 32

        private const val SPAWN_PARTICLE_COUNT = 12
        private const val SPAWN_PARTICLE_SPREAD = 0.3
        private const val SPAWN_PARTICLE_SPEED = 0.02
    }
}
