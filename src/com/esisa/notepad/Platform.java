/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Desktop;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** Everything that differs between Windows and macOS lives here. */
final class Platform {

    private Platform() {}

    static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    /** Cmd on macOS, Ctrl elsewhere. */
    static final int MENU_MASK = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    /**
     * Turns a Windows shortcut ("control S") into the macOS one ("meta S" = Cmd+S).
     * Ctrl+Tab stays Ctrl (Cmd+Tab is the app switcher), and the few keys macOS already uses get another key.
     */
    static String key(String k) {
        if (!MAC || k == null) return k;
        return switch (k) {
            case "control TAB", "control shift TAB" -> k;   // Cmd+Tab switches apps on macOS
            case "alt A" -> "meta L";                        // Option+A types "å"; Cmd+L = select line (like VS Code)
            case "control H" -> "meta alt F";                // Cmd+H hides the app; Cmd+Option+F = replace (Mac standard)
            case "F5" -> "meta shift D";                     // F-keys need fn on most Macs
            default -> k.replace("control", "meta");
        };
    }

    /** Same idea for the texts of the shortcuts help window. */
    static String label(String s) {
        if (!MAC) return s;
        return s.replace("Ctrl+Espace", "⌃Espace (ou ⌥Espace)")
                .replace("Ctrl+Tab", "⌃Tab")
                .replace("Alt+A", "⌘L")
                .replace("Ctrl+H", "⌘⌥F")
                .replace("F5", "⌘⇧D")
                .replace("Ctrl+Alt+", "⌘⌥")
                .replace("Ctrl+Shift+", "⌘⇧")
                .replace("Ctrl+molette", "⌘+molette")
                .replace("Ctrl+", "⌘")
                .replace("Alt+", "⌥");
    }

    /** Where settings and the session live: %APPDATA% on Windows, ~/Library/Application Support on macOS. */
    static Path appDataDir() {
        String home = System.getProperty("user.home");
        if (MAC) return Path.of(home, "Library", "Application Support");
        String appData = System.getenv("APPDATA");
        return Path.of(appData != null ? appData : home);
    }

    /** Best monospaced font installed: Cascadia Code / Consolas on Windows, SF Mono / Menlo on macOS. */
    static String monoFont(Set<String> families) {
        for (String f : new String[] {"Cascadia Code", "Consolas", "SF Mono", "Menlo", "Monaco", "DejaVu Sans Mono"}) {
            if (families.contains(f)) return f;
        }
        return java.awt.Font.MONOSPACED;
    }

    /** Shows the file selected in Explorer (Windows) or Finder (macOS). */
    static void reveal(File file) {
        try {
            if (MAC) new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
            else new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
        } catch (IOException e) {
            try { Desktop.getDesktop().open(file.getParentFile()); } catch (IOException ignored) { /* no shell */ }
        }
    }

    /** macOS settings that must be set before the first window is created. */
    static void beforeUi() {
        if (!MAC) return;
        System.setProperty("apple.awt.application.name", AppFrame.APP);
        System.setProperty("apple.awt.application.appearance", "system"); // dark title bar with dark themes
    }
}
