# Paint By Relaxation
A much simplified implementation of paper [Paint by relaxation](https://mrl.cs.nyu.edu/publications/painterly-relaxation/), a bit more like [Painterly rendering with curved brush strokes of multiple sizes](https://dl.acm.org/doi/10.1145/280814.280951) in practice, coursework for Computer Vision course at Cardiff University in 2022.

[Coursework Pro-forma](https://github.com/user-attachments/files/24261857/CM3113-cw2223.pdf)

### Example Usage
```bash
java Relaxation your_image.ppm example-brushes/brushSquare.pgm example-brushes/brushEllipse.pgm 0.01
```

![dd353448da4d8bc42cb0629fb0b573ea](https://github.com/user-attachments/assets/bebf8907-ed82-4152-b9fd-3703140e85d6)

### Compile & Generate Javadoc
```bash
javac Relaxation.java
javadoc -d doc ./*.java
```

### Usage Help
```
Usage:
> java Relaxation -h | --help
> java Relaxation <input_image> <compact_brush> <elongated_brush> <density> [-f] [-t <threads>] [-r <seed>] [-s <scale>] [-n <std>]
Options:
  -f force the program to proceed with an input image at any size
  -t specifies the number of threads to use for painting, 0 (default): use cpu count
  -r specify a random seed for a consistent output
  -s specify a scaling factor for brush images
  -n specify a standard deviation for optional gaussian noise added to the smaller strokes in the painting
  (if a negative value is set for threads, an unsafe multi-threading strategy is used)
```
The `-n` option can sometimes add a tiny bit of desired randomness.

| Input Image | Painted Output | With Imperfection (`-n 100`) |
|----------------------------------|----------------------------------|----------------------------------|
| ![sushi-cat-original](https://github.com/user-attachments/assets/03c5064d-5f02-47ad-bf00-96bd115799b4) | ![sushi-cat-painted](https://github.com/user-attachments/assets/6bf42780-cfc9-40ba-bc88-9b99f4589b50) | ![sushi-cat-painted-n-100](https://github.com/user-attachments/assets/0b2dbcfe-69ad-4712-b3c3-24f723e69deb) |

### The Painting Process
> Optimistic thread locking is used for **safe** multi-threading painting, **unsafe** version does not check the pixel version numbers

1. Load inputs: target image, two brush masks (compact + elongated), and a stroke density parameter.
2. Build an image pyramid of the target (repeated 2× downsampling).
3. Compute Sobel edge magnitude across pyramid levels (per RGB channel), then combine into one full-resolution edge-strength map.
4. Compute Sobel edge direction (orientation map) from a smoothed grayscale version of the target.
5. Precompute brush variants: a small set of discrete sizes and discrete orientations (scaled + rotated masks).
6. Compute a multi-scale DoG (Difference of Gaussians) map and a thresholded map for region classification.
7. Paint coarse-to-fine: for each scale, sample many candidate stroke positions; choose stroke size from edge strength (stronger edges → smaller), orientation from Sobel direction, colour as mean RGB under the mask; apply a greedy accept/reject test (only keep strokes that improve similarity).
8. Second pass: repeat painting using elongated strokes in regions indicated by the DoG threshold, typically with higher density.
9. Deal with unpainted areas: region growing to form patches of unpainted areas, fill them with average colour.

![35322ee91a02c10a1a5b9981dce4535c](https://github.com/user-attachments/assets/7a2e1400-5c8a-43ec-811c-26c1eb80f810)
> From left-to-right: Five level of stroke sizes, right-most image is the final output
