/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

/**
 * Main window, laid out like the drawing:
 *   row 1  ◁  [logo] tab  [logo] tab  +          (inside the title bar, like Windows 11 Notepad)
 *   row 2  [ file path            ]  [ search ]
 *   row 3  File  Edit  Format  View  Help                 B I U colour
 *   page   the note (or the "Choisir le format" page on a new tab)
 *   status Ln/Col · characters/words · language · zoom · line ending · encoding
 */
public final class AppFrame extends JFrame implements NoteEditor.Host {

    private static final long serialVersionUID = 1L;
    static final String APP = "EasyNotes92";

    /** One tab: the format page first, then an editor once a format is chosen. */
    static final class DocTab {
        final String id;
        NoteEditor editor;
        JScrollPane scroll;
        WelcomePanel welcome;
        JComponent view;
        String sessionFile;
        long sessionSavedAt = -1;

        DocTab(String id) { this.id = id; }
    }

    final transient Settings settings;
    final transient Vocabulary vocab = new Vocabulary();
    private final boolean sessionOwner;

    final List<DocTab> tabs = new ArrayList<>();
    DocTab current;
    private int tabSeq;

    private boolean wordWrap;
    private boolean suggestions;
    private Font baseFont;
    private int zoom;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel header = new JPanel();
    private final JPanel titleRow = new JPanel(new BorderLayout());
    private final JPanel barsRow = new JPanel(new GridBagLayout());
    private final JPanel menuRow = new JPanel(new BorderLayout());
    private final JMenuBar menuBar = new JMenuBar();
    private final TabStrip strip;
    private final JButton recentButton = new JButton(new Glyph(Glyph.Kind.BACK, 16));
    private final Pill pathPill = new Pill();
    private final JTextField search = new JTextField();
    private final JLabel searchCount = new JLabel();
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private final JLabel posLabel = statusLabel();
    private final JLabel countLabel = statusLabel();
    private final JLabel langLabel = statusLabel();
    private final JLabel zoomLabel = statusLabel();
    private final JLabel eolLabel = statusLabel();
    private final JLabel encLabel = statusLabel();
    private final JPanel quickButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    private final FindReplaceDialog replaceDialog;
    private final JFileChooser chooser = new JFileChooser();
    private final FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text Documents (*.txt)", "txt");
    private final FileNameExtensionFilter rtfFilter = new FileNameExtensionFilter("Rich Text (*.rtf)", "rtf");
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final transient NoteTransfer transfer = new NoteTransfer();
    private final transient List<Object[]> shortcuts = new ArrayList<>();
    private final transient List<Object> searchTags = new ArrayList<>();
    private transient NoteEditor searchEditor;
    private final Timer statusTimer;
    private final Timer vocabTimer;
    private final Timer autosaveTimer;
    /** Saves the open tabs shortly after anything changes, so even a crash or shutdown keeps them. */
    private final Timer sessionSoon;
    private JMenu recentMenu;
    private JCheckBoxMenuItem wrapItem;
    private final List<JRadioButtonMenuItem> themeItems = new ArrayList<>();
    private transient NoteEditor.Palette palette;

