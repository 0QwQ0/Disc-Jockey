package semmiedev.disc_jockey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed .lrc file: timestamped lines sorted by time.
 * <p>
 * Times are in milliseconds and are compared against the song position, which runs on the song's
 * own clock (see {@link SongPlayer#getSongElapsedSeconds()}). Because that clock advances at the
 * playback speed, lyrics automatically follow the speed setting without any extra scaling.
 */
public final class Lyrics {
    public record Line(long timeMs, String text) {
    }

    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern METADATA = Pattern.compile("\\[(ti|ar|al|by|au|re|ve|length):[^]]*]", Pattern.CASE_INSENSITIVE);

    private final List<Line> lines;

    private Lyrics(List<Line> lines) {
        this.lines = lines;
    }

    /** @return the parsed lyrics, or null when the file holds no usable lines. */
    public static Lyrics parse(Path file) throws IOException {
        String content = readText(file);

        long offset = 0;
        Matcher offsetMatcher = OFFSET.matcher(content);
        if (offsetMatcher.find()) {
            try {
                offset = Long.parseLong(offsetMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // Malformed offset, just ignore it.
            }
        }

        List<Line> parsed = new ArrayList<>();
        for (String rawLine : content.split("\\r?\\n")) {
            String line = METADATA.matcher(rawLine).replaceAll("");
            Matcher matcher = TIMESTAMP.matcher(line);
            // A line may carry several timestamps for the same text, so strip them all first.
            String text = WORD_TIMESTAMP.matcher(matcher.replaceAll("")).replaceAll("").trim();
            if (text.isEmpty()) continue;
            matcher.reset();
            while (matcher.find()) {
                parsed.add(new Line(Math.max(0, timestamp(matcher) + offset), text));
            }
        }
        if (parsed.isEmpty()) return null;
        parsed.sort(Comparator.comparingLong(Line::timeMs));
        return new Lyrics(parsed);
    }

    private static long timestamp(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0;
        if (fraction != null && !fraction.isEmpty()) {
            millis = switch (fraction.length()) {
                case 1 -> Long.parseLong(fraction) * 100;   // [mm:ss.f] tenths
                case 2 -> Long.parseLong(fraction) * 10;    // [mm:ss.ff] hundredths
                default -> Long.parseLong(fraction.substring(0, 3)); // [mm:ss.fff] milliseconds
            };
        }
        return minutes * 60_000L + seconds * 1_000L + millis;
    }

    private static String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                    .toString();
        } catch (CharacterCodingException exception) {
            // A lot of lyrics files in the wild are still GBK encoded.
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    public int size() {
        return lines.size();
    }

    public Line line(int index) {
        return lines.get(index);
    }

    /** Index of the last line at or before the given song time, or -1 before the first line. */
    public int indexAt(long songMs) {
        int low = 0;
        int high = lines.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs() <= songMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }
}
