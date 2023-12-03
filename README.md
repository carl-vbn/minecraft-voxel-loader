# Minecraft Voxel Loader

[Video posted to my YouTube channel showcasing the project and its development process:](https://www.youtube.com/watch?v=PB0W_gOHxVc)

[![Video thumbnail](https://i.ytimg.com/vi/PB0W_gOHxVc/hqdefault.jpg?sqp=-oaymwEcCNACELwBSFXyq4qpAw4IARUAAIhCGAFwAcABBg==&rs=AOn4CLCKUzuMyac8mYnmV3wVjxWVNCpGtA)](https://www.youtube.com/watch?v=PB0W_gOHxVc)

A Fabric Mod and a set of Python scripts to load and play 3D animations inside Minecraft.  
**All scripts including the Blender extension are located inside the  `Scripts`  directory. All other files relate to the Fabric Mod.**

## Usage guide
### 1. The Blender extension
Paste the contents of `Scripts/blender_voxelizer.py` into a new file inside the `Scripting` tab in your Blender project and press the "Run" button. A "Voxelizer" panel should appear on the right side of the 3D viewport, press "N" to reveal the panel if it is hidden.   
When pressing "Voxelize" the script will go through the desired frame range and save the selected model as it is at that frame to a file inside the specified output directory.  
**Warning: if you use Blender's file browser to select a directory, the path will start with a Blender specific prefix (for example`//..\..\something`) to indicate it is relative to the .blend file. This will not work, the output directory path has to be absolute or at least in a format that is understood by Python (ie: `..\..\something`). This rule does not apply to the `Image texture` input field.**

The image texture is optional. If specified, the model's active UV map will be used to sample the texture. If left blank, the model's vertex colors will be used instead, or just white if there are none.  
The voxelization will only use the model's vertices, so a lack of density can create a hole in the generated structure. Please make sure the distance between two adjacent vertices is never greater than 1m (the size of a Minecraft block). The Subdivision surface modifier can be used to increase the vertex density, with the mode set to "Simple" if you don't want any smoothing. 

**Upon hitting the "Voxelize" button, Blender will freeze and will be unusable until the process is complete. This can take a while depending on the length of the animation and the detail of the model, so it is recommended to open the system console beforehand to track the process, and of course save the file in case something goes wrong.**

### 2. Multiple objects
The voxelization will only work on one object at the time, but the `Scripts/sequence_merger.py` script can be used to fuse multiple voxel sequences into one. 

### 3. The Mod
The mod can be built and launched from source or installed using the provided `.jar`file.
It requires the Fabric Mod loader. Once installed, drag the mod file into your `mods` folder located inside your `.minecraft` directory. You will also need to download the Fabric API mod (which is different from the loader) and place it in the `mods` folder as well.
Once installed, simply start the game using the Fabric profile. You should have access to the added commands.  
  
**Commands (only usable when having access to cheats in the world/server)**:
 - `/voxelstatic` or `/vxs`
This command can be used to place a single frame into the world at your current location. Simply place the frame's `.blocks` file (created by the blender extension) into the `voxelData` folder located at `.minecraft/VoxelLoader` or `run/VoxelLoader` if running the game through gradle when testing. The command then takes the name of the file as an argument.  

 - `/voxelanimation` or `/vxanim`
This command allows you to play and record an animation. The frames of your animation should be placed in a unique folder inside the `voxelData` folder and should be named according to their index in the sequence (`0.blocks`, `1.blocks`, `2.blocks`, etc).
You can then load an animation using `/vxanim load <dirname>` where `<dirname>` is the name of the unique folder.  
An origin position can be specified using `/vxanim setpos [<X> <Y> <Z>]`. If the coordinates are not specified, the player's location will be used instead.   
In singleplayer, you can use `/vxanim preview bounds` to preview the area that will be affected by the animation, `/vxanim preview frame <frame_index>` to indicate which blocks a specific frame will replace and `/vxanim preview hide` to hide the preview overlay.  
You can then play the animation with `/vxanim play <interval>` to play the loaded sequence with the specified interval in game ticks (1/20 of a second) or `/vxanim record <interval> <output_dir_name>` to automatically take screenshots during playback. The screenshot output directory will be created at `VoxelLoader/screenshots/`. It is recommended to hide all UI (using the F1 key by default) and to be completely motionless while recording. The interval can be adjusted to leave time to the game for lighting calculations.   
Playback can be stopped with `/vxanim stop`.
