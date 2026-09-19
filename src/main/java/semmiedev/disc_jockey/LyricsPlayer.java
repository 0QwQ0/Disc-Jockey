package semmiedev.disc_jockey;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the song position and pushes the matching lyric lines out, either to public chat or to the
 * players nearby (see {@link LyricsChat}).
 * <p>
 * The comparison uses the song's own clock, which already advances at the configured playback
 * speed, so at 0.5x the lyrics come out at half the speed without any extra maths here.
 */
public final class LyricsPlayer implements ClientTickEvents.StartLevelTick {
    /** A jump backwards bigger than this counts as the user seeking. */
    private static final long BACKWARD_SEEK_MILLIS = 500;
    /** A jump forward bigger than this counts as seeking or a stall, so lines are skipped silently. */
    private static final long FORWARD_SKIP_MILLIS = 1500;

    private static Song song;
    private static int nextIndex;
    private static long lastSongMillis = -1;

    private LyricsPlayer() {
    }

    public static void register() {
        Main.TICK_LISTENERS.add(new LyricsPlayer());
    }

    @Override
    public void onStartTick(@NonNull ClientLevel level) {
        Song playing = Main.SONG_PLAYER.song;

        if (playing != song) {
            song = playing;
            nextIndex = 0;
            lastSongMillis = -1;
        }

        if (playing == null || playing.lyrics == null) {
            nextIndex = 0;
            lastSongMillis = -1;
            return;
        }

        Lyrics lyrics = playing.lyrics;
        long songMillis = (long) (Main.SONG_PLAYER.getSongElapsedSeconds() * 1000);

        if (lastSongMillis >= 0) {
            long delta = songMillis - lastSongMillis;
            // At high playback speeds a single stalled frame already moves the song clock several
            // seconds ahead, so the limit has to grow with the speed to avoid dropping lines.
            long forwardLimit = (long) (FORWARD_SKIP_MILLIS * Math.max(1.0f, Main.SONG_PLAYER.speed));
            if (delta < -BACKWARD_SEEK_MILLIS || delta > forwardLimit) {
                // Seeked (or the game stalled): jump to the right line without replaying the ones
                // in between, which would otherwise dump a burst of messages into chat.
                nextIndex = lyrics.indexAt(songMillis) + 1;
                lastSongMillis = songMillis;
                return;
            }
        }
        lastSongMillis = songMillis;

        while (nextIndex < lyrics.size() && lyrics.line(nextIndex).timeMs() <= songMillis) {
            if (Main.config.lyricsChatOutput) LyricsChat.send(lyrics.line(nextIndex).text());
            nextIndex++;
        }
    }

    /** The line the song is currently on, or null. Used by the preview in the screen. */
    public static Lyrics.Line currentLine() {
        Song playing = Main.SONG_PLAYER.song;
        if (playing == null || playing.lyrics == null) return null;
        int index = playing.lyrics.indexAt((long) (Main.SONG_PLAYER.getSongElapsedSeconds() * 1000));
        return index < 0 ? null : playing.lyrics.line(index);
    }

    /** The line after the current one, or null when there is none. */
    public static Lyrics.Line followingLine() {
        Song playing = Main.SONG_PLAYER.song;
        if (playing == null || playing.lyrics == null) return null;
        int index = playing.lyrics.indexAt((long) (Main.SONG_PLAYER.getSongElapsedSeconds() * 1000)) + 1;
        return index >= playing.lyrics.size() ? null : playing.lyrics.line(index);
    }

    /** True when the song that is currently loaded has lyrics next to it. */
    public static boolean hasLyrics() {
        Song playing = Main.SONG_PLAYER.song;
        return playing != null && playing.lyrics != null;
    }

    /**
     * The lines the preview shows: the line the song is on plus the next one, or the first two
     * lines while the song is still before its first lyric.
     */
    public static List<Lyrics.Line> previewLines() {
        Song playing = Main.SONG_PLAYER.song;
        if (playing == null || playing.lyrics == null) return List.of();

        Lyrics lyrics = playing.lyrics;
        int index = lyrics.indexAt((long) (Main.SONG_PLAYER.getSongElapsedSeconds() * 1000));
        int first = Math.max(0, index);
        List<Lyrics.Line> lines = new ArrayList<>(2);
        if (first < lyrics.size()) lines.add(lyrics.line(first));
        if (first + 1 < lyrics.size()) lines.add(lyrics.line(first + 1));
        return lines;
    }
}
