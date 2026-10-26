package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Small layout previews: deliberately generic content, with each theme's actual composition and palette. */
final class HomeThemeThumbs {
    static final String[] IDS = {"vibe", "simba", "lamplight", "google"};
    static final String[] NAMES = {"Vibe TV 1", "Simba (Free-My-TV)", "Lamplight", "Google TV"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    void draw(Canvas canvas, RectF target, int theme) {
        int save = canvas.save();
        canvas.translate(target.left, target.top);
        canvas.scale(target.width() / 320, target.height() / 180);
        paint.setColor(theme == 2 ? 0xFF1F1720 : theme == 1 ? 0xFF151824 : 0xFF111214);
        canvas.drawRoundRect(0, 0, 320, 180, 8, 8, paint);
        if (theme == 0) {
            tiles(canvas, 14, 12, 8, 30, 18, 7, 0xFF424448);
            rect(canvas, 14, 40, 292, 38, 0xFF292C30);
            rect(canvas, 21, 48, 49, 6, 0xFFB5B7BB);
            rect(canvas, 21, 60, 31, 10, 0xFFEEEFF0);
            tiles(canvas, 91, 49, 6, 28, 18, 7, 0xFF777C84);
            tiles(canvas, 14, 88, 6, 44, 27, 6, 0xFF59606A);
            tiles(canvas, 14, 135, 4, 67, 36, 8, 0xFF404850);
        } else if (theme == 1) {
            rect(canvas, 14, 16, 173, 82, 0xFF393E4C);
            rect(canvas, 25, 34, 100, 7, 0xFFF2F0EC);
            rect(canvas, 25, 49, 130, 4, 0xFF9297A5);
            rect(canvas, 25, 62, 95, 4, 0xFF9297A5);
            rect(canvas, 25, 78, 49, 12, 0xFFF1F0EF);
            rect(canvas, 202, 22, 99, 12, 0xFF555D70);
            tiles(canvas, 20, 119, 6, 42, 28, 6, 0xFF666578);
            rect(canvas, 20, 163, 280, 3, 0xFF777B8B);
        } else if (theme == 2) {
            rect(canvas, 14, 14, 42, 7, 0xFFFCF1E1);
            rect(canvas, 225, 14, 78, 5, 0xFFCEBBC5);
            tiles(canvas, 14, 39, 6, 44, 26, 6, 0xFF766171);
            tiles(canvas, 14, 82, 4, 68, 12, 8, 0xFF745D6A);
            tiles(canvas, 14, 116, 4, 67, 37, 8, 0xFF594354);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(0xFFFFD380);
            canvas.drawRoundRect(12, 37, 60, 67, 5, 5, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            tiles(canvas, 18, 14, 4, 37, 6, 9, 0xFF878F97);
            rect(canvas, 18, 37, 285, 67, 0xFF293D46);
            rect(canvas, 30, 56, 113, 7, 0xFFF0F2F3);
            rect(canvas, 30, 73, 80, 5, 0xFFA0B3BD);
            tiles(canvas, 18, 120, 6, 42, 24, 6, 0xFF6E7D88);
            tiles(canvas, 18, 156, 4, 65, 18, 8, 0xFF40515A);
        }
        canvas.restoreToCount(save);
    }
    private void tiles(Canvas c, float x, float y, int count, float width, float height, float gap, int color) {
        for (int i = 0; i < count; i++) rect(c, x + i * (width + gap), y, width, height, color);
    }
    private void rect(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        box.set(x, y, x + w, y + h);
        c.drawRoundRect(box, 3, 3, paint);
    }
}
