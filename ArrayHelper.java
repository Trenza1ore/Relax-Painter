import java.util.*;

/**
 * This ArrayHelper class has some array operations I wrote that are useful to image processing
 * @author Hugo (Jin Huang)
 */
public class ArrayHelper {
    
    /** 
     * Convert a 2D array of integers into a 2D array of doubles
     * 
     * @param arr 2D integer array
     * @param width arr's row number
     * @param height arr's column number
     * @return double[][] 2D double array
     */
    public static double[][] Int2Double(int[][] arr, int width, int height)
    {
        double[][] result = new double[width][height];
        int row, col;

        for (row = 0; row < width; row++) {
            for (col = 0; col < height; col++) {
                result[row][col] = arr[row][col];
            }
        }

        return result;
    }

    
    /** 
     * Convert a 2D array of doubles into a 2D array of integers
     * 
     * @param arr 2D double array
     * @param width arr's row number
     * @param height arr's column number
     * @return int[][] 2D integer array
     */
    public static int[][] Double2Int(double[][] arr, int width, int height)
    {
        int[][] result = new int[width][height];
        int row, col;

        for (row = 0; row < width; row++) {
            for (col = 0; col < height; col++) {
                result[row][col] = Integer.max(0, Integer.min(255, (int) Math.round(arr[row][col])));
            }
        }

        return result;
    }

    
    /** 
     * Convert a 3D array of doubles into a 2D array of integers, the shape is 
     * always (3, width, height) as we only need to convert RGB colour images
     * 
     * @param arr 3D double array (RGB colour image)
     * @param width arr's row number
     * @param height arr's column number
     * @return int[][] 3D integer array (RGB colour image)
     */
    public static int[][][] Double2Int(double[][][] arr, int width, int height)
    {
        int[][][] result = new int[3][width][height];

        for (int i = 0; i < 3; i++) {
            result[i] = Double2Int(arr[i], width, height);
        }

        return result;
    }

    
    /** 
     * Clones an image array (grayscale or one colour channel)
     * 
     * @param arr source image (grayscale or one colour channel)
     * @param width arr's row number
     * @return int[][] cloned array
     */
    public static int[][] CloneImage(int[][] arr, int width)
    {
        int[][] clone = new int[width][];

        for (int i = 0; i < width; i++) {
            clone[i] = arr[i].clone();
        }

        return clone;
    }

    
    /** 
     * Clones part of an image array (grayscale or one colour channel) corresponds to 
     * the area [rowStart, rowEnd), [colStart, colEnd) in the source image
     * 
     * @param arr source image (grayscale or one colour channel)
     * @param rStart lower bound in rows (inclusive)
     * @param rEnd upper bound in rows (exclusive)
     * @param cStart lower bound in columns (inclusive)
     * @param cEnd upper bound in columns (exclusive)
     * @return int[][] cloned array (of the specified area)
     */
    public static int[][] CloneImage(int[][] arr, int rStart, int rEnd, int cStart, int cEnd)
    {
        int rLen = rEnd - rStart, cLen = cEnd - cStart;
        
        return CloneImage(arr, rStart, rEnd, rLen, cStart, cEnd, cLen);
    }

    
    /** 
     * Clones part of an image array (grayscale or one colour channel) corresponds to 
     * the area [rowStart, rowEnd), [colStart, colEnd) in the source image
     * 
     * @param arr source image (grayscale or one colour channel)
     * @param rStart lower bound in rows (inclusive)
     * @param rEnd upper bound in rows (exclusive)
     * @param rLen rEnd - rStart
     * @param cStart lower bound in columns (inclusive)
     * @param cEnd upper bound in columns (exclusive)
     * @param cLen cEnd - cStart
     * @return int[][] cloned array (of the specified area)
     */
    public static int[][] CloneImage(int[][] arr, int rStart, int rEnd, int rLen, int cStart, int cEnd, int cLen)
    {
        int[][] clone = new int[rEnd - rStart][cEnd - cStart];

        for (int i = rStart; i < rEnd; i++) {
            System.arraycopy(arr[i], cStart, clone[i-rStart], 0, cLen);
        }

        return clone;
    }

    
    /** 
     * Clones an image array (colour image), the shape is 
     * always (3, width, height) as we only need to convert RGB colour images
     * 
     * @param arr source image (colour image)
     * @param width arr's width (row number in each colour channel's 2D array)
     * @return int[][][] cloned array
     */
    public static int[][][] CloneImage(int[][][] arr, int width)
    {
        int[][][] clone = new int[3][][];

        for (int i = 0; i < 3; i++) {
            clone[i] = CloneImage(arr[i], width);
        }

        return clone;
    }

    
    /** 
     * Clones part of an image array (colour image) corresponds to 
     * the area [rowStart, rowEnd), [colStart, colEnd) in the source image, 
     * the shape of source image is always (3, width, height) as we only 
     * need to convert RGB colour images
     * 
     * @param arr source image (colour image)
     * @param rStart lower bound in rows (inclusive)
     * @param rEnd upper bound in rows (exclusive)
     * @param cStart lower bound in columns (inclusive)
     * @param cEnd upper bound in columns (exclusive)
     * @return int[][][] cloned array (of the specified area)
     */
    public static int[][][] CloneImage(int[][][] arr, int rStart, int rEnd, int cStart, int cEnd)
    {
        int rLen = rEnd - rStart, cLen = cEnd - cStart;
        int[][][] clone = new int[3][][];

        for (int i = 0; i < 3; i++) {
            clone[i] = CloneImage(arr[i], rStart, rEnd, rLen, cStart, cEnd, cLen);
        }

        return clone;
    }

    
    /** 
     * Fill a 2D array of integers with an element
     * 
     * @param arr array to fill
     * @param element element to fill in
     * @param width arr's row number
     */
    public static void FillArray(int[][] arr, int element, int width)
    {
        int i;

        for (i = 0; i < width; i++) {
            Arrays.fill(arr[i], element);
        }
    }

    
    /** 
     * Fill a 3D array of integers with an element
     * 
     * @param arr array to fill
     * @param element element to fill in
     * @param width arr's row number (3 if filling a colour image)
     * @param height arr's column number
     */
    public static void FillArray(int[][][] arr, int element, int width, int height)
    {
        int i, j;

        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                Arrays.fill(arr[i][j], element);
            }
        }
    }

    
    /** 
     * Calculate the sum of all elements in a 2D array of integers
     * 
     * @param arr array to sum
     * @param size arr's row number
     * @return int the sum
     */
    public static int SumArray(int[][] arr, int size)
    {
        int i, result = 0;

        for (i = 0; i < size; i++) {
            result += Arrays.stream(arr[i]).sum();
        }

        return result;
    }

    
    /** 
     * Matrix addition, returns M0 + M1
     * 
     * @param arr0 M0
     * @param arr1 M1
     * @param r the arrays' row number
     * @param c the arrays' column number
     * @return int[][] M0 + M1
     */
    public static int[][] AddArray(int[][] arr0, int[][] arr1, int r, int c)
    {
        int i, j;
        int[][] result = new int[r][c];

        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                result[i][j] = arr0[i][j] + arr1[i][j];
            }
        }

        return result;
    }

    
    /** 
     * Add a matrix by a scalar, returns M0 + element1
     * 
     * @param arr0 M0
     * @param element1 element1
     * @param r M0's row number
     * @param c M0's column number
     * @return int[][] M0 + element1
     */
    public static int[][] AddArray(int[][] arr0, int element1, int r, int c)
    {
        int i, j;
        int[][] result = new int[r][c];

        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                result[i][j] = arr0[i][j] + element1;
            }
        }

        return result;
    }

    
    /** 
     * Matrix addition, in-place (change M0 directly)
     * 
     * @param arr0 M0
     * @param arr1 M1
     * @param r the arrays' row number
     * @param c the arrays' column number
     */
    public static void AddArrayInPlace(int[][] arr0, int[][] arr1, int r, int c)
    {
        int i, j;
        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                arr0[i][j] += arr1[i][j];
            }
        }
    }

    
    /** 
     * Add a matrix by a scalar, in-place (change M0 directly)
     * 
     * @param arr0 M0
     * @param element1 element1
     * @param r M0's row number
     * @param c M0's column number
     */
    public static void AddArrayInPlace(int[][] arr0, int element1, int r, int c)
    {
        int i, j;
        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                arr0[i][j] += element1;
            }
        }
    }

    
    /** 
     * Add a 3D tensor T0 by a scalar, in-place (change T0 directly), 
     * T0's shape is (d, r, c)
     * 
     * @param arr0 T0, shape is (d, r, c)
     * @param element1 element1
     * @param d T0's depth
     * @param r T0's row number
     * @param c T0's column number
     */
    public static void AddArrayInPlace(int[][][] arr0, int element1, int d, int r, int c)
    {
        for (int k = 0; k < d; k++) {
            AddArrayInPlace(arr0[k], element1, r, c);
        }   
    }

    
    /** 
     * Divide a matrix by a scalar, in-place (change M0 directly)
     * 
     * @param arr0 M0
     * @param factor f
     * @param r M0's row number
     * @param c M0's column number
     * @return int[][] M0 / f
     */
    public static int[][] DivArrayInPlace(int[][] arr0, int factor, int r, int c)
    {
        int i, j;
        float f = factor;

        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                arr0[i][j] = Math.round(arr0[i][j] / f);
            }
        }

        return arr0;
    }

    
    /** 
     * Multiply a matrix by a scalar, returns M0 * f
     * 
     * @param arr0 M0
     * @param factor f
     * @param r M0's row number
     * @param c M0's column number
     * @return int[][] M0 * f
     */
    public static int[][] MulArray(int[][] arr0, int factor, int r, int c)
    {
        int i, j;
        int[][] result = new int[r][c];

        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                result[i][j] = arr0[i][j] * factor;
            }
        }

        return result;
    }

    
    /** 
     * Multiply a matrix by a scalar, in-place (change M0 directly)
     * 
     * @param arr0 M0
     * @param factor f
     * @param r M0's row number
     * @param c M0's column number
     * @return int[][] M0 * f
     */
    public static int[][] MulArrayInPlace(int[][] arr0, int factor, int r, int c)
    {
        int i, j;

        for (i = 0; i < r; i++) {
            for (j = 0; j < c; j++) {
                arr0[i][j] *= factor;
            }
        }

        return arr0;
    }

    
    /** 
     * Normalize a 2D array of integers to the range [0, 255], in place
     * 
     * @param img an image (grayscale or one colour channel)
     * @param width arr's row number
     * @param height arr's column number 
     */
    public static void NormalizeImg(int[][] img, int width, int height)
    {
        int i, j, minVal = img[0][0], maxVal = img[0][0], diffMinMax;
        
        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                maxVal = Integer.max(maxVal, img[i][j]);
                minVal = Integer.min(minVal, img[i][j]);
            }
        }

        diffMinMax = maxVal - minVal;

        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                img[i][j] = (img[i][j] - minVal) * 255 / diffMinMax;
            }
        }
    }

    
    /** 
     * Normalize an array of doubles to an array of integers with range [0, cap], 
     * min-max normalization is used as it seems to be the most appropriate
     * 
     * @param arr array of doubles
     * @param width arr's row number
     * @param height arr's column number 
     * @param cap the cap (255 for 8-bit grayscale / 24-bit RGB colour images)
     * @return int[][] normalized array of integers
     */
    public static int[][] NormalizeToImg(double[][] arr, int width, int height, int cap)
    {
        int i, j;
        int[][] newImg = new int[width][height];
        double minVal = arr[0][0], maxVal = arr[0][0], diff;
        
        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                maxVal = Double.max(maxVal, arr[i][j]);
                minVal = Double.min(minVal, arr[i][j]);
            }
        }

        diff = maxVal - minVal;

        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                newImg[i][j] = (int) Math.round((arr[i][j] - minVal) * cap / diff);
            }
        }

        return newImg;
    }

    
    /** 
     * Normalize an array of doubles to an array of integers with range [0, 255], 
     * min-max normalization is used as it seems to be the most appropriate
     * 
     * @param arr array of doubles
     * @param width arr's row number
     * @param height arr's column number
     * @return int[][] normalized array of integers
     */
    public static int[][] NormalizeToImg(double[][] arr, int width, int height)
    {
        return NormalizeToImg(arr, width, height, 255);
    }

    
    /** 
     * Saves a grayscale image
     * @param norm whether the pixel values should be normalized
     */
    public static void SaveImg(int[][] pixels, String name, int depth, int width, int height, boolean norm)
    {
        Image img = new Image(depth, width, height);
        img.pixels = pixels;
        if (norm) {
            NormalizeImg(img.pixels, width, height);
        }
        img.WritePGM(name);
    }

    
    /** 
     * Saves a grayscale image with the same dimensions and colour depth as a template image
     * @param norm whether the pixel values should be normalized
     */
    public static void SaveImgLike(int[][] pixels, String name, ImagePPM image0, boolean norm)
    {
        SaveImg(pixels, name, image0.depth, image0.width, image0.height, norm);
    }

    
    /** 
     * Saves a grayscale image, the pixel values are normalized (since they are doubles)
     */
    public static void SaveImg(double[][] pixels, String name, int depth, int width, int height)
    {
        Image img = new Image(depth, width, height);
        img.pixels = NormalizeToImg(pixels, width, height);
        img.WritePGM(name);
    }

    
    /** 
     * Saves a colour image
     */
    public static void SaveImg(int[][][] pixels, String name, int depth, int width, int height)
    {
        ImagePPM img = new ImagePPM(depth, width, height);
        img.pixels = pixels;
        img.WritePPM(name);
    }
    
    
    /** 
     * Saves a grayscale image with the same dimensions and colour depth as a template image, 
     * the pixel values are normalized (since they are doubles)
     */
    public static void SaveImgLike(double[][] pixels, String name, ImagePPM templateImage)
    {
        SaveImg(pixels, name, templateImage.depth, templateImage.width, templateImage.height);
    }

    
    /** 
     * Create a flattened rotation matrix that rotates vectors by an angle
     * 
     * @param angle rotation angle (radian)
     * @return float[] flattened version of the rotation matrix
     */
    public static float[] RotationMatrix(double angle)
    {
        float cosTheta = (float) Math.cos(angle), sinTheta = (float) Math.sin(angle);
        return new float[] { cosTheta, -sinTheta, sinTheta, cosTheta };
    }

    
    /** 
     * Get a column of a 2D array of integers, since Java's 2D int arrays are essentially 
     * arrays of arrays of primitive int, there is no built-in faster implementations 
     * to get a column for us
     * 
     * @param arr the 2D array
     * @param col index
     * @param width the row number of arr
     * @return int[] the column (arr[:][col])
     */
    public static int[] GetCol(int[][] arr, int col, int width)
    {
        int[] column = new int[width];

        for (int i = 0; i < width; i++) {
            column[i] = arr[i][col];
        }

        return column;
    }
}
