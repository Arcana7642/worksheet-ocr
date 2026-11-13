package kr.edu.worksheetocr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.ImageView;

// This screen uses a platform Activity and theme.
@android.annotation.SuppressLint("AppCompatCustomView")
final class CropPreview extends ImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF photoBounds = new RectF();
    private final RectF selection = new RectF();
    private float startX;
    private float startY;
    private Runnable onSelectionChanged;

    CropPreview(Context context) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
    }

    void setOnSelectionChanged(Runnable listener) {
        onSelectionChanged = listener;
    }

    void clearSelection() {
        selection.setEmpty();
        invalidate();
        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    boolean hasSelection() {
        return !selection.isEmpty();
    }

    Rect selectedPixels(int width, int height) {
        return new Rect((int) Math.floor(selection.left * width),
                (int) Math.floor(selection.top * height),
                Math.min(width, (int) Math.ceil(selection.right * width)),
                Math.min(height, (int) Math.ceil(selection.bottom * height)));
    }

    private boolean updatePhotoBounds() {
        if (getDrawable() == null) return false;
        photoBounds.set(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        getImageMatrix().mapRect(photoBounds);
        return !photoBounds.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasSelection() || !updatePhotoBounds()) return;
        float left = photoBounds.left + selection.left * photoBounds.width();
        float top = photoBounds.top + selection.top * photoBounds.height();
        float right = photoBounds.left + selection.right * photoBounds.width();
        float bottom = photoBounds.top + selection.bottom * photoBounds.height();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        canvas.drawRect(photoBounds.left, photoBounds.top, photoBounds.right, top, paint);
        canvas.drawRect(photoBounds.left, bottom, photoBounds.right, photoBounds.bottom, paint);
        canvas.drawRect(photoBounds.left, top, left, bottom, paint);
        canvas.drawRect(right, top, photoBounds.right, bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        paint.setColor(Color.rgb(33, 150, 243));
        canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !updatePhotoBounds()) return false;
        float x = Math.max(0, Math.min(1, (event.getX() - photoBounds.left) / photoBounds.width()));
        float y = Math.max(0, Math.min(1, (event.getY() - photoBounds.top) / photoBounds.height()));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!photoBounds.contains(event.getX(), event.getY())) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                startX = x;
                startY = y;
                clearSelection();
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                selection.set(Math.min(startX, x), Math.min(startY, y),
                        Math.max(startX, x), Math.max(startY, y));
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    float minSize = 6 * getResources().getDisplayMetrics().density;
                    if (selection.width() * photoBounds.width() < minSize
                            || selection.height() * photoBounds.height() < minSize) {
                        clearSelection();
                        performClick();
                        return true;
                    }
                }
                invalidate();
                if (onSelectionChanged != null) onSelectionChanged.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                clearSelection();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
