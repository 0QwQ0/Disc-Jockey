package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import semmiedev.disc_jockey.gui.SongListWidget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class SongLoader {
    public static final ArrayList<Song> SONGS = new ArrayList<>();
    public static final ArrayList<String> SONG_SUGGESTIONS = new ArrayList<>();
    public static volatile boolean loadingSongs;
    public static volatile boolean showToast;
    public static int reloadVersion;

    public static void loadSongs() {
        if (loadingSongs) return;
        loadingSongs = true;
        Minecraft client = Minecraft.getInstance();
        Thread.startVirtualThread(() -> {
            ArrayList<Song> loadedSongs = new ArrayList<>();
            try (var files = Files.newDirectoryStream(Main.songsFolder.toPath())) {
                for (var path : files) {
                    try {
                        Song song = loadSong(path.toFile());
                        if (song != null) loadedSongs.add(song);
                    } catch (Exception exception) {
                        Main.LOGGER.error("Unable to read or parse song {}", path.getFileName(), exception);
                    }
                }
            } catch (IOException | java.nio.file.DirectoryIteratorException exception) {
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
                loadedSongs.forEach(SongLoader::addSong);
                Main.config.favorites.removeIf(favorite -> SONGS.stream().map(song -> song.fileName).noneMatch(favorite::equals));
                reloadVersion++;
                loadingSongs = false;
                if (showToast) SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID + ".loading_done"));
                showToast = true;
            });
        });
    }

    public static void addSong(Song song) {
        song.entry = new SongListWidget.SongEntry(song, SONGS.size());
        song.entry.favorite = Main.config.favorites.contains(song.fileName);
        SONGS.add(song);
        SONG_SUGGESTIONS.add(song.displayName);
    }

    public static Song loadSong(File file) throws IOException {
        if (!file.isFile()) return null;
        try (var input = Files.newInputStream(file.toPath())) {
            BinaryReader reader = new BinaryReader(input);
            Song song = new Song();

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
            song.searchableFileName = song.fileName.toLowerCase().replaceAll("\\s", "");
            song.searchableName = song.name.toLowerCase().replaceAll("\\s", "");

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
        SONGS.sort(Comparator.comparing(song -> song.displayName));
    }
}
