package kr.edu.worksheetocr;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int CAPTURE = 1;
    private static final int PICK = 2;
    private static final int MAX_IMAGE_EDGE = 2560;

    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private TextRecognizer recognizer;
    private ImageView preview;
    private EditText answer;
    private TextView status;
    private Button capture;
    private Button pick;
    private Button copy;
    private File captureFile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(24));
        page.setFocusableInTouchMode(true);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("활동지 OCR");
        title.setTextSize(26);
        page.addView(title);

        LinearLayout actions = new LinearLayout(this);
        page.addView(actions);
        capture = button("사진 촬영", this::takePhoto);
        pick = button("사진 선택", this::pickPhoto);
        actions.addView(capture, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(pick, new LinearLayout.LayoutParams(0, dp(52), 1));

        status = new TextView(this);
        status.setText("활동지 사진을 찍거나 선택하세요.");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, dp(8), 0, dp(8));
        page.addView(status);

        preview = new ImageView(this);
        preview.setContentDescription("선택한 활동지 사진");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        page.addView(preview, new LinearLayout.LayoutParams(-1, dp(240)));

        TextView label = new TextView(this);
        label.setText("인식 결과");
        label.setPadding(0, dp(12), 0, 0);
        page.addView(label);

        answer = new EditText(this);
        answer.setHint("사진에서 읽은 글자를 여기서 수정할 수 있어요.");
        answer.setGravity(Gravity.TOP | Gravity.START);
        answer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        answer.setMinLines(4);
        page.addView(answer);
        copy = button("텍스트 복사", this::copyAnswer);
        page.addView(copy);
        page.requestFocus();

        if (state != null) {
            answer.setText(state.getString("answer"));
            status.setText(state.getString("status", "활동지 사진을 찍거나 선택하세요."));
            String capturePath = state.getString("capturePath");
            if (capturePath != null) captureFile = new File(capturePath);
        }
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File directory = new File(getCacheDir(), "captures");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
            captureFile = File.createTempFile("worksheet-", ".jpg", directory);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".photos", captureFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.setClipData(ClipData.newRawUri("활동지 사진", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, CAPTURE);
        } catch (ActivityNotFoundException | IOException | SecurityException error) {
            deleteCapture();
            status.setText("카메라를 열 수 없어요. 저장된 사진을 선택해 주세요.");
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, PICK);
        } catch (ActivityNotFoundException error) {
            status.setText("사진 선택 화면을 열 수 없어요. 카메라로 찍어 주세요.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CAPTURE) {
            if (result == RESULT_OK && captureFile != null && captureFile.length() > 0) {
                readPhoto(Uri.fromFile(captureFile), captureFile);
                captureFile = null;
            } else {
                deleteCapture();
                if (result == RESULT_OK) status.setText("촬영한 사진을 불러올 수 없어요.");
            }
        } else if (request == PICK && result == RESULT_OK && data != null && data.getData() != null) {
            readPhoto(data.getData(), null);
        }
    }

    private void readPhoto(Uri uri, File temporaryPhoto) {
        setBusy(true);
        status.setText("사진에서 글자를 읽는 중…");
        long started = SystemClock.elapsedRealtime();
        decoder.execute(() -> {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), uri),
                        (imageDecoder, info, source) -> {
                            imageDecoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            int width = info.getSize().getWidth();
                            int height = info.getSize().getHeight();
                            int edge = Math.max(width, height);
                            if (edge > MAX_IMAGE_EDGE) {
                                float scale = (float) MAX_IMAGE_EDGE / edge;
                                imageDecoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                        Math.max(1, Math.round(height * scale)));
                            }
                        });
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) recognize(bitmap, started);
                });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> showFailure("사진을 읽을 수 없어요. 다른 사진으로 다시 시도해 주세요."));
            } finally {
                if (temporaryPhoto != null) temporaryPhoto.delete();
            }
        });
    }

    private void recognize(Bitmap bitmap, long started) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    if (isDestroyed() || isFinishing()) return;
                    setBusy(false);
                    preview.setImageBitmap(bitmap);
                    preview.setVisibility(View.VISIBLE);
                    String text = result.getText().trim();
                    answer.setText(text);
                    if (text.isEmpty()) {
                        status.setText("읽은 글자가 없어요. 활동지에 가까이 대고 다시 찍어 보세요.");
                    } else {
                        double seconds = (SystemClock.elapsedRealtime() - started) / 1000.0;
                        status.setText(String.format(java.util.Locale.KOREA,
                                "인식 완료 · %.1f초. 사진과 비교해 답안을 확인하세요.", seconds));
                    }
                })
                .addOnFailureListener(error -> showFailure("글자를 읽지 못했어요. 다시 시도해 주세요."));
    }

    private void showFailure(String message) {
        if (isDestroyed() || isFinishing()) return;
        setBusy(false);
        status.setText(message);
    }

    private void setBusy(boolean busy) {
        capture.setEnabled(!busy);
        pick.setEnabled(!busy);
        answer.setEnabled(!busy);
        copy.setEnabled(!busy);
    }

    private void copyAnswer() {
        String text = answer.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "복사할 텍스트가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("활동지 답안", text));
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "답안을 복사했어요.", Toast.LENGTH_SHORT).show();
        }
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void deleteCapture() {
        if (captureFile != null) {
            captureFile.delete();
            captureFile = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("answer", answer.getText().toString());
        if (capture.isEnabled()) state.putString("status", status.getText().toString());
        if (captureFile != null) state.putString("capturePath", captureFile.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) deleteCapture();
        decoder.shutdown();
        recognizer.close();
        super.onDestroy();
    }
}
