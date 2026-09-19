package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sends lyric lines as private messages to the players standing near the user, so a song does not
 * have to be broadcast to the whole server.
 * <p>
 * Vanilla throttles chat and commands separately with roughly one message per second (and a
 * disconnect for non operators above that), so private lyrics can either be sent as a single
 * command using a target selector, or one command per player, which then has to be spread over
 * time. {@link Mode#AUTO} tries the selector first and falls back to the round robin when the
 * server refuses selectors.
 */
public final class LyricsDispatch {
    /** Above this many nearby players the private mode is likely to trip the server's spam filter. */
    public static final int WARN_TARGET_COUNT = 15;

    /** How long a selector probe may stay unanswered before it counts as accepted. */
    private static final long SELECTOR_PROBE_MILLIS = 3000;

    private static int cursor;
    private static boolean selectorRefused;
    private static long selectorProbeAt = -1;
    private static boolean warnedAboutSelector;

    private LyricsDispatch() {
    }

    /** Players inside the configured radius, nearest first, capped at the configured maximum. */
    public static List<AbstractClientPlayer> nearbyPlayers() {
        Minecraft client = Minecraft.getInstance();
        List<AbstractClientPlayer> result = new ArrayList<>();
        if (client.player == null || client.level == null) return result;

        int radius = Math.max(1, Math.min(40, Main.config.lyricsDmRadius));
        double radiusSquared = (double) radius * radius;
        for (AbstractClientPlayer other : client.level.players()) {
            if (other == client.player) continue;
            if (client.player.distanceToSqr(other) <= radiusSquared) result.add(other);
        }
        result.sort(Comparator.comparingDouble(client.player::distanceToSqr));

        int maximum = Math.max(1, Math.min(40, Main.config.lyricsDmMaxTargets));
        return result.size() > maximum ? new ArrayList<>(result.subList(0, maximum)) : result;
    }

    public static void send(ClientPacketListener connection, String message) {
        List<AbstractClientPlayer> targets = nearbyPlayers();
        if (targets.isEmpty()) return;

        String command = Main.config.lyricsCommand;
        if (command == null || command.isBlank()) command = "msg";

        if (useSelector()) {
            Minecraft client = Minecraft.getInstance();
            String self = client.player == null ? "" : client.player.getScoreboardName();
            int radius = Math.max(1, Math.min(40, Main.config.lyricsDmRadius));
            String selector = "@a[distance=.." + radius + (self.isEmpty() ? "" : ",name=!" + self) + "]";
            connection.sendCommand(command + " " + selector + " " + message);
            if (!selectorRefused && selectorProbeAt == -1) selectorProbeAt = System.currentTimeMillis();
            return;
        }

        AbstractClientPlayer target = targets.get(Math.floorMod(cursor, targets.size()));
        cursor++;
        connection.sendCommand(command + " " + target.getScoreboardName() + " " + message);
    }

    /**
     * True while target selectors are the preferred way of addressing the players nearby. Selectors
     * need operator permissions on most servers, so the first refusal switches to one command per
     * player for the rest of the session.
     */
    public static boolean useSelector() {
        return Main.config.lyricsUseSelector && !selectorRefused;
    }

    /** True while a selector command is waiting to see whether the server accepts it. */
    public static boolean awaitingSelectorProbe() {
        if (!useSelector() || selectorProbeAt == -1) return false;
        if (System.currentTimeMillis() - selectorProbeAt > SELECTOR_PROBE_MILLIS) {
            // No complaint arrived, so keep using the selector.
            selectorProbeAt = -1;
            return false;
        }
        return true;
    }

    /** Called when the server answered that selectors are not allowed for this player. */
    public static void onSelectorRefused() {
        if (selectorRefused) return;
        selectorRefused = true;
        selectorProbeAt = -1;
        if (warnedAboutSelector) return;
        warnedAboutSelector = true;
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.getChat().addMessage(net.minecraft.network.chat.Component.translatable(
                    Main.MOD_ID + ".lyrics.selector_not_allowed"));
        }
    }

    /** Number of players that would currently receive private lyrics. */
    public static int targetCount() {
        return nearbyPlayers().size();
    }
}
