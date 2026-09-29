package dev.darcosse.common.cobblemonspawner

import dev.darcosse.common.cobblemonspawner.event.SpawnerEvents
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Platform-agnostic entry point of Cobblemon Spawner.
 * Both the Fabric and NeoForge entry points call [init] once during mod construction.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
object CobblemonSpawner {

    const val MOD_ID = "cobblemonspawner"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger("CobblemonSpawner")

    /**
     * Builds a [ResourceLocation] inside the mod namespace.
     */
    @JvmStatic
    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    /**
     * Common initialization: subscribes to the Cobblemon events used by the spawner.
     */
    @JvmStatic
    fun init() {
        LOGGER.info("Initializing Cobblemon Spawner...")
        SpawnerEvents.register()
    }
}
