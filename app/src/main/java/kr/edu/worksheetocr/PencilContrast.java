package kr.edu.worksheetocr;

import android.graphics.Bitmap;

final class PencilContrast {
    private PencilContrast() {}

    static Bitmap enhance(Bitmap image, int gain) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stride = width + 1;
        int[] pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
        long[] sums = new long[stride * (height + 1)];
        for (int y = 0; y < height; y++) {
            int row = 0;
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int color = pixels[index];
                int gray = (77 * ((color >>> 16) & 255) + 150 * ((color >>> 8) & 255)
                        + 29 * (color & 255)) >>> 8;
                pixels[index] = gray;
                row += gray;
                sums[(y + 1) * stride + x + 1] = sums[y * stride + x + 1] + row;
            }
        }
        int radius = Math.max(4, height / 6);
        for (int y = 0; y < height; y++) {
            int top = Math.max(0, y - radius);
            int bottom = Math.min(height, y + radius + 1);
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(width, x + radius + 1);
                long sum = sums[bottom * stride + right] - sums[bottom * stride + left]
                        - sums[top * stride + right] + sums[top * stride + left];
                int background = (int) (sum / ((right - left) * (bottom - top)));
                int index = y * width + x;
                int value = Math.max(0, Math.min(255, 255 - (background - pixels[index]) * gain));
                pixels[index] = 0xff000000 | (value * 0x010101);
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }
}
