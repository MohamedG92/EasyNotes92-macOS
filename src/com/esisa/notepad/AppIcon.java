/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * The app icon (night-blue tile, a page, and the ===== separators you use in your notes).
 * Running this class's main writes a Windows .ico for the exe (build.ps1 does that).
 */
final class AppIcon {

    private AppIcon() {}

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Ui.smooth(g);
        g.scale(size / 64.0, size / 64.0);

        g.setPaint(new GradientPaint(0, 0, new Color(0x2C4F8F), 64, 64, new Color(0x0D1628)));
        g.fill(new RoundRectangle2D.Double(2, 2, 60, 60, 18, 18));

        Path2D page = new Path2D.Double();
        page.moveTo(17, 10); page.lineTo(39, 10); page.lineTo(48, 19); page.lineTo(48, 54);
        page.lineTo(17, 54); page.closePath();
        g.setColor(new Color(0xEAF0FA));
        g.fill(page);
        Path2D fold = new Path2D.Double();
        fold.moveTo(39, 10); fold.lineTo(39, 19); fold.lineTo(48, 19); fold.closePath();
        g.setColor(new Color(0xA9BBDC));
        g.fill(fold);

        g.setColor(new Color(0x4F8CFF));
        g.fill(new RoundRectangle2D.Double(22, 24, 21, 3.4, 3, 3));
        g.fill(new RoundRectangle2D.Double(22, 31, 21, 3.4, 3, 3));
        g.fill(new RoundRectangle2D.Double(22, 38, 13, 3.4, 3, 3));
        g.setColor(new Color(0xFFB547));
        g.fill(new RoundRectangle2D.Double(22, 45, 21, 2.6, 2, 2));
        g.dispose();
        return img;
    }

    static List<Image> images() {
        List<Image> list = new ArrayList<>();
        for (int s : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) list.add(render(s));
        return list;
    }

    /** Writes a multi-size .ico (PNG-compressed entries, supported since Windows Vista). */
    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("--store-assets")) { storeAssets(Path.of(args[1])); return; }
        if (args.length == 2 && args[0].equals("--iconset")) { iconset(Path.of(args[1])); return; }
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(render(s), "png", out);
            pngs.add(out.toByteArray());
        }
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            header.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
            header.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            header.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        Path target = Path.of(args.length > 0 ? args[0] : "easynotes92.ico");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(target))) {
            out.write(header.array());
            for (byte[] png : pngs) out.write(png);
        }
        System.out.println("icon written: " + target.toAbsolutePath());
    }

    /**
     * macOS icon set (dir must end in .iconset): build-mac.sh turns it into EasyNotes92.icns with iconutil.
     * macOS icons keep a margin around the tile (about 10% each side), like Apple's own icons.
     */
    static void iconset(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (int base : new int[] {16, 32, 128, 256, 512}) {
            for (int scale : new int[] {1, 2}) {
                int px = base * scale;
                BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                int tile = Math.round(px * 0.8f), off = (px - tile) / 2;
                g.drawImage(render(tile), off, off, null);
                g.dispose();
                String name = "icon_" + base + "x" + base + (scale == 2 ? "@2x" : "") + ".png";
                ImageIO.write(img, "png", dir.resolve(name).toFile());
            }
        }
        System.out.println("iconset written: " + dir.toAbsolutePath());
    }

    /** PNG logos for the MSIX package (Microsoft Store), written into dir. */
    static void storeAssets(Path dir) throws IOException {
        Files.createDirectories(dir);
        ImageIO.write(render(44), "png", dir.resolve("Square44x44Logo.png").toFile());
        ImageIO.write(render(44), "png", dir.resolve("Square44x44Logo.targetsize-44_altform-unplated.png").toFile());
        ImageIO.write(render(150), "png", dir.resolve("Square150x150Logo.png").toFile());
        ImageIO.write(render(50), "png", dir.resolve("StoreLogo.png").toFile());
        BufferedImage wide = new BufferedImage(310, 150, BufferedImage.TYPE_INT_ARGB);   // icon centred, transparent sides
        Graphics2D g = wide.createGraphics();
        g.drawImage(render(120), 95, 15, null);
        g.dispose();
        ImageIO.write(wide, "png", dir.resolve("Wide310x150Logo.png").toFile());
        System.out.println("store assets written: " + dir.toAbsolutePath());
    }
}
