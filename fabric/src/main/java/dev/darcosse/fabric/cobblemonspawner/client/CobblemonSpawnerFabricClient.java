package dev.darcosse.fabric.cobblemonspawner.client;

import dev.darcosse.fabric.cobblemonspawner.network.FabricNetworkHandler;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side entry point for the Fabric platform.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
public class CobblemonSpawnerFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricNetworkHandler.INSTANCE.registerClient();
    }
}
