package semmiedev.disc_jockey.gui.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class BlocksOverlay {
    public static ItemStack[] itemStacks;
    public static int[] amounts;
    public static int amountOfNoteBlocks;
    public static String songRelativePath;

    private static final ItemStack NOTE_BLOCK = Blocks.NOTE_BLOCK.asItem().getDefaultInstance();

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (itemStacks != null) {
            context.fill(2, 2, 62, (itemStacks.length + 1) * 20 + 7, 0xFF16161B);
            context.fill(4, 4, 60, (itemStacks.length + 1) * 20 + 5, 0xFF2A2A2F);

            Minecraft client = Minecraft.getInstance();
            Font font = client.font;

            context.text(font, " × " + amountOfNoteBlocks, 26, 10, 0xFFFFFFFF, true);
            context.item(NOTE_BLOCK, 6, 6);

            for (int i = 0; i < itemStacks.length; i++) {
                context.text(font, " × " + amounts[i], 26, 10 + 20 * (i + 1), 0xFFFFFFFF, true);
                context.item(itemStacks[i], 6, 6 + 20 * (i + 1));
            }
        }
    }
}
