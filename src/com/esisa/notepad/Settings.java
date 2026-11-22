/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Everything remembered between runs: %APPDATA%\EasyNotes92 (Windows), ~/Library/Application Support/EasyNotes92 (macOS). */
final class Settings {

    static final int MAX_RECENT = 15;

    final Path dir;
    final Path sessionDir;
    private final Path file;
    private final Properties p = new Properties();
    private FileLock lock;

    Settings() {
        String appData = System.getenv("APPDATA");
        String override = System.getProperty("easynotes.dir"); // used by the tests
        dir = override != null ? Path.of(override) : Platform.appDataDir().resolve("EasyNotes92");
        sessionDir = dir.resolve("session");
        file = dir.resolve("settings.properties");
        try {
            migrateFromOldName(appData);
            Files.createDirectories(sessionDir);
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    p.load(in);
                }
            }
        } catch (IOException ignored) {
            // first run or unreadable: start with defaults
        }
    }

    /** The app used to be called NotePrise: copy its settings and open tabs over once. */
    private void migrateFromOldName(String appData) throws IOException {
        if (appData == null || System.getProperty("easynotes.dir") != null || Files.exists(dir)) return;
        Path old = Path.of(appData, "NotePrise");
        if (!Files.isDirectory(old)) return;
        Files.createDirectories(dir.resolve("session"));
        Path oldSettings = old.resolve("settings.properties");
        if (Files.exists(oldSettings)) Files.copy(oldSettings, file);
        Path oldSession = old.resolve("session");
        if (Files.isDirectory(oldSession)) {
            try (var list = Files.list(oldSession)) {
                for (Path f : (Iterable<Path>) list::iterator) Files.copy(f, dir.resolve("session").resolve(f.getFileName()));
            }
        }
    }

    /** True for the first window only, so two running copies don't overwrite each other's session. */
    boolean acquireSessionLock() {
        try {
            @SuppressWarnings("resource") // kept open on purpose: the lock lives as long as the app
            FileChannel ch = FileChannel.open(dir.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = ch.tryLock();
            return lock != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    String get(String key, String def) { return p.getProperty(key, def); }

    int getInt(String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    boolean getBool(String key, boolean def) { return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def))); }

    void set(String key, Object value) {
        if (value == null) p.remove(key); else p.setProperty(key, String.valueOf(value));
    }

    void save() {
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "EasyNotes92 settings");
        } catch (IOException ignored) {
            // settings are a convenience; never block the user over them
        }
    }

    // ---- recent files ----

    List<File> recent() {
        List<File> list = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String s = p.getProperty("recent." + i);
            if (s != null) list.add(new File(s));
        }
        return list;
    }

    void addRecent(File f) {
        List<File> list = recent();
        list.removeIf(x -> x.getAbsolutePath().equalsIgnoreCase(f.getAbsolutePath()));
        list.add(0, f.getAbsoluteFile());
        storeRecent(list);
    }

    void clearRecent() { storeRecent(List.of()); }

    private void storeRecent(List<File> list) {
        for (int i = 0; i < MAX_RECENT; i++) {
            if (i < list.size()) p.setProperty("recent." + i, list.get(i).getPath());
            else p.remove("recent." + i);
        }
        save();
    }

    // ---- language chosen per file ----

    void rememberLanguage(File f, Language lang, boolean note) {
        set("lang." + key(f), lang.name() + (note ? ":note" : ":plain"));
    }

    Language rememberedLanguage(File f) {
        String v = p.getProperty("lang." + key(f));
        if (v == null) return null;
        try { return Language.valueOf(v.split(":")[0]); } catch (IllegalArgumentException e) { return null; }
    }

    Boolean rememberedNote(File f) {
        String v = p.getProperty("lang." + key(f));
        return v == null ? null : v.endsWith(":note");
    }

    /** Which lines of a file are code blocks (a .txt can't store that itself). */
    void rememberCode(File f, String ranges) {
        set("code." + key(f), ranges == null || ranges.isEmpty() ? null : ranges);
    }

    String rememberedCode(File f) {
        return p.getProperty("code." + key(f));
    }

    Color color(String key, Color def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try { return new Color(Integer.parseUnsignedInt(v.replace("#", ""), 16), v.length() > 7); }
        catch (NumberFormatException e) { return def; }
    }

    void setColor(String key, Color c) {
        set(key, c == null ? null : String.format("#%02x%02x%02x%02x", c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue()));
    }

    private static String key(File f) {
        return f.getAbsolutePath().toLowerCase(Locale.ROOT);
    }

    // ---- session (open tabs) ----

    void clearSession() {
        p.stringPropertyNames().stream().filter(k -> k.startsWith("session.")).forEach(p::remove);
    }
}
