package semmiedev.disc_jockey;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class Previewer implements ClientTickEvents.StartLevelTick {
    public boolean running;

    private int i;
    private float tick;
    private Song song;

    public void start(Song song) {
        this.song = song;
        Main.TICK_LISTENERS.add(this);
        running = true;
    }

    public void stop() {
        Minecraft.getInstance().submit(() -> Main.TICK_LISTENERS.remove(this));
        running = false;
        i = 0;
        tick = 0;
    }

    @Override
    public void onStartTick(@NonNull ClientLevel world) {
        while (running) {
            long note = song.notes[i];
            if ((short)note <= Math.round(tick)) {
                Vec3 pos = Minecraft.getInstance().player.position();
                world.playLocalSound(pos.x, pos.y, pos.z, Note.INSTRUMENTS[(byte)(note >> Note.INSTRUMENT_SHIFT)].getSoundEvent().value(), SoundSource.RECORDS, 3, (float)Math.pow(2.0, ((byte)(note >> Note.NOTE_SHIFT) - 12) / 12.0), false);
                i++;
                if (i >= song.notes.length) {
                    stop();
                    break;
                }
            } else {
                break;
            }
        }

        if (running) tick += song.tempo / 100f / 20f * Main.SONG_PLAYER.speed;
    }
}
