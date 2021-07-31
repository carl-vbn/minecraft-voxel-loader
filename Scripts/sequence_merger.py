'''
Allows you to merge different block animation sequences together. Useful when having multiple objects in a same animation.
'''

import os, sys

FILE_EXTENSION = '.blocks'

def main():
    if len(sys.argv) > 2:
        input_dirs = []
        for i in range(1, len(sys.argv)-1):
            if os.path.exists(sys.argv[i]):
                input_dirs.append(sys.argv[i])
            else:
                print("Error: Input directory '"+sys.argv[i]+"' does not exist.")
                return

        output_dir = sys.argv[-1]
        if not os.path.exists(output_dir):
            os.mkdir(output_dir)

        frame_index = 0
        while True:
            merged_frame = ""
            for input_dir in input_dirs:
                file_path = os.path.join(input_dir, str(frame_index)+FILE_EXTENSION)
                if os.path.exists(file_path):
                    frame_found = True
                    with open(file_path, 'r') as f:
                        content = f.read()
                        if len(merged_frame) == 0 or merged_frame[-1] == ';':
                            merged_frame += content
                        else:
                            merged_frame += ';' + content

            if len(merged_frame) > 0:
                with open(os.path.join(output_dir, str(frame_index)+FILE_EXTENSION), 'w') as f:
                    f.write(merged_frame)
                    print("Saved frame",frame_index)
            else:
                break

            frame_index += 1

        print("Successfully saved",frame_index,"frames")
                    

    else:
        print("Please specify at least one source and the output. The last argument is the output name (will be overwritten if exists) and all arguments before that are the source names")

if __name__ == '__main__':
    main()