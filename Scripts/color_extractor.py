'''
This script requires the 'assets' folder to be extracted from the Minecraft game jar and placed in the same directory as this file. 
'''

from math import floor
from PIL import Image
import os
import json
import re

# Any block which's name matches any of these regex patterns will be skipped.
# No need to add non-solid blocks or blocks with different textures because they are automatically ignored.
BLACKLIST = [
    # Coral because it can change to gray while the animation is playing
    '.*coral_block$',

    # Falling blocks
    '.*concrete_powder$',
    '^sand$',
    '^red_sand$',
    '^gravel$',

    # Spawners just don't look so great and could cause problems if they start spawning stuff
    '^spawner$'
]

def extract_average_color(filename):
    img = Image.open(filename, 'r')

    if img.mode not in ('L', 'RGB', 'RGBA'):
        img = img.convert('RGBA')

    rgb_sum = [0,0,0]
    pixels = img.getdata()
    pixel_count = len(pixels)
    for pixel in pixels:
        if img.mode == 'RGBA' and pixel[3] == 0: # Ignore fully transparent pixels
            pixel_count -= 1
            continue

        if img.mode == 'L':
            for i in range(3):
                rgb_sum[i] += pixel
        else:
            for i in range(3):
                rgb_sum[i] += pixel[i]

    img.close()

    if pixel_count > 0:
        return (floor(rgb_sum[0]/pixel_count), floor(rgb_sum[1]/pixel_count), floor(rgb_sum[2]/pixel_count))
    else:
        return (0,0,0)

if __name__ == '__main__':
    if os.path.exists('assets'):
        with open('average_colors.txt', 'w') as f:
            for model_file_name in os.listdir('assets/minecraft/models/block'):
                block_name = model_file_name[:-5] # Remove '.json'

                blacklisted = False
                for pattern in BLACKLIST:
                    if re.match(pattern, block_name):
                        blacklisted = True
                        break
                
                if blacklisted:
                    print(f"- Skipped '{block_name}' (Blacklisted pattern)")
                else:
                    with open(os.path.join('assets/minecraft/models/block', model_file_name)) as model_file:
                        model_data = json.load(model_file)
                        if 'parent' in model_data and model_data['parent'] == 'minecraft:block/cube_all':
                            texture_name = model_data['textures']['all'].replace('minecraft:', 'assets/minecraft/textures/')+'.png'
                            avg_color = extract_average_color(texture_name)
                            print(f'* {block_name} -> {avg_color}')
                            f.write(f'{block_name}:{avg_color[0]},{avg_color[1]},{avg_color[2]}\n')
    else:
        print('Could not find assets directory. Please extract the assets folder from the Minecraft JAR and place it in the same directory as this script.')