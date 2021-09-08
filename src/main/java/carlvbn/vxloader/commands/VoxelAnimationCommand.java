package carlvbn.vxloader.commands;

import carlvbn.vxloader.VoxelStructure;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static carlvbn.vxloader.VoxelLoader.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelAnimationCommand {

    // Stuff used when an animation is in progress (when currentFrameIndex != -1)
    private static int currentFrameIndex = -1;
    private static int timer;
    private static int interval;
    private static BlockPos position; // Origin of the structure
    private static BlockPos minPos, maxPos; // Relative to the structure's origin. Not world space
    private static World world;
    private static List<VoxelStructure> frames;
    private static String screenshotSaveDirectoryPath;
    private static ServerPlayerEntity animationInvoker;
    private static boolean waitingForScreenshot; // If server is waiting for client to take screenshot
    private static PreviewMode previewMode = PreviewMode.NONE;
    private static int previewedFrame;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        CommandNode<ServerCommandSource> baseNode = dispatcher.register((literal("voxelanimation").requires((source) -> source.hasPermissionLevel(2)))
            .then(literal("setpos").requires((source) -> source.hasPermissionLevel(2)).executes((context) -> setpos(context.getSource(), null)).then(argument("position", BlockPosArgumentType.blockPos()).executes((context) -> setpos(context.getSource(), BlockPosArgumentType.getBlockPos(context, "position")))))
            .then(literal("load").requires((source) -> source.hasPermissionLevel(2))
                .then(argument("dirname", StringArgumentType.string())
                        .executes((context) -> load(context.getSource(), StringArgumentType.getString(context, "dirname"))))
                .executes((context) -> { context.getSource().sendError(Text.of("Please specify a directory name.")); return 0; }))
            .then(literal("play").requires((source) -> source.hasPermissionLevel(2))
                .then(argument("interval", IntegerArgumentType.integer(0))
                        .executes((context) -> play(context.getSource(), IntegerArgumentType.getInteger(context, "interval"), null)))
                .executes((context) -> { context.getSource().sendError(Text.of("Please specify an interval.")); return 0; }))
            .then(literal("record").requires((source) -> source.hasPermissionLevel(2))
                .then(argument("interval", IntegerArgumentType.integer(0)).then(argument("screenshotdir", StringArgumentType.string())
                        .executes((context) -> play(context.getSource(), IntegerArgumentType.getInteger(context, "interval"), StringArgumentType.getString(context, "screenshotdir"))))
                    .executes((context) -> { context.getSource().sendError(Text.of("Please specify an output directory.")); return 0; }))
                .executes((context) -> { context.getSource().sendError(Text.of("Please specify an interval.")); return 0; }))
            .then(literal("preview").requires((source) -> source.hasPermissionLevel(2))
                .then(literal("hide").executes((context) -> preview(context.getSource(), PreviewMode.NONE, -1)))
                .then(literal("bounds").executes((context) -> preview(context.getSource(), PreviewMode.BOUNDS, -1)))
                .then(literal("frame").then(argument("frame_index", IntegerArgumentType.integer(0)).executes((context) -> preview(context.getSource(), PreviewMode.FRAME, IntegerArgumentType.getInteger(context, "frame_index")))).executes((context) -> { context.getSource().sendError(Text.of("Please specify a frame.")); return 0; }))
                .executes((context) -> { context.getSource().sendError(Text.of("Please specify a mode.")); return 0; }))
            .then(literal("stop").requires((source) -> source.hasPermissionLevel(2)).executes((context) -> stop(context.getSource())))
        );

        dispatcher.register(literal("vxanim").redirect(baseNode));

        Identifier screenshotOrderIdentifier = new Identifier(MOD_ID, "take_screenshot");
        Identifier screenshotOrderResponseIdentifier = new Identifier(MOD_ID, "screenshot_taken");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (currentFrameIndex != -1 && !waitingForScreenshot) {
                if (timer <= 0) {
                    timer = interval;

                    if (screenshotSaveDirectoryPath != null) {
                        ServerPlayNetworking.send(animationInvoker, screenshotOrderIdentifier, PacketByteBufs.empty());
                        waitingForScreenshot = true;
                    }

                    if (currentFrameIndex >= frames.size()) { // Animation done
                        currentFrameIndex = -1;
                        waitingForScreenshot = false;
                    } else { // Animation in progress
                        if (currentFrameIndex > 0) { // Not first frame
                            VoxelStructure.place(frames.get(currentFrameIndex).difference(frames.get(currentFrameIndex-1)), world, position); // Replace last structure
                        } else {
                            VoxelStructure.place(frames.get(currentFrameIndex), world, position);
                        }

                        if (!waitingForScreenshot) {
                            currentFrameIndex++;
                            // If a screenshot order has been set, don't increment the currentFrameIndex now, it will be incremented once the screenshot has been taken by the client
                        }
                    }
                } else {
                    timer -= 1;
                }
            }
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(screenshotOrderIdentifier, (client, handler, buffer, responseSender) -> {
                client.execute(() -> {
                    NativeImage screenshot = ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer());

                    PacketByteBuf packetBytes = PacketByteBufs.create();
                    try {
                        packetBytes.writeBytes(screenshot.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    responseSender.sendPacket(screenshotOrderResponseIdentifier, packetBytes);
                });
            });

            WorldRenderEvents.BEFORE_DEBUG_RENDER.register(new WorldRenderEvents.DebugRender() {
                @Override
                public void beforeDebugRender(WorldRenderContext context) {
                    if (frames != null && frames.size() > 0 && currentFrameIndex == -1) {
                        GlStateManager._enableBlend();
                        GlStateManager._enableDepthTest();
                        switch (previewMode) {
                            case NONE:
                                break;
                            case BOUNDS:
                                DebugRenderer.drawBox(minPos.add(position), maxPos.add(position), 1.0F, 0.0F, 0.5F, 0.2F);
                                break;
                            case FRAME:
                                if (previewedFrame < frames.size()) {
                                    for (BlockPos pos : frames.get(previewedFrame).getBlocks().keySet()) {
                                        DebugRenderer.drawBox(position.add(pos), 0.0F, 1.0F, 0.0F, 0.5F, 0.2F);
                                    }
                                }
                                break;
                        }
                        GlStateManager._disableDepthTest();
                        GlStateManager._disableBlend();
                    }
                }
            });
        }

        ServerPlayNetworking.registerGlobalReceiver(screenshotOrderResponseIdentifier, (server, player, handler, buffer, responseSender) -> {
            int screenshotFrameIndex = currentFrameIndex;

            if (screenshotFrameIndex == -1) {
                // Screenshot has been taken once the animation has already finished playing, so it should be the last one
                screenshotFrameIndex = frames.size()-1;
            }

            ByteBuffer imageBuffer = ByteBuffer.allocate(buffer.capacity());
            buffer.getBytes(0, imageBuffer);
            try {
                Files.write(Paths.get(screenshotSaveDirectoryPath, screenshotFrameIndex+".png"), imageBuffer.array());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (waitingForScreenshot) {
                currentFrameIndex++;
                waitingForScreenshot = false;
            }
        });
    }

    private static int setpos(ServerCommandSource source, @Nullable BlockPos position) {
        VoxelAnimationCommand.position = position == null ? new BlockPos(source.getPosition()) : position;
        VoxelAnimationCommand.world = source.getWorld();
        source.sendFeedback(Text.of("Position set to "+VoxelAnimationCommand.position.getX()+" "+VoxelAnimationCommand.position.getY()+" "+VoxelAnimationCommand.position.getZ()), true);
        return 1;
    }

    private static int load(ServerCommandSource source, String dirname) {
        File directory = new File(MAIN_DIRECTORY_NAME +File.separator+"voxelData"+File.separator+dirname);
        if (directory.exists()) {
            if (VoxelAnimationCommand.position == null) {
                VoxelAnimationCommand.position = new BlockPos(source.getPosition());
                System.out.println("Position automatically set to "+VoxelAnimationCommand.position);
            }
            if (VoxelAnimationCommand.world == null) VoxelAnimationCommand.world = source.getWorld();

            source.sendFeedback(Text.of("Loading frames..."), true);
            frames = new ArrayList<>();
            minPos = null;
            maxPos = null;
            HashMap<Color, Block> blockMap = new HashMap<>();
            Arrays.stream(directory.list()).sorted((o1, o2) -> {
                o1 = o1.replace(VOXEL_DATA_EXTENSION, "");
                o2 = o2.replace(VOXEL_DATA_EXTENSION, "");
                try {
                    return Integer.compare(Integer.parseInt(o1), Integer.parseInt(o2));
                } catch (NumberFormatException e) {
                    return o1.compareTo(o2);
                }
            }).forEachOrdered((String filename) -> {
                if (filename.endsWith(VOXEL_DATA_EXTENSION)) {
                    try {
                        VoxelStructure structure = VoxelStructure.read(source.getWorld(), new File(directory, filename), blockMap);
                        frames.add(structure);

                        for (BlockPos pos : structure.getBlocks().keySet()) {
                            if (minPos == null) minPos = pos;
                            else {
                                if (pos.getX() < minPos.getX()) minPos = new BlockPos(pos.getX(), minPos.getY(), minPos.getZ());
                                if (pos.getY() < minPos.getY()) minPos = new BlockPos(minPos.getX(), pos.getY(), minPos.getZ());
                                if (pos.getZ() < minPos.getZ()) minPos = new BlockPos(minPos.getX(), minPos.getY(), pos.getZ());
                            }
                            if (maxPos == null) maxPos = pos;
                            else {
                                if (pos.getX() > maxPos.getX()) maxPos = new BlockPos(pos.getX(), maxPos.getY(), maxPos.getZ());
                                if (pos.getY() > maxPos.getY()) maxPos = new BlockPos(maxPos.getX(), pos.getY(), maxPos.getZ());
                                if (pos.getZ() > maxPos.getZ()) maxPos = new BlockPos(maxPos.getX(), maxPos.getY(), pos.getZ());
                            }

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        source.sendError(Text.of("Loading error ("+filename+"): "+e.toString()));
                    }
                }
            });

            source.sendFeedback(Text.of("Detected "+frames.size()+" frames."), true);
        } else {
            source.sendError(Text.of("Directory not found."));
        }

        return 1;
    }

    private static int play(ServerCommandSource source, int interval, @Nullable  String screenshotDir) throws CommandSyntaxException {
        if (frames == null || frames.size() == 0) {
            source.sendError(Text.of("No animation loaded."));
            return 0;
        }

        source.sendFeedback(Text.of("Animation starting now with an interval of "+interval+" ticks."), true);

        if (screenshotDir != null) {
            File screenshotSaveDirectory = new File(MAIN_DIRECTORY_NAME, "screenshots"+File.separator+screenshotDir);
            if (!screenshotSaveDirectory.exists()) screenshotSaveDirectory.mkdirs();
            screenshotSaveDirectoryPath = screenshotSaveDirectory.getPath();

            source.sendFeedback(Text.of("Screenshots will be saved to '"+screenshotDir+"'"), true);
        } else{
            screenshotSaveDirectoryPath = null;
        }

        animationInvoker = source.getPlayer();
        currentFrameIndex = 0;
        timer = 0;
        waitingForScreenshot = false;
        VoxelAnimationCommand.interval = interval;
        return 1;
    }

    private static int preview(ServerCommandSource source, PreviewMode mode, int frameIndex) {
        previewMode = mode;
        previewedFrame = frameIndex;
        source.sendFeedback(Text.of("Preview mode updated."), false);
        return 1;
    }

    private static int stop(ServerCommandSource source) {
        if (currentFrameIndex != -1) {
            currentFrameIndex = -1;
            waitingForScreenshot = false;
            source.sendFeedback(Text.of("Playback stopped."), true);
        } else {
            source.sendError(Text.of("No animation is in progress."));
        }
        return 1;
    }
}

enum PreviewMode {
    NONE, BOUNDS, FRAME
}
