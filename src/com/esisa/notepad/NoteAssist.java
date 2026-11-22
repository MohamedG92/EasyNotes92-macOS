/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;

/**
 * Note-taking helpers for one editor:
 *   ==+  -> a line of '='          *+  -> a line of '*'          **+ -> a long line of '*'
 *   new <titre> + Ctrl+Space + Enter -> a numbered chapter in your usual layout
 *   sub <titre> + Ctrl+Space + Enter -> a mini section: 41 '=' / titre / 41 '='
 *   a) / I) / 1) + Enter -> b) / II) / 2) on the next line (Enter on an empty item ends the list)
 *   grey suggestion from your past notes, accepted with Tab (ignore it and keep typing otherwise)
 */
final class NoteAssist {

    static final String EQUALS_LINE = "=========================================================";
    /** =+ : the shorter separator (41 '='). */
    static final String SHORT_EQUALS_LINE = "=".repeat(41);
    static final String STARS_LINE = "************************";
    /** **+ : the long stars line (same length as ==+). */
    static final String LONG_STARS_LINE = "*".repeat(57);
    static final String CHAPTER_RULE = "=".repeat(64);

    private static final Pattern NEW_CHAPTER = Pattern.compile("(?i)^\\s*new(?:\\s+(.*?))?\\s*$");
    private static final Pattern NEW_SUB = Pattern.compile("(?i)^\\s*sub(?:\\s+(.*?))?\\s*$");
    private static final Pattern CHAPTER_NUMBER = Pattern.compile("(?im)^\\s*chap(?:itre|ter)\\s+(\\d+)");
    private static final Pattern ITEM = Pattern.compile("^(\\s*)([0-9]{1,6}|[A-Za-z]{1,6})\\)([ \\t]*)(.*)$");

    private final NoteEditor ed;
    private final Vocabulary vocab;
    private boolean typing;

    // Ctrl+Space list
    private JWindow popup;
    private final DefaultListModel<Item> model = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(model);

    record Item(String label, String detail, Icon icon, Runnable apply) {
        @Override public String toString() { return label; }
    }

