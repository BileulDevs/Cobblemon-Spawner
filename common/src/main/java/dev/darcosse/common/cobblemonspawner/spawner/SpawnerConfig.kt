package dev.darcosse.common.cobblemonspawner.spawner

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

/**
 * How the shiny state of a pool entry is decided.
 *
 * @property propertyToken the Cobblemon properties token appended to the entry, or null to keep natural odds
 */
enum class ShinyMode(val propertyToken: String?) {
    DEFAULT(null),
    ALWAYS("shiny=yes"),
    NEVER("shiny=no");

    /** Translation key of the label shown on the shiny cycle button. */
    val translationKey: String get() = "gui.cobblemonspawner.entry.shiny.${name.lowercase()}"
}

/**
 * Time of day during which the spawner is allowed to work.
 */
enum class TimeCondition {
    ANY, DAY, NIGHT;

    /** Translation key of the label shown on the time cycle button. */
    val translationKey: String get() = "gui.cobblemonspawner.time.${name.lowercase()}"
}

/**
 * How the redstone signal received by the spawner gates spawning.
 */
enum class RedstoneMode {
    /** Redstone has no effect. */
    IGNORED,
    /** Only spawns while powered. */
    POWERED,
    /** Only spawns while NOT powered. */
    UNPOWERED;

    /** Translation key of the label shown on the redstone cycle button. */
    val translationKey: String get() = "gui.cobblemonspawner.redstone.${name.lowercase()}"
}

/**
 * One weighted entry of the spawner pool, edited through the entry screen.
 *
 * @property species species id as returned by `Species.showdownId()` (e.g. `pikachu`, `mrmime`)
 * @property weight relative chance of being picked among the entries of the same spawner
 * @property minLevel lowest level this entry can spawn at
 * @property maxLevel highest level this entry can spawn at
 * @property shiny forced shiny state, or natural odds
 * @property extraProperties free Cobblemon properties appended as-is (form aspects, nature, ...)
 * @property placement where around the spawner this entry may appear
 */
data class SpawnEntry(
    val species: String,
    val weight: Int = DEFAULT_WEIGHT,
    val minLevel: Int = DEFAULT_MIN_LEVEL,
    val maxLevel: Int = DEFAULT_MAX_LEVEL,
    val shiny: ShinyMode = ShinyMode.DEFAULT,
    val extraProperties: String = "",
    val placement: SpawnPlacement = SpawnPlacement.AUTO
) {

    /**
     * Builds the Cobblemon properties string used both to validate and to spawn this entry.
     * The level is intentionally left out: the spawner rolls it inside [minLevel]..[maxLevel],
     * unless [extraProperties] already sets one.
     */
    fun toPropertiesString(): String =
        listOfNotNull(species, shiny.propertyToken, extraProperties.ifBlank { null }).joinToString(" ")

    /**
     * Serializes this entry for the block entity save data and the GUI payloads.
     */
    fun toTag(): CompoundTag = CompoundTag().apply {
        putString(KEY_SPECIES, species)
        putInt(KEY_WEIGHT, weight)
        putInt(KEY_MIN_LEVEL, minLevel)
        putInt(KEY_MAX_LEVEL, maxLevel)
        putString(KEY_SHINY, shiny.name)
        putString(KEY_EXTRA, extraProperties)
        putString(KEY_PLACEMENT, placement.name)
    }

    companion object {
        /** Default weight of a new entry. Only relative values matter, 10 leaves room to go lower. */
        const val DEFAULT_WEIGHT = 10

        /** Default level range of a new entry; purely a starting point for the editor. */
        const val DEFAULT_MIN_LEVEL = 5
        const val DEFAULT_MAX_LEVEL = 15

        private const val KEY_SPECIES = "Species"
        private const val KEY_WEIGHT = "Weight"
        private const val KEY_MIN_LEVEL = "MinLevel"
        private const val KEY_MAX_LEVEL = "MaxLevel"
        private const val KEY_SHINY = "Shiny"
        private const val KEY_EXTRA = "Extra"
        private const val KEY_PLACEMENT = "Placement"

        /**
         * Reads an entry written by [toTag]. A missing placement (format version 2) reads as
         * [SpawnPlacement.AUTO], which is the migration from version 2.
         */
        fun fromTag(tag: CompoundTag): SpawnEntry = SpawnEntry(
            species = tag.getString(KEY_SPECIES),
            weight = tag.getInt(KEY_WEIGHT),
            minLevel = tag.getInt(KEY_MIN_LEVEL),
            maxLevel = tag.getInt(KEY_MAX_LEVEL),
            shiny = runCatching { ShinyMode.valueOf(tag.getString(KEY_SHINY)) }.getOrDefault(ShinyMode.DEFAULT),
            extraProperties = tag.getString(KEY_EXTRA),
            placement = runCatching { SpawnPlacement.valueOf(tag.getString(KEY_PLACEMENT)) }.getOrDefault(SpawnPlacement.AUTO)
        )
    }
}

