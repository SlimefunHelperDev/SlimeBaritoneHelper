package me.matl114.baritonefirework;

import baritone.process.elytra.ElytraBehavior;
import me.matl114.baritonefirework.modules.BaritoneFirework;
import me.matl114.baritonefirework.modules.BaritoneDirectFly;
import me.matl114.baritonefirework.modules.GlidePoseFix;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.managers.config.Config;
import me.matl114.plugins.SlimefunHelperPlugin;
import me.matl114.versioned.ModVersion;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class BaritoneFireworkPlugin implements SlimefunHelperPlugin {
    public static final String MOD_ID = "slimefunbaritonefirework";
    public static Config CONFIG;

    @Override
    public void onConfigBootstrap() {
        CONFIG = loadRuntimeConfig("baritone_firework.yml", "baritone firework");
        registerConfig(CONFIG);
    }

    @Override
    public void onListenerBootstrap() {}

    @Override
    public void onModuleBootstrap() {
        ModuleGroup group = new ModuleGroup("Baritone");
        group.registerFactories(manager -> {
            // Load the target now so Mixin applies before Baritone first uses it.
            ElytraBehavior.class.getName();
            new BaritoneFirework().register(manager);
            new GlidePoseFix().register(manager);
            new BaritoneDirectFly().register(manager);
        });
        registerModuleGroup(group);
    }

    @Override
    public void postInitialize() {}

    @Override
    public String getPluginId() {
        return MOD_ID;
    }

    @Override
    public ModVersion getMinimumVersion() {
        return ModVersion.parse("1.2.2.1");
    }

    @Override
    public ModContainer getContainer() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Baritone firework plugin container not found"));
    }
}
