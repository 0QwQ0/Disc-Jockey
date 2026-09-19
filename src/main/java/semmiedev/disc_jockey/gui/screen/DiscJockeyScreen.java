package semmiedev.disc_jockey.gui.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import semmiedev.disc_jockey.*;
import semmiedev.disc_jockey.gui.PlaylistWidget;
import semmiedev.disc_jockey.gui.SongListWidget;
import semmiedev.disc_jockey.gui.SongTimeSliderWidget;
import semmiedev.disc_jockey.gui.hud.BlocksOverlay;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private CycleButton<Boolean> shuffleButton;
    private CycleButton<Config.RepeatMode> repeatButton;
    private SongTimeSliderWidget timeBar;
    private EditBox speedInput;
    private float displayedSpeed;
    private boolean displayedShuffle;
    private Config.RepeatMode displayedRepeatMode;

    private SongListWidget songListWidget;
    private PlaylistWidget playlistWidget;
    private Checkbox lyricsCheckbox, lyricsPublicCheckbox;
    private int lyricsRowY;
    private boolean showLyricsPreview;
    private Button playButton, previewButton, blocksButton, refreshButton, parentDirectoryButton;
    private Button previousButton, nextButton;
    private StringWidget directoryLabel;
    private String currentDirectory = "";
    private boolean shouldFilter;
    private String query = "";
    private int lastLoadedSongCount;
    private int lastReloadVersion;

    public DiscJockeyScreen() {
        super(Main.NAME);
    }

    @Override
    protected void init() {
        songListWidget = new SongListWidget(minecraft, width / 2 - 10, height - 64 - 56, 56, 20);
        songListWidget.setX(width / 2);
        addRenderableWidget(songListWidget);

        if (!SongLoader.loadingSongs) {
            lastReloadVersion = SongLoader.reloadVersion;
            if (!currentDirectory.isEmpty() && !SongLoader.DIRECTORIES.contains(currentDirectory)) currentDirectory = "";
            for (Song song : SongLoader.SONGS) {
                song.entry.songListWidget = songListWidget;
                if (song.entry.selected) songListWidget.setSelected(song.entry);
            }
        }
        refreshSongEntries();

        // Right panel buttons layout - dynamically centered
        int rightCenter = width / 2 + (width / 2 - 10) / 2;
        parentDirectoryButton = Button.builder(Component.translatable(Main.MOD_ID + ".screen.parent_directory"), _ -> {
            int separator = currentDirectory.lastIndexOf('/');
            changeDirectory(separator < 0 ? "" : currentDirectory.substring(0, separator));
        }).bounds(width / 2, 32, 60, 20).build();
        addRenderableWidget(parentDirectoryButton);
        directoryLabel = new StringWidget(width / 2 + 64, 32, width / 2 - 74, 20, Component.empty(), font);
        addRenderableWidget(directoryLabel);
        updateDirectoryHeader();

        int btnY = height - 61;
        int btnW = Math.min(100, (width / 2 - 10 - 20) / 3);
        int gap = Math.min(10, (width / 2 - 10 - btnW * 3) / 2);
        int btnStart = rightCenter - (btnW * 3 + gap * 2) / 2;

        playButton = Button.builder(PLAY, _ -> {
            if (Main.SONG_PLAYER.running) {
                PlaylistManager.stop();
            } else {
                Song selected = getSelectedSong();
                if (selected != null) {
                    // Playing a playlist row keeps the playlist context, playing straight from
                    // the song list plays that one song.
                    if (playlistWidget.getSelectedEntry() != null) {
                        PlaylistManager.play(selected);
                    } else {
                        PlaylistManager.playOneShot(selected);
                    }
                }
            }
        }).bounds(btnStart, btnY, btnW, 20).build();
        addRenderableWidget(playButton);

        previewButton = Button.builder(PREVIEW, _ -> {
            if (Main.PREVIEWER.running) {
                Main.PREVIEWER.stop();
            } else {
                Song selected = getSelectedSong();
                if (selected != null) Main.PREVIEWER.start(selected);
            }
        }).bounds(btnStart + btnW + gap, btnY, btnW, 20).build();
        addRenderableWidget(previewButton);

        blocksButton = Button.builder(Component.translatable(Main.MOD_ID + ".screen.blocks"), _ -> {
                Song selected = getSelectedSong();
                if (selected == null) return;

                // Same song -> close overlay
                // different/not shown -> show/update
                if (BlocksOverlay.itemStacks != null && selected.relativePath.equals(BlocksOverlay.songRelativePath)) {
                    BlocksOverlay.itemStacks = null;
                    return;
                }

                minecraft.gui.setScreen(null);

                BlocksOverlay.songRelativePath = selected.relativePath;
                BlocksOverlay.amountOfNoteBlocks = selected.uniqueNotes.size();
                BlocksOverlay.itemStacks = new ItemStack[0];
                BlocksOverlay.amounts = new int[0];

                for (Note note : selected.uniqueNotes) {
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
        }).bounds(btnStart + (btnW + gap) * 2, btnY, btnW, 20).build();
        addRenderableWidget(blocksButton);

        int searchW = Math.min(150, width / 2 - 30);
        EditBox searchBar = new EditBox(font, rightCenter - searchW / 2, height - 31, searchW, 20, Component.translatable(Main.MOD_ID + ".screen.search"));
        searchBar.setValue(query);
        searchBar.setResponder(query -> {
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
        songTitle.setMaxWidth(leftWidth);
        addRenderableWidget(songTitle);

        timeBar = new SongTimeSliderWidget(leftX, topY + 40, leftWidth, 25);
        addRenderableWidget(timeBar);

        // Control row: previous, play/pause, stop, next, order mode, repeat mode.
        int controlsY = topY + 78;
        int transportSize = 20;
        int modeWidth = 24;
        int fixedWidth = transportSize * 4 + modeWidth * 2;
        int controlGap = Math.max(1, Math.min(5, (leftWidth - fixedWidth) / 5));
        int controlsWidth = fixedWidth + controlGap * 5;
        int controlX = leftX + Math.max(0, (leftWidth - controlsWidth) / 2);

        previousButton = Button.builder(Component.literal("⏮"), _ -> PlaylistManager.skip(-1))
                .pos(controlX, controlsY).size(transportSize, transportSize).build();
        previousButton.setTooltip(Tooltip.create(Component.translatable(Main.MOD_ID + ".screen.previous")));
        addRenderableWidget(previousButton);

        playPauseButton = CycleButton.builder(
                (value) -> Component.literal(value ? "⏸" : "▶"),
                Main.SONG_PLAYER.running
            )
            .displayOnlyValue()
            .withValues(true, false)
            .create(controlX + transportSize + controlGap, controlsY, transportSize, transportSize, Component.empty(), (_, value) -> {
                if (value && Main.SONG_PLAYER.song != null && Main.SONG_PLAYER.didSongReachEnd) {
                    Main.SONG_PLAYER.start(Main.SONG_PLAYER.song);
                } else {
                    Main.SONG_PLAYER.running = value;
                }
            });
        addRenderableWidget(playPauseButton);

        Button stopButton = Button.builder(Component.literal("⏹"), _ -> PlaylistManager.stop())
                .pos(controlX + (transportSize + controlGap) * 2, controlsY)
                .size(transportSize, transportSize)
                .build();
        addRenderableWidget(stopButton);

        nextButton = Button.builder(Component.literal("⏭"), _ -> PlaylistManager.skip(1))
                .pos(controlX + (transportSize + controlGap) * 3, controlsY).size(transportSize, transportSize).build();
        nextButton.setTooltip(Tooltip.create(Component.translatable(Main.MOD_ID + ".screen.next")));
        addRenderableWidget(nextButton);

        shuffleButton = CycleButton.builder(
                (value) -> Component.translatable(Main.MOD_ID + (value ? ".screen.shuffle.on" : ".screen.shuffle.off")),
                PlaylistManager.shuffle()
            )
            .displayOnlyValue()
            .withValues(true, false)
            .create(controlX + (transportSize + controlGap) * 4, controlsY, modeWidth, transportSize, Component.empty(),
                    (_, value) -> PlaylistManager.setShuffle(value));
        shuffleButton.setTooltip(Tooltip.create(Component.translatable(
                Main.MOD_ID + (PlaylistManager.shuffle() ? ".screen.shuffle.on.tooltip" : ".screen.shuffle.off.tooltip"))));
        addRenderableWidget(shuffleButton);

        repeatButton = CycleButton.builder(
                (value) -> Component.translatable(Main.MOD_ID + ".screen.repeat." + value.name().toLowerCase(Locale.ROOT)),
                PlaylistManager.mode()
            )
            .displayOnlyValue()
            .withValues(Config.RepeatMode.SEQUENTIAL, Config.RepeatMode.PLAYLIST, Config.RepeatMode.SINGLE)
            .create(controlX + (transportSize + controlGap) * 4 + modeWidth + controlGap, controlsY, modeWidth, transportSize,
                    Component.empty(), (_, value) -> PlaylistManager.setMode(value));
        repeatButton.setTooltip(Tooltip.create(Component.translatable(
                Main.MOD_ID + ".screen.repeat." + PlaylistManager.mode().name().toLowerCase(Locale.ROOT) + ".tooltip")));
        addRenderableWidget(repeatButton);

        // Lyrics switches: the master switch and whether the output goes to public chat.
        lyricsRowY = controlsY + transportSize + 2;
        lyricsCheckbox = Checkbox.builder(Component.translatable(Main.MOD_ID + ".screen.lyrics"), font)
                .pos(leftX, lyricsRowY)
                .selected(Main.config.lyricsChatOutput)
                .tooltip(Tooltip.create(Component.translatable(Main.MOD_ID + ".screen.lyrics.tooltip")))
                .onValueChange((_, value) -> {
                    Main.config.lyricsChatOutput = value;
                    Main.configHolder.save();
                })
                .build();
        addRenderableWidget(lyricsCheckbox);

        int publicCheckboxX = leftX + Checkbox.getBoxSize(font) + 4
                + font.width(Component.translatable(Main.MOD_ID + ".screen.lyrics")) + 10;
        lyricsPublicCheckbox = Checkbox.builder(Component.translatable(Main.MOD_ID + ".screen.lyrics.public"), font)
                .pos(publicCheckboxX, lyricsRowY)
                .selected(Main.config.lyricsOutputToPublic)
                .tooltip(Tooltip.create(Component.translatable(Main.MOD_ID + ".screen.lyrics.public.tooltip")))
                .onValueChange((_, value) -> onLyricsOutputChanged(value))
                .build();
        addRenderableWidget(lyricsPublicCheckbox);

        // Below a certain height the left column cannot fit three bottom rows plus the playlist, so
        // the lyrics preview is dropped there and the layout stays as it was before.
        showLyricsPreview = height >= 300;

        // Playlist panel, mirroring the vertical extent of the song list on the right.
        int playlistBottom = showLyricsPreview ? height - 94 : height - 64;
        int playlistTop = lyricsRowY + 34;
        int playlistHeight = Math.max(20, playlistBottom - playlistTop);
        playlistWidget = new PlaylistWidget(minecraft, leftWidth, playlistHeight, playlistTop, 20);
        playlistWidget.setX(leftX);
        playlistWidget.onSelectionChanged = () -> {
            if (playlistWidget.getSelectedEntry() != null) songListWidget.setSelected(null);
        };
        addRenderableWidget(playlistWidget);
        playlistWidget.refresh();
        songListWidget.onSelectionChanged = () -> {
            if (songListWidget.getSelectedSongEntry() != null) playlistWidget.setSelected(null);
        };

        int bottomY = showLyricsPreview ? height - 88 : height - 61;
        int utilityWidth = Math.min(100, (leftWidth - 6) / 2);
        refreshButton = Button.builder(REFRESH_SONGS, _ -> {
            SongLoader.loadSongs();
            updateLoadingState();
        }).pos(leftX, bottomY).size(utilityWidth, 20).build();
        addRenderableWidget(refreshButton);
        updateLoadingState();

        addRenderableWidget(Button.builder(Component.translatable(Main.MOD_ID + ".screen.open_folder"), _ ->
                Util.getPlatform().openPath(Main.songsFolder.toPath())
        ).pos(leftX + utilityWidth + 6, bottomY).size(utilityWidth, 20).build());

        int speedY = showLyricsPreview ? height - 62 : height - 31;
        int labelWidth = font.width(PLAYBACK_SPEED);
        int configWidth = Math.max(50, Math.min(80, leftWidth / 3));
        addRenderableWidget(Button.builder(CONFIG, _ ->
                minecraft.gui.setScreen(me.shedaniel.autoconfig.AutoConfigClient.getConfigScreen(Config.class, this).get())
        ).pos(leftX, speedY).size(configWidth, 20).build());

        int speedX = leftX + configWidth + 6;
        addRenderableOnly(new StringWidget(speedX, speedY, labelWidth, 20, PLAYBACK_SPEED, font));
        speedInput = new EditBox(font, speedX + labelWidth + 2, speedY, 36, 20, PLAYBACK_SPEED) {
            @Override
            public void setFocused(boolean focused) {
                boolean lostFocus = isFocused() && !focused;
                super.setFocused(focused);
                if (lostFocus) applyPlaybackSpeed();
            }
        };
        updatePlaybackSpeedInput();
        addRenderableWidget(speedInput);
        addRenderableOnly(new StringWidget(speedInput.getX() + speedInput.getWidth() + 2, speedY, font.width("x"), 20, Component.literal("x"), font));
    }

    /**
     * Switching the lyrics away from public chat can flood the server with private messages, so the
     * user is asked to confirm first when there are a lot of players in range.
     */
    private void onLyricsOutputChanged(boolean publicChat) {
        if (publicChat) {
            Main.config.lyricsOutputToPublic = true;
            Main.configHolder.save();
            return;
        }
        int targets = LyricsDispatch.targetCount();
        if (targets <= LyricsDispatch.WARN_TARGET_COUNT) {
            Main.config.lyricsOutputToPublic = false;
            Main.configHolder.save();
            return;
        }
        // Leave the checkbox checked until the user confirms, so cancelling keeps public chat.
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                Main.config.lyricsOutputToPublic = false;
                Main.configHolder.save();
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable(Main.MOD_ID + ".screen.lyrics.warning"), LyricsChat.warningText(targets, Main.config.lyricsMinIntervalMs)));
    }

    private void applyPlaybackSpeed() {
        String value = speedInput.getValue().trim();
        boolean valid = value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
        if (valid) {
            float speed = Float.parseFloat(value);
            valid = speed >= 0.0001f && speed <= 15.0f;
            if (valid) Main.SONG_PLAYER.setSpeed(value);
        }
        if (!valid) {
            SystemToast.add(minecraft.gui.toastManager(), INVALID_SPEED_TOAST, PLAYBACK_SPEED,
                    Component.translatable(Main.MOD_ID + ".screen.invalid_playback_speed"));
        }
        updatePlaybackSpeedInput();
    }

    private void updatePlaybackSpeedInput() {
        displayedSpeed = Main.SONG_PLAYER.speed;
        speedInput.setValue(new BigDecimal(Float.toString(displayedSpeed)).stripTrailingZeros().toPlainString());
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
        // Playback panel, including the lyrics switches below the control row.
        context.fill(5, 32, width / 2, lyricsRowY + 22, 0x3F000000);
        // Playlist panel, from below the playback panel down to the song list's bottom edge.
        int panelBottom = Math.max(lyricsRowY + 26, playlistWidget.getY() + playlistWidget.getHeight() + 4);
        context.fill(5, lyricsRowY + 22, width / 2, panelBottom, 0x3F000000);
        if (showLyricsPreview) context.fill(5, height - 46, width / 2, height - 18, 0x3F000000);
    }

    /** Elapsed and total time below the progress bar, both scaled by the playback speed. */
    private void drawTimestamps(@NonNull GuiGraphicsExtractor context, int leftX, int leftWidth) {
        if (Main.SONG_PLAYER.song == null) return;
        float speed = Main.SONG_PLAYER.speed > 0.0001f ? Main.SONG_PLAYER.speed : 1.0f;
        String elapsed = SongTimeSliderWidget.formatTimestamp((int) (Main.SONG_PLAYER.getSongElapsedSeconds() / speed));
        String total = SongTimeSliderWidget.formatTimestamp((int) (Main.SONG_PLAYER.song.getLengthInSeconds() / speed));
        context.text(font, elapsed, leftX, 99, 0xFFAAAAAA);
        context.text(font, total, leftX + leftWidth - font.width(total), 99, 0xFFAAAAAA);
    }

    private void drawEmptyPlaylistHint(@NonNull GuiGraphicsExtractor context, int x, int y, int maxWidth) {
        FormattedText source = Component.translatable(Main.MOD_ID + ".screen.playlist.empty");
        int lineY = y;
        for (FormattedCharSequence line : font.split(source, maxWidth)) {
            context.centeredText(font, line, x + maxWidth / 2, lineY, 0xFF808080);
            lineY += 10;
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int rightCenter = width / 2 + (width / 2 - 10) / 2;
        context.centeredText(font, DROP_HINT, width / 2, 5, 0xFFFFFFFF);
        context.centeredText(font, SELECT_SONG, rightCenter, 20, 0xFFFFFFFF);
        if (SongLoader.loadingSongs) {
            context.centeredText(font, LOADING_SONGS, rightCenter, 56 + (height - 120) / 2, 0xFFFFFFFF);
        }

        int leftX = 10;
        int leftWidth = width / 2 - 20;
        drawTimestamps(context, leftX, leftWidth);
        context.text(font, Component.translatable(Main.MOD_ID + ".screen.playlist.count", PlaylistManager.size()).getString(),
                leftX, playlistWidget.getY() - 10, 0xFFDDDDDD);
        if (PlaylistManager.isEmpty()) drawEmptyPlaylistHint(context, leftX, playlistWidget.getY() + 8, leftWidth);

        // Top right corner of the playback panel: how fast the mod is currently hitting the server.
        String packetRate = PacketRateMeter.text();
        context.text(font, packetRate, width / 2 - 12 - font.width(packetRate), 34, PacketRateMeter.color());

        // Right of the lyrics switches: how many players private lyrics would reach right now.
        if (!Main.config.lyricsOutputToPublic && Main.config.lyricsChatOutput) {
            int targets = LyricsDispatch.targetCount();
            String info = Component.translatable(Main.MOD_ID + ".screen.lyrics.targets", targets).getString();
            if (font.width(info) < leftWidth / 2) {
                context.text(font, info, width / 2 - 12 - font.width(info), lyricsRowY + 6, 0xFFAAAAAA);
            }
        }

        // Lyrics preview, only while the playing song has a paired lyric file.
        if (showLyricsPreview && LyricsPlayer.hasLyrics()) {
            List<Lyrics.Line> lines = LyricsPlayer.previewLines();
            if (lines.isEmpty()) {
                context.centeredText(font, Component.translatable(Main.MOD_ID + ".screen.lyrics.empty"),
                        leftX + leftWidth / 2, height - 36, 0xFF808080);
            } else {
                context.text(font, font.plainSubstrByWidth(lines.get(0).text(), leftWidth - 8), leftX + 4, height - 42, 0xFFFFFFFF);
                if (lines.size() > 1) {
                    context.text(font, font.plainSubstrByWidth(lines.get(1).text(), leftWidth - 8), leftX + 4, height - 30, 0xFF909090);
                }
            }
        }
    }

    private void updateLoadingState() {
        boolean loading = SongLoader.loadingSongs;
        refreshButton.active = !loading;
        refreshButton.setMessage(loading ? LOADING_SONGS : REFRESH_SONGS);
        songListWidget.visible = !loading;
        songListWidget.active = !loading;
        if (loading) songListWidget.setSelected(null);
        boolean hasSelection = !loading && getSelectedSong() != null;
        playButton.active = hasSelection || Main.SONG_PLAYER.running;
        previewButton.active = hasSelection || Main.PREVIEWER.running;
        blocksButton.active = hasSelection;
        playPauseButton.active = Main.SONG_PLAYER.song != null;
        parentDirectoryButton.active = !loading && !currentDirectory.isEmpty();
        boolean hasPlaylist = !PlaylistManager.isEmpty();
        previousButton.active = hasPlaylist;
        nextButton.active = hasPlaylist;
    }

    /** The selected song of either list; the playlist panel counts like the song list. */
    private Song getSelectedSong() {
        SongListWidget.SongEntry libraryEntry = songListWidget.getSelectedSongEntry();
        if (libraryEntry != null) return libraryEntry.song;
        PlaylistWidget.PlaylistEntry playlistEntry = playlistWidget.getSelectedEntry();
        return playlistEntry == null ? null : playlistEntry.song;
    }

    private void changeDirectory(String directory) {
        currentDirectory = directory;
        songListWidget.setSelected(null);
        shouldFilter = true;
        updateDirectoryHeader();
        updateLoadingState();
    }

    private void updateDirectoryHeader() {
        Component location = Component.translatable(Main.MOD_ID + ".screen.current_directory",
                currentDirectory.isEmpty() ? "/" : currentDirectory + "/");
        directoryLabel.setMessage(location);
        directoryLabel.setTooltip(Tooltip.create(location));
    }

    private void refreshSongEntries() {
        shouldFilter = false;
        songListWidget.setScrollAmount(0);
        List<SongListWidget.ListEntry> entries = new java.util.ArrayList<>();
        String search = query.toLowerCase(Locale.ROOT).replaceAll("\\s", "");
        String prefix = currentDirectory.isEmpty() ? "" : currentDirectory + "/";
        if (search.isEmpty()) {
            for (String directory : SongLoader.DIRECTORIES) {
                if (directory.startsWith(prefix) && directory.indexOf('/', prefix.length()) < 0) {
                    entries.add(new SongListWidget.DirectoryEntry(directory, this::changeDirectory));
                }
            }
        }

        int favoriteIndex = entries.size();
        for (Song song : SongLoader.SONGS) {
            if (!song.relativePath.startsWith(prefix)) continue;
            if (search.isEmpty()) {
                if (song.relativePath.indexOf('/', prefix.length()) >= 0) continue;
            } else if (!song.searchableRelativePath.contains(search) && !song.searchableName.contains(search)) {
                continue;
            }
            song.entry.songListWidget = songListWidget;
            if (song.entry.favorite) {
                entries.add(favoriteIndex++, song.entry);
            } else {
                entries.add(song.entry);
            }
        }
        songListWidget.setEntries(entries, !search.isEmpty());
    }

    @Override
    public void tick() {
        if (displayedSpeed != Main.SONG_PLAYER.speed) updatePlaybackSpeedInput();
        songState.setMessage(getPlaybackStateText());
        timeBar.update();
        playPauseButton.setValue(Main.SONG_PLAYER.running);
        // Keep the playlist panel and the mode buttons in sync with the manager, which also
        // picks up changes made through the client command.
        if (playlistWidget.children().size() != PlaylistManager.size()) playlistWidget.refresh();
        if (displayedShuffle != PlaylistManager.shuffle()) {
            displayedShuffle = PlaylistManager.shuffle();
            shuffleButton.setValue(displayedShuffle);
            shuffleButton.setTooltip(Tooltip.create(Component.translatable(
                    Main.MOD_ID + (displayedShuffle ? ".screen.shuffle.on.tooltip" : ".screen.shuffle.off.tooltip"))));
        }
        if (displayedRepeatMode != PlaylistManager.mode()) {
            displayedRepeatMode = PlaylistManager.mode();
            repeatButton.setValue(displayedRepeatMode);
            repeatButton.setTooltip(Tooltip.create(Component.translatable(
                    Main.MOD_ID + ".screen.repeat." + displayedRepeatMode.name().toLowerCase(Locale.ROOT) + ".tooltip")));
        }
        songTitle.setMessage(Main.SONG_PLAYER.song != null ? Component.literal(Main.SONG_PLAYER.song.displayName) : PLEASE_SELECT_SONG);

        previewButton.setMessage(Main.PREVIEWER.running ? PREVIEW_STOP : PREVIEW);
        playButton.setMessage(Main.SONG_PLAYER.running ? PLAY_STOP : PLAY);

        updateLoadingState();
        if (SongLoader.loadingSongs) return;

        if (SongLoader.reloadVersion != lastReloadVersion) {
            lastReloadVersion = SongLoader.reloadVersion;
            songListWidget.setSelected(null);
            shouldFilter = true;
            if (!currentDirectory.isEmpty() && !SongLoader.DIRECTORIES.contains(currentDirectory)) {
                changeDirectory("");
            }
        }

        if (SongLoader.SONGS.size() != lastLoadedSongCount) {
            lastLoadedSongCount = SongLoader.SONGS.size();
            shouldFilter = true;
        }

        if (shouldFilter) {
            refreshSongEntries();
            updateLoadingState();
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (SongLoader.loadingSongs) {
            SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID + ".still_loading"));
            return;
        }
        List<Path> files = paths.stream().filter(Files::isRegularFile).toList();
        if (files.isEmpty()) return;
        Path targetDirectory = Main.songsFolder.toPath().resolve(currentDirectory);
        String string = files.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        if (string.length() > 300) string = string.substring(0, 300) + "...";

        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                boolean lyricsImported = false;
                for (Path path : files) {
                    Path target = targetDirectory.resolve(path.getFileName());
                    try {
                        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
                        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SongLoader.LYRICS_EXTENSION)) {
                            // Lyrics are not songs: just copy them, the rescan below pairs them up.
                            Files.copy(path, target);
                            lyricsImported = true;
                            continue;
                        }
                        Song song = SongLoader.loadSong(path.toFile(), SongLoader.relativePath(target));
                        if (song != null) {
                            Files.copy(path, target);
                            SongLoader.addSong(song);
                        }
                    } catch (IOException exception) {
                        Main.LOGGER.warn("Failed to import song file from {} to {}", path, target, exception);
                        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME,
                                Component.translatable(Main.MOD_ID + ".screen.import_failed", SongLoader.relativePath(target)));
                    }
                }

                SongLoader.sort();
                if (lyricsImported) SongLoader.loadSongs();
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable(Main.MOD_ID + ".screen.drop_confirm", currentDirectory.isEmpty() ? "/" : currentDirectory + "/"), Component.literal(string)));
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