/**
 * Full configuration of a single spawner. Serialized to NBT both for the
 * block entity save data and for the client <-> server GUI payloads.
 *
 * Delays are expressed in seconds, distances in blocks. Levels live on each [SpawnEntry].
 *
 * @author Darcosse
 * @version 2.0
 * @since 2026
 */
class SpawnerConfig {
    var entries: MutableList<SpawnEntry> = mutableListOf()
    var radius = 4
    var maxActive = 2
    var spawnsPerCycle = 1
    var minDelay = 10
    var maxDelay = 30
    var activationRange = 24
    var time = TimeCondition.ANY
    var redstone = RedstoneMode.IGNORED
    var onceOnly = false
    var uncatchable = false
    var persistent = false
    var noAi = false

    /**
     * Clamps every value to a safe range. Always called server-side
     * before applying a config received from a client.
     */
    fun sanitize() {
        val maxPokemonLevel = Cobblemon.config.maxPokemonLevel

        radius = radius.coerceIn(0, MAX_RADIUS)
        maxActive = maxActive.coerceIn(1, MAX_ACTIVE)
        spawnsPerCycle = spawnsPerCycle.coerceIn(1, MAX_SPAWNS_PER_CYCLE)
        minDelay = minDelay.coerceIn(0, MAX_DELAY_SECONDS)
        maxDelay = maxDelay.coerceIn(0, MAX_DELAY_SECONDS)
        if (minDelay > maxDelay) minDelay = maxDelay.also { maxDelay = minDelay }
        activationRange = activationRange.coerceIn(1, MAX_ACTIVATION_RANGE)

        entries = entries
            .take(MAX_ENTRIES)
            .map { entry ->
                val low = entry.minLevel.coerceIn(1, maxPokemonLevel)
                val high = entry.maxLevel.coerceIn(1, maxPokemonLevel)
                entry.copy(
                    species = entry.species.trim().lowercase().take(MAX_SPECIES_LENGTH),
                    weight = entry.weight.coerceIn(1, MAX_WEIGHT),
                    // A reversed range is swapped rather than rejected: the intent is obvious.
                    minLevel = minOf(low, high),
                    maxLevel = maxOf(low, high),
                    extraProperties = entry.extraProperties.trim().take(MAX_EXTRA_LENGTH)
                )
            }
            .filter { it.species.isNotBlank() }
            .toMutableList()
    }

    /**
     * Serializes the config, stamped with [FORMAT_VERSION].
     */
    fun toTag(): CompoundTag = CompoundTag().apply {
        putInt(KEY_FORMAT_VERSION, FORMAT_VERSION)
        put(KEY_ENTRIES, ListTag().apply { entries.forEach { add(it.toTag()) } })
        putInt("Radius", radius)
        putInt("MaxActive", maxActive)
        putInt("SpawnsPerCycle", spawnsPerCycle)
        putInt("MinDelay", minDelay)
        putInt("MaxDelay", maxDelay)
        putInt("ActivationRange", activationRange)
        putString("Time", time.name)
        putString("Redstone", redstone.name)
        putBoolean("OnceOnly", onceOnly)
        putBoolean("Uncatchable", uncatchable)
        putBoolean("Persistent", persistent)
        putBoolean("NoAI", noAi)
    }

