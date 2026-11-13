package kr.edu.worksheetocr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

final class HandwritingRecognizer {
    private static final int HEIGHT = 48;
    private static final int MAX_WIDTH = 2048;
    private final List<String> characters = new ArrayList<>();
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;

    HandwritingRecognizer(AssetManager assets) throws IOException, OrtException {
        characters.add(""); // CTC blank
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open("handwriting/ppocrv5_korean_dict.txt"), StandardCharsets.UTF_8))) {
            String character;
            while ((character = reader.readLine()) != null) characters.add(character);
        }
        characters.add(" ");
        try (InputStream model = assets.open("handwriting/rec_korean_hw.onnx");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream(15_000_000);
             OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            byte[] buffer = new byte[32_768];
            int count;
            while ((count = model.read(buffer)) != -1) bytes.write(buffer, 0, count);
            options.setIntraOpNumThreads(2);
            session = environment.createSession(bytes.toByteArray(), options);
        }
    }

    List<String> readVariants(Bitmap image) throws OrtException {
        double scale = Math.min(1.0, 2560.0 / Math.max(image.getWidth(), image.getHeight()));
        Bitmap source = Bitmap.createScaledBitmap(image,
                Math.max(1, (int) Math.round(image.getWidth() * scale)),
                Math.max(1, (int) Math.round(image.getHeight() * scale)), true);
        Bitmap contrast = PencilContrast.enhance(source, 6);
        Bitmap leveled = transform(source, -2, 0);
        Bitmap strongContrast = PencilContrast.enhance(leveled, 10);
        Bitmap straightened = transform(source, 3, -0.12f);
        java.util.Set<String> results = new java.util.LinkedHashSet<>();
        try {
            results.add(readLine(contrast, 1.0));
            results.add(readLine(source, 1.0));
            results.add(readLine(strongContrast, 1.0));
            results.add(readLine(straightened, 0.8));
            results.add(readLine(contrast, 0.82));
            results.add(readLine(contrast, 1.18));
            results.add(readLine(source, 0.82));
            results.remove("");
            return new ArrayList<>(results);
        } finally {
            contrast.recycle();
            strongContrast.recycle();
            straightened.recycle();
            leveled.recycle();
            if (source != image) source.recycle();
        }
    }

    private static Bitmap transform(Bitmap source, double angle, float shear) {
        int width = source.getWidth();
        int height = source.getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;
        float cosine = (float) Math.cos(Math.toRadians(angle));
        float sine = (float) Math.sin(Math.toRadians(angle));
        float slope = -sine + shear;
        Matrix matrix = new Matrix();
        matrix.setValues(new float[] {
                cosine, slope, centerX - cosine * centerX - slope * centerY,
                sine, cosine, centerY - sine * centerX - cosine * centerY,
                0, 0, 1
        });
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return output;
    }

    private String readLine(Bitmap image, double widthScale) throws OrtException {
        int width = Math.max(8, (int) Math.ceil(widthScale * HEIGHT * image.getWidth() / image.getHeight()));
        if (width > MAX_WIDTH) return "";
        Bitmap resized = Bitmap.createScaledBitmap(image, width, HEIGHT, true);
        int plane = HEIGHT * width;
        int[] pixels = new int[plane];
        resized.getPixels(pixels, 0, width, 0, 0, width, HEIGHT);
        if (resized != image) resized.recycle();
        float[] bgr = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            int pixel = pixels[i];
            bgr[i] = (pixel & 255) / 127.5f - 1;
            bgr[plane + i] = ((pixel >> 8) & 255) / 127.5f - 1;
            bgr[2 * plane + i] = ((pixel >> 16) & 255) / 127.5f - 1;
        }
        try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(bgr),
                new long[]{1, 3, HEIGHT, width});
             OrtSession.Result result = session.run(Collections.singletonMap("x", input))) {
            float[][] scores = ((float[][][]) result.get(0).getValue())[0];
            StringBuilder text = new StringBuilder();
            int previous = 0;
            for (float[] step : scores) {
                if (step.length != characters.size()) throw new IllegalStateException("Wrong OCR dictionary");
                int best = 0;
                for (int i = 1; i < step.length; i++) {
                    if (step[i] > step[best]) best = i;
                }
                if (best != 0 && best != previous) text.append(characters.get(best));
                previous = best;
            }
            return text.toString().trim();
        }
    }

    void close() {
        try {
            session.close();
        } catch (OrtException error) {
            android.util.Log.w("WorksheetOcr", "Could not close handwriting model", error);
        }
    }
}
