package me.matl114.baritonefirework.modules;

import me.matl114.baritonefirework.BaritoneFireworkPlugin;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;

public final class GlidePoseFix extends BaseModule {
    private final ModulePath path = makePath(BaritoneFireworkPlugin.CONFIG, "glide-pose-fix");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();
    public final KeyBindRef hotkey = toggleHotkey(path.addHotkey(), new MultiKeyBind(), path.addEnable())
            .build();

    public GlidePoseFix() {
        super("GlidePoseFix");
        bindFlag(enable);
    }
}
