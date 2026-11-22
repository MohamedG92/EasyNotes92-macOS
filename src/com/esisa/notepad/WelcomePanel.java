/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** New-tab page from the drawing: "Choose format : Normal txt or Language Prise de Note txt". */
final class WelcomePanel extends JPanel implements javax.swing.Scrollable {

    private static final long serialVersionUID = 1L;

    interface Choice {
        void chosen(boolean note, Language lang);
    }

    private static final Language[] NOTE_LANGS = {
        Language.PYTHON, Language.JAVA, Language.CSHARP, Language.CPP, Language.C,
        Language.JAVASCRIPT, Language.REACT, Language.HTML, Language.SQL, Language.LINUX
    };

    private static final int MAX_W = 640;

    private final JButton normalCard;
    private final JPanel column = new JPanel();
    private final JPanel grid = new JPanel(new GridLayout(0, 5, 10, 10));
    private int laidOutWidth = -1;

    WelcomePanel(Choice choice) {
        super(new GridBagLayout());

        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel title = label("Choisir le format", Ui.ui(26f, Font.BOLD));
        JLabel subtitle = label("Choose format — fichier normal ou prise de note par langage", Ui.ui(13f, Font.PLAIN));
        subtitle.putClientProperty("dim", true);
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        column.add(subtitle);
        column.add(Box.createVerticalStrut(26));

        normalCard = new Card(new LangIcon(Language.TEXT, 34), "Normal .txt",
                Platform.MAC ? "Un fichier texte simple, comme TextEdit" : "Un fichier texte simple, comme le Notepad de Windows", () -> choice.chosen(false, Language.TEXT));
        normalCard.setAlignmentX(LEFT_ALIGNMENT);
        normalCard.setMaximumSize(new Dimension(MAX_W, 86));
        column.add(normalCard);
        column.add(Box.createVerticalStrut(26));

        JLabel noteTitle = label("Prise de note — choisis le langage", Ui.ui(15f, Font.BOLD));
        column.add(noteTitle);
        column.add(Box.createVerticalStrut(10));

        grid.setOpaque(false);
        for (Language l : NOTE_LANGS) {
            Card card = new Card(new LangIcon(l, 32), l.label, null, () -> choice.chosen(true, l));
            card.stack();   // logo on top, name below, so long names like "JavaScript" fit
            grid.add(card);
        }
        grid.setAlignmentX(LEFT_ALIGNMENT);
        column.add(grid);
        column.add(Box.createVerticalStrut(18));

        JPanel other = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        other.setOpaque(false);
        JButton otherNote = new Card(new LangIcon(Language.TEXT, 20), "Autre sujet (prise de note sans langage)", null,
                () -> choice.chosen(true, Language.TEXT));
        other.add(otherNote);
        other.setAlignmentX(LEFT_ALIGNMENT);
        column.add(other);

        add(column);
        setBorder(BorderFactory.createEmptyBorder(24, 20, 24, 20));
        fitWidth(MAX_W + 40);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { fitWidth(getWidth()); }
        });
    }

    /** Narrow window (snapped to a side or a quarter): shrink the column and use fewer language columns. */
    private void fitWidth(int width) {
        int w = Math.max(220, Math.min(MAX_W, width - 40));
        if (w == laidOutWidth) return;
        laidOutWidth = w;
        int cols = w >= 560 ? 5 : w >= 400 ? 4 : w >= 300 ? 3 : 2;
        ((GridLayout) grid.getLayout()).setColumns(cols);
        int rows = (NOTE_LANGS.length + cols - 1) / cols;
        grid.setMaximumSize(new Dimension(w, rows * 94));
        normalCard.setMaximumSize(new Dimension(w, 86));
        column.setPreferredSize(null);
        column.setPreferredSize(new Dimension(w, column.getPreferredSize().height));
        revalidate();
    }

    // Scrollable: follow the window width, scroll vertically when the window is short (quarter snap)
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 20; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 40; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(Ui.surface());
    }

    void focusDefault() {
        normalCard.requestFocusInWindow();
    }

    private static JLabel label(String text, Font font) {
        JLabel l = new JLabel(text) {
            private static final long serialVersionUID = 1L;
            @Override public void updateUI() {
                super.updateUI();
                setForeground(Boolean.TRUE.equals(getClientProperty("dim")) ? Ui.dim() : Ui.text());
            }
        };
        l.setFont(font);
        l.setForeground(Ui.text());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** A rounded, hoverable card button. Colours come from the theme at paint time. */
    private static final class Card extends JButton {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        Card(javax.swing.Icon icon, String title, String description, Runnable action) {
            setLayout(new BorderLayout(14, 0));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            JLabel iconLabel = new JLabel(icon);
            add(iconLabel, BorderLayout.WEST);
            JPanel texts = new JPanel(new GridLayout(description == null ? 1 : 2, 1));
            texts.setOpaque(false);
            JLabel t = label(title, Ui.ui(description == null ? 13.5f : 16f, Font.BOLD));
            t.setHorizontalAlignment(SwingConstants.LEFT);
            texts.add(t);
            if (description != null) {
                JLabel d = label(description, Ui.ui(12.5f, Font.PLAIN));
                d.putClientProperty("dim", true);
                d.setForeground(Ui.dim());
                texts.add(d);
            }
            add(texts, BorderLayout.CENTER);
            addActionListener(e -> action.run());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        /** Vertical layout: centred logo above the name. */
        void stack() {
            setLayout(new BorderLayout(0, 8));
            java.awt.Component icon = getComponent(0), texts = getComponent(1);
            removeAll();
            ((JLabel) icon).setHorizontalAlignment(SwingConstants.CENTER);
            JLabel t = (JLabel) ((JPanel) texts).getComponent(0);
            t.setHorizontalAlignment(SwingConstants.CENTER);
            add(icon, BorderLayout.CENTER);
            add(texts, BorderLayout.SOUTH);
            setBorder(BorderFactory.createEmptyBorder(14, 6, 12, 6));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(Ui.soft(hover || isFocusOwner() ? 0.11f : 0.055f));
            g.fill(r);
            g.setColor(hover || isFocusOwner() ? Ui.accent() : Ui.soft(0.12f));
            g.draw(r);
            g.dispose();
            super.paintComponent(g0);
        }
    }
}