    companion object {
        /**
         * Version of the NBT layout written by [toTag].
         * - 1: entries were raw properties strings, levels were spawner-wide (no version key written).
         * - 2: structured entries with per-entry levels.
         * - 3: per-entry spawn placement (missing in version 2 = AUTO).
         * Bump it on every field change and extend [fromTag] with a migration.
         */
        const val FORMAT_VERSION = 3

        /** Upper bounds enforced by [sanitize]. Raising them mostly costs server CPU in the spawn scan. */
        const val MAX_ENTRIES = 64
        const val MAX_RADIUS = 32
        const val MAX_ACTIVE = 32
        const val MAX_SPAWNS_PER_CYCLE = 16
        const val MAX_DELAY_SECONDS = 3600
        const val MAX_ACTIVATION_RANGE = 128
        const val MAX_WEIGHT = 10_000
        const val MAX_SPECIES_LENGTH = 64
        const val MAX_EXTRA_LENGTH = 256

        private const val KEY_FORMAT_VERSION = "FormatVersion"
        private const val KEY_ENTRIES = "Entries"

        // Version 1 keys, only read by the migration.
        private const val V1_KEY_PROPERTIES = "Properties"
        private const val V1_KEY_MIN_LEVEL = "MinLevel"
        private const val V1_KEY_MAX_LEVEL = "MaxLevel"

        /**
         * Reads a config written by any known format version, migrating older layouts.
         */
        fun fromTag(tag: CompoundTag): SpawnerConfig = SpawnerConfig().apply {
            // Version 1 never wrote the key, so a missing key means version 1.
            val version = if (tag.contains(KEY_FORMAT_VERSION)) tag.getInt(KEY_FORMAT_VERSION) else 1
            val rawEntries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND.toInt()).filterIsInstance<CompoundTag>()

            entries = if (version == 1) {
                migrateV1Entries(rawEntries, tag)
            } else {
                rawEntries.map { SpawnEntry.fromTag(it) }.toMutableList()
            }

            if (tag.contains("Radius")) radius = tag.getInt("Radius")
            if (tag.contains("MaxActive")) maxActive = tag.getInt("MaxActive")
            if (tag.contains("SpawnsPerCycle")) spawnsPerCycle = tag.getInt("SpawnsPerCycle")
            if (tag.contains("MinDelay")) minDelay = tag.getInt("MinDelay")
            if (tag.contains("MaxDelay")) maxDelay = tag.getInt("MaxDelay")
            if (tag.contains("ActivationRange")) activationRange = tag.getInt("ActivationRange")
            time = runCatching { TimeCondition.valueOf(tag.getString("Time")) }.getOrDefault(TimeCondition.ANY)
            redstone = runCatching { RedstoneMode.valueOf(tag.getString("Redstone")) }.getOrDefault(RedstoneMode.IGNORED)
            onceOnly = tag.getBoolean("OnceOnly")
            uncatchable = tag.getBoolean("Uncatchable")
            persistent = if (tag.contains("Persistent")) tag.getBoolean("Persistent") else true
            noAi = tag.getBoolean("NoAI")
        }

        /**
         * Converts version 1 entries (`weight` + raw properties string, spawner-wide levels)
         * into structured entries. The first bare token becomes the species, everything
         * else is kept as extra properties so no information is lost.
         */
        private fun migrateV1Entries(rawEntries: List<CompoundTag>, root: CompoundTag): MutableList<SpawnEntry> {
            val minLevel = if (root.contains(V1_KEY_MIN_LEVEL)) root.getInt(V1_KEY_MIN_LEVEL) else SpawnEntry.DEFAULT_MIN_LEVEL
            val maxLevel = if (root.contains(V1_KEY_MAX_LEVEL)) root.getInt(V1_KEY_MAX_LEVEL) else SpawnEntry.DEFAULT_MAX_LEVEL

            return rawEntries.map { old ->
                val tokens = old.getString(V1_KEY_PROPERTIES).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val speciesToken = tokens.firstOrNull { '=' !in it } ?: ""
                SpawnEntry(
                    species = speciesToken,
                    weight = old.getInt("Weight").coerceAtLeast(1),
                    minLevel = minLevel,
                    maxLevel = maxLevel,
                    extraProperties = (tokens - speciesToken).joinToString(" ")
                )
            }.toMutableList()
        }
    }
}
