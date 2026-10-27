package io.freemytv;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import java.io.*;
import java.util.zip.*;

/** Owner-authorized support operations, signature-bound to the installed app. No runtime endpoint. */
public final class SupportChecks extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle values) { super.onCreate(values); args = values; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context c = getTargetContext();
            String action = args.getString("action", "checkpoint");
            if (action.equals("checkpoint")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                    for (String dir : new String[] {"files", "shared_prefs", "databases", "no_backup", "cache/resume-art"}) {
                        File root = new File(c.getApplicationInfo().dataDir, dir);
                        add(zip, root, dir);
                    }
                }
                byte[] data = bytes.toByteArray();
                for (int at = 0; at < data.length; at += 24576) {
                    Bundle chunk = new Bundle();
                    chunk.putString("checkpoint_chunk", Base64.encodeToString(data, at, Math.min(24576, data.length - at), Base64.NO_WRAP));
                    sendStatus(2, chunk);
                }
                result.putString("stream", "CHECKPOINT_OK bytes=" + data.length);
            } else if (action.equals("restorechunk")) {
                File stage = new File(c.getCacheDir(), "vibe-support-restore.zip");
                long offset = Long.parseLong(args.getString("offset"));
                byte[] bytes = Base64.decode(args.getString("chunk"), Base64.DEFAULT);
                if (bytes.length > 24576) throw new IOException("Oversized restore chunk");
                try (RandomAccessFile out = new RandomAccessFile(stage, "rw")) {
                    if (offset == 0) out.setLength(0);
                    if (offset != out.length()) throw new IOException("Restore chunk out of order");
                    out.seek(offset); out.write(bytes);
                }
                result.putString("stream", "RESTORE_CHUNK_OK");
            } else if (action.equals("restoredata") || action.equals("verifyrestore")) {
                File stage = new File(c.getCacheDir(), "vibe-support-restore.zip");
                java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
                try (FileInputStream in = new FileInputStream(stage)) {
                    byte[] buffer = new byte[16384]; int n;
                    while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n);
                }
                StringBuilder digest = new StringBuilder();
                for (byte b : hash.digest()) digest.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (!digest.toString().equals(args.getString("digest"))) throw new IOException("Checkpoint digest mismatch");
                File root = new File(c.getApplicationInfo().dataDir);
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(stage))) {
                    ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        String name = e.getName();
                        if (!name.matches("(files|shared_prefs|databases|no_backup|cache/resume-art)/.+")) throw new IOException("Unexpected archive entry");
                        File file = new File(root, name);
                        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + "/")) throw new IOException("Invalid archive path");
                        if (action.equals("restoredata")) {
                            file.getParentFile().mkdirs();
                            try (FileOutputStream out = new FileOutputStream(file)) { copy(zip, out); }
                        } else {
                            byte[] buffer = new byte[16384]; while (zip.read(buffer) != -1) { }
                        }
                    }
                }
                stage.delete();
                result.putString("stream", action.equals("restoredata") ? "RESTORE_DATA_OK" : "RESTORE_VERIFIED");
            } else if (action.equals("cleanup") || action.equals("restorecleanup")) {
                Class<?> mode = Class.forName("io.freemytv.HomeMode");
                java.lang.reflect.Method method = mode.getDeclaredMethod("supportCleanupNow", Context.class, boolean.class);
                method.setAccessible(true);
                Object answer = method.invoke(null, c, action.equals("restorecleanup"));
                result.putString("stream", String.valueOf(answer));
            } else if (action.equals("inspectcleanup")) {
                org.json.JSONObject states = new org.json.JSONObject();
                for (String pkg : new String[] {"com.google.android.feedback", "com.google.android.videos", "com.android.printspooler"}) {
                    states.put(pkg, c.getPackageManager().getApplicationEnabledSetting(pkg));
                }
                for (String component : new String[] {"com.tcl.ttvs/com.tcl.ttvs.bi.core.BIService", "com.tcl.ttvs/com.tcl.ttvs.oversea.usagest.core.service.UsagestService", "com.tcl.ttvs/com.tcl.trackdata.service.TrackDataService"}) {
                    states.put(component, c.getPackageManager().getComponentEnabledSetting(android.content.ComponentName.unflattenFromString(component)));
                }
                result.putString("stream", "CLEANUP_STATES " + states);
            } else if (action.equals("metrics")) {
                java.lang.reflect.Method method = Class.forName("io.freemytv.HomeMode").getDeclaredMethod("supportMeasureNow", Context.class);
                method.setAccessible(true);
                result.putString("stream", String.valueOf(method.invoke(null, c)));
            } else if (action.equals("artcheck")) {
                android.graphics.Bitmap source = android.graphics.Bitmap.createBitmap(960, 540, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setShader(new android.graphics.LinearGradient(0, 0, 960, 540, 0xFF123456, 0xFFDD8844, android.graphics.Shader.TileMode.CLAMP));
                new android.graphics.Canvas(source).drawRect(0, 0, 960, 540, paint);
                Class<?> cls = Class.forName("io.freemytv.Backdrop");
                java.lang.reflect.Method make = cls.getDeclaredMethod("make", android.graphics.Bitmap.class, boolean.class, boolean.class, boolean.class);
                make.setAccessible(true);
                Object full = make.invoke(null, source.copy(android.graphics.Bitmap.Config.ARGB_8888, true), true, false, true);
                Object rows = make.invoke(null, source.copy(android.graphics.Bitmap.Config.ARGB_8888, true), true, false, false);
                long saved = imageBytes(full) - imageBytes(rows);
                java.lang.reflect.Field wide = cls.getDeclaredField("wide"), tall = cls.getDeclaredField("tall");
                wide.setAccessible(true); tall.setAccessible(true);
                android.graphics.Bitmap first = (android.graphics.Bitmap) wide.get(full), second = (android.graphics.Bitmap) wide.get(rows);
                if (!first.sameAs(second) || tall.get(full) == null || tall.get(rows) != null) throw new AssertionError("Artwork invariant failed");
                java.lang.reflect.Method ready = cls.getDeclaredMethod("backgroundReady"), prepare = cls.getDeclaredMethod("prepareBackground"), release = cls.getDeclaredMethod("releaseBackground");
                ready.setAccessible(true); prepare.setAccessible(true); release.setAccessible(true);
                if ((Boolean) ready.invoke(rows)) throw new AssertionError("Unused background must stay deferred");
                prepare.invoke(rows);
                for (String field : new String[] {"frost", "light", "lightGlass", "nightFrost", "nightGlass"}) {
                    java.lang.reflect.Field f = cls.getDeclaredField(field); f.setAccessible(true);
                    if (!java.util.Arrays.equals((int[]) f.get(full), (int[]) f.get(rows))) throw new AssertionError("Background invariant failed");
                }
                release.invoke(rows);
                if ((Boolean) ready.invoke(rows) || !first.sameAs(second)) throw new AssertionError("Release must retain card artwork");
                prepare.invoke(rows);
                java.lang.reflect.Field glass = cls.getDeclaredField("lightGlass"); glass.setAccessible(true);
                if (!java.util.Arrays.equals((int[]) glass.get(full), (int[]) glass.get(rows))) throw new AssertionError("Rebuilt background changed pixels");
                if (saved <= 0) throw new AssertionError("Unused image buffers were not removed");
                result.putString("stream", "ARTCHECK_OK saved_bytes_per_card=" + saved + " landscape and glass pixels identical; portrait omitted");
            } else throw new IllegalArgumentException("Unknown support action");
            finish(-1, result);
        } catch (Throwable e) {
            while (e.getCause() != null) e = e.getCause();
            // No private archive contents, file names or exception messages in support output.
            String detail = e.getMessage();
            String reason = "other";
            for (String known : new String[] {"Free mode required", "Wi-Fi ownership unknown", "Input dependencies unknown", "Cleanup refused", "not authorized", "Connection refused", "timed out", "protocol"}) {
                if (detail != null && detail.toLowerCase(java.util.Locale.ROOT).contains(known.toLowerCase(java.util.Locale.ROOT))) reason = known.replace(' ', '_');
            }
            result.putString("stream", "SUPPORT_FAILED " + e.getClass().getSimpleName() + "_" + reason);
            finish(0, result);
        }
    }
    private static long imageBytes(Object object) throws Exception {
        long bytes = 0;
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        for (java.lang.reflect.Field f : object.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true); Object value = f.get(object);
            if (value == null || !seen.add(value)) continue;
            if (value instanceof int[]) bytes += ((int[]) value).length * 4L;
            if (value instanceof android.graphics.Bitmap) bytes += ((android.graphics.Bitmap) value).getAllocationByteCount();
        }
        return bytes;
    }
    private static void add(ZipOutputStream zip, File file, String name) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list checkpoint directory");
            for (File child : children) add(zip, child, name + "/" + child.getName());
        } else {
            zip.putNextEntry(new ZipEntry(name));
            try (FileInputStream in = new FileInputStream(file)) { copy(in, zip); }
            zip.closeEntry();
        }
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16384]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
}
