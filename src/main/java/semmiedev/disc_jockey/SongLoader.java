package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import semmiedev.disc_jockey.gui.SongListWidget;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class SongLoader {
    public static final ArrayList<Song> SONGS = new ArrayList<>();
    public static final ArrayList<String> SONG_SUGGESTIONS = new ArrayList<>();
    public static final ArrayList<String> DIRECTORIES = new ArrayList<>();
    public static volatile boolean loadingSongs;
    public static volatile boolean showToast;
    public static int reloadVersion;

    public static void loadSongs() {
        if (loadingSongs) return;
        loadingSongs = true;
        Minecraft client = Minecraft.getInstance();
        Thread.startVirtualThread(() -> {
            ArrayList<Song> loadedSongs = new ArrayList<>();
            ArrayList<String> loadedDirectories = new ArrayList<>();
            try {
                Files.walkFileTree(Main.songsFolder.toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                        String relativePath = relativePath(directory);
                        if (!relativePath.isEmpty()) loadedDirectories.add(relativePath);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                        if (!attributes.isRegularFile()) return FileVisitResult.CONTINUE;
                        try {
                            Song song = loadSong(path.toFile(), relativePath(path));
                            if (song != null) loadedSongs.add(song);
                        } catch (Exception exception) {
                            Main.LOGGER.error("Unable to read or parse song {}", path, exception);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException exception) {
                Main.LOGGER.error("Unable to reload songs from {}", Main.songsFolder, exception);
                client.execute(() -> {
                    loadingSongs = false;
                    SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID + ".loading_failed"));
                });
                return;
            }

            client.execute(() -> {
                SONGS.clear();
                SONG_SUGGESTIONS.clear();
                DIRECTORIES.clear();
                loadedDirectories.sort(String::compareTo);
                DIRECTORIES.addAll(loadedDirectories);
                loadedSongs.forEach(SongLoader::addSong);
                sort();
                Main.config.favorites.removeIf(favorite -> SONGS.stream().map(song -> song.relativePath).noneMatch(favorite::equals));
                reloadVersion++;
                loadingSongs = false;
                if (showToast) SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID + ".loading_done"));
                showToast = true;
            });
        });
    }

    public static void addSong(Song song) {
        song.entry = new SongListWidget.SongEntry(song, SONGS.size());
        song.entry.favorite = Main.config.favorites.contains(song.relativePath);
        SONGS.add(song);
        SONG_SUGGESTIONS.add(song.relativePath);
    }

    public static String relativePath(Path path) {
        return Main.songsFolder.toPath().toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
    }

    public static Song loadSong(File file, String relativePath) throws IOException {
        if (!file.isFile()) return null;
        try (var input = Files.newInputStream(file.toPath())) {
            BinaryReader reader = new BinaryReader(input);
            Song song = new Song();

            song.relativePath = relativePath;
            song.fileName = file.getName().replaceAll("[\\n\\r]", "");

            song.length = reader.readShort();

            boolean newFormat = song.length == 0;
            if (newFormat) {
                song.formatVersion = reader.readByte();
                song.vanillaInstrumentCount = reader.readByte();
                song.length = reader.readShort();
            }

            song.height = reader.readShort();
            song.name = reader.readString().replaceAll("[\\n\\r]", "");
            song.author = reader.readString().replaceAll("[\\n\\r]", "");
            song.originalAuthor = reader.readString().replaceAll("[\\n\\r]", "");
            song.description = reader.readString().replaceAll("[\\n\\r]", "");
            song.tempo = reader.readShort();
            song.autoSaving = reader.readByte();
            song.autoSavingDuration = reader.readByte();
            song.timeSignature = reader.readByte();
            song.minutesSpent = reader.readInt();
            song.leftClicks = reader.readInt();
            song.rightClicks = reader.readInt();
            song.blocksAdded = reader.readInt();
            song.blocksRemoved = reader.readInt();
            song.importFileName = reader.readString().replaceAll("[\\n\\r]", "");

            if (newFormat) {
                song.loop = reader.readByte();
                song.maxLoopCount = reader.readByte();
                song.loopStartTick = reader.readShort();
            }

            song.displayName = song.name.replaceAll("\\s", "").isEmpty() ? song.fileName : song.name + " (" + song.fileName + ")";
            song.searchableRelativePath = song.relativePath.toLowerCase(Locale.ROOT).replaceAll("\\s", "");
            song.searchableName = song.name.toLowerCase(Locale.ROOT).replaceAll("\\s", "");

            short tick = -1;
            short jumps;
            while ((jumps = reader.readShort()) != 0) {
                tick += jumps;
                short layer = -1;
                while ((jumps = reader.readShort()) != 0) {
                    layer += jumps;

                    byte instrumentId = reader.readByte();
                    byte noteId = (byte)(reader.readByte() - 33);

                    if (newFormat) {
                        // Data that is not needed as it only works with commands
                        reader.readByte(); // Velocity
                        reader.readByte(); // Panning
                        reader.readShort(); // Pitch
                    }

                    if (noteId < 0) {
                        noteId = 0;
                    } else if (noteId > 24) {
                        noteId = 24;
                    }

                    Note note = new Note(Note.INSTRUMENTS[instrumentId], noteId);
                    if (!song.uniqueNotes.contains(note)) song.uniqueNotes.add(note);

                    song.notes = Arrays.copyOf(song.notes, song.notes.length + 1);
                    song.notes[song.notes.length - 1] = tick | layer << Note.LAYER_SHIFT | (long)instrumentId << Note.INSTRUMENT_SHIFT | (long)noteId << Note.NOTE_SHIFT;
                }
            }

            return song;
        }
    }

    public static void sort() {
        SONGS.sort(Comparator.comparing((Song song) -> song.displayName).thenComparing(song -> song.relativePath));
    }
}
