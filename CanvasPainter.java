import java.util.concurrent.locks.ReentrantLock;

/**
 * This CanvasPainter class implements Runnable and is a task that a
 * fixed-size thread pool can execute, implementing optimistic locking
 * for concurrent painting operations.
 * @author Hugo (Jin Huang)
 */
public class CanvasPainter implements Runnable
{
    private Canvas canvas;
    private int brushType;
    private int s;
    private double edgeOrientation;
    private int x;
    private int y;
    private boolean noisyStroke;
    private ReentrantLock lock;

    /**
     * Constructor for CanvasPainter
     *
     * @param canvas the Canvas object to paint on
     * @param brushType 0 for compact brush, 1 for elongated brush
     * @param s size of the brush to use
     * @param edgeOrientation sobel orientation of the source image at position (x, y)
     * @param x row-position of the proposed stroke on canvas
     * @param y column-position of the proposed stroke on canvas
     * @param noisyStroke whether to use noisy stroke rendering
     * @param lock the lock for synchronizing access to the canvas
     */
    public CanvasPainter(Canvas canvas, int brushType, int s, double edgeOrientation,
                         int x, int y, boolean noisyStroke, ReentrantLock lock)
    {
        this.canvas = canvas;
        this.brushType = brushType;
        this.s = s;
        this.edgeOrientation = edgeOrientation;
        this.x = x;
        this.y = y;
        this.noisyStroke = noisyStroke;
        this.lock = lock;
    }

    @Override
    public void run()
    {
        int row, col;
        int[][] brush = canvas.brushes[brushType][s][canvas.FindClosestBrushAngle(edgeOrientation)];

        // colour of normal stroke and noisy stroke
        int[] strokeColour = new int[3], strokeColourNoisy;

        // size/half size/surface area of the selected brush
        int brushSize = canvas.diagLen[brushType][s], hfBrushSize = brushSize / 2, maskArea = 0;
        float maskAreaFloat; // use float for improved accuracy when dividing

        // Calculate the area we need to draw on
        x -= hfBrushSize; y -= hfBrushSize; // get the coordinate of the starting pixel
        int rowStart = Integer.max(0, x), rowEnd = Integer.min(x+brushSize, canvas.width),
            colStart = Integer.max(0, y), colEnd = Integer.min(y+brushSize, canvas.height),
            colLen = colEnd - colStart;

        // Calculate the colour of the stroke (doesn't need lock, only reads from srcImg)
        maskArea = 0;
        strokeColour[0] = 0;
        strokeColour[1] = 0;
        strokeColour[2] = 0;

        if (noisyStroke) {
            // If a noisy stroke is wanted
            strokeColourNoisy = new int[3];
            for (row = rowStart; row < rowEnd; row++) {
                for (col = colStart; col < colEnd; col++) {
                    if (brush[row-x][col-y] > 0) {
                        maskArea++;
                        strokeColour[0] += canvas.srcImg[0][row][col];
                        strokeColour[1] += canvas.srcImg[1][row][col];
                        strokeColour[2] += canvas.srcImg[2][row][col];
                        strokeColourNoisy[0] += canvas.srcImgNoise[0][row][col];
                        strokeColourNoisy[1] += canvas.srcImgNoise[1][row][col];
                        strokeColourNoisy[2] += canvas.srcImgNoise[2][row][col];
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
                        strokeColour[0] += canvas.srcImg[0][row][col];
                        strokeColour[1] += canvas.srcImg[1][row][col];
                        strokeColour[2] += canvas.srcImg[2][row][col];
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

        // Optimistic locking: retry loop
        int maxRetries = 100; // Prevent infinite loops
        int retryCount = 0;
        int[][] regionVersions = null;
        int[][][] newCanvas = null;

        while (retryCount < maxRetries) {
            if (canvas.safe) {
                // Acquire lock to read versions and clone canvas atomically
                lock.lock();
                try {
                    // Read version numbers for the region (inside lock for consistency)
                    regionVersions = new int[rowEnd - rowStart][colEnd - colStart];
                    for (row = rowStart; row < rowEnd; row++) {
                        for (col = colStart; col < colEnd; col++) {
                            regionVersions[row - rowStart][col - colStart] = canvas.canvasVersion[row][col];
                        }
                    }
                    // Release lock before actual computation
                } finally {
                    lock.unlock();
                }
            }

            // Clone canvas region for evaluation (inside lock, after reading versions)
            newCanvas = ArrayHelper.CloneImage(canvas.canvas, rowStart, rowEnd, colStart, colEnd);

            // Paint the new stroke on the cloned canvas (outside lock)
            for (row = rowStart; row < rowEnd; row++) {
                for (col = colStart; col < colEnd; col++) {
                    if (brush[row-x][col-y] > 0) {
                        newCanvas[0][row-rowStart][col-colStart] = strokeColour[0];
                        newCanvas[1][row-rowStart][col-colStart] = strokeColour[1];
                        newCanvas[2][row-rowStart][col-colStart] = strokeColour[2];
                    }
                }
            }

            // Re-acquire lock for version check and potential update
            lock.lock();
            try {
                if (canvas.safe) {
                    // Check if version changed (optimistic check)
                    boolean versionChanged = false;
                    for (row = rowStart; row < rowEnd; row++) {
                        for (col = colStart; col < colEnd; col++) {
                            if (canvas.canvasVersion[row][col] != regionVersions[row - rowStart][col - colStart]) {
                                versionChanged = true;
                                break;
                            }
                        }
                        if (versionChanged) break;
                    }

                    // If version changed, restart
                    if (versionChanged) {
                        retryCount++;
                        continue;
                    }
                }

                // Keep the stroke if it makes the canvas more similar to the input image
                if (canvas.EvalStroke(newCanvas, rowStart, rowEnd, colStart, colEnd)) {
                    // Increment version for the region
                    for (row = rowStart; row < rowEnd; row++) {
                        for (col = colStart; col < colEnd; col++) {
                            canvas.canvasVersion[row][col]++;
                        }
                    }

                    // Render this stroke onto the canvas
                    if (noisyStroke) {
                        // If noisy strokes are wanted, render the noisy stroke
                        for (row = rowStart; row < rowEnd; row++) {
                            for (col = colStart; col < colEnd; col++) {
                                if (brush[row-x][col-y] > 0) {
                                    canvas.canvas[0][row][col] = strokeColourNoisy[0];
                                    canvas.canvas[1][row][col] = strokeColourNoisy[1];
                                    canvas.canvas[2][row][col] = strokeColourNoisy[2];
                                }
                            }
                        }
                    } else {
                        // If no noise, then just render the normal stroke
                        for (row = rowStart; row < rowEnd; row++) {
                            System.arraycopy(
                                newCanvas[0][row-rowStart], 0, canvas.canvas[0][row], colStart, colLen
                            );
                            System.arraycopy(
                                newCanvas[1][row-rowStart], 0, canvas.canvas[1][row], colStart, colLen
                            );
                            System.arraycopy(
                                newCanvas[2][row-rowStart], 0, canvas.canvas[2][row], colStart, colLen
                            );
                        }
                    }
                }
                // Successful, exiting
                break;
            } finally {
                lock.unlock();
            }
        }
        // If we exhausted retries, the stroke was not applied
    }
}
