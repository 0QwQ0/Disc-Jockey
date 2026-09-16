package semmiedev.disc_jockey.gui.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import semmiedev.disc_jockey.*;
import semmiedev.disc_jockey.gui.SongListWidget;
import semmiedev.disc_jockey.gui.SongTimeSliderWidget;
import semmiedev.disc_jockey.gui.hud.BlocksOverlay;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiscJockeyScreen extends Screen {
    private static final MutableComponent
            SELECT_SONG = Component.translatable(Main.MOD_ID + ".screen.select_song"),
            PLAY = Component.translatable(Main.MOD_ID + ".screen.play"),
            PLAY_STOP = Component.translatable(Main.MOD_ID + ".screen.play.stop"),
            PREVIEW = Component.translatable(Main.MOD_ID + ".screen.preview"),
            PREVIEW_STOP = Component.translatable(Main.MOD_ID + ".screen.preview.stop"),
            REFRESH_SONGS = Component.translatable(Main.MOD_ID + ".screen.refresh_songs"),
            LOADING_SONGS = Component.translatable(Main.MOD_ID + ".screen.loading_songs"),
            DROP_HINT = Component.translatable(Main.MOD_ID + ".screen.drop_hint").copy().withStyle(ChatFormatting.GRAY),
            SONG_STATE_PLAYING = Component.translatable(Main.MOD_ID + ".screen.songstate.playing").withStyle(style -> style.withItalic(true).withColor(0xDDDDDD)),
            SONG_STATE_PAUSED = Component.translatable(Main.MOD_ID + ".screen.songstate.paused").withStyle(style -> style.withItalic(true).withColor(0xDDDDDD)),
            SONG_STATE_FINISHED = Component.translatable(Main.MOD_ID + ".screen.songstate.finished").withStyle(style -> style.withItalic(true).withColor(0xDDDDDD)),
            SONG_STATE_STOPPED = Component.translatable(Main.MOD_ID + ".screen.songstate.stopped").withStyle(style -> style.withItalic(true).withColor(0xDDDDDD)),
            SONG_STATE_TUNING = Component.translatable(Main.MOD_ID + ".screen.songstate.tuning").withStyle(style -> style.withItalic(true).withColor(0xDDDDDD)),
            PLEASE_SELECT_SONG = Component.translatable(Main.MOD_ID + ".screen.please_select_song").withStyle(style -> style.withItalic(true)),
            CONFIG = Component.translatable(Main.MOD_ID + ".screen.config")
    ;
    private static final Component PLAYBACK_SPEED = Component.translatable(Main.MOD_ID + ".screen.playback_speed");
    private static final SystemToast.SystemToastId INVALID_SPEED_TOAST = new SystemToast.SystemToastId();

    private StringWidget songTitle;
    private StringWidget songState;
    private CycleButton<Boolean> playPauseButton;
    private SongTimeSliderWidget timeBar;
    private EditBox speedInput;

    private SongListWidget songListWidget;
    private Button playButton, previewButton, refreshButton;
    private boolean shouldFilter;
    private String query = "";
    private int lastLoadedSongCount;
    private int lastReloadVersion;

    public DiscJockeyScreen() {
        super(Main.NAME);
    }

    @Override
    protected void init() {
        shouldFilter = true;

        songListWidget = new SongListWidget(minecraft, width / 2 - 10, height - 64 - 32, 32, 20);
        songListWidget.setX(width / 2);
        addRenderableWidget(songListWidget);

        List<SongListWidget.SongEntry> entries = new java.util.ArrayList<>();
        if (!SongLoader.loadingSongs) {
            lastReloadVersion = SongLoader.reloadVersion;
            for (Song song : SongLoader.SONGS) {
                song.entry.songListWidget = songListWidget;
                if (song.entry.selected) songListWidget.setSelected(song.entry);
                entries.add(song.entry);
            }
        }
        songListWidget.replaceEntries(entries);

        // Right panel buttons layout - dynamically centered
        int rightCenter = width / 2 + (width / 2 - 10) / 2;
        int btnY = height - 61;
        int btnW = Math.min(100, (width / 2 - 10 - 20) / 3);
        int gap = Math.min(10, (width / 2 - 10 - btnW * 3) / 2);
        int btnStart = rightCenter - (btnW * 3 + gap * 2) / 2;

        playButton = Button.builder(PLAY, _ -> {
            if (Main.SONG_PLAYER.running) {
                Main.SONG_PLAYER.stop();
            } else {
                SongListWidget.SongEntry entry = songListWidget.getSelected();
                if (entry != null) {
                    Main.SONG_PLAYER.start(entry.song);
                }
            }
        }).bounds(btnStart, btnY, btnW, 20).build();
        addRenderableWidget(playButton);

        previewButton = Button.builder(PREVIEW, _ -> {
            if (Main.PREVIEWER.running) {
                Main.PREVIEWER.stop();
            } else {
                SongListWidget.SongEntry entry = songListWidget.getSelected();
                if (entry != null) Main.PREVIEWER.start(entry.song);
            }
        }).bounds(btnStart + btnW + gap, btnY, btnW, 20).build();
        addRenderableWidget(previewButton);

        addRenderableWidget(Button.builder(Component.translatable(Main.MOD_ID + ".screen.blocks"), _ -> {
                SongListWidget.SongEntry entry = songListWidget.getSelected();
                if (entry == null) return;

                // Same song -> close overlay
                // different/not shown -> show/update
                if (BlocksOverlay.itemStacks != null && entry.song.fileName.equals(BlocksOverlay.songFileName)) {
                    BlocksOverlay.itemStacks = null;
                    return;
                }

                minecraft.gui.setScreen(null);

                BlocksOverlay.songFileName = entry.song.fileName;
                BlocksOverlay.amountOfNoteBlocks = entry.song.uniqueNotes.size();
                BlocksOverlay.itemStacks = new ItemStack[0];
                BlocksOverlay.amounts = new int[0];

                for (Note note : entry.song.uniqueNotes) {
                    ItemStack itemStack = Note.INSTRUMENT_BLOCKS.get(note.instrument()).asItem().getDefaultInstance();
                    int index = -1;

                    for (int i = 0; i < BlocksOverlay.itemStacks.length; i++) {
                        if (BlocksOverlay.itemStacks[i].getItem() == itemStack.getItem()) {
                            index = i;
                            break;
                        }
                    }

                    if (index == -1) {
                        BlocksOverlay.itemStacks = Arrays.copyOf(BlocksOverlay.itemStacks, BlocksOverlay.itemStacks.length + 1);
                        BlocksOverlay.amounts = Arrays.copyOf(BlocksOverlay.amounts, BlocksOverlay.amounts.length + 1);

                        BlocksOverlay.itemStacks[BlocksOverlay.itemStacks.length - 1] = itemStack;
                        BlocksOverlay.amounts[BlocksOverlay.amounts.length - 1] = 1;
                    } else {
                        BlocksOverlay.amounts[index] = BlocksOverlay.amounts[index] + 1;
                    }
                }
        }).bounds(btnStart + (btnW + gap) * 2, btnY, btnW, 20).build());

        int searchW = Math.min(150, width / 2 - 30);
        EditBox searchBar = new EditBox(font, rightCenter - searchW / 2, height - 31, searchW, 20, Component.translatable(Main.MOD_ID + ".screen.search"));
        searchBar.setResponder(query -> {
            query = query.toLowerCase().replaceAll("\\s", "");
            if (this.query.equals(query)) return;
            this.query = query;
            shouldFilter = true;
        });
        addRenderableWidget(searchBar);

        int leftX = 10;
        int leftWidth = width / 2 - 20;
        int topY = 32;

        songState = new StringWidget(leftX, topY, leftWidth, 20, Component.empty(), font);
        addRenderableWidget(songState);

        songTitle = new StringWidget(leftX, topY + 20, leftWidth, 20, Component.empty(), font);
        addRenderableWidget(songTitle);

        timeBar = new SongTimeSliderWidget(leftX, topY + 40, leftWidth, 25);
        addRenderableWidget(timeBar);

        int controlsY = topY + 40 + 25 + 5;
        playPauseButton = CycleButton.builder(
                (value) -> Component.literal(value ? "⏸" : "▶"),
                Main.SONG_PLAYER.running
            )
            .displayOnlyValue()
            .withValues(true, false)
            .create((width / 4) - 25, controlsY, 20, 20, Component.empty(), (_, value) -> {
                if (value && Main.SONG_PLAYER.song != null && Main.SONG_PLAYER.didSongReachEnd) {
                    Main.SONG_PLAYER.start(Main.SONG_PLAYER.song);
                } else {
                    Main.SONG_PLAYER.running = value;
                }
            });
        addRenderableWidget(playPauseButton);

        Button stopButton = Button.builder(Component.literal("⏹"), _ -> Main.SONG_PLAYER.stop())
                .pos((width / 4) + 5, controlsY)
                .size(20, 20)
                .build();
        addRenderableWidget(stopButton);

        refreshButton = Button.builder(REFRESH_SONGS, _ -> {
            SongLoader.loadSongs();
            updateLoadingState();
        }).pos(10, height - 80).size(100, 20).build();
        addRenderableWidget(refreshButton);
        updateLoadingState();

        addRenderableWidget(Button.builder(Component.translatable(Main.MOD_ID + ".screen.open_folder"), _ ->
                Util.getPlatform().openPath(Main.songsFolder.toPath())
        ).pos(10, height - 55).size(100, 20).build());

        int speedY = height - 30;
        int labelWidth = font.width(PLAYBACK_SPEED);
        addRenderableWidget(Button.builder(CONFIG, _ ->
                minecraft.gui.setScreen(me.shedaniel.autoconfig.AutoConfigClient.getConfigScreen(Config.class, this).get())
        ).pos(leftX, speedY).size(100, 20).build());

        int speedX = leftX + 100 + 6;
        addRenderableOnly(new StringWidget(speedX, speedY, labelWidth, 20, PLAYBACK_SPEED, font));
        speedInput = new EditBox(font, speedX + labelWidth + 2, speedY, 36, 20, PLAYBACK_SPEED) {
            @Override
            public void setFocused(boolean focused) {
                boolean lostFocus = isFocused() && !focused;
                super.setFocused(focused);
                if (lostFocus) applyPlaybackSpeed();
            }
        };
        speedInput.setValue(formatPlaybackSpeed());
        addRenderableWidget(speedInput);
        addRenderableOnly(new StringWidget(speedInput.getX() + speedInput.getWidth() + 2, speedY, font.width("x"), 20, Component.literal("x"), font));
    }

    private void applyPlaybackSpeed() {
        String value = speedInput.getValue().trim();
        boolean valid = value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
        if (valid) {
            float speed = Float.parseFloat(value);
            valid = speed >= 0.0001f && speed <= 15.0f;
            if (valid) Main.SONG_PLAYER.speed = speed;
        }
        if (!valid) {
            SystemToast.add(minecraft.gui.toastManager(), INVALID_SPEED_TOAST, PLAYBACK_SPEED,
                    Component.translatable(Main.MOD_ID + ".screen.invalid_playback_speed"));
        }
        speedInput.setValue(formatPlaybackSpeed());
    }

    private static String formatPlaybackSpeed() {
        return new BigDecimal(Float.toString(Main.SONG_PLAYER.speed)).stripTrailingZeros().toPlainString();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (speedInput.isFocused() && !speedInput.isMouseOver(event.x(), event.y())) {
            setFocused(null);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        clearFocus();
        super.removed();
    }

    private static Component getPlaybackStateText() {
        boolean running = Main.SONG_PLAYER.running;
        boolean tuned = Main.SONG_PLAYER.tuned;
        boolean didSongReachEnd = Main.SONG_PLAYER.didSongReachEnd;

        if (!running) {
            if (didSongReachEnd) {
                return SONG_STATE_FINISHED;
            } else if (Main.SONG_PLAYER.getSongElapsedSeconds() == 0.0) {
                return SONG_STATE_STOPPED;
            } else {
                return SONG_STATE_PAUSED;
            }
        } else {
            if (!tuned) {
                return SONG_STATE_TUNING;
            } else {
                return SONG_STATE_PLAYING;
            }
        }
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(5, 32, width / 2, 32 + 20 + 20 + 30 + 5 + 20 + 5, 0x3F000000);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int rightCenter = width / 2 + (width / 2 - 10) / 2;
        context.centeredText(font, DROP_HINT, width / 2, 5, 0xFFFFFFFF);
        context.centeredText(font, SELECT_SONG, rightCenter, 20, 0xFFFFFFFF);
        if (SongLoader.loadingSongs) {
            context.centeredText(font, LOADING_SONGS, rightCenter, 32 + (height - 96) / 2, 0xFFFFFFFF);
        }
    }

    private void updateLoadingState() {
        boolean loading = SongLoader.loadingSongs;
        refreshButton.active = !loading;
        refreshButton.setMessage(loading ? LOADING_SONGS : REFRESH_SONGS);
        songListWidget.visible = !loading;
        songListWidget.active = !loading;
        if (loading) songListWidget.setSelected(null);
        playButton.active = !loading || Main.SONG_PLAYER.running;
        previewButton.active = !loading || Main.PREVIEWER.running;
    }

    @Override
    public void tick() {
        songState.setMessage(getPlaybackStateText());
        timeBar.update();
        playPauseButton.setValue(Main.SONG_PLAYER.running);
        songTitle.setMessage(Main.SONG_PLAYER.song != null ? Component.literal(Main.SONG_PLAYER.song.displayName) : PLEASE_SELECT_SONG);

        previewButton.setMessage(Main.PREVIEWER.running ? PREVIEW_STOP : PREVIEW);
        playButton.setMessage(Main.SONG_PLAYER.running ? PLAY_STOP : PLAY);

        updateLoadingState();
        if (SongLoader.loadingSongs) return;

        if (SongLoader.reloadVersion != lastReloadVersion) {
            lastReloadVersion = SongLoader.reloadVersion;
            songListWidget.setSelected(null);
            shouldFilter = true;
        }

        if (SongLoader.SONGS.size() != lastLoadedSongCount) {
            lastLoadedSongCount = SongLoader.SONGS.size();
            shouldFilter = true;
        }

        if (shouldFilter) {
            shouldFilter = false;
            songListWidget.setScrollAmount(0);
            List<SongListWidget.SongEntry> newEntries = new java.util.ArrayList<>();
            boolean empty = query.isEmpty();
            int favoriteIndex = 0;
            for (Song song : SongLoader.SONGS) {
                if (empty || song.searchableFileName.contains(query) || song.searchableName.contains(query)) {
                    song.entry.songListWidget = songListWidget;
                    if (song.entry.favorite) {
                        newEntries.add(favoriteIndex++, song.entry);
                    } else {
                        newEntries.add(song.entry);
                    }
                }
            }
            songListWidget.replaceEntries(newEntries);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (SongLoader.loadingSongs) {
            SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID + ".still_loading"));
            return;
        }
        String string = paths.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        if (string.length() > 300) string = string.substring(0, 300) + "...";

        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                paths.forEach(path -> {
                    try {
                        File file = path.toFile();

                        if (SongLoader.SONGS.stream().anyMatch(input -> input.fileName.equalsIgnoreCase(file.getName()))) return;

                        Song song = SongLoader.loadSong(file);
                        if (song != null) {
                            Files.copy(path, Main.songsFolder.toPath().resolve(file.getName()));
                            SongLoader.addSong(song);
                        }
                    } catch (IOException exception) {
                        Main.LOGGER.warn("Failed to copy song file from {} to {}", path, Main.songsFolder.toPath(), exception);
                    }
                });

                SongLoader.sort();
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable(Main.MOD_ID + ".screen.drop_confirm"), Component.literal(string)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        Thread.startVirtualThread(() -> Main.configHolder.save());
    }
}
