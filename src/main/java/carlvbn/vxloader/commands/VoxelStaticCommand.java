package carlvbn.vxloader.commands;

import carlvbn.vxloader.VoxelStructure;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;

import static carlvbn.vxloader.VoxelLoader.MAIN_DIRECTORY_NAME;
import static carlvbn.vxloader.VoxelLoader.VOXEL_DATA_EXTENSION;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelStaticCommand {

    public VoxelStaticCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        CommandNode<ServerCommandSource> baseNode = dispatcher.register((literal("voxelstatic").requires((source) ->
            source.hasPermissionLevel(2)
        ).then(argument("filename", StringArgumentType.string()).executes((context) ->
            execute(context.getSource(), StringArgumentType.getString(context, "filename"))
        ))));

        dispatcher.register(literal("vxs").redirect(baseNode));
    }

    private static int execute(ServerCommandSource source, String filename) throws CommandSyntaxException {
        File file = new File(MAIN_DIRECTORY_NAME + File.separator + "voxelData" + File.separator + filename + (filename.endsWith(VOXEL_DATA_EXTENSION) ? "" : VOXEL_DATA_EXTENSION));

        if (file.exists()) {
            source.sendFeedback(Text.of("Loading '" + filename + "'..."), true);

            try {
                VoxelStructure.place(VoxelStructure.read(file), source.getWorld(), new BlockPos(source.getPosition()));

                source.sendFeedback(Text.of("Successfully loaded '" + filename + "'"), true);
            } catch (Exception e) {
                source.sendError(Text.of("Loading error: " + e.toString()));
            }
        } else {
            source.sendError(Text.of("File not found."));
        }
        return 1;
    }
}
