package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** SDK-only TV home painter. All measurements use a letterboxed 1920 x 1080 design plane. */
public final class ImpeccableHomeRenderer {
    public static final int APPS = 0, ACTIONS = 1, RESUME = 2, DISCOVERY = 3;
    private static final float LEFT = 80, RIGHT = 1840, TOP = 174, BOTTOM = 1010;
    private static final int BG = Color.rgb(31, 23, 32), SURFACE = Color.rgb(57, 43, 54);
    private static final int TEXT = Color.rgb(252, 241, 225), MUTED = Color.rgb(206, 187, 197);
    private static final int FOCUS = Color.rgb(255, 211, 128), INK = Color.rgb(39, 29, 25);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public enum Icon { INPUT, SETTINGS, GOOGLE_TV, APPS, PLAY }
    public enum Status { READY, LOADING, ERROR }
    public static final class Item {
        public final String id, title, detail;
        public final Bitmap image;
        public final Icon icon;
        public final boolean enabled;
        /** Resume progress in [0,1], or negative when unknown. */
        public final float progress;
        public Item(String id, String title, String detail, Bitmap image, Icon icon,
                    boolean enabled, float progress) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.image = image;
            this.icon = icon == null ? Icon.APPS : icon;
            this.enabled = enabled;
            this.progress = Float.isNaN(progress) ? -1 : Math.max(-1, Math.min(1, progress));
        }
    }
    public static final class Row {
        public final String title;
        public final List<Item> items;
        public final Status status;
        /** Data-provider explanation; no fabricated content is displayed. */
        public final String message;
        public Row(String title, List<Item> items, Status status, String message) {
            this.title = title == null ? "" : title;
            ArrayList<Item> safe = new ArrayList<>();
            if (items != null) for (Item item : items) if (item != null) safe.add(item);
            this.items = Collections.unmodifiableList(safe);
            this.status = status == null ? Status.READY : status;
            this.message = message == null ? "" : message;
        }
    }
    public static final class Weather {
        public final String temperature, condition, location;
        public Weather(String temperature, String condition, String location) {
            this.temperature = temperature == null ? "" : temperature;
            this.condition = condition == null ? "" : condition;
            this.location = location == null ? "" : location;
        }
    }
    public static final class Model {
        public Row apps = new Row(Ui.tr("Shelf"), null, Status.READY, Ui.tr("No apps available."));
        public Row actions = new Row(Ui.tr("Top Buttons"), null, Status.READY, Ui.tr("None yet."));
        public Row resume = new Row(Ui.tr("Continue Watching"), null, Status.READY, Ui.tr("None yet."));
        public Row discovery = new Row(Ui.tr("For You"), null, Status.READY, Ui.tr("None yet."));
        public Weather weather;
        /** Host supplies localized time, without a fabricated default. */
        public String time = "";
        /** Host supplies system font preference; visual text expands within reserved label areas. */
        public float textScale = 1f;
        public int focusedSection = APPS, focusedItem = 0;
        public float scrollY;
        /** Independent horizontal positions are retained when moving between sections. */
        public final float[] scrollX = new float[4];
        public final int[] rememberedItem = new int[4];
    }
    private Row row(Model m, int s) {
        Row r = s == APPS ? m.apps : s == ACTIONS ? m.actions : s == RESUME ? m.resume : m.discovery;
        return r == null ? new Row("", null, Status.READY, Ui.tr("Unavailable")) : r;
    }
    private float rowY(int s) { return s == APPS ? 174 : s == ACTIONS ? 444 : s == RESUME ? 590 : 966; }
    private float cardY(int s) { return rowY(s) + (s == ACTIONS ? 0 : 58); }
    private float cardW(int s) { return s == APPS ? 260 : s == ACTIONS ? 332 : 416; }
    private float cardH(int s) { return s == APPS ? 150 : s == ACTIONS ? 78 : 234; }
    private float gap(int s) { return s == ACTIONS ? 24 : 28; }
    private float fullH(int s) { return cardH(s) + (s >= RESUME ? 90 : s == APPS ? 44 : 0); }
    private boolean available(Model m, int s, int i) {
        Row r = row(m, s);
        return i >= 0 && i < r.items.size() && r.items.get(i).enabled;
    }
    private int firstEnabled(Model m, int s, int preferred) {
        List<Item> items = row(m, s).items;
        if (items.isEmpty()) return -1;
        preferred = Math.max(0, Math.min(items.size() - 1, preferred));
        for (int d = 0; d < items.size(); d++) {
            if (available(m, s, preferred + d)) return preferred + d;
            if (available(m, s, preferred - d)) return preferred - d;
        }
        return -1;
    }
    /** Call after replacing data. Does not alter external settings or launch an app. */
    public void normalize(Model m) {
        m.focusedSection = Math.max(0, Math.min(3, m.focusedSection));
        int found = firstEnabled(m, m.focusedSection, m.focusedItem);
        m.focusedItem = found;
        reveal(m);
    }
    /** Returns true only for a handled D-pad direction. Host owns Back and activation. */
    public boolean move(Model m, int keyCode) {
        normalize(m);
        int s = m.focusedSection, i = m.focusedItem;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int step = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            for (int n = i + step; n >= 0 && n < row(m, s).items.size(); n += step) {
                if (available(m, s, n)) { m.focusedItem = n; break; }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int step = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
            m.rememberedItem[s] = Math.max(0, i);
            int next = s + step;
            if (next >= 0 && next < 4) {
                m.focusedSection = next;
                m.focusedItem = firstEnabled(m, next, m.rememberedItem[next]);
            }
        } else return false;
        m.rememberedItem[m.focusedSection] = Math.max(0, m.focusedItem);
        reveal(m);
        return true;
    }
    public Item focused(Model m) {
        return available(m, m.focusedSection, m.focusedItem)
                ? row(m, m.focusedSection).items.get(m.focusedItem) : null;
    }
    /** Visible hit/accessibility bounds in physical View pixels; false when clipped or invalid. */
    public boolean itemBounds(Model m, int section, int index, int width, int height, RectF out) {
        if (m == null || out == null || section < 0 || section > 3 || index < 0
                || index >= row(m, section).items.size() || width <= 0 || height <= 0) return false;
        float x = LEFT + index * (cardW(section) + gap(section)) - m.scrollX[section];
        float y = cardY(section) - m.scrollY;
        out.set(x, y, x + cardW(section), y + fullH(section));
        if (!out.intersect(LEFT, TOP, RIGHT, BOTTOM)) return false;
        float scale = Math.min(width / 1920f, height / 1080f);
        out.set(out.left * scale + (width - 1920 * scale) / 2,
                out.top * scale + (height - 1080 * scale) / 2,
                out.right * scale + (width - 1920 * scale) / 2,
                out.bottom * scale + (height - 1080 * scale) / 2);
        return true;
    }
    private void reveal(Model m) {
        for (int s = 0; s < 4; s++) {
            float max = Math.max(0, row(m, s).items.size() * (cardW(s) + gap(s)) - gap(s) - (RIGHT - LEFT));
            if (!Float.isFinite(m.scrollX[s])) m.scrollX[s] = 0;
            m.scrollX[s] = Math.max(0, Math.min(max, m.scrollX[s]));
        }
        if (!Float.isFinite(m.scrollY)) m.scrollY = 0;
        m.scrollY = Math.max(0, Math.min(338, m.scrollY));
        int s = m.focusedSection;
        float x = Math.max(0, m.focusedItem) * (cardW(s) + gap(s));
        if (x < m.scrollX[s]) m.scrollX[s] = x;
        if (x + cardW(s) > m.scrollX[s] + RIGHT - LEFT)
            m.scrollX[s] = x + cardW(s) - (RIGHT - LEFT);
        float top = rowY(s), bottom = cardY(s) + fullH(s);
        if (top - m.scrollY < TOP) m.scrollY = top - TOP;
        if (bottom - m.scrollY > BOTTOM) m.scrollY = bottom - BOTTOM;
        m.scrollY = Math.max(0, Math.min(338, m.scrollY));
    }
    public void render(Canvas canvas, int width, int height, Model m) {
        if (canvas == null || m == null || width <= 0 || height <= 0) return;
        normalize(m);
        canvas.drawColor(BG);
        float scale = Math.min(width / 1920f, height / 1080f);
        int save = canvas.save();
        canvas.translate((width - 1920 * scale) / 2, (height - 1080 * scale) / 2);
        canvas.scale(scale, scale);
        float ts = Float.isFinite(m.textScale) ? Math.max(1, Math.min(1.3f, m.textScale)) : 1;
        text(canvas, "Lamplight", LEFT, 112, 52, TEXT, medium, 550);
        if (m.weather != null) {
            text(canvas, m.weather.temperature, 1120, 82, 34 * ts, TEXT, medium, 210);
            text(canvas, m.weather.condition, 1120, 119, 25 * ts, MUTED, regular, 390);
            text(canvas, m.weather.location, 1530, 119, 25 * ts, MUTED, regular, 310);
        }
        textRight(canvas, m.time == null ? "" : m.time, RIGHT, 80, 34 * ts, TEXT);
        fill(Color.rgb(85, 63, 76)); canvas.drawRect(LEFT, 149, RIGHT, 151, p);
        int clip = canvas.save();
        canvas.clipRect(LEFT - 12, TOP - 12, RIGHT + 12, BOTTOM + 12);
        canvas.translate(0, -m.scrollY);
        for (int s = 0; s < 4; s++) drawRow(canvas, m, s, ts);
        canvas.restoreToCount(clip);
        text(canvas, Ui.tr("Use arrows to browse. OK opens. Back returns home."), LEFT, 1054, 23 * ts, MUTED, regular, 650);
        if (m.scrollY < 338) { fill(MUTED); Path down = new Path(); down.moveTo(1798,1041); down.lineTo(1808,1051); down.lineTo(1818,1041); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); canvas.drawPath(down,p); p.setStyle(Paint.Style.FILL); }
        canvas.restoreToCount(save);
    }
    private void drawRow(Canvas c, Model m, int s, float ts) {
        Row r = row(m, s);
        if (s != ACTIONS) {
            text(c, r.title, LEFT, rowY(s) + 32, 32 * ts, s == m.focusedSection && m.focusedItem < 0 ? FOCUS : TEXT, medium, 1060);
            if (!r.items.isEmpty() && r.status != Status.READY)
                text(c, r.status == Status.LOADING ? Ui.tr("Fetching titles…") : Ui.tr("Unavailable"),
                        1280, rowY(s) + 32, 24 * ts, MUTED, regular, 560);
        }
        if (r.items.isEmpty()) {
            String label = !r.message.isEmpty() ? r.message : r.status == Status.LOADING ? Ui.tr("Fetching titles…") : r.status == Status.ERROR ? Ui.tr("Unavailable") : Ui.tr("None yet.");
            text(c, label, LEFT + 18, cardY(s) + 46, 28 * ts, MUTED, regular, RIGHT - LEFT - 36);
            if (s == m.focusedSection) {
                fill(FOCUS); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4);
                c.drawRoundRect(LEFT-6,cardY(s)-6,RIGHT+6,cardY(s)+78,14,14,p);
                p.setStyle(Paint.Style.FILL);
            }
            return;
        }
        for (int i = 0; i < r.items.size(); i++) {
            float x = LEFT + i * (cardW(s) + gap(s)) - m.scrollX[s];
            if (x + cardW(s) < LEFT || x > RIGHT) continue;
            Item item = r.items.get(i);
            boolean focus = s == m.focusedSection && i == m.focusedItem;
            float y = cardY(s), w = cardW(s), h = cardH(s);
            p.setAlpha(item.enabled ? 255 : 110);
            fill(focus && s == ACTIONS ? FOCUS : SURFACE);
            c.drawRoundRect(x, y, x + w, y + h, 14, 14, p);
            if (s == ACTIONS) {
                icon(c, item.icon, x + 24, y + 22, 34, focus ? INK : TEXT);
                text(c, item.title, x + 76, y + 49, 26 * ts, focus ? INK : TEXT, medium, w - 94);
            } else {
                if (item.image != null && !item.image.isRecycled()) {
                    int save = c.save(); Path mask = new Path(); rect.set(x, y, x + w, y + h); mask.addRoundRect(rect, 14, 14, Path.Direction.CW); c.clipPath(mask);
                    if (s == APPS) {
                        float fit = Math.min((w - 40) / item.image.getWidth(), (h - 34) / item.image.getHeight());
                        float iw = item.image.getWidth() * fit, ih = item.image.getHeight() * fit;
                        rect.set(x + (w-iw)/2, y + (h-ih)/2, x + (w+iw)/2, y + (h+ih)/2);
                    } else rect.set(x, y, x + w, y + h);
                    src.set(0,0,item.image.getWidth(),item.image.getHeight());
                    if (s >= RESUME) {
                        float target = w / h, imageRatio = item.image.getWidth() / (float)item.image.getHeight();
                        if (imageRatio > target) { int crop = Math.round(item.image.getHeight()*target); int start=(item.image.getWidth()-crop)/2; src.set(start,0,start+crop,item.image.getHeight()); }
                        else { int crop=Math.round(item.image.getWidth()/target); int start=(item.image.getHeight()-crop)/2; src.set(0,start,item.image.getWidth(),start+crop); }
                    }
                    c.drawBitmap(item.image, src, rect, p); c.restoreToCount(save);
                } else icon(c, item.icon, x + (w-48)/2, y + (h-48)/2, 48, MUTED);
                text(c, item.title, x, y + h + 37, 28 * ts, focus ? FOCUS : TEXT, medium, w);
                if (s >= RESUME) text(c, item.detail, x, y + h + 74, 24 * ts, MUTED, regular, w);
                if (s == RESUME && item.progress >= 0) {
                    fill(Color.rgb(89, 68, 79)); c.drawRect(x + 14, y + h - 14, x+w-14, y+h-8,p);
                    fill(FOCUS); c.drawRect(x+14,y+h-14,x+14+(w-28)*item.progress,y+h-8,p);
                }
            }
            if (focus) { p.setColor(FOCUS); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); c.drawRoundRect(x-6,y-6,x+w+6,y+h+6,19,19,p); p.setStyle(Paint.Style.FILL); }
            p.setAlpha(255);
        }
    }
    private void fill(int color) { int alpha=p.getAlpha(); p.setColor(color); p.setAlpha(alpha); p.setStyle(Paint.Style.FILL); }
    private void text(Canvas c,String value,float x,float baseline,float size,int color,Typeface face,float maxWidth) {
        p.setTypeface(face); p.setTextSize(size); fill(color);
        if (value == null || value.isEmpty() || maxWidth <= 0) return;
        // One line, surrogate-safe ellipsis; captions never change row geometry.
        value = value.replace('\n',' ').replace('\r',' ');
        if (p.measureText(value) > maxWidth) {
            int n=p.breakText(value,true,Math.max(0,maxWidth-p.measureText("…")),null);
            if (n>0 && n<value.length() && Character.isHighSurrogate(value.charAt(n-1))) n--;
            value=value.substring(0,n)+"…";
        }
        c.drawText(value,x,baseline,p);
    }
    private void textRight(Canvas c,String value,float right,float baseline,float size,int color) {
        p.setTypeface(regular); p.setTextSize(size);
        if (p.measureText(value) > 300) {
            int n = p.breakText(value, true, 300 - p.measureText("…"), null);
            if (n > 0 && n < value.length() && Character.isHighSurrogate(value.charAt(n-1))) n--;
            value = value.substring(0,n) + "…";
        }
        text(c,value,right-p.measureText(value),baseline,size,color,regular,300);
    }
    private void icon(Canvas c,Icon icon,float x,float y,float size,int color) {
        int save=c.save(); c.translate(x,y); c.scale(size/48,size/48); fill(color);
        p.setStrokeWidth(3); p.setStrokeCap(Paint.Cap.ROUND); p.setStyle(Paint.Style.STROKE);
        if (icon==Icon.INPUT || icon==Icon.GOOGLE_TV) {
            c.drawRoundRect(3,6,45,35,4,4,p); c.drawLine(17,43,31,43,p); c.drawLine(24,35,24,43,p);
            if(icon==Icon.INPUT) { c.drawLine(14,20,32,20,p); c.drawLine(26,14,32,20,p); c.drawLine(26,26,32,20,p); }
        } else if(icon==Icon.SETTINGS) {
            c.drawCircle(24,24,12,p); c.drawCircle(24,24,4,p);
            for(int a=0;a<8;a++){ double t=a*Math.PI/4; c.drawLine(24+(float)Math.cos(t)*17,24+(float)Math.sin(t)*17,24+(float)Math.cos(t)*21,24+(float)Math.sin(t)*21,p); }
        } else if(icon==Icon.PLAY) {
            Path path=new Path(); path.moveTo(17,9); path.lineTo(38,24); path.lineTo(17,39); path.close(); c.drawPath(path,p);
        } else for(int a=0;a<2;a++) for(int b=0;b<2;b++) c.drawRoundRect(5+a*23,5+b*23,20+a*23,20+b*23,2,2,p);
        p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT); c.restoreToCount(save);
    }
}