    public AppFrame(Settings settings, boolean sessionOwner) {
        super(APP);
        this.settings = settings;
        this.sessionOwner = sessionOwner;
        wordWrap = settings.getBool("wordWrap", true);
        suggestions = settings.getBool("suggestions", true);
        zoom = settings.getInt("zoom", 100);
        baseFont = new Font(settings.get("font.family", defaultFontFamily()),
                settings.getInt("font.style", Font.PLAIN), settings.getInt("font.size", 15));
        setIconImages(AppIcon.images());
        palette = computePalette();

        strip = new TabStrip(new TabStrip.Model() {
            @Override public String title(Object t) { return tabTitle((DocTab) t); }
            @Override public Icon icon(Object t) { return tabIcon((DocTab) t, 16); }
            @Override public boolean dirty(Object t) { return ((DocTab) t).editor != null && ((DocTab) t).editor.dirty; }
            @Override public String tooltip(Object t) { return tabTooltip((DocTab) t); }
            @Override public boolean selected(Object t) { return t == current; }
        }, t -> select((DocTab) t), t -> closeTab((DocTab) t), this::newTab, (t, index) -> moveTab((DocTab) t, index));

        replaceDialog = new FindReplaceDialog(this, this::editor);
        buildHeader();
        buildMenus();
        buildContextMenu();
        buildStatusBar();

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        statusBar.setVisible(settings.getBool("statusBar", true));
        getRootPane().setTransferHandler(transfer);  // drop files anywhere to open them

        statusTimer = new Timer(120, e -> updateStatus());
        statusTimer.setRepeats(false);
        vocabTimer = new Timer(2000, e -> {
            if (current != null && current.editor != null) vocab.setSource("tab:" + current.id, current.editor.text());
        });
        vocabTimer.setRepeats(false);
        autosaveTimer = new Timer(15000, e -> saveSession());
        autosaveTimer.start();
        sessionSoon = new Timer(2000, e -> saveSession());
        sessionSoon.setRepeats(false);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitApp(); }
            /** The first frame on screen could show the restored tab without its colours until a click:
             *  repaint everything once the window is really up (and once more a moment later). */
            @Override public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(AppFrame.this::repaintAll);
                Timer again = new Timer(400, ev -> repaintAll());
                again.setRepeats(false);
                again.start();
            }
        });

        setSize(settings.getInt("window.w", 1180), settings.getInt("window.h", 760));
        setMinimumSize(new Dimension(380, 300)); // small enough for quarter snaps, even at 150% scaling
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { fitWidth(); }
        });
        setLocationRelativeTo(null);
        if (settings.getBool("window.max", false)) setExtendedState(Frame.MAXIMIZED_BOTH);

        restoreSession();
        if (tabs.isEmpty()) newTab();
        vocab.scanAsync(noteFiles());
        recolor();
    }

    private static String defaultFontFamily() {
        Set<String> fams = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        return Platform.monoFont(fams);
    }

    NoteEditor editor() {
        return current == null ? null : current.editor;
    }

    private void repaintAll() {
        NoteEditor ed = editor();
        if (ed != null) {
            ed.revalidate();
            ed.repaint();
        }
        getRootPane().repaint();
    }

    private void withEditor(Consumer<NoteEditor> action) {
        NoteEditor ed = editor();
        if (ed != null) action.accept(ed);
    }

    // =====================================================================
    // Header: tab row, path + search row, menu row
    // =====================================================================

    private void buildHeader() {
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        boolean inTitleBar = FlatLaf.supportsNativeWindowDecorations() || Platform.MAC;
        if (Platform.MAC) {
            // tabs drawn in the title bar, next to the red/yellow/green buttons
            getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        } else if (inTitleBar) {
            getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        }

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JLabel appIcon = new JLabel(new javax.swing.ImageIcon(AppIcon.render(20)));
        appIcon.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
        left.add(appIcon);
        recentButton.setToolTipText("Fichiers récents");
        recentButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        recentButton.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, false);
        recentButton.setFocusable(false);
        recentButton.addActionListener(e -> showRecentPopup());
        left.add(recentButton);
        left.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 4));
        left.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);

        JPanel buttonsPlaceholder = new JPanel();
        buttonsPlaceholder.setOpaque(false);
        if (inTitleBar) buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER,
                Platform.MAC ? "mac" : "win");
        else buttonsPlaceholder.setPreferredSize(new Dimension(8, 8));

        if (Platform.MAC) {
            // macOS: window buttons on the left, before the app icon
            JPanel west = new JPanel(new BorderLayout());
            west.setOpaque(false);
            west.add(buttonsPlaceholder, BorderLayout.WEST);
            west.add(left, BorderLayout.CENTER);
            west.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);
            titleRow.add(west, BorderLayout.WEST);
            titleRow.add(strip, BorderLayout.CENTER);
        } else {
            titleRow.add(left, BorderLayout.WEST);
            titleRow.add(strip, BorderLayout.CENTER);
            titleRow.add(buttonsPlaceholder, BorderLayout.EAST);
        }
        titleRow.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, TabStrip.HEIGHT));

        // row 2: path + search (the two long bars of the drawing)
        pathPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pathPill.setToolTipText("Ouvrir le dossier du fichier");
        pathPill.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openContainingFolder(); }
        });
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Platform.label("Rechercher dans la note…  (Ctrl+F)"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Glyph(Glyph.Kind.SEARCH, 15));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        JPanel searchNav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchNav.setOpaque(false);
        searchNav.add(searchCount);
        for (boolean down : new boolean[] {false, true}) {
            JButton b = new JButton(new Glyph(down ? Glyph.Kind.DOWN : Glyph.Kind.UP, 13));
            b.setToolTipText(down ? "Suivant (↓ ou Entrée)" : "Précédent (↑ ou Shift+Entrée)");
            b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            b.setFocusable(false);
            b.setMargin(new Insets(2, 3, 2, 3));
            b.addActionListener(e -> findInEditor(down));
            searchNav.add(b);
        }
        search.putClientProperty("JTextField.trailingComponent", searchNav);
        search.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 5,10,5,10");
        searchCount.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
        search.setPreferredSize(new Dimension(380, 34));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { highlightMatches(); }
            @Override public void removeUpdate(DocumentEvent e) { highlightMatches(); }
            @Override public void changedUpdate(DocumentEvent e) { /* attributes only */ }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "next");
        search.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "prev");
        search.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "next");
        search.getInputMap().put(KeyStroke.getKeyStroke("UP"), "prev");
        search.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "leave");
        search.getActionMap().put("next", action(() -> findInEditor(true)));
        search.getActionMap().put("prev", action(() -> findInEditor(false)));
        search.getActionMap().put("leave", action(() -> { search.setText(""); withEditor(JComponent::requestFocusInWindow); }));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(8, 14, 4, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        barsRow.add(pathPill, c);
        c.weightx = 0;
        c.insets = new Insets(8, 10, 4, 14);
        barsRow.add(search, c);
        barsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        // row 3: menus + quick format buttons
        menuBar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        menuBar.setOpaque(false);
        menuRow.add(menuBar, BorderLayout.WEST);
        quickButtons.setOpaque(false);
        quickButtons.add(quick("B", Font.BOLD, Platform.label("Gras (Ctrl+B)"), () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Bold))));
        quickButtons.add(quick("I", Font.ITALIC, Platform.label("Italique (Ctrl+I)"), () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Italic))));
        quickButtons.add(quick("U", Font.PLAIN, Platform.label("Souligné (Ctrl+U)"), () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Underline))));
        JButton color = new JButton(new Glyph(Glyph.Kind.PALETTE, 16));
        styleQuick(color, Platform.label("Couleur du texte (Ctrl+Shift+C)"));
        color.addActionListener(e -> chooseColor());
        quickButtons.add(color);
        quickButtons.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        menuRow.add(quickButtons, BorderLayout.EAST);
        menuRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        header.add(titleRow);
        header.add(barsRow);
        header.add(menuRow);
    }

    private JButton quick(String text, int style, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(Ui.ui(14f, style == Font.PLAIN ? Font.PLAIN : style));
        if (text.equals("U")) {
            b.setText("<html><u>U</u></html>");
        }
        styleQuick(b, tip);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static void styleQuick(JButton b, String tip) {
        b.setToolTipText(tip);
        b.setFocusable(false);   // keep the editor's selection
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setPreferredSize(new Dimension(34, 30));
    }

    /** Snapped to half / a third / a quarter of the screen: drop the less useful bits so the note keeps its space. */
    private void fitWidth() {
        int w = getWidth();
        pathPill.setVisible(w >= 620);
        int searchW = pathPill.isVisible() ? Math.max(220, Math.min(380, (int) (w * 0.38))) : Math.max(160, w - 40);
        search.setPreferredSize(new Dimension(searchW, 34));
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                w >= 760 ? Platform.label("Rechercher dans la note…  (Ctrl+F)") : "Rechercher…");
        countLabel.setVisible(w >= 700);
        eolLabel.setVisible(w >= 820);
        encLabel.setVisible(w >= 560);
        zoomLabel.setVisible(w >= 480);
        quickButtons.setVisible(w >= 440);
        barsRow.revalidate();
        statusBar.revalidate();
    }

    /** Re-applies the colours of the custom-painted parts (after startup and after every theme change). */
    private void recolor() {
        Color surface = Ui.surface(), chrome = Ui.chrome();
        titleRow.setBackground(chrome);
        barsRow.setBackground(surface);
        menuRow.setBackground(surface);
        menuBar.setBackground(surface);
        menuRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.soft(0.08f)));
        content.setBackground(surface);
        statusBar.setBackground(chrome);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.soft(0.06f)));
        for (JLabel l : new JLabel[] {posLabel, countLabel, langLabel, zoomLabel, eolLabel, encLabel}) {
            l.setForeground(Ui.dim());
        }
        searchCount.setForeground(Ui.dim());
        for (DocTab t : tabs) {
            if (t.scroll != null) t.scroll.getViewport().setBackground(surface);
            if (t.welcome != null && t.view instanceof JScrollPane sp) sp.getViewport().setBackground(surface);
        }
        for (JComponent extra : new JComponent[] {contextMenu, chooser}) SwingUtilities.updateComponentTreeUI(extra);
        strip.repaint();
        repaint();
    }

    // =====================================================================
    // Menus + shortcuts
    // =====================================================================

    private void buildMenus() {
        JMenu file = menu("File", 'F');
        item(file, "Nouvel onglet", "control N", this::newTab);
        item(file, "Open...", "control O", this::openWithDialog);
        recentMenu = new JMenu("Open Recent");
        recentMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) { fillRecent(recentMenu.getPopupMenu()); }
            @Override public void menuDeselected(MenuEvent e) { /* nothing */ }
            @Override public void menuCanceled(MenuEvent e) { /* nothing */ }
        });
        file.add(recentMenu);
        file.addSeparator();
        item(file, "Save", "control S", () -> { if (current != null) save(current); });
        item(file, "Save As...", "control shift S", () -> {
            if (current != null && current.editor != null) saveAs(current, current.editor.hasFormatting() || NoteEditor.isRtf(current.editor.file));
        });
        item(file, "Save All", "control alt S", this::saveAll);
        file.addSeparator();
        item(file, "Close Tab", "control W", () -> { if (current != null) closeTab(current); });
        file.addSeparator();
        item(file, "Print...", "control P", () -> withEditor(this::print));
        file.addSeparator();
        item(file, "Exit", null, this::exitApp);
        extra("control T", this::newTab);

        JMenu edit = menu("Edit", 'E');
        item(edit, "Undo", "control Z", () -> withEditor(NoteEditor::undo));
        item(edit, "Redo", "control Y", () -> withEditor(NoteEditor::redo));
        extra("control shift Z", () -> withEditor(NoteEditor::redo));
        edit.addSeparator();
        item(edit, "Cut", "control X", () -> withEditor(JTextComponent::cut));
        item(edit, "Copy", "control C", () -> withEditor(JTextComponent::copy));
        item(edit, "Paste", "control V", () -> withEditor(JTextComponent::paste));
        JMenuItem delete = new JMenuItem("Delete");
        delete.setAccelerator(KeyStroke.getKeyStroke("DELETE")); // displayed only; the editor handles Delete itself
        delete.addActionListener(e -> withEditor(ed -> ed.getActionMap().get(DefaultEditorKit.deleteNextCharAction)
                .actionPerformed(new ActionEvent(ed, ActionEvent.ACTION_PERFORMED, "delete"))));
        edit.add(delete);
        edit.addSeparator();
        item(edit, "Find...", "control F", this::focusSearch);
        item(edit, "Find Next", "F3", () -> findAgain(true));
        item(edit, "Find Previous", "shift F3", () -> findAgain(false));
        item(edit, "Replace...", "control H", () -> replaceDialog.open(true, search.getText()));
        item(edit, "Go To...", "control G", () -> withEditor(this::goToLine));
        edit.addSeparator();
        item(edit, "Select All", "control A", () -> withEditor(JTextComponent::selectAll));
        item(edit, "Time/Date", "F5", () -> withEditor(ed -> ed.replaceSelection(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.FRANCE)))));
        edit.addSeparator();
        item(edit, "Duplicate Line Up", "control alt UP", () -> withEditor(ed -> ed.duplicateLines(true)));
        item(edit, "Duplicate Line Down", "control alt DOWN", () -> withEditor(ed -> ed.duplicateLines(false)));
        item(edit, "Move Line Up", "alt UP", () -> withEditor(ed -> ed.moveLines(true)));
        item(edit, "Move Line Down", "alt DOWN", () -> withEditor(ed -> ed.moveLines(false)));
        item(edit, "Delete Line", "control D", () -> withEditor(NoteEditor::deleteLines));
        item(edit, "Select Line", "alt A", () -> withEditor(NoteEditor::selectLines));
        installLineKeys();

        JMenu format = menu("Format", 'O');
        item(format, "Bold", "control B", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Bold)));
        item(format, "Italic", "control I", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Italic)));
        item(format, "Underline", "control U", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Underline)));
        item(format, "Text Color...", "control shift C", this::chooseColor);
        format.addSeparator();
        item(format, "Bloc de code", "control shift K", () -> withEditor(NoteEditor::toggleCodeBlock));
        item(format, "Note Colors...  (=====, *****, code)", null, this::chooseNoteColors);
        format.addSeparator();
        item(format, "Ligne de =====          (tape ==+)", null,
                () -> withEditor(ed -> ed.replaceSelection(NoteAssist.EQUALS_LINE)));
        item(format, "Ligne de === courte    (tape =+)", null,
                () -> withEditor(ed -> ed.replaceSelection(NoteAssist.SHORT_EQUALS_LINE)));
        item(format, "Ligne de *****            (tape *+)", null,
                () -> withEditor(ed -> ed.replaceSelection(NoteAssist.STARS_LINE)));
        item(format, "Ligne de ***** longue  (tape **+)", null,
                () -> withEditor(ed -> ed.replaceSelection(NoteAssist.LONG_STARS_LINE)));
        item(format, "Nouveau chapitre        (new titre + " + (Platform.MAC ? "⌃Espace" : "Ctrl+Espace") + ")", null, () -> withEditor(this::newChapterHere));
        format.addSeparator();
        item(format, "Font...", null, this::chooseFont);

        JMenu view = menu("View", 'V');
        JMenu themes = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();
        String active = settings.get("theme", Theme.ALL.get(0).name());
        boolean addedLightSeparator = false;
        for (Theme.Entry t : Theme.ALL) {
            if (!t.dark() && !addedLightSeparator) {
                themes.addSeparator();
                addedLightSeparator = true;
            }
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(t.name(), t.name().equals(active));
            it.addActionListener(e -> applyTheme(t.name()));
            group.add(it);
            themes.add(it);
            themeItems.add(it);
        }
        view.add(themes);
        JMenu zoomMenu = new JMenu("Zoom");
        item(zoomMenu, "Zoom In", "control EQUALS", () -> setZoom(zoom + 10));
        item(zoomMenu, "Zoom Out", "control MINUS", () -> setZoom(zoom - 10));
        item(zoomMenu, "Restore Default Zoom", "control 0", () -> setZoom(100));
        extra("control ADD", () -> setZoom(zoom + 10));
        extra("control shift EQUALS", () -> setZoom(zoom + 10));
        extra("control PLUS", () -> setZoom(zoom + 10));
        extra("control SUBTRACT", () -> setZoom(zoom - 10));
        extra("control NUMPAD0", () -> setZoom(100));
        view.add(zoomMenu);
        view.addSeparator();
        wrapItem = new JCheckBoxMenuItem("Word Wrap", wordWrap);
        wrapItem.addActionListener(e -> setWordWrap(wrapItem.isSelected()));
        view.add(wrapItem);
        JCheckBoxMenuItem statusItem = new JCheckBoxMenuItem("Status Bar", settings.getBool("statusBar", true));
        statusItem.addActionListener(e -> {
            statusBar.setVisible(statusItem.isSelected());
            settings.set("statusBar", statusItem.isSelected());
            revalidate();
        });
        view.add(statusItem);
        JCheckBoxMenuItem suggestItem = new JCheckBoxMenuItem("Suggestions automatiques (Tab)", suggestions);
        suggestItem.addActionListener(e -> {
            suggestions = suggestItem.isSelected();
            settings.set("suggestions", suggestions);
            withEditor(ed -> ed.assist.clearGhost());
        });
        view.add(suggestItem);
        view.addSeparator();
        item(view, "Onglet suivant", "control TAB", () -> cycleTab(1));
        item(view, "Onglet précédent", "control shift TAB", () -> cycleTab(-1));

        JMenu help = menu("Help", 'H');
        item(help, "Raccourcis clavier", "F1", this::showShortcuts);
        item(help, "About " + APP, null, this::showAbout);
    }

    void showAbout() {
        JOptionPane.showMessageDialog(this,
                APP + " — bloc-notes pour la prise de note\n\n"
                        + "Créé par Mohamed Iraqi Houssaini\n"
                        + "Copyright © 2026 Mohamed Iraqi Houssaini\n\n"
                        + "Logiciel gratuit, sous licence PolyForm Noncommercial 1.0.0 :\n"
                        + "vous pouvez le modifier et le partager, mais jamais le vendre.\n\n"
                        + "Java Swing + FlatLaf (Apache 2.0)\n\nFichiers de session : " + settings.dir,
                "About", JOptionPane.INFORMATION_MESSAGE, new javax.swing.ImageIcon(AppIcon.render(64)));
    }

    private JMenu menu(String name, char mnemonic) {
        JMenu m = new JMenu(name);
        m.setMnemonic(mnemonic);
        menuBar.add(m);
        return m;
    }

    private JMenuItem item(JMenu menu, String text, String key, Runnable action) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(e -> action.run());
        if (key != null) {
            KeyStroke ks = KeyStroke.getKeyStroke(Platform.key(key));
            it.setAccelerator(ks);
            shortcuts.add(new Object[] {ks, action});
        }
        menu.add(it);
        return it;
    }

    /**
     * Alt+Up/Down (move) and Ctrl+Alt+Up/Down (duplicate) are caught before Swing's key bindings and the
     * menu bar see them, so nothing else (menu Alt handling, numpad arrows) can swallow them.
     */
    private void installLineKeys() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;
            if (!(e.getComponent() instanceof NoteEditor ed) || javax.swing.SwingUtilities.getWindowAncestor(ed) != this) return false;
            int code = e.getKeyCode();
            boolean upKey = code == java.awt.event.KeyEvent.VK_UP || code == java.awt.event.KeyEvent.VK_KP_UP;
            boolean downKey = code == java.awt.event.KeyEvent.VK_DOWN || code == java.awt.event.KeyEvent.VK_KP_DOWN;
            int mods = e.getModifiersEx() & (java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK
                    | java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.META_DOWN_MASK);
            if (!(upKey || downKey) || !ed.isEditable()) return false;
            if (mods == java.awt.event.InputEvent.ALT_DOWN_MASK) {
                ed.moveLines(upKey);
            } else if (mods == (java.awt.event.InputEvent.ALT_DOWN_MASK | Platform.MENU_MASK)) {
                ed.duplicateLines(upKey);
            } else {
                return false;
            }
            e.consume();
            return true;
        });
    }

    /** A shortcut without a menu item (alternative keys). */
    private void extra(String key, Runnable action) {
        key = Platform.key(key);
        KeyStroke ks = KeyStroke.getKeyStroke(key);
        shortcuts.add(new Object[] {ks, action});
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "extra:" + key);
        getRootPane().getActionMap().put("extra:" + key, action(action));
    }

    /** Shortcuts are also bound on each editor so they beat Swing's built-ins (e.g. Ctrl+H = backspace). */
    private void installShortcuts(NoteEditor ed) {
        for (Object[] s : shortcuts) {
            KeyStroke ks = (KeyStroke) s[0];
            String name = "app:" + ks;
            ed.getInputMap(JComponent.WHEN_FOCUSED).put(ks, name);
            ed.getActionMap().put(name, action((Runnable) s[1]));
        }
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        };
    }

    private void buildContextMenu() {
        addCtx("Undo", () -> withEditor(NoteEditor::undo));
        addCtx("Redo", () -> withEditor(NoteEditor::redo));
        contextMenu.addSeparator();
        addCtx("Cut", () -> withEditor(JTextComponent::cut));
        addCtx("Copy", () -> withEditor(JTextComponent::copy));
        addCtx("Paste", () -> withEditor(JTextComponent::paste));
        addCtx("Select All", () -> withEditor(JTextComponent::selectAll));
        contextMenu.addSeparator();
        addCtx("Bold", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Bold)));
        addCtx("Italic", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Italic)));
        addCtx("Underline", () -> withEditor(ed -> ed.toggleStyle(StyleConstants.Underline)));
        addCtx("Text Color...", this::chooseColor);
        addCtx(Platform.label("Bloc de code (Ctrl+Shift+K)"), () -> withEditor(NoteEditor::toggleCodeBlock));
        contextMenu.addSeparator();
        addCtx("Duplicate Line Down", () -> withEditor(ed -> ed.duplicateLines(false)));
    }

    private void addCtx(String text, Runnable r) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(e -> r.run());
        contextMenu.add(it);
    }

    // =====================================================================
    // Status bar
    // =====================================================================

    private static JLabel statusLabel() {
        JLabel l = new JLabel();
        l.setFont(Ui.ui(12f, Font.PLAIN));
        l.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return l;
    }

    private void buildStatusBar() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(posLabel);
        left.add(countLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        langLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        langLabel.setToolTipText("Changer le langage / le format de ce fichier");
        langLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showLanguagePopup(); }
        });
        right.add(langLabel);
        right.add(zoomLabel);
        right.add(eolLabel);
        right.add(encLabel);
        statusBar.add(left, BorderLayout.WEST);
        statusBar.add(right, BorderLayout.EAST);
    }

    private void updateStatus() {
        NoteEditor ed = editor();
        if (ed == null) {
            posLabel.setText("Choisis un format pour commencer");
            for (JLabel l : new JLabel[] {countLabel, langLabel, eolLabel, encLabel}) { l.setText(""); l.setIcon(null); }
            zoomLabel.setText(zoom + "%");
            return;
        }
        int dot = ed.getCaretPosition();
        Element root = ed.doc.getDefaultRootElement();
        int line = root.getElementIndex(dot);
        int col = dot - root.getElement(line).getStartOffset() + 1;
        int sel = Math.abs(ed.getSelectionEnd() - ed.getSelectionStart());
        posLabel.setText("Ln " + (line + 1) + ", Col " + col + (sel > 0 ? "   (" + sel + " sélectionnés)" : ""));
        String text = ed.text();
        countLabel.setText(String.format(Locale.FRANCE, "%,d caractères · %,d mots", text.length(), wordCount(text)));
        langLabel.setIcon(new LangIcon(ed.language, 14));
        langLabel.setText(ed.noteMode ? "Prise de note · " + ed.language.label : "Texte normal");
        zoomLabel.setText(zoom + "%");
        eolLabel.setText(switch (ed.eol) {
            case "\n" -> "Unix (LF)";
            case "\r" -> "Macintosh (CR)";
            default -> "Windows (CRLF)";
        });
        encLabel.setText(ed.encoding);
    }

    static int wordCount(String s) {
        int n = 0;
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            boolean w = Character.isLetterOrDigit(s.charAt(i));
            if (w && !in) n++;
            in = w;
        }
        return n;
    }

    private void showLanguagePopup() {
        NoteEditor ed = editor();
        if (ed == null) return;
        JPopupMenu p = new JPopupMenu();
        JMenuItem normal = new JMenuItem("Texte normal", new LangIcon(Language.TEXT, 16));
        normal.addActionListener(e -> setLanguage(ed, Language.TEXT, false));
        p.add(normal);
        p.addSeparator();
        for (Language l : Language.values()) {
            JMenuItem it = new JMenuItem("Prise de note · " + (l == Language.TEXT ? "Autre" : l.label), new LangIcon(l, 16));
            it.addActionListener(e -> setLanguage(ed, l, true));
            p.add(it);
        }
        p.show(langLabel, 0, -p.getPreferredSize().height);
    }

    private void setLanguage(NoteEditor ed, Language lang, boolean note) {
        ed.language = lang;
        ed.noteMode = note;
        if (ed.file != null) {
            settings.rememberLanguage(ed.file, lang, note);
            settings.save();
        }
        strip.revalidate();
        strip.repaint();
        updatePath();
        updateStatus();
        updateTitle();
    }

    // =====================================================================
    // NoteEditor.Host
    // =====================================================================

    @Override public boolean wordWrap() { return wordWrap; }

    @Override public boolean suggestions() { return suggestions; }

    @Override public NoteEditor.Palette palette() { return palette; }

    /** Saved colours, or defaults that suit the current theme (dark or light). */
    private NoteEditor.Palette computePalette() {
        boolean dark = Ui.dark();
        Color eq = settings.color("color.equals", dark ? new Color(0x5B8CFF) : new Color(0x2F6FEB));
        Color star = settings.color("color.stars", dark ? new Color(0xF2B84B) : new Color(0xB86E00));
        Color code = settings.color("color.code", dark ? new Color(0x7FDBCA) : new Color(0x0F766E));
        Color bg = settings.color("color.codeBg", null);
        if (bg == null) bg = Ui.mix(Ui.surface(), code, dark ? 0.085f : 0.07f);
        return new NoteEditor.Palette(eq, star, code, bg,
                settings.color("color.syn.keyword", dark ? new Color(0xC792EA) : new Color(0x7C3AED)),
                settings.color("color.syn.string", dark ? new Color(0xECC48D) : new Color(0xA16207)),
                settings.color("color.syn.number", dark ? new Color(0xF78C6C) : new Color(0xC2410C)),
                settings.color("color.syn.comment", dark ? new Color(0x7CC47F) : new Color(0x2E7D32)),
                settings.color("color.syn.operator", dark ? new Color(0x89DDFF) : new Color(0x0E7490)),
                settings.color("color.syn.paren", dark ? new Color(0xFFCB6B) : new Color(0xB45309)),
                settings.color("color.syn.bracket", dark ? new Color(0xF07178) : new Color(0xBE185D)),
                settings.color("color.syn.brace", dark ? new Color(0x82AAFF) : new Color(0x1D4ED8)));
    }

    private void refreshPalette() {
        palette = computePalette();
        for (DocTab t : tabs) if (t.editor != null) t.editor.applyPalette();
    }

    /** Format > Note Colors: pick the colour of ===== lines, ***** lines and code blocks. Changes show live. */
    private void chooseNoteColors() {
        String[][] rows = {
            {"color.equals", "Lignes =====", "les séparateurs ==+ et les lignes des chapitres"},
            {"color.stars", "Lignes ***** et les *", "les séparateurs *+ et chaque * dans tes notes"},
            {"color.code", "Texte du code", Platform.label("le code collé depuis un IDE (Ctrl+Shift+K)")},
            {"color.codeBg", "Fond des blocs de code", "la carte derrière le code"},
            {"color.syn.keyword", "Mots-clés", "public, class, def, return, if ..."},
            {"color.syn.operator", "Opérateurs", "= + - / < > ! & | ; (dans tes notes et le code)"},
            {"color.syn.paren", "Parenthèses ( )", ""},
            {"color.syn.bracket", "Crochets [ ]", ""},
            {"color.syn.brace", "Accolades { }", ""},
            {"color.syn.string", "Chaînes \"texte\"", ""},
            {"color.syn.number", "Nombres", "42, 3.14, 0xFF"},
            {"color.syn.comment", "Commentaires //", "// ... dans tes notes et dans le code"},
        };
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 10);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rows.length; i++) {
            String key = rows[i][0];
            String title = rows[i][1];
            JButton swatch = new JButton();
            swatch.setPreferredSize(new Dimension(56, 28));
            swatch.setIcon(swatchIcon(() -> paletteColor(key)));
            swatch.setToolTipText("Changer la couleur");
            swatch.addActionListener(e -> {
                Color picked = JColorChooser.showDialog(this, title, paletteColor(key));
                if (picked != null) {
                    settings.setColor(key, picked);
                    settings.save();
                    refreshPalette();
                    swatch.repaint();
                }
            });
            JButton reset = new JButton("Défaut");
            reset.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            reset.addActionListener(e -> {
                settings.setColor(key, null);
                settings.save();
                refreshPalette();
                swatch.repaint();
            });
            String detail = rows[i][2].isEmpty() ? "" : "<br><font color='#" + hex(Ui.dim()) + "'>" + rows[i][2].replace("<", "&lt;").replace(">", "&gt;") + "</font>";
            JLabel name = new JLabel("<html><b>" + rows[i][1].replace("<", "&lt;") + "</b>" + detail + "</html>");
            c.gridy = i;
            c.gridx = 0; panel.add(swatch, c);
            c.gridx = 1; panel.add(name, c);
            c.gridx = 2; panel.add(reset, c);
        }
        JOptionPane.showMessageDialog(this, panel, "Couleurs de la note", JOptionPane.PLAIN_MESSAGE);
    }

    private Color paletteColor(String key) {
        return switch (key) {
            case "color.equals" -> palette.equalsLine();
            case "color.stars" -> palette.starsLine();
            case "color.code" -> palette.code();
            case "color.syn.keyword" -> palette.keyword();
            case "color.syn.operator" -> palette.operator();
            case "color.syn.paren" -> palette.paren();
            case "color.syn.bracket" -> palette.bracket();
            case "color.syn.brace" -> palette.brace();
            case "color.syn.string" -> palette.string();
            case "color.syn.number" -> palette.number();
            case "color.syn.comment" -> palette.comment();
            default -> palette.codeBackground();
        };
    }

    private static Icon swatchIcon(java.util.function.Supplier<Color> color) {
        return new Icon() {
            @Override public int getIconWidth() { return 34; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(java.awt.Component comp, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                Ui.smooth(g);
                g.setColor(color.get());
                g.fill(new RoundRectangle2D.Double(x, y, 34, 16, 6, 6));
                g.setColor(Ui.soft(0.3f));
                g.draw(new RoundRectangle2D.Double(x, y, 33, 15, 6, 6));
                g.dispose();
            }
        };
    }

    @Override
    public void changed(NoteEditor ed) {
        strip.repaint();
        if (ed == editor()) {
            updateTitle();
            if (statusTimer != null) statusTimer.restart();
        }
        if (vocabTimer != null) vocabTimer.restart();
        saveSessionSoon();
    }

    private void saveSessionSoon() {
        if (sessionSoon != null) sessionSoon.restart();
    }

    /** Another launch of the app asked for this window: show it on top. */
    void bringToFront() {
        if ((getExtendedState() & Frame.ICONIFIED) != 0) setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        setVisible(true);
        setAlwaysOnTop(true);   // Windows only lets a window jump to the front like this
        toFront();
        setAlwaysOnTop(false);
        requestFocus();
        withEditor(JComponent::requestFocusInWindow);
    }

    // =====================================================================
    // Tabs
    // =====================================================================

    String tabTitle(DocTab t) {
        if (t.editor == null) return "Nouvel onglet";
        if (t.editor.file != null) return t.editor.file.getName();
        return t.editor.noteMode ? t.editor.language.untitledNote() : "Untitled.txt";
    }

    private static Icon tabIcon(DocTab t, int size) {
        return new LangIcon(t.editor == null ? Language.TEXT : t.editor.language, size);
    }

    private String tabTooltip(DocTab t) {
        if (t.editor == null) return "Nouvel onglet — choisis un format";
        return t.editor.file != null ? t.editor.file.getPath() : "Pas encore enregistré";
    }

    void newTab() {
        DocTab t = new DocTab("tab" + (++tabSeq));
        t.welcome = new WelcomePanel((note, lang) -> chooseFormat(t, note, lang));
        t.view = welcomeScroll(t.welcome);
        tabs.add(t);
        content.add(t.view, t.id);
        strip.setTabs(tabs);
        select(t);
    }

    private static JScrollPane welcomeScroll(WelcomePanel w) {
        JScrollPane sp = new JScrollPane(w, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(20);
        return sp;
    }

    void chooseFormat(DocTab t, boolean note, Language lang) {
        makeEditor(t, lang, note);
        swapView(t);
        select(t);
    }

    private void makeEditor(DocTab t, Language lang, boolean note) {
        NoteEditor ed = new NoteEditor(this, vocab, lang, note, baseFont, zoom);
        installShortcuts(ed);
        ed.setComponentPopupMenu(contextMenu);
        ed.setTransferHandler(transfer);
        ed.addCaretListener(e -> { if (ed == editor() && statusTimer != null) statusTimer.restart(); });
        JScrollPane sp = new JScrollPane(ed);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Ui.surface());
        // no fixed step here: the editor decides (half a line per step, see NoteEditor.getScrollableUnitIncrement)
        ed.addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & Platform.MENU_MASK) != 0) {
                setZoom(zoom + (e.getWheelRotation() < 0 ? 10 : -10));
            } else {
                sp.dispatchEvent((MouseWheelEvent) SwingUtilities.convertMouseEvent(ed, e, sp));
            }
        });
        t.editor = ed;
        t.scroll = sp;
    }

    private void swapView(DocTab t) {
        if (t.view != null) content.remove(t.view);
        t.view = t.scroll;
        t.welcome = null;
        content.add(t.view, t.id);
        content.revalidate();
    }

    void select(DocTab t) {
        clearHighlights();
        current = t;
        saveSessionSoon();
        cards.show(content, t.id);
        strip.revalidate();
        strip.repaint();
        updateTitle();
        updatePath();
        updateStatus();
        currentMatch = -1;
        highlightMatches(false);   // switching tabs: show the count, don't move the cursor
        SwingUtilities.invokeLater(() -> {
            if (t.editor != null) t.editor.requestFocusInWindow();
            else if (t.welcome != null) t.welcome.focusDefault();
        });
    }

    /** Drag a tab to a new place: the order is kept (and restored next time). */
    void moveTab(DocTab t, int index) {
        if (!tabs.remove(t)) return;
        tabs.add(Math.max(0, Math.min(index, tabs.size())), t);
        saveSessionSoon();
    }

    private void cycleTab(int delta) {
        if (tabs.size() < 2 || current == null) return;
        int i = (tabs.indexOf(current) + delta + tabs.size()) % tabs.size();
        select(tabs.get(i));
    }

    void closeTab(DocTab t) {
        if (t.editor != null && t.editor.dirty && !(t.editor.file == null && t.editor.doc.getLength() == 0)) {
            select(t);
            Object[] options = {"Save", "Don't Save", "Cancel"};
            int r = JOptionPane.showOptionDialog(this, "Do you want to save changes to " + tabTitle(t) + "?",
                    APP, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (r == 2 || r == JOptionPane.CLOSED_OPTION) return;
            if (r == 0 && !save(t)) return;
        }
        int idx = tabs.indexOf(t);
        tabs.remove(t);
        saveSessionSoon();
        content.remove(t.view);
        vocab.removeSource("tab:" + t.id);
        if (t.sessionFile != null) settings.sessionDir.resolve(t.sessionFile).toFile().delete();
        if (tabs.isEmpty()) {
            current = null;
            newTab();
        } else {
            strip.setTabs(tabs);
            if (current == t) select(tabs.get(Math.min(idx, tabs.size() - 1)));
        }
    }

    private void updateTitle() {
        if (current == null) return;
        boolean dirty = current.editor != null && current.editor.dirty;
        setTitle((dirty ? "*" : "") + tabTitle(current) + " - " + APP);
    }

    private void updatePath() {
        NoteEditor ed = editor();
        if (ed == null) {
            pathPill.set(new LangIcon(Language.TEXT, 15), "Nouvel onglet — choisis un format ci-dessous");
        } else if (ed.file != null) {
            pathPill.set(new Glyph(Glyph.Kind.FOLDER, 15), ed.file.getPath());
        } else {
            pathPill.set(new LangIcon(ed.language, 15), "Non enregistré · "
                    + (ed.noteMode ? "Prise de note " + ed.language.label : "Texte normal"));
        }
    }

    private void openContainingFolder() {
        NoteEditor ed = editor();
        if (ed == null || ed.file == null) return;
        Platform.reveal(ed.file);
    }

    /** Rounded "path" bar of row 2. */
    private static final class Pill extends JLabel {
        private static final long serialVersionUID = 1L;

        Pill() {
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            setIconTextGap(9);
            setPreferredSize(new Dimension(200, 34));
        }

        void set(Icon icon, String text) {
            setIcon(icon);
            setText(text);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            g.setColor(Ui.soft(0.06f));
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
            g.dispose();
            setForeground(Ui.dim());
            super.paintComponent(g0);
        }
    }

    // =====================================================================
    // Recent files (◁ button + File > Open Recent)
    // =====================================================================

    private void showRecentPopup() {
        JPopupMenu p = new JPopupMenu();
        JLabel head = new JLabel("Fichiers récents");
        head.setFont(Ui.ui(12f, Font.BOLD));
        head.setForeground(Ui.dim());
        head.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        p.add(head);
        fillRecent(p);
        p.show(recentButton, 0, recentButton.getHeight());
    }

    /** Adds the recent files to a menu (File > Open Recent is rebuilt each time it opens). */
    private void fillRecent(JPopupMenu p) {
        if (p == recentMenu.getPopupMenu()) p.removeAll();
        List<File> recent = settings.recent();
        int shown = 0;
        for (File f : recent) {
            if (!f.isFile()) continue;
            Language lang = settings.rememberedLanguage(f);
            if (lang == null) lang = Language.detect(f);
            JMenuItem it = new JMenuItem("<html><b>" + f.getName() + "</b>&nbsp;&nbsp;<font color='#"
                    + hex(Ui.dim()) + "'>" + f.getParent() + "</font></html>", new LangIcon(lang, 18));
            it.setIconTextGap(10);
            it.addActionListener(e -> openFile(f));
            p.add(it);
            shown++;
        }
        if (shown == 0) {
            JMenuItem none = new JMenuItem("Aucun fichier récent");
            none.setEnabled(false);
            p.add(none);
        } else {
            p.addSeparator();
            JMenuItem clear = new JMenuItem("Effacer la liste");
            clear.addActionListener(e -> settings.clearRecent());
            p.add(clear);
        }
    }

    private static String hex(Color c) {
        return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // =====================================================================
    // Search bar (row 2)
    // =====================================================================

    private void focusSearch() {
        NoteEditor ed = editor();
        String sel = ed == null ? null : ed.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) search.setText(sel);
        search.requestFocusInWindow();
        search.selectAll();
    }

    /** The match you are on: a bright orange box with an outline, easy to spot anywhere on the page. */
    static final Color CURRENT_MATCH = new Color(0xFF9500);

    /** Rounded highlight drawn under the text. Other matches get a soft tint, the current one the orange box. */
    private static final class MatchPainter implements Highlighter.HighlightPainter {
        private final boolean current;

        MatchPainter(boolean current) { this.current = current; }

        @Override
        public void paint(Graphics g0, int p0, int p1, java.awt.Shape bounds, JTextComponent c) {
            try {
                java.awt.geom.Rectangle2D a = c.modelToView2D(p0), b = c.modelToView2D(p1);
                if (a == null || b == null) return;
                double w = b.getY() == a.getY() ? b.getX() - a.getX() : c.getWidth() - a.getX() - 20; // wrapped match: to line end
                RoundRectangle2D r = new RoundRectangle2D.Double(a.getX() - 2, a.getY() + 1, w + 4, a.getHeight() - 2, 7, 7);
                Graphics2D g = (Graphics2D) g0.create();
                Ui.smooth(g);
                if (current) {
                    g.setColor(Ui.alpha(CURRENT_MATCH, 165));
                    g.fill(r);
                    g.setColor(CURRENT_MATCH);
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.draw(r);
                } else {
                    g.setColor(Ui.alpha(Ui.accent(), 70));
                    g.fill(r);
                }
                g.dispose();
            } catch (BadLocationException ignored) {
                // text moved: drawn again on the next repaint
            }
        }
    }

    private static final MatchPainter SOFT_MATCH = new MatchPainter(false);
    private static final MatchPainter STRONG_MATCH = new MatchPainter(true);
    private transient Object currentTag;
    /** Start of the match you're on, or -1. */
    int currentMatch = -1;

    /** Where the query appears (case ignored). */
    private static List<Integer> matchStarts(NoteEditor ed, String q) {
        List<Integer> starts = new ArrayList<>();
        String hay = ed.text().toLowerCase(Locale.ROOT), needle = q.toLowerCase(Locale.ROOT);
        for (int i = hay.indexOf(needle); i >= 0 && starts.size() < 5000; i = hay.indexOf(needle, i + needle.length())) {
            starts.add(i);
        }
        return starts;
    }

    /** Typing in the search bar: tint every match and jump to the first one from the cursor. */
    private void highlightMatches() {
        highlightMatches(true);
    }

    private void highlightMatches(boolean jump) {
        clearHighlights();
        NoteEditor ed = editor();
        String q = search.getText();
        if (ed == null || q.isEmpty()) {
            searchCount.setText("");
            currentMatch = -1;
            return;
        }
        List<Integer> starts = markAll(ed, q);
        if (starts.isEmpty()) {
            searchCount.setText("aucun");
        } else if (jump) {
            int caret = ed.getCaretPosition();
            int k = 0;
            while (k < starts.size() && starts.get(k) < caret) k++;
            showMatch(ed, starts, k < starts.size() ? k : 0, q.length());
        } else {
            searchCount.setText(String.valueOf(starts.size()));
        }
    }

    private List<Integer> markAll(NoteEditor ed, String q) {
        clearHighlights();
        List<Integer> starts = matchStarts(ed, q);
        Highlighter h = ed.getHighlighter();
        try {
            for (int s : starts) searchTags.add(h.addHighlight(s, s + q.length(), SOFT_MATCH));
        } catch (BadLocationException ignored) {
            // text changed underneath: partial highlight is fine
        }
        searchEditor = ed;
        return starts;
    }

    private void showMatch(NoteEditor ed, List<Integer> starts, int k, int length) {
        int start = starts.get(k);
        try {
            currentTag = ed.getHighlighter().addHighlight(start, start + length, STRONG_MATCH);
            searchTags.add(currentTag);
            ed.setCaretPosition(start);
            java.awt.geom.Rectangle2D r = ed.modelToView2D(start);
            java.awt.Rectangle vis = ed.getVisibleRect();
            if (r != null) {   // put the match in the middle of the page
                ed.scrollRectToVisible(new java.awt.Rectangle((int) r.getX(),
                        (int) (r.getY() + r.getHeight() / 2) - vis.height / 2, 1, vis.height));
            }
        } catch (BadLocationException ignored) {
            // the text changed under us
        }
        currentMatch = start;
        searchCount.setText((k + 1) + " / " + starts.size());
        ed.repaint();
    }

    private void clearHighlights() {
        if (searchEditor != null) {
            for (Object tag : searchTags) searchEditor.getHighlighter().removeHighlight(tag);
            searchEditor.repaint();
        }
        searchTags.clear();
        searchEditor = null;
        currentTag = null;
    }

    /** ↓ / Enter / F3 = next match, ↑ / Shift+Enter / Shift+F3 = previous; wraps around at the ends. */
    void findInEditor(boolean forward) {
        NoteEditor ed = editor();
        String q = search.getText();
        if (ed == null || q.isEmpty()) return;
        List<Integer> starts = markAll(ed, q);
        if (starts.isEmpty()) {
            searchCount.setText("aucun");
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }
        int from = currentMatch >= 0 ? currentMatch : ed.getCaretPosition() - (forward ? 1 : 0);
        int k;
        if (forward) {
            k = 0;
            while (k < starts.size() && starts.get(k) <= from) k++;
            if (k == starts.size()) k = 0;
        } else {
            k = starts.size() - 1;
            while (k >= 0 && starts.get(k) >= from) k--;
            if (k < 0) k = starts.size() - 1;
        }
        showMatch(ed, starts, k, q.length());
    }

    private void findAgain(boolean forward) {
        if (!search.getText().isEmpty()) findInEditor(forward);
        else focusSearch();
    }

    // =====================================================================
    // Format / view actions
    // =====================================================================

    private void chooseColor() {
        NoteEditor ed = editor();
        if (ed == null) return;
        Color cur = StyleConstants.getForeground(ed.getInputAttributes());
        Color c = JColorChooser.showDialog(this, "Couleur du texte", cur);
        if (c != null) ed.applyColor(c);
        ed.requestFocusInWindow();
    }

    private void chooseFont() {
        Font chosen = FontDialog.choose(this, baseFont);
        if (chosen == null) return;
        baseFont = chosen;
        settings.set("font.family", chosen.getFamily());
        settings.set("font.size", chosen.getSize());
        settings.set("font.style", chosen.getStyle());
        settings.save();
        for (DocTab t : tabs) if (t.editor != null) t.editor.setBaseFont(baseFont, zoom);
    }

    private void setZoom(int percent) {
        zoom = Math.max(10, Math.min(500, percent));
        settings.set("zoom", zoom);
        for (DocTab t : tabs) if (t.editor != null) t.editor.setBaseFont(baseFont, zoom);
        updateStatus();
    }

    private void setWordWrap(boolean on) {
        wordWrap = on;
        settings.set("wordWrap", on);
        for (DocTab t : tabs) {
            if (t.editor != null) {
                t.editor.revalidate();
                t.editor.repaint();
            }
        }
    }

    void applyTheme(String name) {
        Theme.Entry e = Theme.byName(name);
        settings.set("theme", e.name());
        settings.save();
        Theme.apply(e);
        FlatLaf.updateUI();
        recolor();
        refreshPalette();
        for (JRadioButtonMenuItem it : themeItems) it.setSelected(it.getText().equals(e.name()));
    }

    private void newChapterHere(NoteEditor ed) {
        Element root = ed.doc.getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(ed.getCaretPosition()));
        int ls = line.getStartOffset();
        int le = Math.min(line.getEndOffset() - 1, ed.doc.getLength());
        try {
            if (!ed.doc.getText(ls, le - ls).isBlank()) {
                ed.setCaretPosition(le);
                ed.replaceSelection("\n");
                ls = le + 1;
                le = ls;
            }
        } catch (BadLocationException e) {
            return;
        }
        ed.assist.insertChapter(ls, le, ed.assist.nextChapterNumber(), "");
        ed.requestFocusInWindow();
    }

    private void goToLine(NoteEditor ed) {
        Element root = ed.doc.getDefaultRootElement();
        int currentLine = root.getElementIndex(ed.getCaretPosition()) + 1;
        Object input = JOptionPane.showInputDialog(this, "Line number:", "Go To Line",
                JOptionPane.PLAIN_MESSAGE, null, null, String.valueOf(currentLine));
        if (input == null) return;
        try {
            int ln = Integer.parseInt(input.toString().trim());
            if (ln < 1 || ln > root.getElementCount()) {
                JOptionPane.showMessageDialog(this, "The line number is beyond the total number of lines",
                        APP + " - Goto Line", JOptionPane.PLAIN_MESSAGE);
                return;
            }
            ed.setCaretPosition(root.getElement(ln - 1).getStartOffset());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a whole number.", APP, JOptionPane.PLAIN_MESSAGE);
        }
    }

    private void print(NoteEditor ed) {
        try {
            ed.print();
        } catch (PrinterException ex) {
            error("Impossible d'imprimer : " + ex.getMessage());
        }
    }

    private void showShortcuts() {
        String rows = String.join("", List.of(
                row("==+", "ligne de ===== (57)"), row("=+", "ligne de ===== courte (41)"), row("*+", "ligne de ***** (24)"), row("**+", "ligne de ***** longue (57)"),
                row("new titre + Ctrl+Espace + Entrée", "nouveau chapitre numéroté"),
                row("Ctrl+Espace", "liste de suggestions"), row("Tab", "accepter la suggestion grise"),
                row("a)  I)  1)  + Entrée", "élément suivant (Entrée sur un élément vide = fin)"),
                row("Ctrl+Alt+↓ / ↑", "dupliquer la ligne"),
                row("Ctrl+D", "supprimer la ligne"),
                row("Alt+A", "sélectionner la ligne"),
                row("Alt+↓ / ↑", "déplacer la ligne"),
                row("Coller du code (Ctrl+V)", "bloc de code coloré automatique"),
                row("Ctrl+Shift+K", "bloc de code on/off · Entrée sur une ligne vide = sortir"),
                row("Ctrl+B / I / U", "gras / italique / souligné"), row("Ctrl+Shift+C", "couleur du texte"),
                row("Glisser un dossier sur la fenêtre", "ouvre toutes ses notes, chacune avec son langage"),
                row("Ctrl+N  /  Ctrl+W", "nouvel onglet / fermer l'onglet"), row("Ctrl+Tab", "onglet suivant"),
                row("Ctrl+F  /  F3", "rechercher / suivant"), row("Ctrl+H", "remplacer"), row("Ctrl+G", "aller à la ligne"),
                row("Ctrl+molette  /  Ctrl+ + −  0", "zoom"), row("F5", "heure et date")));
        JOptionPane.showMessageDialog(this, "<html><table cellpadding='3'>" + rows + "</table></html>",
                "Raccourcis clavier", JOptionPane.PLAIN_MESSAGE);
    }

    private static String row(String keys, String what) {
        keys = Platform.label(keys);
        return "<tr><td><b>" + keys.replace("<", "&lt;") + "</b></td><td>&nbsp;&nbsp;" + what + "</td></tr>";
    }

    // =====================================================================
    // Open / save
    // =====================================================================

    private File notesFolder() {
        String saved = settings.get("notes.folder", null);
        if (saved != null && new File(saved).isDirectory()) return new File(saved);
        File desktop = FileSystemView.getFileSystemView().getHomeDirectory();
        File home = new File(System.getProperty("user.home"));
        for (File f : new File[] {new File(desktop, "la_prise_de_note"),
                new File(new File(new File(home, "OneDrive"), "Desktop"), "la_prise_de_note"),
                new File(new File(home, "Desktop"), "la_prise_de_note")}) {
            if (f.isDirectory()) return f;
        }
        return null;
    }

    private List<File> noteFiles() {
        List<File> files = new ArrayList<>();
        File folder = notesFolder();
        if (folder != null) {
            File[] list = folder.listFiles();
            if (list != null) files.addAll(Arrays.asList(list));
        }
        files.addAll(settings.recent());
        return files;
    }

    private void openWithDialog() {
        chooser.resetChoosableFileFilters();
        chooser.addChoosableFileFilter(txtFilter);
        chooser.addChoosableFileFilter(rtfFilter);
        chooser.setFileFilter(chooser.getAcceptAllFileFilter());
        chooser.setMultiSelectionEnabled(true);
        String last = settings.get("lastDir", null);
        File folder = last != null ? new File(last) : notesFolder();
        if (folder != null) chooser.setCurrentDirectory(folder);
        chooser.setSelectedFiles(new File[0]);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) openFile(f);
        }
        chooser.setMultiSelectionEnabled(false);
    }

    /** Opens a file, or every note inside a folder (drag a whole folder of notes onto the window). */
    public void openFile(File f) {
        if (f.isDirectory()) openFolder(f);
        else openFile(f, false);
    }

    /** Opens each note of the folder in its own tab, as a prise de note with its own language. */
    void openFolder(File dir) {
        File[] list = dir.listFiles(x -> x.isFile() && (Vocabulary.isTextNote(x) || NoteEditor.isRtf(x)));
        if (list == null || list.length == 0) {
            error("Aucune note (.txt, .md, .rtf, code…) dans le dossier :\n" + dir);
            return;
        }
        Arrays.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        if (list.length > 40 && JOptionPane.showConfirmDialog(this, "Ouvrir les " + list.length + " fichiers de « "
                + dir.getName() + " » ?", APP, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        DocTab first = null;
        for (File f : list) {
            openFile(f, true);
            if (first == null) first = current;
        }
        if (first != null) select(first);
    }

    private void openFile(File f, boolean fromNotesFolder) {
        File file = f.getAbsoluteFile();
        for (DocTab t : tabs) {
            if (t.editor != null && file.equals(t.editor.file)) {
                select(t);
                return;
            }
        }
        if (!file.isFile()) {
            error("Le fichier n'existe pas :\n" + file);
            return;
        }
        Language lang = settings.rememberedLanguage(file);
        Boolean note = settings.rememberedNote(file);
        boolean guessFromContent = lang == null && Language.detect(file) == Language.TEXT;
        if (lang == null) lang = Language.detect(file);
        if (note == null) note = fromNotesFolder || lang != Language.TEXT;

        boolean reuse = current != null && (current.editor == null
                || (current.editor.file == null && !current.editor.dirty && current.editor.doc.getLength() == 0));
        DocTab t = reuse ? current : new DocTab("tab" + (++tabSeq));
        NoteEditor previous = t.editor;
        JScrollPane previousScroll = t.scroll;
        makeEditor(t, lang, note);
        try {
            t.editor.load(file);
        } catch (IOException ex) {
            t.editor = previous;
            t.scroll = previousScroll;
            error("Impossible d'ouvrir " + file.getName() + " :\n" + ex.getMessage());
            return;
        }
        if (guessFromContent) {
            // the name says nothing (cms.txt): look at what the note talks about (npm, require, const ... -> JavaScript)
            Language guess = Language.fromText(t.editor.text());
            if (guess != Language.TEXT) {
                t.editor.language = guess;
                t.editor.noteMode = true;
            }
            settings.rememberLanguage(file, t.editor.language, t.editor.noteMode);
        }
        t.editor.applyCodeRanges(settings.rememberedCode(file));
        swapView(t);
        if (!reuse) tabs.add(t);
        strip.setTabs(tabs);
        select(t);
        settings.addRecent(file);
        settings.set("lastDir", file.getParent());
        vocab.setSource("tab:" + t.id, t.editor.text());
    }

    boolean save(DocTab t) {
        if (t.editor == null) return true;
        return t.editor.file == null ? saveAs(t, t.editor.hasFormatting()) : writeTo(t, t.editor.file);
    }

    private void saveAll() {
        for (DocTab t : new ArrayList<>(tabs)) {
            if (t.editor != null && t.editor.dirty) {
                select(t);
                if (!save(t)) return;
            }
        }
    }

    private boolean saveAs(DocTab t, boolean preferRtf) {
        NoteEditor ed = t.editor;
        chooser.resetChoosableFileFilters();
        chooser.addChoosableFileFilter(txtFilter);
        chooser.addChoosableFileFilter(rtfFilter);
        chooser.setFileFilter(preferRtf ? rtfFilter : txtFilter);
        String ext = preferRtf ? ".rtf" : ".txt";
        File dir;
        String base;
        if (ed.file != null) {
            dir = ed.file.getParentFile();
            base = ed.file.getName().replaceFirst("\\.[^.]*$", "");
        } else if (ed.noteMode && notesFolder() != null) {
            dir = notesFolder();
            base = ed.language.noteFileBase;
            for (int i = 2; new File(dir, base + ext).exists(); i++) base = ed.language.noteFileBase + "_" + i;
        } else {
            String last = settings.get("lastDir", null);
            dir = last != null ? new File(last) : chooser.getCurrentDirectory();
            base = "Untitled";
        }
        chooser.setCurrentDirectory(dir);
        chooser.setSelectedFile(new File(dir, base + ext));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;

        File f = chooser.getSelectedFile();
        if (!f.getName().contains(".")) f = new File(f.getPath() + (chooser.getFileFilter() == rtfFilter ? ".rtf" : ".txt"));
        if (f.exists() && !f.equals(ed.file)) {
            int r = JOptionPane.showConfirmDialog(this, f.getName() + " already exists.\nDo you want to replace it?",
                    "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return false;
        }
        if (!f.equals(ed.file)) ed.plainWarned = false;
        return writeTo(t, f);
    }

    private boolean writeTo(DocTab t, File f) {
        NoteEditor ed = t.editor;
        if (!NoteEditor.isRtf(f) && !ed.plainWarned && ed.hasFormatting()) {
            Object[] options = {"Save as .txt anyway", "Save as .rtf instead", "Cancel"};
            int r = JOptionPane.showOptionDialog(this,
                    f.getName() + " is a plain text file.\nBold, italic, underline and colours can't be stored in it and will be lost.",
                    APP, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (r == 1) return saveAs(t, true);
            if (r != 0) return false;
            ed.plainWarned = true;
        }
        try {
            ed.write(f);
        } catch (IOException ex) {
            error("Impossible d'enregistrer " + f.getName() + " :\n" + ex.getMessage());
            return false;
        }
        settings.addRecent(f);
        settings.rememberLanguage(f, ed.language, ed.noteMode);
        settings.rememberCode(f, ed.codeRanges());
        settings.set("lastDir", f.getParent());
        settings.save();
        vocab.setSource("tab:" + t.id, ed.text());
        strip.revalidate();
        strip.repaint();
        updateTitle();
        updatePath();
        updateStatus();
        return true;
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, APP, JOptionPane.ERROR_MESSAGE);
    }

    private static boolean inCodeBlock(NoteEditor ed) {
        Element root = ed.doc.getDefaultRootElement();
        return ed.isCode(root.getElement(root.getElementIndex(ed.getCaretPosition())));
    }

    /** Clipboard text in, plain text out, and files dropped anywhere get opened. */
    private final class NoteTransfer extends TransferHandler {
        private static final long serialVersionUID = 1L;

        @Override public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }

        @Override
        protected Transferable createTransferable(JComponent c) {
            String s = c instanceof JTextComponent tc ? tc.getSelectedText() : null;
            return s == null ? null : new StringSelection(s);
        }

        @Override
        protected void exportDone(JComponent c, Transferable data, int action) {
            if (action == MOVE && c instanceof JTextComponent tc) tc.replaceSelection("");
        }

        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || (s.getComponent() instanceof JTextComponent && s.isDataFlavorSupported(DataFlavor.stringFlavor));
        }

        @Override
        public boolean importData(TransferSupport s) {
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    for (Object o : (List<?>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)) {
                        if (o instanceof File f) openFile(f);
                    }
                    return true;
                }
                if (s.getComponent() instanceof JTextComponent tc && s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String text = ((String) s.getTransferable().getTransferData(DataFlavor.stringFlavor))
                            .replaceAll("\r+\n", "\n").replace('\r', '\n');
                    if (s.isDrop() && s.getDropLocation() instanceof JTextComponent.DropLocation loc) {
                        tc.setCaretPosition(loc.getIndex());
                    }
                    if (tc instanceof NoteEditor ed && NoteEditor.looksLikeCode(text) && !inCodeBlock(ed)) {
                        ed.pasteAsCode(text);   // code copied from an IDE gets its own coloured block
                    } else {
                        tc.replaceSelection(text);
                    }
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
            return false;
        }
    }

    // =====================================================================
    // Session: every tab (even unsaved) comes back next time, like Windows 11 Notepad
    // =====================================================================

    void saveSession() {
        if (!sessionOwner) return;
        settings.clearSession();
        Set<String> keep = new HashSet<>();
        int i = 0;
        for (DocTab t : tabs) {
            String k = "session." + i + ".";
            if (t.editor == null) {
                settings.set(k + "kind", "welcome");
            } else {
                NoteEditor ed = t.editor;
                settings.set(k + "kind", "doc");
                settings.set(k + "lang", ed.language.name());
                settings.set(k + "note", ed.noteMode);
                settings.set(k + "eol", ed.eol.equals("\n") ? "LF" : ed.eol.equals("\r") ? "CR" : "CRLF");
                settings.set(k + "enc", ed.encoding);
                settings.set(k + "caret", ed.getCaretPosition());
                settings.set(k + "code", ed.codeRanges().isEmpty() ? null : ed.codeRanges());
                if (ed.file != null) settings.set(k + "path", ed.file.getPath());
                if (ed.file == null || ed.dirty) {
                    if (t.sessionFile == null) t.sessionFile = "tab-" + UUID.randomUUID() + ".rtf";
                    File sf = settings.sessionDir.resolve(t.sessionFile).toFile();
                    if (t.sessionSavedAt != ed.changeCount || !sf.exists()) {
                        try {
                            ed.writeSessionCopy(sf);
                            t.sessionSavedAt = ed.changeCount;
                        } catch (IOException ignored) {
                            // try again on the next autosave
                        }
                    }
                    settings.set(k + "temp", t.sessionFile);
                    settings.set(k + "dirty", ed.dirty);
                    keep.add(t.sessionFile);
                }
            }
            i++;
        }
        settings.set("session.count", i);
        settings.set("session.selected", tabs.indexOf(current));
        settings.save();
        File[] files = settings.sessionDir.toFile().listFiles();
        if (files != null) {
            for (File f : files) if (!keep.contains(f.getName())) f.delete();
        }
    }

    private void restoreSession() {
        if (!sessionOwner) return;
        int n = settings.getInt("session.count", 0);
        for (int i = 0; i < n; i++) {
            String k = "session." + i + ".";
            DocTab t = new DocTab("tab" + (++tabSeq));
            if ("welcome".equals(settings.get(k + "kind", "welcome"))) {
                t.welcome = new WelcomePanel((note, lang) -> chooseFormat(t, note, lang));
                t.view = welcomeScroll(t.welcome);
                content.add(t.view, t.id);
                tabs.add(t);
                continue;
            }
            Language lang;
            try { lang = Language.valueOf(settings.get(k + "lang", "TEXT")); } catch (IllegalArgumentException e) { lang = Language.TEXT; }
            makeEditor(t, lang, settings.getBool(k + "note", false));
            NoteEditor ed = t.editor;
            String path = settings.get(k + "path", null);
            String temp = settings.get(k + "temp", null);
            try {
                File tempFile = temp == null ? null : settings.sessionDir.resolve(temp).toFile();
                if (tempFile != null && tempFile.isFile()) {
                    ed.loadSessionCopy(tempFile);
                    ed.file = path == null ? null : new File(path);
                    ed.eol = switch (settings.get(k + "eol", "CRLF")) { case "LF" -> "\n"; case "CR" -> "\r"; default -> "\r\n"; };
                    ed.encoding = settings.get(k + "enc", "UTF-8");
                    if (settings.getBool(k + "dirty", false) || (ed.file == null && ed.doc.getLength() > 0)) ed.markDirty();
                    else ed.markClean();
                    t.sessionFile = temp;
                    t.sessionSavedAt = ed.changeCount;
                } else if (path != null && new File(path).isFile()) {
                    ed.load(new File(path));
                } else {
                    continue; // the file is gone
                }
            } catch (IOException e) {
                continue;
            }
            String code = settings.get(k + "code", null);
            ed.applyCodeRanges(code != null ? code : ed.file != null ? settings.rememberedCode(ed.file) : null);
            ed.setCaretPosition(Math.max(0, Math.min(settings.getInt(k + "caret", 0), ed.doc.getLength())));
            t.view = t.scroll;
            content.add(t.view, t.id);
            tabs.add(t);
            vocab.setSource("tab:" + t.id, ed.text());
        }
        if (!tabs.isEmpty()) {
            strip.setTabs(tabs);
            int sel = settings.getInt("session.selected", 0);
            select(tabs.get(Math.max(0, Math.min(sel, tabs.size() - 1))));
        }
    }

    void exitApp() {
        if (!sessionOwner) {
            // a second window doesn't own the session: ask like classic Notepad
            for (DocTab t : new ArrayList<>(tabs)) {
                if (t.editor != null && t.editor.dirty) {
                    select(t);
                    Object[] options = {"Save", "Don't Save", "Cancel"};
                    int r = JOptionPane.showOptionDialog(this, "Do you want to save changes to " + tabTitle(t) + "?",
                            APP, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                    if (r == 2 || r == JOptionPane.CLOSED_OPTION) return;
                    if (r == 0 && !save(t)) return;
                }
            }
        }
        boolean max = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        settings.set("window.max", max);
        if (!max) {
            settings.set("window.w", getWidth());
            settings.set("window.h", getHeight());
        }
        saveSession();
        settings.save();
        dispose();
        System.exit(0);
    }

    /** Test hook. */
    void selectForTest(int index) {
        select(tabs.get(index));
    }
}
