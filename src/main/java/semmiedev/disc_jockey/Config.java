package semmiedev.disc_jockey;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@me.shedaniel.autoconfig.annotation.Config(name = Main.MOD_ID)
@me.shedaniel.autoconfig.annotation.Config.Gui.Background("textures/block/note_block.png")
public class Config implements ConfigData {
    public boolean hideWarning;
    @ConfigEntry.Gui.Tooltip(count = 2) public boolean disableAsyncPlayback;
    @ConfigEntry.Gui.Tooltip(count = 2) public boolean omnidirectionalNoteBlockSounds = true;

    public enum ExpectedServerVersion {
        All,
        v1_20_4_Or_Earlier,
        v1_20_5_Or_Later;

        @Override
        public String toString() {
            return switch (this) {
                case All -> "All (universal)";
                case v1_20_4_Or_Earlier -> "≤1.20.4";
                case v1_20_5_Or_Later -> "≥1.20.5";
            };
        }
    }

    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    @ConfigEntry.Gui.Tooltip(count = 4)
    public ExpectedServerVersion expectedServerVersion = ExpectedServerVersion.All;

    @ConfigEntry.Gui.Tooltip()
    public float delayPlaybackStartBySecs = 0.0f;

    @ConfigEntry.Gui.Excluded
    public ArrayList<String> favorites = new ArrayList<>();

    @ConfigEntry.Gui.Excluded
    public Map<String, String> songSpeeds = new HashMap<>();
}
