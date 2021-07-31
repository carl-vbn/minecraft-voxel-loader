package carlvbn.vxloader;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;

public class VoxelStructure {
    private HashMap<BlockPos, Block> blocks;

    public VoxelStructure(HashMap<BlockPos, Color> voxels) {
        blocks = new HashMap<>();
        HashMap<Color, Block> blockMap = new HashMap<>();

        for (BlockPos pos : voxels.keySet()) {
            Color voxelColor = voxels.get(pos);
            Block block;

            if (blockMap.containsKey(voxelColor)) {
                block = blockMap.get(voxelColor);
            } else {
                block = VoxelLoader.getInstance().getColoredBlockType(voxelColor);
                blockMap.put(voxelColor, block);
            }

            blocks.put(pos, block);
        }
    }

    public VoxelStructure(HashMap<BlockPos, Color> voxels, @NotNull HashMap<Color, Block> blockMap) {
        blocks = new HashMap<>();

        for (BlockPos pos : voxels.keySet()) {
            Color voxelColor = voxels.get(pos);

            Block block = null;
            if (blockMap.containsKey(voxelColor)) {
                block = blockMap.get(voxelColor);
            } else {
                block = VoxelLoader.getInstance().getColoredBlockType(voxelColor);
                blockMap.put(voxelColor, block);
            }

            blocks.put(pos, block);
        }
    }

    public HashMap<BlockPos, Block> difference(VoxelStructure other) {
        HashMap<BlockPos, Block> difference = new HashMap<>();

        for (BlockPos pos : blocks.keySet()) {
            Block block = blocks.get(pos);
            if (other.getBlock(pos) != block) difference.put(pos, block);
        }

        for (BlockPos pos : other.getBlocks().keySet()) {
            if (!blocks.containsKey(pos)) {
                difference.put(pos, Blocks.AIR);
            }
        }

        return difference;
    }

    public Block getBlock(BlockPos pos) {
        return blocks.getOrDefault(pos, null);
    }

    public HashMap<BlockPos, Block> getBlocks() {
        return blocks;
    }

    public static VoxelStructure read(File file, HashMap<Color, Block> blockMap) {
        StringBuilder sb = new StringBuilder(512);
        try {
            Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            int c = 0;
            while ((c = r.read()) != -1) {
                sb.append((char) c);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String data = sb.toString();
        HashMap<BlockPos, Color> voxels = new HashMap<>();

        if (data.length() > 0) {
            String[] positionStrings = data.split(";");
            for (String positionString : positionStrings) {
                String[] voxelComponents = positionString.split(","); // X,Y,Z,R,G,B (RGB might not be present)
                BlockPos pos = new BlockPos(-Integer.parseInt(voxelComponents[0]), Integer.parseInt(voxelComponents[1]), Integer.parseInt(voxelComponents[2])); // During the process, the models seems to get mirrored. The '-' before the x component is there to correct that.

                Color voxelColor = voxelComponents.length < 6 ? Color.WHITE : new Color(Integer.parseInt(voxelComponents[3]), Integer.parseInt(voxelComponents[4]), Integer.parseInt(voxelComponents[5]));

                voxels.put(pos, voxelColor);
            }
        }

        return blockMap != null ? new VoxelStructure(voxels, blockMap) : new VoxelStructure(voxels);
    }

    public static VoxelStructure read(File file) {
        return read(file, null);
    }

    public static void place(VoxelStructure structure, World world, BlockPos pos) {
        place(structure.getBlocks(), world, pos);
    }

    public static void place(HashMap<BlockPos, Block> structure, World world, BlockPos pos) {
        for (BlockPos blockPos : structure.keySet()) {
            world.setBlockState(pos.add(blockPos), structure.get(blockPos).getDefaultState());
        }
    }
}