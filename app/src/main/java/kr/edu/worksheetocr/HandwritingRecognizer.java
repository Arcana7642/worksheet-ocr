package kr.edu.worksheetocr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
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

    String readLine(Bitmap image) throws OrtException {
        int width = Math.max(8, (int) Math.ceil((double) HEIGHT * image.getWidth() / image.getHeight()));
        if (width > MAX_WIDTH) throw new IllegalArgumentException("Selected line is too wide");
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