    NoteAssist(NoteEditor ed, Vocabulary vocab) {
        this.ed = ed;
        this.vocab = vocab;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, sel, focus);
                Item it = (Item) value;
                c.setIcon(it.icon());
                c.setIconTextGap(8);
                String detail = it.detail() == null ? "" : "&nbsp;&nbsp;<font color='#" + hex(Ui.dim()) + "'>" + it.detail() + "</font>";
                c.setText("<html>" + escape(it.label()) + detail + "</html>");
                c.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 14));
                return c;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1 && list.getSelectedIndex() >= 0) accept();
            }
        });
        install();
    }

    // =====================================================================
    // Wiring
    // =====================================================================

    void attach(StyledDocument d) {
        ((AbstractDocument) d).setDocumentFilter(new Expander());
        d.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                if (typing) SwingUtilities.invokeLater(NoteAssist.this::afterTyping);
            }
            @Override public void removeUpdate(DocumentEvent e) {
                clearGhost();
                if (popupVisible()) SwingUtilities.invokeLater(NoteAssist.this::refreshPopup);
            }
            @Override public void changedUpdate(DocumentEvent e) { /* styles only */ }
        });
    }

    private void install() {
        ed.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                typing = true;                       // set before the editor inserts the character
                SwingUtilities.invokeLater(() -> typing = false);
            }

            @Override public void keyPressed(KeyEvent e) { onKeyPressed(e); }
        });
        ed.addCaretListener(e -> {
            if (ed.ghost != null && e.getDot() != ed.ghostPos) clearGhost();
        });
        ed.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { hidePopup(); }
        });
    }

    private void onKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        int mods = e.getModifiersEx();
        boolean ctrl = (mods & KeyEvent.CTRL_DOWN_MASK) != 0;
        boolean plain = (mods & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
                | KeyEvent.SHIFT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) == 0;

        boolean alt = (mods & KeyEvent.ALT_DOWN_MASK) != 0;
        boolean meta = (mods & KeyEvent.META_DOWN_MASK) != 0;
        boolean shift = (mods & KeyEvent.SHIFT_DOWN_MASK) != 0;
        // Ctrl+Space everywhere; on macOS also Option+Space (Ctrl+Space may switch the keyboard input source there)
        boolean suggestKey = code == KeyEvent.VK_SPACE && !shift && !meta
                && ((ctrl && !alt) || (Platform.MAC && alt && !ctrl));
        if (suggestKey) {
            showPopup();
            e.consume();
            return;
        }
        if (popupVisible()) {
            switch (code) {
                case KeyEvent.VK_DOWN -> { move(1); e.consume(); }
                case KeyEvent.VK_UP -> { move(-1); e.consume(); }
                case KeyEvent.VK_PAGE_DOWN -> { move(5); e.consume(); }
                case KeyEvent.VK_PAGE_UP -> { move(-5); e.consume(); }
                case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> { accept(); e.consume(); }
                case KeyEvent.VK_ESCAPE -> { hidePopup(); e.consume(); }
                case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END -> hidePopup();
                default -> { /* typing continues; the list refreshes after the character lands */ }
            }
            return;
        }
        if (code == KeyEvent.VK_TAB && plain && ed.ghost != null) {
            acceptGhost();
            e.consume();
            return;
        }
        if (code == KeyEvent.VK_ESCAPE && ed.ghost != null) {
            clearGhost();
            e.consume();
            return;
        }
        if (code == KeyEvent.VK_ENTER && plain && (ed.leaveCodeBlockOnEnter() || continueList())) {
            e.consume();
        }
    }

    private void afterTyping() {
        if (popupVisible()) refreshPopup();
        else updateGhost();
    }

    // =====================================================================
    // ==+  =+  **+  and  *+
    // =====================================================================

    private final class Expander extends DocumentFilter {
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (typing && "+".equals(text) && offset >= 1) {
                String before = fb.getDocument().getText(Math.max(0, offset - 2), Math.min(2, offset));
                if (before.equals("==")) {
                    fb.replace(offset - 2, length + 2, EQUALS_LINE, attrs);
                    return;
                }
                if (before.endsWith("=")) {                       // =+  (checked after ==+)
                    fb.replace(offset - 1, length + 1, SHORT_EQUALS_LINE, attrs);
                    return;
                }
                if (before.equals("**")) {                        // **+ (checked before *+)
                    fb.replace(offset - 2, length + 2, LONG_STARS_LINE, attrs);
                    return;
                }
                if (before.endsWith("*")) {
                    fb.replace(offset - 1, length + 1, STARS_LINE, attrs);
                    return;
                }
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }

    // =====================================================================
    // a) b) c)   I) II) III)   1) 2) 3)
    // =====================================================================

    /** Called on Enter. Returns true when it handled the key (continued or ended a list). */
    boolean continueList() {
        if (ed.getSelectionStart() != ed.getSelectionEnd()) return false;
        StyledDocument doc = ed.doc;
        int dot = ed.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        int li = root.getElementIndex(dot);
        Element line = root.getElement(li);
        int ls = line.getStartOffset();
        int le = Math.min(line.getEndOffset() - 1, doc.getLength());
        try {
            if (!doc.getText(dot, le - dot).isBlank()) return false;   // caret in the middle of a line
            Matcher m = ITEM.matcher(doc.getText(ls, dot - ls));
            if (!m.matches()) return false;
            String prev = null;
            if (li > 0) {
                Element p = root.getElement(li - 1);
                Matcher pm = ITEM.matcher(doc.getText(p.getStartOffset(), p.getEndOffset() - 1 - p.getStartOffset()));
                if (pm.matches()) prev = pm.group(2);
            }
            String next = nextMarker(m.group(2), prev);
            if (next == null) return false;

            if (m.group(4).isBlank()) {              // empty item: Enter ends the list, like Word
                String indent = m.group(1);
                ed.inOneUndo(() -> {
                    try {
                        doc.remove(ls, le - ls);
                        doc.insertString(ls, indent, null);
                    } catch (BadLocationException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                return true;
            }
            String gap = m.group(3).isEmpty() ? " " : m.group(3);
            ed.replaceSelection("\n" + m.group(1) + next + ")" + gap);
            return true;
        } catch (BadLocationException e) {
            return false;
        }
    }

    /** a->b, Z->null, 9->10, I->II, iv->v. Single I/V/X/... count as Roman unless the list before was letters. */
    static String nextMarker(String marker, String previous) {
        if (Character.isDigit(marker.charAt(0))) {
            return String.valueOf(Long.parseLong(marker) + 1);
        }
        boolean upper = Character.isUpperCase(marker.charAt(0));
        if (marker.length() > 1) {
            int v = roman(marker);
            return v > 0 && v < 40 ? caseOf(toRoman(v + 1), upper) : null;
        }
        char c = marker.charAt(0);
        boolean roman;
        if (previous != null) {
            int pv = roman(previous);
            roman = pv > 0 && roman(marker) == pv + 1;
        } else {
            roman = c == 'I' || c == 'i';
        }
        if (roman) return caseOf(toRoman(roman(marker) + 1), upper);
        if (c == 'z' || c == 'Z') return null;
        return String.valueOf((char) (c + 1));
    }

    /** Value of a canonical Roman numeral, or -1. */
    static int roman(String s) {
        String u = s.toUpperCase();
        int total = 0, prev = 0;
        for (int i = u.length() - 1; i >= 0; i--) {
            int v = switch (u.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000;
                default -> -1;
            };
            if (v < 0) return -1;
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total > 0 && toRoman(total).equals(u) ? total : -1;
    }

    static String toRoman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) { sb.append(r[i]); n -= v[i]; }
        }
        return sb.toString();
    }

    private static String caseOf(String s, boolean upper) {
        return upper ? s : s.toLowerCase();
    }

    // =====================================================================
    // Chapters
    // =====================================================================

    int nextChapterNumber() {
        Matcher m = CHAPTER_NUMBER.matcher(ed.text());
        int max = 0;
        while (m.find()) {
            try { max = Math.max(max, Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) { /* huge */ }
        }
        return max + 1;
    }

    static String chapterBlock(int n, String title) {
        return CHAPTER_RULE + "\n\t\t\tChapitre " + n + " : " + title + "\n" + CHAPTER_RULE + "\n";
    }

    /** Replaces the "new ..." line [lineStart, lineEnd) with a chapter block. */
    void insertChapter(int lineStart, int lineEnd, int n, String rawTitle) {
        String title = capitalize(rawTitle.trim());
        String block = chapterBlock(n, title);
        StyledDocument doc = ed.doc;
        ed.inOneUndo(() -> {
            try {
                doc.remove(lineStart, lineEnd - lineStart);
                doc.insertString(lineStart, block, null);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        int caret = title.isEmpty()
                ? lineStart + CHAPTER_RULE.length() + 1 + ("\t\t\tChapitre " + n + " : ").length()
                : lineStart + block.length();
        ed.setCaretPosition(Math.min(caret, doc.getLength()));
    }

    static final String SUB_INDENT = "\t\t";

    static String subBlock(String title) {
        return SHORT_EQUALS_LINE + "\n" + SUB_INDENT + title + "\n" + SHORT_EQUALS_LINE + "\n";
    }

    /** Replaces the "sub ..." line [lineStart, lineEnd) with a mini section (no number). */
    void insertSub(int lineStart, int lineEnd, String rawTitle) {
        String title = capitalize(rawTitle.trim());
        String block = subBlock(title);
        StyledDocument doc = ed.doc;
        ed.inOneUndo(() -> {
            try {
                doc.remove(lineStart, lineEnd - lineStart);
                doc.insertString(lineStart, block, null);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        int caret = title.isEmpty()
                ? lineStart + SHORT_EQUALS_LINE.length() + 1 + SUB_INDENT.length()   // empty title: type it there
                : lineStart + block.length();
        ed.setCaretPosition(Math.min(caret, doc.getLength()));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // =====================================================================
    // Ghost suggestion (Tab)
    // =====================================================================

    void updateGhost() {
        clearGhost();
        if (!ed.host.suggestions() || ed.getSelectionStart() != ed.getSelectionEnd()) return;
        int dot = ed.getCaretPosition();
        try {
            Element line = ed.doc.getDefaultRootElement().getElement(ed.doc.getDefaultRootElement().getElementIndex(dot));
            int le = Math.min(line.getEndOffset() - 1, ed.doc.getLength());
            if (!ed.doc.getText(dot, le - dot).isBlank()) return; // only at the end of a line, never over text
        } catch (BadLocationException e) {
            return;
        }
        String prefix = wordBefore(dot);
        if (prefix.length() < 2) return;
        String rest = vocab.complete(prefix, ed.language);
        if (rest == null) return;
        ed.ghost = rest;
        ed.ghostPos = dot;
        ed.repaint();
    }

    void acceptGhost() {
        String g = ed.ghost;
        clearGhost();
        if (g != null) ed.replaceSelection(g);
    }

    void clearGhost() {
        if (ed.ghost != null) {
            ed.ghost = null;
            ed.ghostPos = -1;
            ed.repaint();
        }
    }

    private String wordBefore(int dot) {
        try {
            int start = dot;
            while (start > 0) {
                char c = ed.doc.getText(start - 1, 1).charAt(0);
                if (!Character.isLetterOrDigit(c) && c != '_') break;
                start--;
            }
            String w = ed.doc.getText(start, dot - start);
            return !w.isEmpty() && Character.isDigit(w.charAt(0)) ? "" : w;
        } catch (BadLocationException e) {
            return "";
        }
    }

    // =====================================================================
    // Ctrl+Space list
    // =====================================================================

    void showPopup() {
        clearGhost();
        if (!fillModel()) {
            hidePopup();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(ed);
        if (popup == null || popup.getOwner() != owner) {
            popup = new JWindow(owner);
            popup.setFocusableWindowState(false);
            popup.setType(Window.Type.POPUP);
            JScrollPane sp = new JScrollPane(list);
            sp.setBorder(BorderFactory.createEmptyBorder());
            popup.getContentPane().add(sp, BorderLayout.CENTER);
        }
        SwingUtilities.updateComponentTreeUI(popup);
        ((javax.swing.JComponent) popup.getContentPane()).setBorder(BorderFactory.createLineBorder(Ui.soft(0.18f)));
        list.setBackground(Ui.mix(Ui.surface(), Ui.text(), 0.05f));
        list.setVisibleRowCount(Math.min(8, model.size()));
        list.setSelectedIndex(0);
        popup.pack();
        popup.setSize(Math.max(320, popup.getWidth()), popup.getHeight());
        try {
            Rectangle2D r = ed.modelToView2D(ed.getCaretPosition());
            Point p = new Point((int) r.getX(), (int) (r.getY() + r.getHeight() + 4));
            SwingUtilities.convertPointToScreen(p, ed);
            java.awt.Rectangle visible = ed.getVisibleRect();
            Point bottom = new Point(0, visible.y + visible.height);
            SwingUtilities.convertPointToScreen(bottom, ed);
            if (p.y + popup.getHeight() > bottom.y) {           // no room below the line: open above it
                Point above = new Point((int) r.getX(), (int) r.getY() - popup.getHeight() - 4);
                SwingUtilities.convertPointToScreen(above, ed);
                p = above;
            }
            popup.setLocation(p);
        } catch (BadLocationException e) {
            return;
        }
        popup.setVisible(true);
    }

    private void refreshPopup() {
        if (popupVisible()) showPopup();
    }

    /** Builds the list for the current caret. Returns false when there is nothing to offer. */
    private boolean fillModel() {
        model.clear();
        StyledDocument doc = ed.doc;
        int dot = ed.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(dot));
        int ls = line.getStartOffset();
        int le = Math.min(line.getEndOffset() - 1, doc.getLength());
        String before;
        try {
            before = doc.getText(ls, dot - ls);
        } catch (BadLocationException e) {
            return false;
        }

        Matcher m = NEW_CHAPTER.matcher(before);
        boolean chapter = m.matches();
        if (chapter) {
            String title = m.group(1) == null ? "" : m.group(1).trim();
            int n = nextChapterNumber();
            String label = "Nouveau chapitre " + n + (title.isEmpty() ? "" : " : " + capitalize(title));
            model.addElement(new Item(label, "chapitre", new LangIcon(ed.language, 16),
                    () -> insertChapter(ls, le, n, title)));
        }
        Matcher sm = NEW_SUB.matcher(before);
        boolean sub = sm.matches();
        if (sub) {
            String title = sm.group(1) == null ? "" : sm.group(1).trim();
            String label = "Nouvelle sous-partie" + (title.isEmpty() ? "" : " : " + capitalize(title));
            model.addElement(new Item(label, "sous-partie", new LangIcon(ed.language, 16),
                    () -> insertSub(ls, le, title)));
        }
        String prefix = wordBefore(dot);
        if (!(chapter && prefix.equalsIgnoreCase("new")) && !(sub && prefix.equalsIgnoreCase("sub"))) {
            for (String w : vocab.candidates(prefix, ed.language, 12)) {
                String rest = w.substring(prefix.length());
                model.addElement(new Item(w, null, null, () -> ed.replaceSelection(rest)));
            }
        }
        return !model.isEmpty();   // nothing to suggest: no popup at all
    }

    private void move(int delta) {
        int i = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void accept() {
        Item it = list.getSelectedValue();
        hidePopup();
        if (it != null) it.apply().run();
    }

    void hidePopup() {
        if (popup != null) popup.setVisible(false);
    }

    boolean popupVisible() {
        return popup != null && popup.isVisible();
    }

    /** Test hook: the items Ctrl+Space would show right now. */
    List<Item> itemsForTest() {
        fillModel();
        return java.util.Collections.list(model.elements());
    }

    private static String hex(java.awt.Color c) {
        return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
