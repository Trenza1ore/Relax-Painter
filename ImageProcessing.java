import java.util.*;

/**
 * This ImageProcessing class has some methods I wrote that are useful to image processing
 * @author Hugo (Jin Huang)
 */
public class ImageProcessing {
    // Kernal of Sobel operator, approximates horizontal derivatives
    // its transpose approximates vertical derivative
    // Since our input image is transposed (due to ImagePPM's implementation)
    // so must our sobel operator
    public static int[][] sobelX = {
        { 1,  2,  1},
        { 0,  0,  0},
        {-1, -2, -1}
    };

    /**
     * Threshold a grayscale image at a set threshold value
     *
     * @param img input image (grayscale or one colour channel)
     * @param threshold the threshold value
     * @param width img's row number
     * @param height img's column number
     */
    public static void Threshold(int[][] img, int threshold, int width, int height)
    {
        int i, j;

        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                if (img[i][j] < threshold) {
                    img[i][j] = 0;
                } else {
                    img[i][j] = 255;
                }
            }
        }
    }


    /**
     * Creates the grayscale version of an image (equal RGB weighting)
     *
     * @param img input image
     * @return int[][] grayscale image
     */
    public static int[][] GrayScaleImage(ImagePPM img)
    {
        int i, j;
        int[][] result = new int[img.width][img.height];

        for (i = 0; i < img.width; i++) {
            for (j = 0; j < img.height; j++) {
                result[i][j] = Math.round((img.pixels[0][i][j]+img.pixels[1][i][j]+img.pixels[2][i][j])/3f);
            }
        }

        return result;
    }


    /**
     * Apply a odd-number-sized box filter to a grayscale image
     *
     * @param img input image (grayscale or one colour channel)
     * @param width img's row number
     * @param height img's column number
     * @param filterSize size of the box filter (must be a positive odd integer)
     * @return int[][] filtered image
     */
    public static int[][] BoxFilter(int[][] img, int width, int height, int filterSize)
    {
        if (filterSize % 2 == 0 || filterSize < 1) {
            Relaxation.ErrorMessage("Box filter size is not a positive odd number!");
        }

        int row, col, i, j, pixel, offset = filterSize / 2;
        float filterArea = filterSize*filterSize;
        int[][] result = new int[width][height], padded = PadBorder(img, offset, 'R', width, height);

        // Filter all pixels
        for (row = 0; row < width; row++) {
            for (col = 0; col < height; col++) {
                // Calculate average of the pixel's filterSize x filterSize surrounding area
                pixel = 0;
                for (i = 0; i < filterSize; i++) {
                    for (j = 0; j < filterSize; j++) {
                        pixel += padded[row+i][col+j];
                    }
                }
                result[row][col] = Math.round(pixel/filterArea);
            }
        }

        return result;
    }


    /**
     * Calculates the Multi-scale Difference of Gaussians of a grayscale image,
     * for every sigma (standard deviation) passed in, an unsigned DoG is calculated by
     * subtracting GaussianBlur(img, sigma) from GaussianBlur(img, 2 * sigma)
     *
     * @param img input image (grayscale or one colour channel)
     * @param width img's row number
     * @param height img's column number
     * @param sigmas the standard deviation
     * @return int[][] Multi-scale Difference of Gaussians
     */
    public static int[][] MDoG(int[][] img, int width, int height, int[] sigmas)
    {
        int row, col, sigma2;
        double[][] MDoG = new double[width][height], gauss, gauss2;
        Map<Integer, double[][]> cached = new HashMap<>();

        for (int sigma: sigmas) {
            sigma2 = 2 * sigma;
            // Cache the results to prevent redundant calculations
            // Useful if duplication is common
            if (cached.containsKey(sigma)) {
                gauss = cached.get(sigma);
            } else {
                gauss = GaussianBlur(img, sigma, width, height);
                cached.put(sigma, gauss);
            }
            if (cached.containsKey(sigma2)) {
                gauss2 = cached.get(sigma2);
            } else {
                gauss2 = GaussianBlur(img, sigma2, width, height);
                cached.put(sigma2, gauss2);
            }
            // Save DoG as the absolute difference and add to MDoG
            for (row = 0; row < width; row++) {
                for (col = 0; col < height; col++) {
                    MDoG[row][col] += Math.abs(gauss2[row][col] - gauss[row][col]);
                }
            }
        }

        return ArrayHelper.NormalizeToImg(MDoG, width, height);
    }


    /**
     * FOR DEBUG USE ONLY, can be used to verify the result of the GaussianBlur method
     * which uses two-pass 1D convolution with a 1D gaussian kernel instead of the
     * very inefficient 2D gaussian kernel used here
     *
     * @param img input image (grayscale or one colour channel)
     * @param sigma standard deviation of the gaussian function
     * @param width img's row number
     * @param height img's column number
     * @return double[][] filtered image, double is used instead of int for accuracy
     */
    public static double[][] GaussianBlur2D(int[][] img, int sigma, int width, int height)
    {
        // Gaussian filter can't have sigma <= 0
        if (sigma < 1) {
            return ArrayHelper.Int2Double(img, width, height);
        }

        int i, j, kernelSize = 6 * sigma + 1, radius = 3 * sigma;
        double twoSigmaSq = 2 * Math.pow(sigma, 2), firstTerm, tmp, kernelSum = 0;
        double[] kernel = new double[kernelSize];
        double[][] kernel2D = new double[kernelSize][kernelSize];

        // Create the gaussian kernel at given sigma
        firstTerm = 1 / (Math.PI * twoSigmaSq);
        for (i = 0; i < radius+1; i++) {
            tmp = firstTerm * Math.exp(-(i*i) / twoSigmaSq);
            kernel[radius-i] = tmp;
            kernel[radius+i] = tmp;
        }

        kernelSum = 0;
        for (i = 0; i < kernelSize; i++) {
            for (j = 0; j < kernelSize; j++) {
                kernel2D[i][j] = kernel[i] * kernel[j];
                kernelSum += kernel2D[i][j];
            }
        }

        for (i = 0; i < kernelSize; i++) {
            for (j = 0; j < kernelSize; j++) {
                kernel2D[i][j] /= kernelSum;
            }
        }

        return Conv2D(img, kernel2D, width, height, kernelSize);
    }


    /**
     * An implementation of gaussian blur that uses a 1D gaussian filter, the filter size
     * equals to (6*sigma)+1, this filter size captures almost the whole bell shape and
     * provides a great balance between efficiency and accuracy, a two-pass 1D convolution
     * is utilized to gaussian blur the image at a reasonable speed
     *
     * @param img input image (grayscale or one colour channel)
     * @param sigma standard deviation of the gaussian function
     * @param width img's row number
     * @param height img's column number
     * @return double[][] filtered image, double is used instead of int for accuracy
     */
    public static double[][] GaussianBlur(int[][] img, int sigma, int width, int height)
    {
        // Gaussian filter can't have sigma <= 0
        if (sigma < 1) {
            return ArrayHelper.Int2Double(img, width, height);
        }

        int i, kernelSize = 6 * sigma + 1, radius = 3 * sigma;
        double twoSigmaSq = 2 * Math.pow(sigma, 2), firstTerm, tmp, kernelSum = 0;
        double[] kernel = new double[kernelSize];

        // Create the gaussian kernel at given sigma
        firstTerm = 1 / (Math.PI * twoSigmaSq);
        for (i = 0; i < radius+1; i++) {
            tmp = firstTerm * Math.exp(-(i*i) / twoSigmaSq);
            kernel[radius-i] = tmp;
            kernel[radius+i] = tmp;
        }

        kernelSum = Arrays.stream(kernel).sum();
        for (i = 0; i < kernelSize; i++) {
            kernel[i] /= kernelSum;
        }

        return Conv1DTwoPass(img, kernel, width, height, kernelSize);
    }


    /**
     * Apply a two-pass 1D convolution to a grayscale image, can be used for separable kernels
     * like gaussian kernel, box filter kernel or sobel kernel, this implementation only supports
     * kernels of odd-numbered size for simplicity
     *
     * @param img input image (grayscale or one colour channel)
     * @param kernel 1D kernel
     * @param width img's row number
     * @param height img's column number
     * @param kernelSize size of the 1D kernel
     * @return double[][] filtered image
     */
    public static double[][] Conv1DTwoPass(int[][] img, double[] kernel, int width, int height, int kernelSize)
    {
        int row, col, i, radius = kernelSize / 2, newWidth = width+radius+radius, newHeight = height+radius+radius;
        double pixel;
        double[][] result = new double[width][height],
            intermediateArr = new double[newWidth][newHeight],
            paddedArr = ArrayHelper.Int2Double(
                PadBorder(img, radius, 'R', width, height), newWidth, newHeight);

        // First pass (gaussian blur each column)
        for (col = 0; col < newHeight; col++) {
            for (row = 0; row < width; row++) {
                pixel = 0;
                for (i = 0; i < kernelSize; i++) {
                    pixel += kernel[i] * paddedArr[row+i][col];
                }
                intermediateArr[radius+row][col] = pixel;
            }
        }

        // Second pass (gaussian blur each row)
        for (row = 0; row < width; row++) {
            for (col = 0; col < height; col++) {
                pixel = 0;
                for (i = 0; i < kernelSize; i++) {
                    pixel += kernel[i] * intermediateArr[row+radius][col+i];
                }
                result[row][col] = pixel;
            }
        }

        return result;
    }


    /**
     * Apply a 2D convolution to a grayscale image, this implementation only supports square 2D
     * kernels of odd-numbered size for simplicity, written for debug purpose only
     *
     * @param img input image (grayscale or one colour channel)
     * @param kernel 2D kernel
     * @param width img's row number
     * @param height img's column number
     * @param kernelSize size of the 2D kernel
     * @return double[][] filtered image
     */
    public static double[][] Conv2D(int[][] img, double[][] kernel, int width, int height, int kernelSize)
    {
        int row, col, i, j, radius = kernelSize / 2, newWidth = width+radius+radius, newHeight = height+radius+radius;
        double pixel;
        double[][] result = new double[width][height];
        double[][] paddedArr = ArrayHelper.Int2Double(PadBorder(
            img, radius, 'R', width, height), newWidth, newHeight);

        // First pass (gaussian blur by column)
        for (row = 0; row < width; row++) {
            for (col = 0; col < height; col++) {
                pixel = 0;
                for (i = 0; i < kernelSize; i++) {
                    for (j = 0; j < kernelSize; j++) {
                        pixel += kernel[i][j] * paddedArr[row+i][col+j];
                    }
                }
                result[row][col] = pixel;
            }
        }

        return result;
    }


    /**
     * Create a sobel magnitude map of a colour image, the magnitudes across all three colour
     * channels are combined via a maximum operation, does not use a two-pass 1D convolution
     * because the input image can be rather high and the performance gained is insignificant
     *
     * @param img input image (grayscale or one colour channel)
     * @param width img's row number
     * @param height img's column number
     * @return int[][] unsigned sobel magnitude map
     */
    public static int[][] SobelMagnitude(int[][][] img, int width, int height)
    {
        int[][] sobelMap = new int[width][height];
        int row, col, ch; // row/column/color channel index for the image
        int i, j; // row/column index for sobel operator kernals
        int I; // value of a pixel in the input image
        int Gx, Gy; // horizontal and vertical derivatives
        double maxMag;

        // Compute the image's convolution with Sobel operator
        // Like week 3 lab solution's implementation, it has zeros at the border
        for (row = 0; row < width-2; row++) {
            for (col = 0; col < height-2; col++) {
                // Compute the gradient magnitude for pixel at [row+1][col+1]
                maxMag = 0;
                for (ch = 0; ch < 3; ch++) {
                    Gx = 0; Gy = 0;
                    // Calculate the image's convolution with sobel operator kernals
                    for (i = 0; i < 3; i++) {
                        for (j = 0; j < 3; j++) {
                            I = img[ch][row+i][col+j];
                            Gx += sobelX[i][j] * I;
                            Gy += sobelX[j][i] * I;
                        }
                    }
                    // Store the maximum magnitude across the 3 channels (RGB)
                    maxMag = Double.max(Math.sqrt(Gx*Gx + Gy*Gy), maxMag);
                }
                sobelMap[row+1][col+1] = (int) maxMag;
            }
        }
        return sobelMap;
    }


    /**
     * Pad a grayscale image at its borders, the padding method defaults to zero padding
     * but reflection padding provides the best effect for edge detection purpose
     * <p>
     * method 'R': reflection padding
     * <p>
     * method 'r': reflection padding (only reflect row, zero pad columns)
     * <p>
     * method 'c': reflection padding (only reflect column, zero pad rows)
     *
     * @param img input image (grayscale or one colour channel)
     * @param pad number of pixels that needs to be padded at each border
     * @param method default: zero padding
     * @param width img's row number
     * @param height img's column number
     * @return int[][] padded image
     */
    public static int[][] PadBorder(int[][] img, int pad, char method, int width, int height)
    {
        int newWidth = width + pad + pad, newHeight = height + pad + pad;
        int[][] result = new int[newWidth][newHeight];
        int[] borderL, borderR;
        int row, col;

        // Copy the elements of the old array into the center of the padded array
        for (row = pad; row < newWidth-pad; row++) {
            System.arraycopy(img[row-pad], 0, result[row], pad, height);
        }

        switch (method) {
            // Reflection padding
            case 'R':
                // Reflect the columns
                for (col = 0; col < pad; col++) {
                    borderL = ArrayHelper.GetCol(img, col, width);
                    borderR = ArrayHelper.GetCol(img, height-col-1, width);
                    for (row = pad; row < newWidth-pad; row++) {
                        result[row][pad-col-1] = borderL[row-pad];
                        result[row][pad+height+col] = borderR[row-pad];
                    }
                }
                // Reflect the rows
                for (row = 0; row < pad; row++) {
                    borderL = result[pad+row];
                    borderR = result[pad+width-row-1];
                    System.arraycopy(borderL, 0, result[pad-row-1], 0, newHeight);
                    System.arraycopy(borderR, 0, result[pad+width+row], 0, newHeight);
                }
                break;

            case 'r':
                // Reflect the rows
                for (row = 0; row < pad; row++) {
                    borderL = result[pad+row];
                    borderR = result[pad+width-row-1];
                    System.arraycopy(borderL, 0, result[pad-row-1], 0, newHeight);
                    System.arraycopy(borderR, 0, result[pad+width+row], 0, newHeight);
                }
                break;

            case 'c':
                // Reflect the columns
                for (col = 0; col < pad; col++) {
                    borderL = ArrayHelper.GetCol(img, col, width);
                    borderR = ArrayHelper.GetCol(img, height-col-1, width);
                    for (row = pad; row < newWidth-pad; row++) {
                        result[row][pad-col-1] = borderL[row-pad];
                        result[row][pad+height+col] = borderR[row-pad];
                    }
                }
                break;

            // Zero padding
            default:
                break;
        }

        return result;
    }


    /**
     * An inplementation of bi-linear interpolation, if the input image is zero-padded,
     * this method would automatically aligns it to be a perfect up-scale, otherwise
     * this is a semi-naive implementation that has the same pixel shifting problems,
     * an improved version with a half pixel offset won't benefit the painting anyway:
     * <p>
     * - Only sobel magnitude maps (which are zero-padded) are upsampled
     * <p>
     * - Only the brushes are downsampled (they also need to go through a rotation,
     * which would flatten the marginal gain in accuracy completely)
     *
     * @param img input image
     * @param inWidth input image's width (row number)
     * @param inHeight input image's height (column number)
     * @param tgWidth target image's width (row number)
     * @param tgHeight target image's height (column number)
     * @param inZeroPad whether the input has been zero-padded
     * @return int[][]
     */
    public static int[][] BilinearInterpolate(int[][] img, int inWidth, int inHeight, int tgWidth, int tgHeight, boolean inZeroPad)
    {
        double scaleFactorY = inWidth / (double) tgWidth, scaleFactorX = inHeight / (double) tgHeight;
        double x, y, yDiff, r, s;
        int x0, x1, y0, y1, offsetX = 0, offsetY = 0;
        // If input has a border of zeros, offset image by one unit in both directions
        if (inZeroPad) {
            offsetX = (int) Math.round(tgHeight / (double) inHeight / 2);
            offsetY = (int) Math.round(tgWidth / (double) inWidth / 2);
        }
        int row, col;
        int[][] result = new int[tgWidth][tgHeight];

        // Implementation of the bilinear interpolation formula in lecture slide
        for (row = 0; row < tgWidth-offsetY; row++) {
            y = scaleFactorY * row;
            y0 = (int) y;
            y1 = Integer.min(y0, inWidth-2) + 1;
            yDiff = y - y0;
            for (col = 0; col < tgHeight-offsetX; col++) {
                x = scaleFactorX * col;
                x0 = (int) x;
                x1 = Integer.min(x0, inHeight-2) + 1;
                r = img[y0][x0] + yDiff * (img[y1][x0] - img[y0][x0]);
                s = img[y0][x1] + yDiff * (img[y1][x1] - img[y0][x1]);
                result[row+offsetY][col+offsetX] = (int) Math.round(r + (x - x0) * (s - r));
            }
        }
        return result;
    }


    /**
     * An inplementation of bi-linear interpolation, this method overload is for
     * down-sampling the brushes, equivalent to
     * BilinearInterpolate(img, inWidth, inWidth, tgWidth, tgWidth, false)
     *
     * @param img input image
     * @param inWidth input image's width (row number)
     * @param tgWidth target image's width (row number)
     * @return int[][]
     */
    public static int[][] BilinearInterpolate(int[][] img, int inWidth, int tgWidth)
    {
        return BilinearInterpolate(img, inWidth, inWidth, tgWidth, tgWidth, false);
    }
}
