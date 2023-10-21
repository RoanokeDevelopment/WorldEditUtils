package dev.roanoke.worldeditutils;

import net.fabricmc.api.ModInitializer;

public class WorldEditUtils implements ModInitializer {
    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        new WEUCommands();
    }
}
