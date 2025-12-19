# Paint By Relaxation
A simplified implementation of paper [Paint by relaxation](https://ieeexplore.ieee.org/document/934657), coursework for Computer Vision course at Cardiff University in 2022.

### Compile
```bash
javac Relaxation.java
```

### Usage Help
```bash
Usage:
> java Relaxation -h | --help
> java Relaxation <input_image> <compact_brush> <elongated_brush> <density> [-f] [-r <seed>] [-g <std>] [-n <std2>]
Options:
  -f force the program to proceed with an input image at any size
  -r specify a random seed for a consistent output
  -g specify a standard deviation for optional gaussian blurred versions of the output images
  -n specify a standard deviation for optional gaussian noise added to the smaller strokes in the painting
```
