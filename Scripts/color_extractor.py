'''
This script requires the 'assets' folder to be extracted from the Minecraft game jar and placed in the same directory as this file. 
'''

from math import floor
from PIL import Image
import os

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

with open('average_colors.txt', 'w') as f:
    for (dirpath, dirnames, filenames) in os.walk('assets'):
        for filename in filenames:
            if filename.endswith('.png'):
                fullpath = os.path.join(dirpath, filename)
                avg_color = extract_average_color(fullpath)
                print('Average color of file', filename, 'is',avg_color) 
                f.write(f'{fullpath}:{avg_color[0]},{avg_color[1]},{avg_color[2]}\n')