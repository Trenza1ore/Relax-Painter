/**
 * This PixelRotator class implements Runnable and is a task that a 
 * fixed-size thread pool can execute, rotating 2D image arrays 
 * pixel-by-pixel is the most time-consuming task except for painting 
 * the strokes, however each stroke has to be evaluated in chronological 
 * order and I can only multi-thread the rotation as a result <p>
 * Yes, I can indeed multi-thread painting by locking the canvas array, 
 * but it can be problematic and would also break the ability to produce 
 * identical results given a set random seed... So, no
 * @author Hugo (Jin Huang)
 */
public class PixelRotator implements Runnable
{
    private float a, b, c, d;
    private int[][] srcImg, dstImg;
    private int n, m; 

    /**
     * PixelRotator rotates a square source image by a rotation defined in 
     * a rotation matrix, and stores the rotated result in a destination 
     * image, target size (size of destination image) equals to the diagonal 
     * length of the square source image, it's passed in as an argument to 
     * prevent calculating it each time a PixelRotator is initiated
     * 
     * @param srcImg source image (grayscale or one colour channel)
     * @param dstImg destination image (grayscale or one colour channel)
     * @param inputSize source image's size
     * @param targetSize destination image's size
     * @param rotMatrix rotation matrix
     */
    public PixelRotator(int[][] srcImg, int[][] dstImg, int inputSize, int targetSize, float[] rotMatrix)
    {
        this.srcImg = srcImg;
        this.dstImg = dstImg;
        n = inputSize;
        m = targetSize;
        // Extract elements in the rotation matrix
        a = rotMatrix[0];
        b = rotMatrix[1];
        c = rotMatrix[2];
        d = rotMatrix[3];
    }

    /**
     * PixelRotator rotates a square source image by an angle and stores the 
     * rotated result in a destination image, target size 
     * (size of destination image) equals to the diagonal length of the square 
     * source image, it's passed in as an argument to prevent calculating it 
     * each time a PixelRotator is initiated
     * 
     * @param srcImg source image (grayscale or one colour channel)
     * @param dstImg destination image (grayscale or one colour channel)
     * @param inputSize source image's size
     * @param targetSize destination image's size
     * @param angle the angle to rotate the source image by
     */
    public PixelRotator(int[][] img, int[][] imgCopy, int imgSize, float angle)
    {
        this.srcImg = img;
        this.dstImg = imgCopy;
        n = imgSize;
        m = (int) Math.sqrt(2*n*n) + 1;
        float[] rotMatrix = ArrayHelper.RotationMatrix(angle);
        // Extract elements in the rotation matrix
        a = rotMatrix[0];
        b = rotMatrix[1];
        c = rotMatrix[2];
        d = rotMatrix[3];
    }

    @Override
    public void run()
    {
        int i, j;
        // Offset from the centre of the image
        float inputMid = ((float) (n + 1))/2 - 1, targetMid = ((float) (m + 1))/2 - 1;
        // X and Y Coordinates before and after transformation by the rotation matrix
        float x0, x1, y0, y1;
        int xLo, xHi, yLo, yHi;
        
        // For every brush pixel (value 0) in the input image:
        // - calculate its position vector relative to the mid-point of the input image
        // - rotate the position vector
        // - map the brush pixel to the target image's vector space
        // - mark its 4 closest neighbouring pixels in the target image
        // If a pixel in the target image has been marked more than once, it is a brush pixel
        for (i = 0; i < n; i++) {
            for (j = 0; j < n; j++) {
                if (srcImg[i][j] == 0) {
                    x0 = (j - inputMid);
                    y0 = (i - inputMid);
                    // Matrix multiplication, unravelled
                    x1 = a*x0 + b*y0 + targetMid;
                    y1 = c*x0 + d*y0 + targetMid;
                    // Map the rotated brush pixel to imgCopy
                    yLo = Math.min((int) y1, m-1);
                    yHi = Math.min(yLo+1, m-1);
                    xLo = Math.min((int) x1, m-1);
                    xHi = Math.min(xLo+1, m-1);
                    dstImg[xLo][yLo] += 1;
                    dstImg[xLo][yHi] += 1;
                    dstImg[xHi][yLo] += 1;
                    dstImg[xHi][yHi] += 1;
                }
            }
        }
        
        // If a pixel in the destination image is the neightbour of two or more
        // pixels in the rotated source image, mark it as a brush pixel, otherwise
        // the pixel is background and need to be 0.
        // The brushes are mask arrays, so I've made the following changes:
        // Brush pixels are now 1 instead of 0 in the pgm files
        // Background pixels are now 0 instead of 255 in the pgm files
        for (i = 0; i < m; i++) {
            for (j = 0; j < m; j++) {
                if (dstImg[i][j] < 2) {
                    dstImg[i][j] = 0;
                } else {
                    dstImg[i][j] = 1;
                }
            }
        }
    }
}