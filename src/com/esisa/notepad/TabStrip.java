/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatClientProperties;

/** Notepad-style tabs: language logo, name, ● when unsaved, × to close. The selected tab merges into the page below. */
final class TabStrip extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int HEIGHT = 42;
    private static final int MAX_W = 230, MIN_W = 96, GAP = 2;

    interface Model {
        String title(Object tab);
        Icon icon(Object tab);
        boolean dirty(Object tab);
        String tooltip(Object tab);
        boolean selected(Object tab);
    }

    private final Model model;
    private final Consumer<Object> onSelect;
    private final Consumer<Object> onClose;
    private final java.util.function.BiConsumer<Object, Integer> onMove;
    private final List<Chip> chips = new ArrayList<>();
    final JButton plus;

    TabStrip(Model model, Consumer<Object> onSelect, Consumer<Object> onClose, Runnable onNew,
             java.util.function.BiConsumer<Object, Integer> onMove) {
        super(null);
        this.model = model;
        this.onSelect = onSelect;
        this.onClose = onClose;
        this.onMove = onMove;
        setOpaque(false);
        putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true); // empty space drags the window
        plus = new JButton(new Glyph(Glyph.Kind.PLUS, 16));
        plus.setToolTipText(Platform.label("Nouvel onglet (Ctrl+N)"));
        plus.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        plus.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, false);
        plus.setFocusable(false);
        plus.addActionListener(e -> onNew.run());
        add(plus);
    }

    void setTabs(List<?> tabs) {
        for (Chip c : chips) remove(c);
        chips.clear();
        for (Object t : tabs) {
            Chip c = new Chip(t);
            chips.add(c);
            add(c);
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, HEIGHT);
    }

    @Override
    public void doLayout() {
        int n = chips.size();
        int plusW = 36;
        int avail = Math.max(0, getWidth() - plusW - 8);
        int x = 0;
        int each = n == 0 ? 0 : Math.max(MIN_W, Math.min(MAX_W, (avail - GAP * n) / n));
        for (Chip c : chips) {
            int w = Math.min(each, c.naturalWidth());
            w = Math.max(Math.min(each, MIN_W), w);
            c.setBounds(x, 6, w, HEIGHT - 6);
            x += w + GAP;
        }
        plus.setBounds(Math.min(x + 2, getWidth() - plusW), 9, plusW - 4, HEIGHT - 16);
    }

    private final class Chip extends JComponent {
        private static final long serialVersionUID = 1L;
        final Object tab;
        boolean hover, hoverClose;
        /** Set once the mouse has moved enough to count as a drag (not a click). */
        boolean dragging;
        int pressX;

        Chip(Object tab) {
            this.tab = tab;
            putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, false);
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(model.tooltip(tab));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragging = false;
                    pressX = e.getXOnScreen();
                    if (SwingUtilities.isMiddleMouseButton(e)) { onClose.accept(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(e) && !closeRect().contains(e.getPoint())) onSelect.accept(tab);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean wasDrag = dragging;
                    dragging = false;
                    setCursor(Cursor.getDefaultCursor());
                    TabStrip.this.repaint();
                    if (!wasDrag && SwingUtilities.isLeftMouseButton(e) && closeRect().contains(e.getPoint())) onClose.accept(tab);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (!dragging && Math.abs(e.getXOnScreen() - pressX) < 6) return;   // small jitter is still a click
                    dragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    int x = SwingUtilities.convertPoint(Chip.this, e.getPoint(), TabStrip.this).x;
                    moveChip(Chip.this, x);
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; hoverClose = false; repaint(); }
                @Override public void mouseMoved(MouseEvent e) {
                    boolean hc = closeRect().contains(e.getPoint());
                    if (hc != hoverClose) { hoverClose = hc; repaint(); }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        int naturalWidth() {
            FontMetrics fm = getFontMetrics(Ui.ui(13f, Font.BOLD)); // measure bold: the selected tab is bold
            return fm.stringWidth(model.title(tab)) + 84;
        }

        Rectangle closeRect() {
            return new Rectangle(getWidth() - 30, (getHeight() - 22) / 2, 22, 22);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            return model.tooltip(tab);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            boolean sel = model.selected(tab);
            int w = getWidth(), h = getHeight();
            if (sel) {
                Path2D shape = new Path2D.Double();
                double r = 10;
                shape.moveTo(0, h + 1);
                shape.lineTo(0, r);
                shape.quadTo(0, 0, r, 0);
                shape.lineTo(w - r, 0);
                shape.quadTo(w, 0, w, r);
                shape.lineTo(w, h + 1);
                shape.closePath();
                g.setColor(Ui.surface());
                g.fill(shape);
                g.setColor(Ui.accent());
                g.fill(new RoundRectangle2D.Double(12, 0, w - 24, 2.2, 2, 2));
            } else if (hover) {
                g.setColor(Ui.alpha(Ui.text(), 20));
                g.fill(new RoundRectangle2D.Double(2, 3, w - 4, h - 9, 10, 10));
            }

            Icon icon = model.icon(tab);
            int iy = (h - icon.getIconHeight()) / 2 - (sel ? 0 : 2);
            icon.paintIcon(this, g, 12, iy);

            Font f = Ui.ui(13f, sel ? Font.BOLD : Font.PLAIN);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            Color fg = sel ? Ui.text() : Ui.mix(Ui.text(), Ui.chrome(), 0.3f);
            g.setColor(fg);
            int textX = 12 + icon.getIconWidth() + 9;
            int textMax = w - textX - 34;
            g.drawString(ellipsize(model.title(tab), fm, textMax), textX, (h + fm.getAscent() - fm.getDescent()) / 2 - (sel ? 0 : 2));

            Rectangle cr = closeRect();
            cr.y -= sel ? 0 : 2;
            boolean dirty = model.dirty(tab);
            if (hoverClose) {
                g.setColor(Ui.alpha(Ui.text(), 36));
                g.fill(new RoundRectangle2D.Double(cr.x, cr.y, cr.width, cr.height, 7, 7));
            }
            if (dirty && !hover) {
                g.setColor(fg);
                g.fill(new Ellipse2D.Double(cr.getCenterX() - 3.5, cr.getCenterY() - 3.5, 7, 7));
            } else if (sel || hover) {
                g.setColor(fg);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double cx = cr.getCenterX(), cy = cr.getCenterY(), d = 4.2;
                g.draw(new Line2D.Double(cx - d, cy - d, cx + d, cy + d));
                g.draw(new Line2D.Double(cx - d, cy + d, cx + d, cy - d));
            }
            g.dispose();
        }
    }

    /** Puts the dragged tab where the mouse is: before the first tab whose middle is to the right of x. */
    private void moveChip(Chip chip, int x) {
        int from = chips.indexOf(chip);
        int to = chips.size() - 1;
        for (int i = 0; i < chips.size(); i++) {
            Chip c = chips.get(i);
            if (c != chip && x < c.getX() + c.getWidth() / 2) {
                to = i > from ? i - 1 : i;
                break;
            }
        }
        if (to == from || from < 0) return;
        chips.remove(chip);
        chips.add(to, chip);
        doLayout();
        repaint();
        onMove.accept(chip.tab, to);
    }

    private static String ellipsize(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > max) end--;
        return s.substring(0, end) + dots;
    }

    @Override
    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }
}
