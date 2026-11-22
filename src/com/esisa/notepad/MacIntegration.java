/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Desktop;
import java.awt.Taskbar;
import java.io.File;

import javax.swing.SwingUtilities;

/**
 * macOS app-menu and Finder hooks: Cmd+Q saves the session like closing the window,
 * About opens our dialog, files double-clicked in Finder open as tabs, and the Dock shows our icon.
 * Does nothing on Windows.
 */
final class MacIntegration {

    private MacIntegration() {}

    static void install(AppFrame frame) {
        if (!Platform.MAC || !Desktop.isDesktopSupported()) return;
        Desktop d = Desktop.getDesktop();
        if (d.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            d.setQuitHandler((e, response) -> SwingUtilities.invokeLater(() -> {
                frame.exitApp();          // saves the session and exits; returns only if the user cancelled
                response.cancelQuit();
            }));
        }
        if (d.isSupported(Desktop.Action.APP_ABOUT)) {
            d.setAboutHandler(e -> SwingUtilities.invokeLater(frame::showAbout));
        }
        if (d.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            d.setOpenFileHandler(e -> SwingUtilities.invokeLater(() -> {
                frame.bringToFront();
                for (File f : e.getFiles()) frame.openFile(f);
            }));
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(AppIcon.render(256));
        }
    }
}
