import java.util.*;
import java.util.concurrent.*;
import java.time.LocalTime;

/**
 * This Relaxation class implements a stroke-based rendering algorithm inspired by the 
 * conference paper Paint By Relaxation (Aaron Hertzmann, 2001)
 * @author Hugo (Jin Huang)
 */
public class Relaxation
{
    private static ImagePPM inputImg = new ImagePPM();
    private static TaskManager taskStatus= new TaskManager();
    private static int inputWidth, inputHeight;
    private static double density, noiseSigma = -1;
    private static long randomSeed = -1;
    private static Image brush0 = new Image(), brush1 = new Image();
    private static int[][][][] compactBrushes, elongatedBrushes;
    private static Canvas canvas;
    private static int[][] magMap, grayImg, DoG;
    private static double[][] oriMap;

    /**
     * Creates a unsigned sobel magnitude map with image pyramid, the sobel magnitude maps for
     * each layer of the pyramid are upscaled via bi-linear interpolation and summed together 
     * to form the final sobel magnitude map
     */
    private static void CreateMagMap()
    {
        int width, height; // of the current layer of pyramid
        int i, j, k; // Color channel/row/column index

        // The current and previous layer of the pyramid
        int[][][] current, previous = inputImg.pixels;

        // Create the first layer of the image pyramid
        width = inputWidth; height = inputHeight;
        magMap = ImageProcessing.SobelMagnitude(previous, inputWidth, inputHeight);

        // Scale the image down until the next layer would have a width/height lower than 20
        while (width > 39 && height > 39) {
            // Create the current layer of the pyramid by halving width and height
            width /= 2; height /= 2;
            current = new int[3][width][height];

            // Downsample pixels as a mean of the 4 corresponding pixels
            for (i = 0; i < 3; i++) {
                for (j = 0; j < width; j++) {
                    for (k = 0; k < height; k++) {
                        current[i][j][k] = (
                            previous[i][2*j][2*k] + previous[i][2*j+1][2*k] + 
                            previous[i][2*j+1][2*k+1] + previous[i][2*j][2*k+1]) / 4;
                    }
                }
            }

            // Calculate Sobel Magnitude Map for the current layer
            int[][] upscaledMap = ImageProcessing.BilinearInterpolate(
                ImageProcessing.SobelMagnitude(current, width, height), width, height, inputWidth, inputHeight, true);
            ArrayHelper.AddArrayInPlace(magMap, upscaledMap, inputWidth, inputHeight);
            previous = current;
        }

        ArrayHelper.NormalizeImg(magMap, inputWidth, inputHeight);
    }

    /**
     * Box-filter the grayscale version of the input image and create the sobel orientation map
     */
    private static void CreateOriMap()
    {
        int[][] img = ImageProcessing.BoxFilter(grayImg, inputWidth, inputHeight, 5);
        int row, col; // row/column index for the image
        int i, j; // row/column index for sobel operator kernals
        int I; // value of a pixel in the grayscale image
        int Gx, Gy; // Horizontal/vertical derivatives

        oriMap = new double[inputWidth][inputHeight];

        // Compute the image's convolution with Sobel operator
        // Like week 3 lab solution's implementation, it has zeros at the border
        for (row = 0; row < inputWidth-2; row++) {
            for (col = 0; col < inputHeight-2; col++) {
                Gx = 0; Gy = 0;
                // Calculate the image's convolution with sobel operator kernals
                for (i = 0; i < 3; i++) {
                    for (j = 0; j < 3; j++) {
                        I = img[row+i][col+j];
                        Gx += ImageProcessing.sobelX[i][j] * I;
                        Gy += ImageProcessing.sobelX[j][i] * I;
                    }
                }
                oriMap[row][col] = Math.atan2(Gy, Gx);
            }
        }
    }


    /**
     * Create the Multi-scale Difference of Gaussians map and threshold it with threshold value 64
     */
    private static void CreateMDoG()
    {
        DoG = ImageProcessing.MDoG(grayImg, inputWidth, inputHeight, new int[] {1, 2, 4, 8});
        ImageProcessing.Threshold(DoG, 64, inputWidth, inputHeight);
    }


