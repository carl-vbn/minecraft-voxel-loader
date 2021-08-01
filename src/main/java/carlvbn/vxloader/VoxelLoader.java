package carlvbn.vxloader;

import carlvbn.vxloader.commands.VoxelAnimationCommand;
import carlvbn.vxloader.commands.VoxelStaticCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.block.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class VoxelLoader implements ModInitializer {

    public static Logger LOGGER = LogManager.getLogger();

    public static final String MOD_ID = "vxloader";
    public static final String MOD_NAME = "VoxelLoader";
    public static final String MAIN_DIRECTORY_NAME = "VoxelLoader";
    public static final String VOXEL_DATA_EXTENSION = ".blocks";

    private static VoxelLoader instance;
    private static HashMap<Block, Color> averageBlockColors;

    @Override
    public void onInitialize() {
        log(Level.INFO, "Initializing");

        instance = this;

        File mainDir = new File(MAIN_DIRECTORY_NAME);
        if (!mainDir.exists())
            mainDir.mkdir();

        File dataDir = new File(mainDir, "voxelData");
        if (!dataDir.exists())
            dataDir.mkdir();

        generateBlockColorMap();
        log(Level.INFO, "Found colors for "+averageBlockColors.size()+" blocks.");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            VoxelStaticCommand.register(dispatcher);
            VoxelAnimationCommand.register(dispatcher);
        });
    }

    private void generateBlockColorMap() {
        averageBlockColors = new HashMap<>();

        try {
            List<String> lines = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/average_colors.txt"), StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
            for (String line : lines) {
                String[] splitLine = line.split(":");
                if (splitLine[0].startsWith("assets/minecraft/textures/block/")) {
                    String[] pathElements = splitLine[0].split("/");
                    String blockName = pathElements[pathElements.length-1].replace(".png", "");
                    Block block = Registry.BLOCK.get(new Identifier("minecraft", blockName));
                    if (!(block instanceof FallingBlock) && !(block instanceof FluidBlock) && !blockName.contains("coral") && block != Blocks.SPAWNER && !(block instanceof ShulkerBoxBlock)) { // Coral changes color after being placed
                        String[] rgbValues = splitLine[1].split(",");
                        Color color = new Color(Integer.parseInt(rgbValues[0]), Integer.parseInt(rgbValues[1]), Integer.parseInt(rgbValues[2]));
                        averageBlockColors.put(block, color);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Block getColoredBlockType(World world, Color color) {
        Block closestColorBlock = null;
        int closestDifference = Integer.MAX_VALUE;
        for (Block block : averageBlockColors.keySet()) {
            if (block.isTranslucent(block.getDefaultState(), world, BlockPos.ORIGIN)) continue;

            Color matColor = averageBlockColors.get(block);
            int redDiff = color.getRed()-matColor.getRed();
            int greenDiff = color.getGreen()-matColor.getGreen();
            int blueDiff = color.getBlue()-matColor.getBlue();
            int difference = redDiff*redDiff+greenDiff*greenDiff+blueDiff*blueDiff;

            if (difference < closestDifference) {
                closestColorBlock = block;
                closestDifference = difference;
            }
        }

        return closestColorBlock;
    }

    public static void log(Level level, String message){
        LOGGER.log(level, "["+MOD_NAME+"] " + message);
    }

    public static VoxelLoader getInstance() {
        return instance;
    }

}