/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.io.File;

import javax.swing.SwingUtilities;

/** Entry point: Run As > Java Application on this class (or start EasyNotes92.exe). */
public class Main {

    public static void main(String[] args) {
        Platform.beforeUi();
        Settings settings = new Settings();
        boolean ownsSession = settings.acquireSessionLock();
        if (!ownsSession && SingleInstance.sendToRunning(settings.dir, args)) {
            return; // EasyNotes92 is already open: it comes to the front (and opens the files) instead
        }
        SwingUtilities.invokeLater(() -> {
            Theme.apply(Theme.byName(settings.get("theme", Theme.ALL.get(0).name())));
            AppFrame frame = new AppFrame(settings, ownsSession);
            frame.setVisible(true);
            for (String a : args) frame.openFile(new File(a));
            MacIntegration.install(frame);
            if (ownsSession) {
                SingleInstance.listen(settings.dir, files -> SwingUtilities.invokeLater(() -> {
                    frame.bringToFront();
                    for (String f : files) frame.openFile(new File(f));
                }));
            }
        });
    }
}