    /**
     * Create multiple versions of the two brushes, create the MDoG map, and create the canvas
     */
    private static void CreateBrushes(Float scale)
    {
        // Use a thread pool with 8 threads to rotate the brushes
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        PixelRotator task;
        int i, s = 5, brushSize0 = Math.round(brush0.height * scale), brushSize1 = Math.round(brush1.height);
        int[] scaledSize0 = new int[5], scaledSize1 = new int[5], diagLen0 = new int[5], diagLen1 = new int[5];
        int[][] compact = brush0.pixels, elongated = brush1.pixels, temp0, temp1;
        double rotAngle = Math.toRadians(22.5);
        double[] brushAngles = new double[16];
        float[][] rotMatrices = new float[16][];
        if (scale != 1.0f) {
            compact = ImageProcessing.BilinearInterpolate(compact, brush0.height, brushSize0);
            elongated = ImageProcessing.BilinearInterpolate(elongated, brush1.height, brushSize1);
        }
        compactBrushes = new int[5][16][][]; elongatedBrushes = new int[5][16][][];

        // Generate the rotation matrices for each orientation
        for (i = 0; i < 16; i++) {
            brushAngles[i] = i * rotAngle;
            rotMatrices[i] = ArrayHelper.RotationMatrix(brushAngles[i]);
        }

        // Create the various versions of brushes multi-threaded
        for (s = 0; s < 5; s++) {
            scaledSize0[s] = (s+1)*brushSize0/5;
            scaledSize1[s] = (s+1)*brushSize1/5;
            for (i = 0; i < 16; i++) {
                if (s == 4) {
                    temp0 = compact;
                    temp1 = elongated;
                } else {
                    temp0 = ImageProcessing.BilinearInterpolate(compact, brushSize0, scaledSize0[s]);
                    temp1 = ImageProcessing.BilinearInterpolate(elongated, brushSize1, scaledSize1[s]);
                }
                // Use a larger array to hold the rotated brushes to not crop the out-of-bound parts
                diagLen0[s] = (int) Math.sqrt(2*scaledSize0[s]*scaledSize0[s]);
                diagLen1[s] = (int) Math.sqrt(2*scaledSize1[s]*scaledSize1[s]);
                compactBrushes[s][i] = new int[diagLen0[s]][diagLen0[s]];
                elongatedBrushes[s][i] = new int[diagLen1[s]][diagLen1[s]];
                // Create tasks for the thread workers in the thread pool to work on
                task = new PixelRotator(temp0, compactBrushes[s][i], scaledSize0[s], diagLen0[s], rotMatrices[i]);
                pool.execute(task);
                task = new PixelRotator(temp1, elongatedBrushes[s][i], scaledSize1[s], diagLen1[s], rotMatrices[i]);
                pool.execute(task);
            }
        }

        // Shut down the thread pool
        pool.shutdown();
        // Block execution of the program until thread pool is shut down
        // This ensures that all of the brushes of different scales are rotated correctly
        while (!pool.isTerminated()) {}

        // Different versions of the brushes are created, start to create MDoG maps
        taskStatus.FinishTask();
        CreateMDoG();

        // Create the canvas and set the brushes
        canvas = new Canvas(taskStatus, inputImg.pixels, magMap, oriMap, DoG, brushAngles, inputImg.width,
            inputImg.height, inputImg.width * inputImg.height * density, noiseSigma, randomSeed);
        canvas.SetUpCompactBrush(compactBrushes, scaledSize0, diagLen0);
        canvas.SetUpElongatedBrush(elongatedBrushes, scaledSize1, diagLen1);
    }

    
    /**
     * Add gaussian noise to the input image
     */
    private static void AddGaussianNoise()
    {
        int i, row, col;
        Random RNG = new Random(randomSeed);

        for (row = 0; row < inputWidth; row++) {
            for (col = 0; col < inputHeight; col++) {
                for (i = 0; i < 3; i++) {
                    inputImg.pixels[i][row][col] = Integer.min(
                        Integer.max((int) Math.round(
                        inputImg.pixels[i][row][col] + RNG.nextGaussian() * noiseSigma), 
                        0), 255);
                }
            }
        }
        
        System.out.println("Gaussian noise has been added to the input image");
    }

    
    /** 
     * Checks if the input image / brush images are loaded correctly and are valid: 
     * <p>
     * The dimensions must be within limits unless the user forces the program to read 
     * in bigger images (not recommended)
     * 
     * @param forceValid whether the user has passed in the -f flag as an argument
     */
    private static void ValidateInputImages(boolean forceValid)
    {
        inputWidth = inputImg.width;
        inputHeight = inputImg.height;

        // Check if the image and the brushes have dimensions within the set range
        if ((Integer.max(inputWidth, inputHeight) > 1500) || (Integer.max(brush0.height, brush1.height) > 500)) {
            // The user can force the program to read in a larger image via the -f flag
            if (forceValid) {
                System.out.println("Warning: Image size exceeds maximum (input image maximum: 1500x1500)");
            } else {
                ErrorMessage("Image size exceeds maximum (input image maximum: 1500x1500, mask/brush image maximum: 500x500)");
            }
        }

        // Check if either the input image or the brush images have a dimension of 0 (possibly file doesn't exist)
        if (Integer.min(Integer.min(inputWidth, inputHeight), Integer.min(brush0.height, brush1.height)) < 1) {
            ErrorMessage("One of the input images have a size of 0");
        }

        // Check if the colour depth is 24-bit (all ppm files should have 0-255 depth for each colour channel)
        if (inputImg.depth != 255) {
            ErrorMessage(String.format("Input image has a colour depth of %d-bit instead of 24 " +
                "thus it is not a valid ppm file!\n", 
                3 * Math.round(Math.log(inputImg.depth+1) / Math.log(2))));
        }
    }

    
    /** 
     * Display an error message and stop execution of the program
     * 
     * @param msg the error message
     */
    public static void ErrorMessage(String msg)
    {
        System.out.printf("\nError (during task %d): \n%s\n", taskStatus.taskID, msg);
        System.exit(0);
    }

    
    /** 
     * Additional flags: <p>
     * -f forces the program to read input images bigger than the limit (1500x1500) <p>
     * -r sets the random seed that is used to render the strokes (for a consistent output) <p>
     * -s specify a scaling factor for brush images <p>
     * -n sets the standard deviation of gaussian noise added to the smaller strokes in the painting
     * 
     * @param args: input_image compact_brush elongated_brush density [-f] [-r seed] [-s scale] [-n std]
     */
    public static void main(String[] args)
    {
        int len = args.length;
        float scale = 1.0f;
        boolean forceValid = false;
        String[] inputImgPath = args[0].split("/");
        String name = inputImgPath[inputImgPath.length-1], helpMsg = 
        "\nUsage:\n" +
        "> java Relaxation -h | --help\n" +
        "> java Relaxation <input_image> <compact_brush> <elongated_brush> <density> " +
        "[-f] [-r <seed>] [-s <scale>] [-n <std>]\n" +
        "Options:\n" +
        "  -f force the program to proceed with an input image at any size\n" +
        "  -r specify a random seed for a consistent output\n" +
        "  -s specify a scaling factor for brush images\n" +
        "  -n specify a standard deviation for optional gaussian noise added to the smaller strokes in the painting\n\n";

        taskStatus.StartTask();

        // Check if the user enters the "help" command
        if (len > 0) {
            if (args[0].equals("-h") || args[0].equals("--help")) {
                System.out.print(helpMsg);
                System.exit(0);
            }
        }

        // Parse the arguments
        if (len < 4) {
            // Less than 4 arguments passed
            ErrorMessage(String.format("%d arguments received instead of 4.\n" + helpMsg, len));
        } else if (len > 4) {
            // More than 4 arguments passed (possibly flags)
            for (int i = 4; i < len; i += 2) {
                // Parse additional arguments
                switch (args[i]) {
                    // User specified a random seed
                    case "-r":
                        try {
                            randomSeed = Long.parseUnsignedLong(args[i+1]);
                        } catch (Exception numberFormatException) {
                            ErrorMessage("Random seed isn't entered as a natural number");
                        }
                        break;
                    
                    // User specified a random seed
                    case "-s":
                        try {
                            scale = Float.parseFloat(args[i+1]);
                        } catch (Exception numberFormatException) {
                            ErrorMessage("Brush scale isn't entered as a floating point number");
                        }
                        break;
                    
                    // User specified a standard deviation for gaussian noise
                    case "-n":
                        try {
                            noiseSigma = Double.parseDouble(args[i+1]);
                            if (noiseSigma <= 0) {
                                throw new NumberFormatException("Standard deviation of gaussian noise can't be non-positive");
                            }
                        } catch (Exception numberFormatException) {
                            ErrorMessage("Gaussian noise standard deviation isn't entered as a positive decimal number");
                        }
                        break;
                    
                    // User forced the program to read the input image regardless of its dimensions
                    case "-f":
                        i--; // this command only has a length of one, correct the index i
                        forceValid = true;
                        break;
                
                    // User passed in a random flag
                    default:
                        System.out.println("Warning: unknown arguments received and ignored.");
                        break;
                }
            }
        }

        // Set the random seed to current time (in seconds) if not specified
        if (randomSeed < 0) {
            randomSeed = LocalTime.now().toSecondOfDay();
        }
        System.out.printf("Random seed is set to %d\n", randomSeed);

        // Read the input image and brushes
        inputImg.ReadPPM(args[0]);
        brush0.ReadPGM(args[1]);
        brush1.ReadPGM(args[2]);

        // Validate the input images
        ValidateInputImages(forceValid); 

        // Try to parse the density argument
        try {
            density = Double.parseDouble(args[3]);
            if (density <= 0) {
                throw new NumberFormatException();
            }
        } catch (Exception numberFormatException) {
            ErrorMessage("Density isn't entered as a decimal number");
        }

        // Finished parsing arguments, now try to perform the tasks
        try {
            // Check if the image is purely black where every pixel is (0, 0, 0)
            grayImg = ImageProcessing.GrayScaleImage(inputImg);
            if (ArrayHelper.SumArray(grayImg, inputWidth) < 1) {
                ErrorMessage("An image of purely black is passed in as an input image, ");
            }
            taskStatus.FinishTask();

            // Extract the Sobel magnitude & orientation feature maps
            CreateMagMap();
            taskStatus.FinishTask();
            CreateOriMap();
            taskStatus.FinishTask();

            // Create the canvas and brushes
            CreateBrushes(scale);

            // Get the input image name
            canvas.name = name.substring(0, name.lastIndexOf("."));
            // Add gaussian noise to the image if user specified
            if (noiseSigma > 0) {
                AddGaussianNoise();
                inputImg.WritePPM(canvas.name + "noisy.ppm");
            }
            taskStatus.FinishTask();
            System.gc();

            // Painterly render
            canvas.PaintAll();
            taskStatus.FinishTask();
            canvas.RenderImages();
            taskStatus.FinishTask();
        } catch (Exception e) {
            // Specify when the exception took place
            System.out.printf("\nError encountered during task %d\n\n", taskStatus.taskID);
            throw e;
        }
    }
}
