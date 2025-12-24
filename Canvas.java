import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This canvas class produces paintings according to an input source image, 
 * the painting process can be multi-threaded by locking the canvas array, 
 * but it can be problematic and would also break the ability to produce 
 * identical results given a set random seed...
 * @author Hugo (Jin Huang)
 */
public class Canvas {
    public int[][][][][] brushes;
    public int[][][] srcImg, srcImgNoise, canvas;
    public int[][] diagLen, canvasVersion;
    public int width, height, threads = 0;
    public boolean safe = true;
    public String name = "Result";
    private TaskManager taskStatus;
    private Random RNG;
    private double PD;
    private boolean noisyRender;
    private int[][] magMap, DoG, scaledSize;
    private boolean[][] visited;
    private double[][] oriMap;
    private double[] brushAngles;
    private int[] DoGValue = {0, 255};
    private ReentrantLock canvasLock;

    
    public Canvas(TaskManager taskStatus, int[][][] srcImg, int[][] magMap, double[][] oriMap, int[][] DoG, 
        double[] brushAngles, int width, int height, double PD, double noiseSigma, long randomSeed)
    {
        this.taskStatus = taskStatus;
        // If noise would be added, break the reference with source image
        if (noiseSigma > 0) {
            this.srcImg = ArrayHelper.CloneImage(srcImg, width);
            noisyRender = true;
        } else {
            this.srcImg = srcImg;
            noisyRender = false;
        }
        this.srcImgNoise = srcImg;
        this.magMap = magMap;
        this.oriMap = oriMap;
        this.DoG = DoG;
        this.brushAngles = brushAngles;
        this.width = width;
        this.height = height;
        this.PD = PD;
        scaledSize = new int[2][];
        diagLen = new int[2][];
        brushes = new int[2][][][][];
        canvasLock = new ReentrantLock();
        ReSeed(randomSeed);
        Clear();
    }
    
    
    /** 
     * Set up the compact brush for the canvas
     * 
     * @param compactBrushes an array of brushes of different scales and orientations, 
     * array shape = (scales, orientations)
     * @param scaledSize0 size of the original brush
     * @param diagLen0 size of the rotated brushes
     */
    public void SetUpCompactBrush(int[][][][] compactBrushes, int[] scaledSize0, int[] diagLen0)
    {
        brushes[0] = compactBrushes;
        scaledSize[0] = scaledSize0;
        diagLen[0] = diagLen0;
    }
    
    
    /** 
     * Set up the elongated brush for the canvas
     * 
     * @param elongatedBrushes an array of brushes of different scales and orientations, 
     * array shape = (scales, orientations)
     * @param scaledSize1 size of the original brush
     * @param diagLen1 size of the rotated brushes
     */
    public void SetUpElongatedBrush(int[][][][] elongatedBrushes, int[] scaledSize1, int[] diagLen1)
    {
        brushes[1] = elongatedBrushes;
        scaledSize[1] = scaledSize1;
        diagLen[1] = diagLen1;
    } 
    
    
    /** 
     * Set a seed for the random number generator
     * 
     * @param randomSeed seed for the random number generator
     */
    public void ReSeed(long randomSeed)
    {
        if (RNG == null) {
            RNG = new Random(randomSeed);
        } else {
            RNG.setSeed(randomSeed);
        }
    }

    
    /**
     * Clear the canvas
     */
    public void Clear()
    {
        canvas = new int[3][width][height];
        if (safe)
            canvasVersion = new int[width][height];
    }
    
    
    /** 
     * Evaluate a stroke by comparing the canvas's Absolute Difference with the source image 
     * before and after the stroke's addition, returns true if the stroke is an improvement
     * 
     * @param newCanvas part of the canvas after the stroke's addition, corresponds to 
     * the area [rowStart, rowEnd), [colStart, colEnd) on the full canvas
     * @param rowStart lower bound in rows (inclusive)
     * @param rowEnd upper bound in rows (exclusive)
     * @param colStart lower bound in columns (inclusive)
     * @param colEnd upper bound in columns (exclusive)
     * @return boolean whether the stroke is an improvement
     */
    boolean EvalStroke(int[][][] newCanvas, int rowStart, int rowEnd, int colStart, int colEnd)
    {
        int oldAE = 0, newAE = 0; // Absolute errors
        int i, j, k; // index for channel/row/column

        for (i = 0; i < 3; i++) {
            for (j = rowStart; j < rowEnd; j++) {
                for (k = colStart; k < colEnd; k++) {
                    // To assist unpainted area detection, canvas colors are increased by 1
                    oldAE += Math.abs(srcImg[i][j][k] - canvas[i][j][k]-1); 
                    newAE += Math.abs(srcImg[i][j][k] - newCanvas[i][j-rowStart][k-colStart]-1);
                }
            }
        }

        return (oldAE > newAE);
    }
    
    
    /** 
     * Find the closest orientation of a brush given an angle
     * 
     * @param angle the angle to match
     * @return int index of the closest orientation
     */
    int FindClosestBrushAngle(double angle)
    {
        double next, current = Math.abs(brushAngles[0] - angle);
        
        for (int i = 0; i < 15; i++) {
            next = Math.abs(brushAngles[i+1] - angle);
            if (current < next) {
                // Check if the angle is between the first and last brush angle
                if (i == 0) {
                    // If the last brush angle is actually closer, return its index
                    if (Math.abs((brushAngles[15] - 2 * Math.PI) - angle) < current) {
                        return 15;
                    }
                }
                return i;
            }
            current = next;
        }
        return 15; // the last brush angle is the closest
    }

    
    /**
     * Paint the canvas in full
     */
    public void PaintAll()
    {
        int i;
        long N;
        
        // Start the timer for the sub tasks
        taskStatus.StartSubTask();

        // First pass with compact brushes
        for (i = 0; i < 5; i++) {
            N = Math.round(PD*Math.pow(2, i));
            PaintStrokes(0, i, N, noisyRender && i > 1);
            // One scale of the compact brush strokes painted onto the canvas, start next sub task 
            taskStatus.FinishSubTask();
        }

        // Double the density in the second pass
        PD *= 2;

        // Second pass with elongated brushes
        for (i = 0; i < 5; i++) {
            N = Math.round(PD*Math.pow(2, i));
            PaintStrokes(1, i, N, noisyRender && i > 1);
            // One scale of the elongated brush strokes painted onto the canvas, start next sub task 
            taskStatus.FinishSubTask();
        }
    }
    
    
    /** 
     * Paint all the strokes of a given size of a given brush, if the edge magnitude 
     * corresponds to i and the thresholded MDoG value is appropriate 
     * (compact: 0, elongated: 255) <p>
     *  s | i | Size | Magnitude <p>
     *  4 | 0 |  5/5 |[  0,  51] <p>
     *  3 | 1 |  4/5 |[ 52, 102] <p>
     *  2 | 2 |  3/5 |[103, 153] <p>
     *  1 | 3 |  2/5 |[154, 204] <p>
     *  0 | 4 |  1/5 |[205, 255] <p>
     * 
     * @param brushType 0 for compact brush, 1 for elongated brush
     * @param i index of brush
     * @param N number of positions to consider painting a stroke
     * @param noisyStroke whether the stroke's colour should be noisy
     */
    public void PaintStrokes(int brushType, int i, long N, boolean noisyStroke)
    {
        int x, y;
        int nThreads = Math.abs(threads);
        if (nThreads < 2)
            nThreads = Runtime.getRuntime().availableProcessors();

        if (nThreads == 1) {
            // Draw the random strokes
            for (int p = 0; p < N; p++) {
                x = RNG.nextInt(width);
                y = RNG.nextInt(height);
                // Only paint if the edge magnitude corresponds to i and the thresholded 
                // MDoG value is appropriate (compact: 0, elongated: 255)
                // For Java's integers, -1/n = 0, so this condition works
                if ((DoG[x][y] == DoGValue[brushType]) && (((magMap[x][y]-1) / 51) == i)) {
                    // Only render the stroke as a noisy stroke if the stroke's size is
                    // 1/5 or 2/5 or 3/5 of the original brush size and the user wants noise
                    Paint(brushType, 4 - i, oriMap[x][y], x, y, noisyStroke);
                }
            }
        } else {
            // Create a thread pool for concurrent painting
            ExecutorService executor = Executors.newFixedThreadPool(nThreads);
            java.util.List<Future<?>> futures = new java.util.ArrayList<>();

            // Draw the random strokes
            for (int p = 0; p < N; p++) {
                x = RNG.nextInt(width);
                y = RNG.nextInt(height);
                // Only paint if the edge magnitude corresponds to i and the thresholded 
                // MDoG value is appropriate (compact: 0, elongated: 255)
                // For Java's integers, -1/n = 0, so this condition works
                if ((DoG[x][y] == DoGValue[brushType]) && (((magMap[x][y]-1) / 51) == i)) {
                    // Only render the stroke as a noisy stroke if the stroke's size is
                    // 1/5 or 2/5 or 3/5 of the original brush size and the user wants noise
                    CanvasPainter painter = new CanvasPainter(
                        this, brushType, 4 - i, oriMap[x][y], x, y, noisyStroke, canvasLock
                    );
                    futures.add(executor.submit(painter));
                }
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            executor.shutdown();
        }
    }

    /** 
     * Attempt to paint a stroke at position (x, y) with a brush with size (s+1)/5 
     * of the original, the stroke is only painted if it improves the canvas's similarity
     * with the source image, the colour of the stroke is an average of all the pixels 
     * it masks in the source image, the colour is increased by 1 in each channel to 
     * assist the detection of unpainted areas 
     * (e.g. if the average colour is pure black, it is painted as [1, 1, 1]) <p>
     * If the user has specifed that they want a noisy rendering, then the stroke's 
     * colour when evaluating whether it improves the canvas's similarity with the
     * source image is normal, but the final painted stroke colour of smaller strokes 
     * (1/5, 2/5, 3/5 of the original brush size) would be an average of all the pixels masked 
     * by the stroke in the noisy version of the source image
     * 
     * @param brushType 0 for compact brush, 1 for elongated brush
     * @param s size of the brush to use, 
     * @param edgeOrientation sobel orientation of the source image at position (x, y)
     * @param x row-position of the proposed stroke on canvas
     * @param y column-position of the proposed stroke on canvas
     * @param noisyStroke whether the stroke's colour should be noisy
     */
    public void Paint(int brushType, int s, double edgeOrientation, int x, int y, boolean noisyStroke)
    {
        int row, col;
        int[][] brush = brushes[brushType][s][FindClosestBrushAngle(edgeOrientation)];

        // colour of normal stroke and noisy stroke
        int[] strokeColour = new int[3], strokeColourNoisy; 

        // size/half size/surface area of the selected brush
        int brushSize = diagLen[brushType][s], hfBrushSize = brushSize / 2, maskArea = 0;
        float maskAreaFloat; // use float for improved accuracy when dividing

        // Calculate the area we need to draw on
        x -= hfBrushSize; y -= hfBrushSize; // get the coordinate of the starting pixel
        int rowStart = Integer.max(0, x), rowEnd = Integer.min(x+brushSize, width),
            colStart = Integer.max(0, y), colEnd = Integer.min(y+brushSize, height),
            colLen = colEnd - colStart;
        
        int[][][] newCanvas = ArrayHelper.CloneImage(canvas, rowStart, rowEnd, colStart, colEnd);
    
        // Calculate the colour of the stroke
        // For performance, unravel the for-loops that iterate the colour channels
        if (noisyStroke) {
            // If a noisy stroke is wanted
            strokeColourNoisy = new int[3];
            for (row = rowStart; row < rowEnd; row++) {
                for (col = colStart; col < colEnd; col++) {
                    if (brush[row-x][col-y] > 0) {
                        maskArea++;
                        strokeColour[0] += srcImg[0][row][col];
                        strokeColour[1] += srcImg[1][row][col];
                        strokeColour[2] += srcImg[2][row][col];
                        strokeColourNoisy[0] += srcImgNoise[0][row][col];
                        strokeColourNoisy[1] += srcImgNoise[1][row][col];
                        strokeColourNoisy[2] += srcImgNoise[2][row][col];
                    }
                }
            }
            maskAreaFloat = maskArea;
            // Increment the mean colour by 1 for unpainted area detection
            strokeColourNoisy[0] = Math.round(strokeColourNoisy[0] / maskAreaFloat) + 1;
            strokeColourNoisy[1] = Math.round(strokeColourNoisy[1] / maskAreaFloat) + 1;
            strokeColourNoisy[2] = Math.round(strokeColourNoisy[2] / maskAreaFloat) + 1;
        } else {
            // Just calculate the normal stroke colour
            for (row = rowStart; row < rowEnd; row++) {
                for (col = colStart; col < colEnd; col++) {
                    if (brush[row-x][col-y] > 0) {
                        maskArea++;
                        strokeColour[0] += srcImg[0][row][col];
                        strokeColour[1] += srcImg[1][row][col];
                        strokeColour[2] += srcImg[2][row][col];
                    }
                }
            }
            maskAreaFloat = maskArea;
            strokeColourNoisy = strokeColour;
        }

        // Increment the mean colour by 1 for unpainted area detection
        strokeColour[0] = Math.round(strokeColour[0] / maskAreaFloat) + 1;
        strokeColour[1] = Math.round(strokeColour[1] / maskAreaFloat) + 1;
        strokeColour[2] = Math.round(strokeColour[2] / maskAreaFloat) + 1;

        // Paint the new stroke
        for (row = rowStart; row < rowEnd; row++) {
            for (col = colStart; col < colEnd; col++) {
                if (brush[row-x][col-y] > 0) {
                    newCanvas[0][row-rowStart][col-colStart] = strokeColour[0];
                    newCanvas[1][row-rowStart][col-colStart] = strokeColour[1];
                    newCanvas[2][row-rowStart][col-colStart] = strokeColour[2];
                }
            }
        }

        // Keep the stroke if it makes the canvas more similar to the input image
        if (EvalStroke(newCanvas, rowStart, rowEnd, colStart, colEnd)) {
            // Render this stroke onto the canvas
            if (noisyStroke) {
                // If noisy strokes are wanted, render the noisy stroke
                for (row = rowStart; row < rowEnd; row++) {
                    for (col = colStart; col < colEnd; col++) {
                        if (brush[row-x][col-y] > 0) {
                            canvas[0][row][col] = strokeColourNoisy[0];
                            canvas[1][row][col] = strokeColourNoisy[1];
                            canvas[2][row][col] = strokeColourNoisy[2];
                        }
                    }
                }
            } else {
                // If no noise, then just render the normal stroke
                for (row = rowStart; row < rowEnd; row++) {
                    System.arraycopy(newCanvas[0][row-rowStart], 0, canvas[0][row], colStart, colLen);
                    System.arraycopy(newCanvas[1][row-rowStart], 0, canvas[1][row], colStart, colLen);
                    System.arraycopy(newCanvas[2][row-rowStart], 0, canvas[2][row], colStart, colLen);
                }
            }
        }
    }

    /** 
     * Fill the unpainted areas (not all black areas are unpainted!), this implementation 
     * implements region growing via Breath-First Search: every pixel in the canvas is checked 
     * to see if it's unpainted (pixel value = 0, 0, 0), while there are still unpainted pixels 
     * in the input canvas, it would select the first unpainted pixel found as a new seed and 
     * use BFS to grow the region until all neighbouring unpainted pixels are grouped together 
     * this process is repeated until all pixels are checked, each region is then filled with the 
     * average colour of the corresponding pixels in the source image
     * 
     * @param canvas input canvas
     * @param offset the offset value of all pixels in the canvas, used to assist 
     * unpainted area detection
     */
    public void FillBackground(int[][][] canvas, int offset)
    {
        // A list of all unpainted regions in the canvas, each region is stored
        // as a list of coordinates (which are integer arrays with 2 elements) 
        LinkedList<LinkedList<int[]>> unpaintedRegions = new LinkedList<>();
        int row, col, i, childrenCount, rStart, cStart;
        int[][] canvasSlice = canvas[0];
        int[] bgColour = new int[3], pos;
        int regionCounter = 0;
        boolean searchNotFinished = true;

        // Use a search queue and a visited array to implement BFS
        LinkedList<int[]> searchQueue = new LinkedList<>();

        // At the start, every pixel are not visited
        visited = new boolean[width][height];
        rStart = 0; 
        cStart = 0;

        // Region growing (via breadth-first search)
        while (searchNotFinished) {
            // Find a pixel that is unpainted and hasn't been visited
            searchNotFinished = false;
            for (row = rStart; row < width; row++) {
                // Remember the last visited position
                if (row == rStart) {
                    col = cStart;
                } else {
                    col = 0;
                }
                for (; col < height; col++) {
                    // After visiting an unpainted pixel, push it into search queue as new seed
                    if (canvasSlice[row][col] == 0 && !visited[row][col]) {
                        searchQueue.push(new int[] { row, col });
                        visited[row][col] = true;
                        searchNotFinished = true;
                        // A new region's discovered, initialize its list of members
                        unpaintedRegions.add(new LinkedList<>());
                        // Memorize the last visited position
                        rStart = row;
                        cStart = col + 1;
                        break;
                    }
                }
                // If the for loops are exited via break, then there is a new seed
                if (searchNotFinished) {
                    break;
                }
            }

            // Breadth-first search the pixels in the seed's region
            while (!searchQueue.isEmpty()) {
                pos = searchQueue.removeLast();
                row = pos[0]; col = pos[1];
                
                // Visit the neighbouring unpainted pixels
                if (canvasSlice[row][col] == 0) {
                    unpaintedRegions.get(regionCounter).add(pos);
                    if (row > 0 && !visited[row-1][col]) {
                        visited[row-1][col] = true;
                        searchQueue.push(new int[] { row-1, col });
                    }
                    if (row < width-1 && !visited[row+1][col]) {
                        visited[row+1][col] = true;
                        searchQueue.push(new int[] { row+1, col });
                    }
                    if (col > 0 && !visited[row][col-1]) {
                        visited[row][col-1] = true;
                        searchQueue.push(new int[] { row, col-1 });
                    }
                    if (col < height-1 && !visited[row][col+1]) {
                        visited[row][col+1] = true;
                        searchQueue.push(new int[] { row, col+1 });
                    }
                }
            }
            regionCounter++;
        }

        // Paint all unpainted regions
        for (LinkedList<int[]> region : unpaintedRegions) {
            // Get the number of pixels in this region
            childrenCount = region.size();

            // Calculate the mean colour of this region
            for (i = 0; i < 3; i++) {
                bgColour[i] = 0;
                for (int[] pixelPos : region) {
                    bgColour[i] += srcImg[i][pixelPos[0]][pixelPos[1]];
                }
                bgColour[i] /= childrenCount;
                bgColour[i] += offset;
            }
            
            // Paint this region with its mean colour
            for (int[] pixelPos : region) {
                for (i = 0; i < 3; i++) {
                    canvas[i][pixelPos[0]][pixelPos[1]] = bgColour[i];
                }
            }
        }
    }
    
    /** 
     * Normalize all pixels' RGB values in a canvas from [offset, 255+offset] to the [0, 255] 
     * range, the offset value is used to differentiate the painted black pixels with the 
     * unpainted pixels: since a stroke of pure black (0, 0, 0) is incremented to 
     * (offset, offset, offset), the pixels that are still pure black (0, 0, 0) are unpainted
     * 
     * @param canvas input canvas
     * @param offset the offset value of all pixels in the canvas, used to assist 
     * unpainted area detection
     * @return int[][][] the canvas with all pixels' RGB values normalized to [0, 255]
     */
    public int[][][] NormalizeCanvas(int[][][] canvas, int offset)
    {
        int[][][] result = new int[3][width][height];
        int i, j, k;

        for (i = 0; i < 3; i++) {
            for (j = 0; j < width; j++) {
                for (k = 0; k < height; k++) {
                    result[i][j][k] = Integer.max(0, Integer.min(255, canvas[i][j][k] - offset));
                }
            }
        }

        return result;
    }

    /** 
     * Apply gaussian blur to a canvas (a colour image) with a standard deviation of sigma
     * 
     * @param canvas a colour image
     * @param sigma standard deviation
     * @return int[][][] blurred image
     */
    public int[][][] GaussianBlur(int[][][] canvas, int sigma)
    {
        int[][][] result = new int[3][][];

        for (int i = 0; i < 3; i++) {
            result[i] = ArrayHelper.Double2Int(ImageProcessing.GaussianBlur(canvas[i], sigma, width, height), width, height);
        }

        return NormalizeCanvas(result, 0);
    }

    
    /**
     * Render all of the resulting images and save them separately<p>
     * (The following images are always rendered)
     * <p>
     * - Result.ppm: the painted image
     * <p>
     * - Result-filled.ppm: the painted image with all unpainted pixels filled
     * <p>
     * (Optional gaussian-blurred images if -g flag is used when running Relaxation)
     * <p>
     * - Result-blur.ppm: blurred version of the painted image
     * <p>
     * - Result-filled-blur.ppm: blurred version of the painted image with unpainted pixels filled
     */
    public void RenderImages()
    {
        int[][][] img;

        // Render normal canvas
        img = NormalizeCanvas(canvas, 1);
        ArrayHelper.SaveImg(img, name + "-raw.ppm", 255, width, height);

        // Render normal canvas with unpainted pixels filled
        FillBackground(canvas, 1);
        img = NormalizeCanvas(canvas, 1);
        ArrayHelper.SaveImg(img, name + "-result.ppm", 255, width, height);
    }
}
