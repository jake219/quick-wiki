package com.jake219.quickwiki;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemInfoPanel extends PluginPanel
{
    private final JLabel nameLabel = new JLabel();
    private final JLabel iconLabel = new JLabel();
    private final JLabel propertiesHeaderLabel = new JLabel();
    private final JLabel descriptionHeaderLabel = new JLabel();
    private final JTextArea descriptionArea = new JTextArea();
    private final JLabel readMoreLabel = new JLabel("Read more");
    private final JLabel backToTopLabel = new JLabel("Back to top");
    private final JLabel backButtonLabel = new JLabel("Back");
    private final JLabel itemSourcesHeaderLabel = new JLabel();
    private final JLabel dropsHeaderLabel = new JLabel();
    private final JLabel shopsHeaderLabel = new JLabel();

    private final JPanel viewContainer = new JPanel(new BorderLayout(0, 10));
    private JPanel mainView;
    private JPanel iconNamePanel;
    private JPanel emptyStatePanel;
    private boolean hasShownFirstItem = false;

    private JPanel infoTable;
    private JPanel propertiesPanel;
    private JPanel descriptionPanel;
    private JPanel descriptionContent;
    private JPanel itemSourcesPanel;
    private JPanel itemSourcesContent;
    private JPanel dropsContent;
    private JScrollPane dropsScrollPane;
    /** Max height (px) the Drops list grows to before it starts scrolling internally,
     * keeping the "Drops" header fixed in place above it. */
    private static final int SOURCES_MAX_HEIGHT = 500;
    private static final int DESCRIPTION_MAX_HEIGHT = 400;
    private JPanel shopsContent;
    private JScrollPane shopsScrollPane;
    private JScrollPane descriptionScrollPane;

    private String lastFullDescription = "";
    /** Price data captured in showItem(), rendered later by setItemProperties() once the
     * wiki infobox data has also arrived, so the whole Properties table can be built in one
     * deliberately-ordered pass instead of whatever order data happens to show up in. */
    private int pendingPrice;
    private int pendingHighAlch;
    private int pendingLowAlch;
    private boolean showFullDescription = false;
    private boolean readMoreHovering = false;
    private boolean propertiesExpanded = true;
    private boolean propertiesHovering = false;
    private boolean descriptionExpanded = true;
    private boolean descriptionHovering = false;
    private boolean itemSourcesExpanded = false;
    /** When true, the outer "Item sources" accordion relabels to "Drops" and the redundant
     * nested "Drops" toggle is skipped entirely - NPCs only have drops (no shops), so
     * nesting it under an "Item sources" umbrella is an unnecessary extra click and a label
     * that doesn't really fit a monster's own page. Items keep the normal two-level
     * Item Sources > Drops/Shops structure. */
    private boolean npcDropsMode = false;
    /** Registered by the plugin, since only it has access to game/client resources needed
     * to actually resolve and display a clicked drop-row name. Receives the clicked name
     * (already stripped of any sub-location suffix for monster names). */
    /** Registered by the plugin, since only it has access to game/client resources needed
     * to actually resolve and display a clicked drop-row name. Receives the clicked name
     * (already stripped of any sub-location suffix for monster names) plus whether that
     * name represents an NPC (true) or an item (false), since a name clicked while viewing
     * an item's own drops is a monster name, while a name clicked while viewing an NPC's
     * own drops is an item name. */
    private java.util.function.BiConsumer<String, Boolean> dropRowClickListener;
    /** Registered by the plugin - fired when the back button is clicked. */
    private Runnable backButtonListener;
    private boolean itemSourcesHovering = false;
    private boolean dropsExpanded = false;
    private boolean dropsHovering = false;
    private boolean shopsExpanded = false;
    private boolean shopsHovering = false;

    /**
     * Set the first time the Item Sources section is expanded, since a single combined
     * fetch covers both drops and shops - we only need to fire it once per item, regardless
     * of which of the two nested rows (Drops/Shops) the user opens first.
     */
    private boolean sourcesRequested = false;

    /** Cached once the combined fetch resolves, so re-collapsing/re-expanding afterward
     * doesn't need to re-fetch or re-build anything. */
    private List<ItemInfoClient.DropSource> cachedDrops;
    private List<ItemInfoClient.ShopSource> cachedShops;

    /**
     * Set by the plugin each time an item is examined (it has to read this from the game
     * client, which the panel itself has no access to). -1 means unknown/unavailable, in
     * which case monster levels are shown in a neutral color rather than guessing.
     */
    private int playerCombatLevel = -1;

    /**
     * Real game sprites (coins item icon, equipment-weight icon), fetched once by the
     * plugin at startup since they're static rather than per-item. Null until they arrive
     * (or if a fetch ever fails), in which case the existing hand-drawn icons are used as
     * a fallback rather than showing nothing.
     */
    private Icon realCoinIcon;
    private Icon realWeightIcon;
    private Icon realYesIcon;
    private Icon realNoIcon;
    private Icon realHighAlchIcon;
    private Icon realLowAlchIcon;
    private Icon realMaxHitIcon;
    private Icon realPoisonIcon;
    private Icon realQuestIcon;
    private Icon realNoteIcon;
    private Icon realAggressiveIcon;
    private Icon realMemberIcon;
    private Icon realF2pIcon;

    /**
     * Set by the plugin each time a new item is shown. Invoked lazily, the first time the
     * user expands the Item Sources section, so we don't fire a bucket query for every
     * single examine click - only for the ones where the user actually wants sources.
     */
    private Runnable sourcesLoader;


    private static final Color GOLD = new Color(224, 168, 58);
    private static final Color GOLD_HOVER = new Color(240, 195, 110);
    private static final Color BLUE = new Color(90, 184, 224);
    private static final Color GREEN = new Color(90, 214, 130);
    private static final Color RED = new Color(214, 100, 90);
    private static final Color NEUTRAL = new Color(160, 160, 160);
    private static final Color NEUTRAL_HOVER = new Color(215, 215, 215);

    public ItemInfoPanel()
    {
        setLayout(new BorderLayout());

        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(25f));
        nameLabel.setForeground(GOLD);

        descriptionHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        descriptionHeaderLabel.setForeground(new Color(150, 150, 150));
        descriptionHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        descriptionArea.setFont(FontManager.getRunescapeFont());
        descriptionArea.setForeground(Color.WHITE);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(255, 255, 255, 40)),
                BorderFactory.createEmptyBorder(0, 8, 0, 0)
        ));

        readMoreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        readMoreLabel.setForeground(GOLD);
        readMoreLabel.setIconTextGap(4);
        readMoreLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        readMoreLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                showFullDescription = !showFullDescription;
                refreshDescriptionText();
                scrollToDescription();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                readMoreHovering = true;
                updateReadMoreLabel();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                readMoreHovering = false;
                updateReadMoreLabel();
            }
        });

        backToTopLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        backToTopLabel.setForeground(new Color(150, 150, 150));
        backToTopLabel.setIcon(createTriangleIcon(DIR_UP, new Color(150, 150, 150)));
        backToTopLabel.setIconTextGap(4);
        backToTopLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        backToTopLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backToTopLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                scrollToTop();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                backToTopLabel.setForeground(NEUTRAL_HOVER);
                backToTopLabel.setIcon(createTriangleIcon(DIR_UP, NEUTRAL_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                backToTopLabel.setForeground(new Color(150, 150, 150));
                backToTopLabel.setIcon(createTriangleIcon(DIR_UP, new Color(150, 150, 150)));
            }
        });

        iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        backButtonLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        backButtonLabel.setForeground(NEUTRAL);
        backButtonLabel.setIcon(createTriangleIcon(DIR_LEFT, NEUTRAL));
        backButtonLabel.setIconTextGap(4);
        backButtonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        backButtonLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButtonLabel.setVisible(false);
        backButtonLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (backButtonListener != null)
                {
                    backButtonListener.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                backButtonLabel.setForeground(NEUTRAL_HOVER);
                backButtonLabel.setIcon(createTriangleIcon(DIR_LEFT, NEUTRAL_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                backButtonLabel.setForeground(NEUTRAL);
                backButtonLabel.setIcon(createTriangleIcon(DIR_LEFT, NEUTRAL));
            }
        });

        iconNamePanel = new JPanel();
        iconNamePanel.setLayout(new BoxLayout(iconNamePanel, BoxLayout.Y_AXIS));
        iconNamePanel.add(backButtonLabel);
        iconNamePanel.add(Box.createVerticalStrut(6));
        iconNamePanel.add(iconLabel);
        iconNamePanel.add(Box.createVerticalStrut(8));
        iconNamePanel.add(nameLabel);
        iconNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconNamePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        infoTable = new JPanel();
        infoTable.setLayout(new GridBagLayout());
        infoTable.setOpaque(false);
        infoTable.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoTable.setVisible(false);

        wireAccordionHeader(propertiesHeaderLabel, () -> "Properties", () -> propertiesExpanded,
                () -> propertiesHovering, hovering -> propertiesHovering = hovering, this::toggleProperties);

        wireAccordionHeader(descriptionHeaderLabel, () -> "Description", () -> descriptionExpanded,
                () -> descriptionHovering, hovering -> descriptionHovering = hovering, this::toggleDescription);

        // Properties section: flat header (chevron + title, no boxed card) with a thin
        // divider line underneath, matching the reference style - collapsible stats grid.
        propertiesPanel = new JPanel();
        propertiesPanel.setLayout(new BoxLayout(propertiesPanel, BoxLayout.Y_AXIS));
        propertiesPanel.setOpaque(false);
        propertiesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        propertiesPanel.add(propertiesHeaderLabel);
        propertiesPanel.add(Box.createVerticalStrut(6));
        propertiesPanel.add(infoTable);
        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createDivider());
        propertiesPanel.setVisible(false);

        // Item Sources: same flat header treatment as Properties/Description, but expanding
        // it reveals two further nested dropdown rows (Drops/Shops) rather than a stats
        // grid - matches the reference's "Sources > Drops/Shops" nested structure.
        itemSourcesPanel = buildItemSourcesSection();

        JPanel actionsRow = new JPanel(new BorderLayout());
        actionsRow.setOpaque(false);
        actionsRow.add(readMoreLabel, BorderLayout.WEST);
        actionsRow.add(backToTopLabel, BorderLayout.EAST);
        actionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        // Same fixed-height/internal-scroll treatment as Drops/Shops - a long description
        // scrolls within its own confined area instead of pushing the whole page down,
        // while actionsRow (Read more/Back to top) stays outside it, always visible.
        // Sized dynamically in refreshDescriptionText() based on the actual text height.
        descriptionScrollPane = new JScrollPane(descriptionArea);
        descriptionScrollPane.setOpaque(false);
        descriptionScrollPane.getViewport().setOpaque(false);
        descriptionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        descriptionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(descriptionScrollPane);
        descriptionScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        descriptionContent = new JPanel();
        descriptionContent.setLayout(new BoxLayout(descriptionContent, BoxLayout.Y_AXIS));
        descriptionContent.setOpaque(false);
        descriptionContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionContent.add(descriptionScrollPane);
        descriptionContent.add(Box.createVerticalStrut(8));
        descriptionContent.add(actionsRow);

        descriptionPanel = new JPanel();
        descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.Y_AXIS));
        descriptionPanel.setOpaque(false);
        descriptionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionPanel.add(descriptionHeaderLabel);
        descriptionPanel.add(Box.createVerticalStrut(6));
        descriptionPanel.add(descriptionContent);
        descriptionPanel.setVisible(false);

        mainView = new JPanel();
        mainView.setLayout(new BoxLayout(mainView, BoxLayout.Y_AXIS));
        mainView.add(propertiesPanel);
        mainView.add(Box.createVerticalStrut(8));
        mainView.add(itemSourcesPanel);
        mainView.add(Box.createVerticalStrut(8));
        mainView.add(descriptionPanel);

        emptyStatePanel = buildEmptyStatePanel();

        // Shows the empty-state message until the first item/NPC/object is actually
        // examined, at which point ensureItemViewShown() swaps this out for the real
        // icon/name + mainView layout (see showItem/showNonItem).
        viewContainer.setOpaque(false);
        viewContainer.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        viewContainer.add(emptyStatePanel, BorderLayout.CENTER);
        add(viewContainer, BorderLayout.CENTER);
    }

    /**
     * TODO: fill in your actual repo URL - this is a placeholder until then. Used by the
     * info row's click handler below.
     */
    private static final String PLUGIN_REPO_URL = "https://github.com/jake219/quick-wiki/blob/main/README.md";
    private static final String PLUGIN_VERSION = "1.0.3";

    /**
     * Builds the friendly placeholder shown before the user has searched anything, so the
     * panel doesn't just look blank/broken on first open.
     */
    private JPanel buildEmptyStatePanel()
    {
        JLabel title = new JLabel("Quick Wiki");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        title.setForeground(GOLD);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel version = new JLabel("Version: " + PLUGIN_VERSION);
        version.setFont(FontManager.getRunescapeFont());
        version.setForeground(NEUTRAL);
        version.setAlignmentX(Component.CENTER_ALIGNMENT);

        String wrapped = wrapTextManually(
                "Right-click any item, object, or NPC and select Wiki to search wiki data.",
                170, FontManager.getRunescapeFont()).replace("Wiki", "<b>Wiki</b>");
        JLabel body = new JLabel("<html><div style='text-align: center;'>" + wrapped + "</div></html>");
        body.setFont(FontManager.getRunescapeFont());
        body.setForeground(Color.WHITE);
        body.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(48, 16, 16, 16));
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(version);
        panel.add(Box.createVerticalStrut(10));
        panel.add(body);
        panel.add(Box.createVerticalStrut(20));
        panel.add(buildInfoRow());
        return panel;
    }

    /**
     * A clickable "View on GitHub" row - icon, text, trailing chevron - matching the
     * icon+text+arrow row style from the reference screenshot. Opens PLUGIN_REPO_URL in
     * the system browser.
     */
    private JPanel buildInfoRow()
    {
        JLabel icon = new JLabel(createInfoIcon(NEUTRAL));
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel text = new JLabel("View on GitHub");
        text.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        text.setForeground(NEUTRAL);

        JLabel chevron = new JLabel(createTriangleIcon(DIR_RIGHT, NEUTRAL));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(190, 26));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(255, 255, 255, 30)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        left.add(icon);
        left.add(Box.createHorizontalStrut(6));
        left.add(text);

        row.add(left, BorderLayout.WEST);
        row.add(chevron, BorderLayout.EAST);

        MouseAdapter listener = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                openInBrowser(PLUGIN_REPO_URL);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                text.setForeground(NEUTRAL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                text.setForeground(NEUTRAL);
            }
        };
        row.addMouseListener(listener);
        left.addMouseListener(listener);
        text.addMouseListener(listener);
        icon.addMouseListener(listener);
        chevron.addMouseListener(listener);

        return row;
    }

    /**
     * Opens a URL in the system's default browser. Logged rather than thrown if it fails
     * (e.g. no browser support in a given environment) - this is a non-critical convenience
     * action, not something that should ever disrupt the rest of the panel.
     */
    private void openInBrowser(String url)
    {
        try
        {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            {
                Desktop.getDesktop().browse(new java.net.URI(url));
            }
        }
        catch (Exception e)
        {
            System.err.println("Quick Wiki: failed to open " + url + " - " + e.getMessage());
        }
    }

    /**
     * Small "i" info-circle icon for the GitHub/info row.
     */
    private Icon createInfoIcon(Color color)
    {
        final int size = 14;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawOval(x + 1, y + 1, size - 2, size - 2);
                g2.fillOval(x + size / 2 - 1, y + 3, 2, 2);
                g2.drawLine(x + size / 2, y + 6, x + size / 2, y + size - 4);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Swaps the empty-state placeholder out for the real icon/name + mainView layout, the
     * first time an item/NPC/object is actually examined. A no-op on every call after that.
     */
    private void ensureItemViewShown()
    {
        if (!hasShownFirstItem)
        {
            hasShownFirstItem = true;
            viewContainer.removeAll();
            viewContainer.add(iconNamePanel, BorderLayout.NORTH);
            viewContainer.add(mainView, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    /**
     * A thin horizontal rule used to separate accordion sections, matching the reference
     * style's divider lines instead of wrapping each section in its own bordered card.
     */
    private JComponent createDivider()
    {
        JPanel divider = new JPanel();
        divider.setBackground(new Color(255, 255, 255, 35));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(10, 1));
        return divider;
    }

    /**
     * Builds the Item Sources section: a flat header (matching Properties/Description) that
     * expands to reveal two further nested dropdown rows, Drops and Shops, each independently
     * collapsible - directly mirroring the reference's nested "Sources > Drops / Shops"
     * structure rather than navigating to a separate page.
     */
    private JPanel buildItemSourcesSection()
    {
        wireAccordionHeader(itemSourcesHeaderLabel, () -> npcDropsMode ? "Drops" : "Item sources", () -> itemSourcesExpanded,
                () -> itemSourcesHovering, hovering -> itemSourcesHovering = hovering, this::toggleItemSources);

        wireAccordionHeader(dropsHeaderLabel, () -> "Drops", () -> dropsExpanded,
                () -> dropsHovering, hovering -> dropsHovering = hovering, this::toggleDrops);

        wireAccordionHeader(shopsHeaderLabel, () -> "Shops", () -> shopsExpanded,
                () -> shopsHovering, hovering -> shopsHovering = hovering, this::toggleShops);

        dropsContent = new JPanel();
        dropsContent.setLayout(new BoxLayout(dropsContent, BoxLayout.Y_AXIS));
        dropsContent.setOpaque(false);
        dropsContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Wrapping in a JScrollPane with a capped height means the Drops header (added to
        // "nested" below, outside this scrollpane) stays fixed in place while you scroll
        // through a long drop list, instead of scrolling away with the rest of the page -
        // the list scrolls internally within its own confined area. setSources() resizes
        // this dynamically up to SOURCES_MAX_HEIGHT based on the actual row count, so short
        // lists aren't padded with empty scroll space.
        dropsScrollPane = new JScrollPane(dropsContent);
        dropsScrollPane.setOpaque(false);
        dropsScrollPane.getViewport().setOpaque(false);
        dropsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        dropsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        dropsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dropsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(dropsScrollPane);
        dropsScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropsScrollPane.setVisible(false);

        shopsContent = new JPanel();
        shopsContent.setLayout(new BoxLayout(shopsContent, BoxLayout.Y_AXIS));
        shopsContent.setOpaque(false);
        shopsContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Same fixed-height/internal-scroll treatment as Drops - the Shops header stays
        // fixed above this scrollpane while a long shop list scrolls within its own
        // confined area, capped and sized dynamically in setSources().
        shopsScrollPane = new JScrollPane(shopsContent);
        shopsScrollPane.setOpaque(false);
        shopsScrollPane.getViewport().setOpaque(false);
        shopsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        shopsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        shopsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        shopsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(shopsScrollPane);
        shopsScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        shopsScrollPane.setVisible(false);

        // Nested rows get a small left indent so the Drops/Shops hierarchy under Item
        // Sources reads clearly, matching the reference's indented sub-items.
        JPanel nested = new JPanel();
        nested.setLayout(new BoxLayout(nested, BoxLayout.Y_AXIS));
        nested.setOpaque(false);
        nested.setAlignmentX(Component.LEFT_ALIGNMENT);
        nested.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        nested.add(dropsHeaderLabel);
        nested.add(Box.createVerticalStrut(4));
        nested.add(dropsScrollPane);
        nested.add(Box.createVerticalStrut(8));
        nested.add(shopsHeaderLabel);
        nested.add(Box.createVerticalStrut(4));
        nested.add(shopsScrollPane);
        nested.setVisible(false);
        itemSourcesContent = nested;

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(itemSourcesHeaderLabel);
        section.add(Box.createVerticalStrut(6));
        section.add(itemSourcesContent);
        section.add(Box.createVerticalStrut(8));
        section.add(createDivider());
        section.setVisible(false);

        return section;
    }

    private JScrollPane findScrollPane()
    {
        Container parent = getParent();
        while (parent != null && !(parent instanceof JScrollPane))
        {
            parent = parent.getParent();
        }
        return (parent instanceof JScrollPane) ? (JScrollPane) parent : null;
    }

    /**
     * Makes mouse-wheel scrolling "chain" from a nested scrollpane (Drops/Shops/
     * Description) to the outer page scrollpane once you hit the top or bottom edge,
     * instead of the wheel just doing nothing there. Swing doesn't do this by default for
     * nested JScrollPanes - reaching the end of the inner one normally just dead-ends.
     * <p>
     * This adds an extra listener rather than replacing anything, so normal scrolling
     * within the inner pane is untouched; it only forwards the event upward on the
     * specific edge case where the inner pane is already maxed out in the scroll direction
     * the wheel is moving.
     */
    private void enableScrollChaining(JScrollPane inner)
    {
        inner.addMouseWheelListener(e ->
        {
            JScrollBar bar = inner.getVerticalScrollBar();
            boolean atTop = bar.getValue() <= 0;
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum();
            boolean scrollingUp = e.getWheelRotation() < 0;
            boolean scrollingDown = e.getWheelRotation() > 0;

            if ((scrollingUp && atTop) || (scrollingDown && atBottom))
            {
                JScrollPane outer = findScrollPane();
                if (outer != null && outer != inner)
                {
                    outer.dispatchEvent(SwingUtilities.convertMouseEvent(inner, e, outer));
                }
            }
        });
    }

    private void scrollToTop()
    {
        SwingUtilities.invokeLater(() ->
                SwingUtilities.invokeLater(() ->
                {
                    JScrollPane sp = findScrollPane();
                    if (sp != null)
                    {
                        sp.getViewport().setViewPosition(new Point(0, 0));
                    }
                    else
                    {
                        scrollRectToVisible(new Rectangle(0, 0, 1, 1));
                    }
                })
        );
    }

    /**
     * Scrolls the sidebar so the Description box is actually in view - used after
     * expanding/collapsing "Read more", since toggling that changes the box's height and
     * without this the newly revealed (or now-shorter) content could end up off-screen
     * with no obvious indication anything changed. Deferred a tick (same double-invokeLater
     * pattern as scrollToTop) so it runs after the height change has actually been laid out.
     */
    private void scrollToDescription()
    {
        SwingUtilities.invokeLater(() ->
                SwingUtilities.invokeLater(() ->
                        descriptionPanel.scrollRectToVisible(
                                new Rectangle(0, 0, descriptionPanel.getWidth(), descriptionPanel.getHeight()))
                )
        );
    }

    private void showLoadingImage()
    {
        final int boxSize = 60;
        BufferedImage placeholder = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        g.setColor(new Color(255, 255, 255, 120));
        g.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        g.drawString("...", 14, 38);

        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(placeholder));
    }

    private String formatPrice(int value)
    {
        return String.format("%,d", value);
    }

    /**
     * Adds comma thousand-separators to a shop price string (e.g. "750" -> "750",
     * "2500" -> "2,500"). Falls back to the raw string unchanged if it isn't a plain
     * integer (the wiki's price fields are usually clean numbers, but this avoids crashing
     * or mangling anything unexpected).
     */
    /**
     * Sanity ceiling for a single shop-sold price. No item in OSRS is legitimately sold by
     * a shop for anywhere near this much - anything above it is essentially certain to be
     * a wiki data error (a real one was found: "Tree (Draynor guard)" showing 10,000,000,000
     * gp for Stew, when every other shop sells it for 20-24 gp) rather than a real price.
     */
    private static final long SHOP_PRICE_SANITY_CAP = 1_000_000_000L;

    private String formatShopPrice(String rawPrice)
    {
        try
        {
            // long, not int - a raw value like "10000000000" overflows int's ~2.1 billion
            // range and would silently throw here, falling through to display the raw
            // unformatted string rather than being caught by the sanity check below.
            long parsed = Long.parseLong(rawPrice.trim().replace(",", ""));
            if (parsed > SHOP_PRICE_SANITY_CAP)
            {
                return "Unavailable (data error)";
            }
            return String.format("%,d", parsed);
        }
        catch (NumberFormatException e)
        {
            return rawPrice;
        }
    }

    /**
     * Draws a small side-view coin stack (flattened discs stacked vertically, like a
     * short roll of coins) used for money-related rows: GE price, alch values, item value.
     */
    private Icon createCoinIcon(Color color)
    {
        final int width = 14;
        final int height = 14;
        Color rim = color.darker();
        Color highlight = new Color(
                Math.min(255, color.getRed() + 45),
                Math.min(255, color.getGreen() + 40),
                Math.min(255, color.getBlue() + 10));

        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int discW = width;
                int discH = 6;

                // Two flattened "edge" discs stacked beneath, showing the side of the stack.
                for (int i = 2; i >= 1; i--)
                {
                    int cy = y + 3 + i * 3;
                    g2.setColor(rim);
                    g2.fillOval(x, cy, discW, discH);
                }

                // Top disc: the visible coin face, with a highlight arc for shine.
                g2.setColor(rim);
                g2.fillOval(x, y + 2, discW, discH + 2);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 2, discW - 2, discH);
                g2.setColor(highlight);
                g2.fillOval(x + 3, y + 3, discW / 3, discH / 2);

                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return width;
            }

            @Override
            public int getIconHeight()
            {
                return height;
            }
        };
    }

    /**
     * Draws a small calendar icon (rounded rectangle with a header bar and two hanger
     * tabs) used for the Released row.
     */
    private Icon createCalendarIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(x, y + 1, size, size - 2, 2, 2);
                g2.setColor(new Color(30, 30, 30));
                g2.fillRect(x + 1, y + 3, size - 2, 2);
                g2.fillRect(x + 2, y, 2, 2);
                g2.fillRect(x + size - 4, y, 2, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small balance-scale icon (two circles on a horizontal bar) used for
     * the Weight row.
     */
    private Icon createScaleIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + 1, y + 2, x + size - 1, y + 2);
                g2.drawLine(x + size / 2, y + 2, x + size / 2, y + size - 3);
                g2.drawOval(x, y + 3, 4, 4);
                g2.drawOval(x + size - 4, y + 3, 4, 4);
                g2.fillRect(x + size / 2 - 3, y + size - 3, 6, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small checkmark used for "Yes" boolean rows (tradeable, equipable, etc).
     */
    private Icon createCheckIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 2, y + 6, x + 5, y + 9);
                g2.drawLine(x + 5, y + 9, x + 10, y + 3);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small X used for "No" boolean rows.
     */
    private Icon createCrossIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 2, y + 2, x + 10, y + 10);
                g2.drawLine(x + 10, y + 2, x + 2, y + 10);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small three-line "list" icon used for rows that aren't a single yes/no
     * or number, like the Options row.
     */
    private Icon createListIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRect(x + 1, y + 2, size - 2, 2);
                g2.fillRect(x + 1, y + 5, size - 2, 2);
                g2.fillRect(x + 1, y + 8, size - 2, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Picks the right icon for a boolean-style value: the real checkbox-checked sprite for
     * Yes, checkbox-crossed sprite for No (falling back to hand-drawn versions of the same
     * shapes if those sprites haven't loaded), or a hand-drawn list icon for anything else.
     */
    private Icon yesNoIcon(String value, Color color)
    {
        if ("Yes".equalsIgnoreCase(value))
        {
            return realYesIcon != null ? realYesIcon : createCheckIcon(color);
        }
        if ("No".equalsIgnoreCase(value))
        {
            return realNoIcon != null ? realNoIcon : createCrossIcon(color);
        }
        return createListIcon(color);
    }

    /**
     * Returns green for "Yes", a muted red for "No", or neutral gray for anything else -
     * used to color-code boolean-style stats (Tradeable, Equipable, Members, etc).
     */
    private Color yesNoColor(String value)
    {
        if ("Yes".equalsIgnoreCase(value))
        {
            return GREEN;
        }
        if ("No".equalsIgnoreCase(value))
        {
            return RED;
        }
        return NEUTRAL;
    }

    /**
     * Adds one label/value row to the info table using GridBagLayout, with a small
     * category icon and the value text tinted to match. Pass null for icon/accentColor
     * to fall back to the default (uncolored, iconless) look.
     */
    private void addTableRow(int row, String label, String value, Icon icon, Color accentColor)
    {
        Font labelFont = FontManager.getRunescapeFont();
        Font valueFont = FontManager.getRunescapeFont();

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);
        labelComp.setForeground(Color.WHITE);
        if (icon != null)
        {
            labelComp.setIcon(icon);
            labelComp.setIconTextGap(6);
        }

        JLabel valueComp = new JLabel("<html>" + wrapTextManually(value, 130, valueFont) + "</html>");
        valueComp.setFont(valueFont);
        if (accentColor != null)
        {
            valueComp.setForeground(accentColor);
        }

        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.gridx = 0;
        gcLabel.gridy = row;
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.insets = new Insets(2, 0, 2, 8);

        GridBagConstraints gcValue = new GridBagConstraints();
        gcValue.gridx = 1;
        gcValue.gridy = row;
        gcValue.anchor = GridBagConstraints.NORTHWEST;
        gcValue.weightx = 1.0;
        gcValue.fill = GridBagConstraints.HORIZONTAL;
        gcValue.insets = new Insets(2, 0, 2, 0);

        infoTable.add(labelComp, gcLabel);
        infoTable.add(valueComp, gcValue);
    }

    private void addTableRow(int row, String label, String value)
    {
        addTableRow(row, label, value, null, null);
    }

    public void showItem(String name, BufferedImage image, int price, int highAlch, int lowAlch)
    {
        ensureItemViewShown();
        nameLabel.setText("<html>" + wrapTextManually(name, 140, FontManager.getRunescapeBoldFont().deriveFont(25f)) + "</html>");
        if (image != null)
        {
            setImage(image);
        }
        else
        {
            showLoadingImage();
        }

        infoTable.removeAll();
        // Price data is known synchronously here, but the rest of the properties (Released,
        // Members, etc.) only arrive later via a separate async wiki fetch. Rather than add
        // these rows now and the rest afterward - which would lock in "Price first" purely
        // by timing, regardless of what order actually looks best - they're stashed until
        // setItemProperties() renders everything together in one deliberately-chosen order.
        pendingPrice = price;
        pendingHighAlch = highAlch;
        pendingLowAlch = lowAlch;
        updatePropertiesVisibility();

        lastFullDescription = "Loading description...";
        descriptionArea.setText(lastFullDescription);
        descriptionPanel.setVisible(true);

        resetSources();

        revalidate();
        repaint();
        scrollToTop();
    }

    /**
     * Renders the full Properties table for an item in one pass, combining the price data
     * captured earlier in showItem() with the wiki infobox data that just arrived - this is
     * what lets the final row order (Released, then price/value info, then the yes/no
     * fields, then Options) be chosen deliberately instead of being at the mercy of which
     * data happened to arrive first.
     */
    public void setItemProperties(ItemInfoClient.InfoboxData info)
    {
        infoTable.removeAll();
        int row = 0;

        if (info != null)
        {
            addTableRow(row++, "Released:", info.released, createCalendarIcon(BLUE), BLUE);
        }
        if (pendingPrice > 0)
        {
            addTableRow(row++, "GE Price:", formatPrice(pendingPrice) + " gp", coinIcon(), GOLD);
        }
        if (pendingHighAlch > 0)
        {
            addTableRow(row++, "High alch:", formatPrice(pendingHighAlch) + " gp", highAlchIcon(), GOLD);
        }
        if (pendingLowAlch > 0)
        {
            addTableRow(row++, "Low alch:", formatPrice(pendingLowAlch) + " gp", lowAlchIcon(), GOLD);
        }
        if (info != null)
        {
            String formattedValue = formatValueString(info.value) + " gp";
            addTableRow(row++, "Value:", formattedValue, coinIcon(), GOLD);
            addTableRow(row++, "Weight:", info.weight + " kg", weightIcon(), BLUE);
            addTableRow(row++, "Members:", info.members, membersRowIcon(info.members), yesNoColor(info.members));
            addTableRow(row++, "Tradeable:", info.tradeable, yesNoIcon(info.tradeable, yesNoColor(info.tradeable)), yesNoColor(info.tradeable));
            addTableRow(row++, "Equipable:", info.equipable, yesNoIcon(info.equipable, yesNoColor(info.equipable)), yesNoColor(info.equipable));
            addTableRow(row++, "Stackable:", info.stackable, yesNoIcon(info.stackable, yesNoColor(info.stackable)), yesNoColor(info.stackable));
            addTableRow(row++, "Noteable:", info.noteable, noteIcon(), yesNoColor(info.noteable));
            addTableRow(row++, "Quest item:", info.questItem, questIcon(), yesNoColor(info.questItem));
            addTableRow(row++, "Options:", info.options, createListIcon(NEUTRAL), NEUTRAL);
        }

        updatePropertiesVisibility();
        revalidate();
        repaint();
        scrollToTop();
    }

    /**
     * Populates Properties for an NPC. Unlike items (where every field is always expected
     * and missing ones show "Unknown"), NPC fields are conditional on which of the two NPC
     * infobox templates the page actually uses - a row is only added when that field is
     * genuinely present, rather than cluttering the list with "Unknown" for fields that
     * were never applicable to begin with (e.g. "Max hit" for a friendly, non-combat NPC).
     */
    public void setNpcProperties(ItemInfoClient.NpcInfoboxData data)
    {
        if (data == null)
        {
            updatePropertiesVisibility();
            return;
        }

        int row = currentRowCount();
        // Order: identity info first (Released, Race), then combat stats (Combat level,
        // Attack style), then Max hit paired right next to Poisonous as specifically
        // requested, then the remaining yes/no "checkbox" fields grouped together
        // (Aggressive, Members), then the more niche fields last (Slayer level, Quest).
        if (data.released != null)
        {
            addTableRow(row++, "Released:", data.released, createCalendarIcon(BLUE), BLUE);
        }
        if (data.race != null)
        {
            addTableRow(row++, "Race:", data.race, createListIcon(NEUTRAL), NEUTRAL);
        }
        if (data.combatLevel != null)
        {
            Color combatColor = combatLevelColorForLevel(data.combatLevel);
            addTableRow(row++, "Combat level:", data.combatLevel, createListIcon(NEUTRAL), combatColor);
        }
        if (data.attackStyle != null)
        {
            addTableRow(row++, "Attack style:", data.attackStyle, createListIcon(NEUTRAL), NEUTRAL);
        }
        if (data.maxHit != null)
        {
            addTableRow(row++, "Max hit:", data.maxHit, maxHitIcon(), RED);
        }
        if (data.poisonous != null)
        {
            addTableRow(row++, "Poisonous:", data.poisonous, poisonIcon(), yesNoColor(data.poisonous));
        }
        if (data.aggressive != null)
        {
            addTableRow(row++, "Aggressive:", data.aggressive, aggressiveIcon(), yesNoColor(data.aggressive));
        }
        if (data.members != null)
        {
            addTableRow(row++, "Members:", data.members, membersRowIcon(data.members), yesNoColor(data.members));
        }
        if (data.slayerLevel != null)
        {
            addTableRow(row++, "Slayer level:", data.slayerLevel, createListIcon(NEUTRAL), NEUTRAL);
        }
        if (data.quest != null)
        {
            addTableRow(row++, "Quest:", data.quest, questIcon(), yesNoColor(data.quest));
        }

        updatePropertiesVisibility();
        revalidate();
        repaint();
        scrollToTop();
    }

    /**
     * Populates Properties for an object/scenery, same conditional-row approach as NPCs.
     */
    public void setObjectProperties(ItemInfoClient.ObjectInfoboxData data)
    {
        if (data == null)
        {
            updatePropertiesVisibility();
            return;
        }

        int row = currentRowCount();
        if (data.released != null)
        {
            addTableRow(row++, "Released:", data.released, createCalendarIcon(BLUE), BLUE);
        }
        if (data.members != null)
        {
            addTableRow(row++, "Members:", data.members, membersRowIcon(data.members), yesNoColor(data.members));
        }
        if (data.quest != null)
        {
            addTableRow(row++, "Quest:", data.quest, questIcon(), yesNoColor(data.quest));
        }
        if (data.options != null)
        {
            addTableRow(row++, "Options:", data.options, createListIcon(NEUTRAL), NEUTRAL);
        }

        updatePropertiesVisibility();
        revalidate();
        repaint();
        scrollToTop();
    }

    private String formatValueString(String raw)
    {
        try
        {
            int parsed = Integer.parseInt(raw.trim().replaceAll("[^0-9-]", ""));
            return formatPrice(parsed);
        }
        catch (NumberFormatException e)
        {
            return raw;
        }
    }

    private int currentRowCount()
    {
        // Each row adds 2 components (label + value), so divide by 2 for the next free row index.
        return infoTable.getComponentCount() / 2;
    }

    /**
     * Registers the callback that fetches drop/shop sources for the item currently shown.
     * Passing null (e.g. for NPCs/objects, which don't have item sources) hides the whole
     * Item Sources section instead of showing one that goes nowhere useful.
     * <p>
     * If Item Sources was already expanded (from a previous item), this immediately
     * re-fetches for the new item rather than waiting for the user to expand it again -
     * their expand/collapse choices should carry over between items, not reset each time.
     */
    public void setSourcesLoader(Runnable loader)
    {
        this.sourcesLoader = loader;
        itemSourcesPanel.setVisible(loader != null);

        if (itemSourcesExpanded && loader != null && !sourcesRequested)
        {
            sourcesRequested = true;
            dropsContent.removeAll();
            dropsContent.add(makeSourcesInfoLabel("Loading..."));
            shopsContent.removeAll();
            shopsContent.add(makeSourcesInfoLabel("Loading..."));
            loader.run();
        }
    }

    /**
     * Shows or hides the whole "Shops" accordion under Item Sources - used to hide it
     * entirely for NPCs (which aren't sold anywhere, unlike items), rather than showing an
     * always-empty "Not sold in any shops" section that doesn't apply to them at all.
     */
    public void setShopsSectionVisible(boolean visible)
    {
        shopsHeaderLabel.setVisible(visible);
        shopsScrollPane.setVisible(visible && shopsExpanded);
        revalidate();
        repaint();
    }

    /**
     * Switches between the normal item behaviour (outer "Item sources" accordion with
     * nested "Drops"/"Shops" toggles inside it) and NPC mode (the outer accordion relabels
     * to "Drops" directly, and the now-redundant nested "Drops" toggle is hidden entirely -
     * one click shows the list instead of two). Call with false to restore normal behaviour
     * (e.g. switching from an NPC back to examining an item).
     */
    public void setNpcDropsMode(boolean npcMode)
    {
        this.npcDropsMode = npcMode;
        dropsHeaderLabel.setVisible(!npcMode);
        updateAccordionHeader(itemSourcesHeaderLabel, npcMode ? "Drops" : "Item sources", itemSourcesExpanded, itemSourcesHovering);
        dropsScrollPane.setVisible(npcMode ? itemSourcesExpanded : (itemSourcesExpanded && dropsExpanded));
        revalidate();
        repaint();
    }

    /**
     * Registers the callback fired when a name in the Drops list is clicked - the panel
     * itself has no access to game/client resources needed to actually resolve and display
     * the clicked item/NPC, so this is wired up once by the plugin at startup.
     */
    public void setDropRowClickListener(java.util.function.BiConsumer<String, Boolean> listener)
    {
        this.dropRowClickListener = listener;
    }

    /** Registers the callback fired when the back button (top-left, above the icon/name)
     * is clicked - the plugin owns the actual navigation history, since it needs to
     * re-invoke game/client-dependent show flows to go back. */
    public void setBackButtonListener(Runnable listener)
    {
        this.backButtonListener = listener;
    }

    /** Shows or hides the back button - only relevant once there's actually somewhere to
     * go back to. */
    public void setBackButtonVisible(boolean visible)
    {
        backButtonLabel.setVisible(visible);
        revalidate();
        repaint();
    }

    /**
     * Strips a sub-location suffix like " (Wilderness Slayer Cave)" before using a clicked
     * name for navigation - but only for monster names (where that suffix was artificially
     * added by formatSourceName). Item names never get stripped, since a parenthetical
     * suffix there can be part of the item's real, distinct wiki page name (e.g. "Ring of
     * wealth (5)"). isNpcRow is passed in explicitly per-row (rather than inferred from
     * panel-wide state), so this stays correct regardless of which section a row is in.
     */
    private String stripSubLocationForNav(String name, boolean isNpcRow)
    {
        if (!isNpcRow)
        {
            return name;
        }
        int idx = name.indexOf(" (");
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /**
     * Called by the plugin (which has access to the game client, unlike this panel) each
     * time an item is examined, so the drops list can colour-code monster combat levels
     * relative to the player's own. Pass -1 if the player's level couldn't be read.
     */
    public void setPlayerCombatLevel(int level)
    {
        this.playerCombatLevel = level;
    }

    /**
     * Called once by the plugin at startup with the real coins item sprite (item 995).
     * Scaled down to match the other property-row icons.
     */
    public void setCoinIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realCoinIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the bundled max-hit hitsplat icon (a real
     * cropped screenshot, not a fetched sprite - there was no confirmed-safe sprite
     * constant for a standard damage hitsplat to fetch instead).
     */
    public void setMaxHitIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realMaxHitIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the bundled poison icon (also a cropped
     * screenshot rather than a fetched sprite, for consistency with the max-hit icon).
     */
    public void setPoisonIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realPoisonIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the bundled quest icon.
     */
    public void setQuestIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realQuestIcon = new ImageIcon(scaled);
        }
    }

    /** Called once by the plugin at startup with the bundled bank-note icon, used for the
     * Noteable row. */
    public void setNoteIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realNoteIcon = new ImageIcon(scaled);
        }
    }

    /** Called once by the plugin at startup with the bundled crossed-swords icon, used for
     * the NPC Aggressive row. */
    public void setAggressiveIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realAggressiveIcon = new ImageIcon(scaled);
        }
    }

    /** Called once by the plugin at startup with the bundled gold-star members icon, shown
     * for the Members row when the value is Yes. */
    public void setMemberIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realMemberIcon = new ImageIcon(scaled);
        }
    }

    /** Called once by the plugin at startup with the bundled gray-star free-to-play icon,
     * shown for the Members row when the value is No. */
    public void setF2pIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realF2pIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the real equipment-weight sprite.
     * Scaled down to match the other property-row icons.
     */
    public void setWeightIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realWeightIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the real checkbox-checked sprite, used for
     * "Yes" boolean rows (Members, Tradeable, etc).
     */
    public void setYesIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realYesIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the real checkbox-crossed sprite, used for
     * "No" boolean rows (Quest item, Noteable, etc).
     */
    public void setNoIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realNoIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the real High Level Alchemy spell icon.
     */
    public void setHighAlchIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realHighAlchIcon = new ImageIcon(scaled);
        }
    }

    /**
     * Called once by the plugin at startup with the real Low Level Alchemy spell icon.
     */
    public void setLowAlchIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realLowAlchIcon = new ImageIcon(scaled);
        }
    }

    /** The real coins sprite once it's arrived, otherwise the hand-drawn fallback. */
    private Icon coinIcon()
    {
        return realCoinIcon != null ? realCoinIcon : createCoinIcon(GOLD);
    }

    /** The bundled max-hit icon, otherwise the hand-drawn list-icon fallback. */
    private Icon maxHitIcon()
    {
        return realMaxHitIcon != null ? realMaxHitIcon : createListIcon(RED);
    }

    /** The bundled poison icon, otherwise the hand-drawn check-icon fallback. */
    private Icon poisonIcon()
    {
        return realPoisonIcon != null ? realPoisonIcon : createCheckIcon(GREEN);
    }

    /** The bundled quest icon, otherwise the hand-drawn list-icon fallback. */
    private Icon questIcon()
    {
        return realQuestIcon != null ? realQuestIcon : createListIcon(NEUTRAL);
    }

    /** The bundled bank-note icon, otherwise the hand-drawn list-icon fallback. */
    private Icon noteIcon()
    {
        return realNoteIcon != null ? realNoteIcon : createListIcon(NEUTRAL);
    }

    /** The bundled crossed-swords icon, otherwise the hand-drawn list-icon fallback. */
    private Icon aggressiveIcon()
    {
        return realAggressiveIcon != null ? realAggressiveIcon : createListIcon(RED);
    }

    /** The bundled gold-star icon (Members: Yes), otherwise the hand-drawn check fallback. */
    private Icon memberIcon()
    {
        return realMemberIcon != null ? realMemberIcon : createCheckIcon(GREEN);
    }

    /** The bundled gray-star icon (Members: No), otherwise the hand-drawn cross fallback. */
    private Icon f2pIcon()
    {
        return realF2pIcon != null ? realF2pIcon : createCrossIcon(RED);
    }

    /** Picks the gold-star member icon for "Yes" or the gray-star f2p icon for "No" -
     * used for every "Members:" row (items, NPCs, and objects all have one). */
    private Icon membersRowIcon(String value)
    {
        if ("Yes".equalsIgnoreCase(value))
        {
            return memberIcon();
        }
        if ("No".equalsIgnoreCase(value))
        {
            return f2pIcon();
        }
        return createListIcon(NEUTRAL);
    }

    /** The real equipment-weight sprite once it's arrived, otherwise the hand-drawn fallback. */
    private Icon weightIcon()
    {
        return realWeightIcon != null ? realWeightIcon : createScaleIcon(BLUE);
    }

    /** The real High Alchemy spell icon once it's arrived, otherwise the coin fallback. */
    private Icon highAlchIcon()
    {
        return realHighAlchIcon != null ? realHighAlchIcon : createCoinIcon(GOLD);
    }

    /** The real Low Alchemy spell icon once it's arrived, otherwise the coin fallback. */
    private Icon lowAlchIcon()
    {
        return realLowAlchIcon != null ? realLowAlchIcon : createCoinIcon(GOLD);
    }

    /**
     * Clears out any stale sources data from the previous item, but deliberately does NOT
     * collapse the Item Sources/Drops/Shops sections if the user already had them open -
     * examining a new item should keep your expand/collapse choices, not reset them every
     * time. The actual re-fetch for the new item is triggered separately by
     * setSourcesLoader once the new item's loader is wired up (this runs too early for
     * that - the old item's loader is still in place at this point).
     */
    private void resetSources()
    {
        sourcesRequested = false;
        cachedDrops = null;
        cachedShops = null;
        dropsContent.removeAll();
        shopsContent.removeAll();
        itemSourcesContent.setVisible(itemSourcesExpanded);
        dropsScrollPane.setVisible(dropsExpanded);
        shopsScrollPane.setVisible(shopsExpanded);
    }

    /**
     * Wires up a dropdown-arrow accordion header label: hover highlight plus a click handler
     * that flips the given expanded flag (via the supplied getter/setter pair) and re-runs
     * the toggle callback.
     */
    private void wireAccordionHeader(JLabel label, Supplier<String> titleSupplier, Supplier<Boolean> getExpanded,
                                     Supplier<Boolean> getHovering, Consumer<Boolean> setHovering, Runnable onToggle)
    {
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        label.setIconTextGap(6);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateAccordionHeader(label, titleSupplier.get(), getExpanded.get(), getHovering.get());

        label.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                onToggle.run();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setHovering.accept(true);
                updateAccordionHeader(label, titleSupplier.get(), getExpanded.get(), true);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setHovering.accept(false);
                updateAccordionHeader(label, titleSupplier.get(), getExpanded.get(), false);
            }
        });
    }

    private void updateAccordionHeader(JLabel label, String title, boolean expanded, boolean hovering)
    {
        Color color = hovering ? GOLD_HOVER : GOLD;
        label.setText(title);
        label.setIcon(createChevronIcon(expanded, color));
        label.setForeground(color);
    }

    private static final int DIR_RIGHT = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_UP = 2;
    private static final int DIR_LEFT = 3;

    private Icon createChevronIcon(boolean expanded, Color color)
    {
        return createTriangleIcon(expanded ? DIR_DOWN : DIR_RIGHT, color);
    }

    /**
     * Draws a small solid triangle pointing right, down, or up, as an actual icon rather
     * than a text character. Pixel fonts like the RuneScape one don't reliably include
     * clean arrow/chevron glyphs, so drawing the shape directly guarantees it always looks
     * right regardless of font glyph coverage. Shared by the section accordion headers,
     * Read more/less, and Back to top so all the dropdown/expand indicators in the panel
     * look consistent.
     */
    private Icon createTriangleIcon(int direction, Color color)
    {
        final int size = 10;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                Polygon triangle = new Polygon();
                switch (direction)
                {
                    case DIR_DOWN:
                        triangle.addPoint(x, y + 2);
                        triangle.addPoint(x + size, y + 2);
                        triangle.addPoint(x + size / 2, y + size - 1);
                        break;
                    case DIR_UP:
                        triangle.addPoint(x, y + size - 2);
                        triangle.addPoint(x + size, y + size - 2);
                        triangle.addPoint(x + size / 2, y + 1);
                        break;
                    case DIR_LEFT:
                        triangle.addPoint(x + size - 1, y);
                        triangle.addPoint(x + size - 1, y + size);
                        triangle.addPoint(x + 1, y + size / 2);
                        break;
                    default:
                        triangle.addPoint(x + 1, y);
                        triangle.addPoint(x + 1, y + size);
                        triangle.addPoint(x + size - 1, y + size / 2);
                        break;
                }
                g2.fillPolygon(triangle);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    private void toggleProperties()
    {
        propertiesExpanded = !propertiesExpanded;
        updateAccordionHeader(propertiesHeaderLabel, "Properties", propertiesExpanded, propertiesHovering);
        updatePropertiesVisibility();
        revalidate();
        repaint();
    }

    /**
     * Keeps the Properties card's visibility in sync: it only shows up once there's actual
     * stats data for the current item, and within that, the stats table itself respects
     * the user's expand/collapse choice. Item Sources is its own independent section now, so
     * it no longer factors into whether Properties shows up.
     */
    private void updatePropertiesVisibility()
    {
        boolean hasStats = currentRowCount() > 0;
        infoTable.setVisible(hasStats && propertiesExpanded);
        propertiesPanel.setVisible(hasStats);
    }

    private void toggleDescription()
    {
        descriptionExpanded = !descriptionExpanded;
        updateAccordionHeader(descriptionHeaderLabel, "Description", descriptionExpanded, descriptionHovering);
        descriptionContent.setVisible(descriptionExpanded);
        revalidate();
        repaint();
    }

    /**
     * Toggles the outer Item Sources row. Expanding it reveals the nested Drops/Shops rows
     * (still collapsed themselves at first) and fires the lazy combined fetch the first
     * time this is opened for the current item - both nested rows share this one fetch.
     */
    private void toggleItemSources()
    {
        itemSourcesExpanded = !itemSourcesExpanded;
        updateAccordionHeader(itemSourcesHeaderLabel, npcDropsMode ? "Drops" : "Item sources", itemSourcesExpanded, itemSourcesHovering);
        itemSourcesContent.setVisible(itemSourcesExpanded);

        if (npcDropsMode)
        {
            // No nested "Drops" toggle in this mode - the outer accordion directly
            // controls the drops list's visibility instead of requiring a second click.
            dropsExpanded = itemSourcesExpanded;
            dropsScrollPane.setVisible(itemSourcesExpanded);
        }

        if (itemSourcesExpanded && !sourcesRequested)
        {
            sourcesRequested = true;
            dropsContent.removeAll();
            dropsContent.add(makeSourcesInfoLabel("Loading..."));
            shopsContent.removeAll();
            shopsContent.add(makeSourcesInfoLabel("Loading..."));

            if (sourcesLoader != null)
            {
                sourcesLoader.run();
            }
        }

        revalidate();
        repaint();
    }

    private void toggleDrops()
    {
        dropsExpanded = !dropsExpanded;
        updateAccordionHeader(dropsHeaderLabel, "Drops", dropsExpanded, dropsHovering);
        dropsScrollPane.setVisible(dropsExpanded);
        revalidate();
        repaint();
    }

    private void toggleShops()
    {
        shopsExpanded = !shopsExpanded;
        updateAccordionHeader(shopsHeaderLabel, "Shops", shopsExpanded, shopsHovering);
        shopsScrollPane.setVisible(shopsExpanded);
        revalidate();
        repaint();
    }

    private JLabel makeSourcesInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        label.setForeground(NEUTRAL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static final Color ALWAYS_COLOR = new Color(135, 206, 235);
    // Four-tier rarity gradient (green -> yellow -> red -> purple), replacing the previous
    // all-green-shades scheme - the higher tiers now read as visibly more significant
    // rather than just slightly darker green, closer to how OSRS players actually talk
    // about drop tiers (common/uncommon/rare/legendary-tier "insane rarity" drops).
    private static final Color RARITY_COMMON = new Color(80, 220, 100);
    private static final Color RARITY_UNCOMMON = new Color(230, 210, 60);
    private static final Color RARITY_RARE = new Color(225, 80, 80);
    private static final Color RARITY_LEGENDARY = new Color(190, 110, 235);
    private static final Color RARITY_UNKNOWN = NEUTRAL;

    /**
     * Populates the nested Drops and Shops rows independently. Results are cached so
     * re-collapsing/re-expanding either row doesn't rebuild anything, and this is safe to
     * call even if the user has since collapsed everything (nothing renders until they
     * expand it again).
     */
    public void setSources(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops)
    {
        cachedDrops = drops != null ? drops : new ArrayList<>();
        cachedShops = shops != null ? shops : new ArrayList<>();

        dropsContent.removeAll();
        if (cachedDrops.isEmpty())
        {
            // NPCs get a more specific message than items, since in NPC mode this section
            // IS the drops list (there's no separate Shops to fall back to) - "no known
            // drop sources" reads oddly for a monster, "no drop data available" is clearer.
            String emptyMessage = npcDropsMode
                    ? "No drop data available for this NPC."
                    : "No known drop sources.";
            dropsContent.add(makeSourcesInfoLabel(emptyMessage));
        }
        else
        {
            for (ItemInfoClient.DropSource drop : cachedDrops)
            {
                dropsContent.add(buildDropRow(drop, !npcDropsMode));
            }
        }

        // Size the scrollpane to fit the actual content up to the cap, so a short list
        // shows at its natural height (no empty scroll space, no scrollbar) while a long
        // one caps out and scrolls internally - with the "Drops" header staying fixed
        // above it either way, since the header lives outside this scrollpane.
        dropsContent.revalidate();
        int contentHeight = dropsContent.getPreferredSize().height;
        int cappedHeight = Math.min(contentHeight, SOURCES_MAX_HEIGHT);
        dropsScrollPane.setPreferredSize(new Dimension(10, cappedHeight));
        dropsScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, cappedHeight));

        shopsContent.removeAll();
        if (cachedShops.isEmpty())
        {
            shopsContent.add(makeSourcesInfoLabel("Not sold in any shops."));
        }
        else
        {
            for (ItemInfoClient.ShopSource shop : cachedShops)
            {
                shopsContent.add(buildShopRow(shop));
            }
        }

        shopsContent.revalidate();
        int shopsContentHeight = shopsContent.getPreferredSize().height;
        int shopsCappedHeight = Math.min(shopsContentHeight, SOURCES_MAX_HEIGHT);
        shopsScrollPane.setPreferredSize(new Dimension(10, shopsCappedHeight));
        shopsScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, shopsCappedHeight));

        revalidate();
        repaint();
    }

    /**
     * One drop-source card, laid out as four separate full-width lines - name, then
     * icon+level (colour-coded relative to the player's own combat level, when it's an
     * actual monster combat level), then labelled Quantity, then labelled Drop rate. Each
     * piece gets its own line deliberately: cramming level/quantity/rate onto shared lines
     * via BorderLayout(WEST/EAST) doesn't reflow when the text is long (quantity ranges
     * like "100-150 (noted)" collided with the drop rate text), so every line here is
     * independently wrap-safe instead.
     */
    private JPanel buildDropRow(ItemInfoClient.DropSource drop, boolean rowIsNpc)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 20)),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)
        ));

        String rawName = drop.source != null ? drop.source : "Unknown";
        JLabel nameLabel = new JLabel("<html>" + wrapTextManually(rawName, 160, FontManager.getRunescapeFont()) + "</html>");
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (dropRowClickListener != null)
        {
            nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nameLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    dropRowClickListener.accept(stripSubLocationForNav(rawName, rowIsNpc), rowIsNpc);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    nameLabel.setForeground(GOLD_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    nameLabel.setForeground(Color.WHITE);
                }
            });
        }
        row.add(nameLabel);

        // Some sub-locations are themselves just a level indicator (e.g. "Black Guard#Level
        // 25" formats to "Black Guard (Level 25)"), which would otherwise show the same
        // level twice once we add our own "(Lvl 25)" line - skip the separate line in that
        // case rather than showing "Black Guard (Level 25)" followed by "(Lvl 25)".
        boolean levelAlreadyShown = drop.level != null
                && rawName.toLowerCase().contains("level " + drop.level.toLowerCase());

        if (drop.level != null && !drop.level.isEmpty() && !levelAlreadyShown)
        {
            JLabel levelLabel = new JLabel("(Lvl " + drop.level + ")");
            levelLabel.setFont(FontManager.getRunescapeFont());
            levelLabel.setForeground(combatLevelColor(drop));
            levelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (drop.skillIcon != null)
            {
                final int iconSize = 16;
                Image scaled = drop.skillIcon.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
                levelLabel.setIcon(new ImageIcon(scaled));
                levelLabel.setIconTextGap(6);
            }

            row.add(Box.createVerticalStrut(2));
            row.add(levelLabel);
        }

        JLabel qtyLabel = new JLabel("Quantity: " + formatQuantity(drop.quantity));
        qtyLabel.setFont(FontManager.getRunescapeFont());
        qtyLabel.setForeground(Color.WHITE);
        qtyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createVerticalStrut(4));
        row.add(qtyLabel);

        JLabel rarityLabel = buildRarityLabel(drop.rarity);
        rarityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createVerticalStrut(2));
        row.add(rarityLabel);

        return row;
    }

    /**
     * The wiki's quantity ranges use an en dash ("5\u201314"); swapped for a plain hyphen
     * here since that's what was asked for and it's also just a more common, easier to
     * read/type character in a UI context.
     */
    private String formatQuantity(String rawQuantity)
    {
        if (rawQuantity == null)
        {
            return "-";
        }
        return rawQuantity.replace("\u2013", "-").replace("\u2014", "-");
    }

    // Exact anchor points from the wiki's own "Combat level" page ("Displayed colours"
    // table): level difference -> hex code. Values between these anchors in the official
    // table aren't perfectly linear (they look hand-picked to round hex bytes), but this
    // is close enough to be visually identical, and beyond +-10 the table caps at the pure
    // red/green endpoints.
    private static final int[] COMBAT_DIFF_ANCHORS = {-10, -8, -5, -2, 0, 2, 5, 8, 10};
    private static final Color[] COMBAT_COLOR_ANCHORS = {
            new Color(0x00, 0xff, 0x00),
            new Color(0x40, 0xff, 0x00),
            new Color(0x80, 0xff, 0x00),
            new Color(0xc0, 0xff, 0x00),
            new Color(0xff, 0xff, 0x00),
            new Color(0xff, 0xb0, 0x00),
            new Color(0xff, 0x70, 0x00),
            new Color(0xff, 0x30, 0x00),
            new Color(0xff, 0x00, 0x00),
    };

    /**
     * Colour-codes a monster's combat level relative to the player's own, using the exact
     * anchor colours published on the wiki's own "Combat level" page ("Displayed colours"
     * table) rather than an approximated gradient. Falls back to neutral gray whenever the
     * comparison wouldn't make sense: no player level available yet, the drop isn't from
     * monster combat (skilling/reward sources have levels too, just not combat ones), or
     * the level string isn't a plain number (or comma-separated list of them, e.g.
     * "86,104,109" for a monster with several regional variants - the average of those is
     * used for the gradient position). The wiki's "Cannot attack" -> white case is specific
     * to the Wilderness PvP level-bracket restriction between players, which doesn't apply
     * to monster drop levels, so it's intentionally not reproduced here.
     */
    private Color combatLevelColor(ItemInfoClient.DropSource drop)
    {
        if (drop.level == null || !"combat".equalsIgnoreCase(drop.dropType))
        {
            return NEUTRAL;
        }
        return combatLevelColorForLevel(drop.level);
    }

    /**
     * Same combat-level colour gradient as the drops list, but taking a plain level string
     * directly - used for the NPC Properties "Combat level" row, which isn't tied to a
     * DropSource/dropType at all.
     */
    private Color combatLevelColorForLevel(String levelStr)
    {
        if (playerCombatLevel <= 0 || levelStr == null)
        {
            return NEUTRAL;
        }

        double sum = 0;
        int count = 0;
        for (String part : levelStr.split(","))
        {
            try
            {
                sum += Double.parseDouble(part.trim());
                count++;
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        if (count == 0)
        {
            return NEUTRAL;
        }

        double diff = (sum / count) - playerCombatLevel;
        if (diff <= COMBAT_DIFF_ANCHORS[0])
        {
            return COMBAT_COLOR_ANCHORS[0];
        }
        int lastIndex = COMBAT_DIFF_ANCHORS.length - 1;
        if (diff >= COMBAT_DIFF_ANCHORS[lastIndex])
        {
            return COMBAT_COLOR_ANCHORS[lastIndex];
        }

        for (int i = 0; i < lastIndex; i++)
        {
            int lo = COMBAT_DIFF_ANCHORS[i];
            int hi = COMBAT_DIFF_ANCHORS[i + 1];
            if (diff >= lo && diff <= hi)
            {
                double t = (diff - lo) / (double) (hi - lo);
                return lerpColor(COMBAT_COLOR_ANCHORS[i], COMBAT_COLOR_ANCHORS[i + 1], t);
            }
        }
        // Unreachable in practice (every value is covered by the cap checks and loop above),
        // but returns the diff=0 anchor (yellow) rather than leaving this an ambiguous path.
        return COMBAT_COLOR_ANCHORS[4];
    }

    private Color lerpColor(Color from, Color to, double t)
    {
        t = Math.max(0, Math.min(1, t));
        int r = clampByte(Math.round(from.getRed() + (to.getRed() - from.getRed()) * t));
        int g = clampByte(Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t));
        int b = clampByte(Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
        return new Color(r, g, b);
    }

    private int clampByte(long value)
    {
        return (int) Math.max(0, Math.min(255, value));
    }

    private String escapeHtml(String raw)
    {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Manually wraps text into multiple lines (joined with "&lt;br&gt;") using actual font
     * measurements, instead of relying on a CSS "width" on an HTML div - that approach was
     * tried twice for these row names and didn't reliably wrap inside a nested
     * JScrollPane's viewport, so this sidesteps Swing's HTML/CSS layout quirks entirely by
     * computing the exact break points ourselves. Expects raw (non-HTML-escaped) text and
     * returns HTML-safe output.
     */
    private String wrapTextManually(String text, int maxWidthPx, Font font)
    {
        FontMetrics metrics = getFontMetrics(font);
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" "))
        {
            String candidate = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (metrics.stringWidth(candidate) > maxWidthPx && currentLine.length() > 0)
            {
                if (result.length() > 0)
                {
                    result.append("<br>");
                }
                result.append(escapeHtml(currentLine.toString()));
                currentLine = new StringBuilder(word);
            }
            else
            {
                currentLine = new StringBuilder(candidate);
            }
        }

        if (currentLine.length() > 0)
        {
            if (result.length() > 0)
            {
                result.append("<br>");
            }
            result.append(escapeHtml(currentLine.toString()));
        }

        return result.toString();
    }

    /**
     * One shop-source card: shop name on its own wrapped line, price below it - matches
     * the same collision fix applied to drop rows (BorderLayout WEST/EAST doesn't reflow
     * when the shop name is long enough to run into the price).
     */
    private JPanel buildShopRow(ItemInfoClient.ShopSource shop)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 20)),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)
        ));

        String rawName = shop.shopName != null ? shop.shopName : "Unknown";
        JLabel shopNameLabel = new JLabel("<html>" + wrapTextManually(rawName, 160, FontManager.getRunescapeFont()) + "</html>");
        shopNameLabel.setFont(FontManager.getRunescapeFont());
        shopNameLabel.setForeground(Color.WHITE);
        shopNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(shopNameLabel);

        boolean isCoins = shop.currency == null || shop.currency.isEmpty()
                || "coins".equalsIgnoreCase(shop.currency);

        String formattedPrice = shop.price != null ? formatShopPrice(shop.price) : null;
        boolean isFlagged = formattedPrice != null && formattedPrice.startsWith("Unavailable");

        String priceText;
        if (shop.price == null)
        {
            priceText = "Price: -";
        }
        else if (isFlagged)
        {
            // Don't tack " gp"/currency onto the sanity-check message - "Unavailable
            // (data error) gp" reads oddly.
            priceText = "Price: " + formattedPrice;
        }
        else if (isCoins)
        {
            priceText = "Price: " + formattedPrice + " gp";
        }
        else
        {
            // Non-GP currency (Slayer Rewards points, minigame tokens, etc.) - show the
            // wiki's own currency label rather than assuming everything is coins.
            priceText = "Price: " + formattedPrice + " " + shop.currency;
        }

        JLabel priceLabel = new JLabel(priceText);
        priceLabel.setFont(FontManager.getRunescapeFont());
        priceLabel.setForeground(isFlagged ? NEUTRAL : GOLD);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (isCoins && !isFlagged)
        {
            // The coin icon specifically implies a real GP value - showing it next to a
            // points price or a flagged data-error message would be misleading, so it's
            // only used for actual, sane Coins-based prices.
            priceLabel.setIcon(coinIcon());
            priceLabel.setIconTextGap(6);
        }
        row.add(Box.createVerticalStrut(2));
        row.add(priceLabel);

        return row;
    }

    /**
     * Builds the drop-rate label with colour-coded text - no underline (it read as messy
     * against the RuneScape font at this size), just the color coding to distinguish
     * "Always" from numeric rates.
     */
    private JLabel buildRarityLabel(String rarity)
    {
        String display = (rarity != null && !rarity.isEmpty()) ? rarity : "-";
        display = addThousandsCommas(display);

        JLabel label = new JLabel("Drop rate: " + display);
        // Bold variant at a slightly larger size, rather than the regular weight - the
        // extra stroke weight makes dense fraction numbers like "1/8,192" easier to read
        // while staying in the RuneScape font family.
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
        label.setForeground(rarityColor(display));
        return label;
    }

    /**
     * Finds every number in a string and adds comma thousand-separators to any that are
     * 1000 or over (e.g. "1/8192" -> "1/8,192", "7 x 1/2448" -> "7 x 1/2,448"), leaving
     * smaller numbers and everything else in the string untouched.
     */
    private String addThousandsCommas(String text)
    {
        Matcher matcher = Pattern.compile("\\d+(\\.\\d+)?").matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find())
        {
            result.append(text, lastEnd, matcher.start());
            String numStr = matcher.group();

            try
            {
                if (numStr.contains("."))
                {
                    int dotIndex = numStr.indexOf('.');
                    long intPart = Long.parseLong(numStr.substring(0, dotIndex));
                    String decimalPart = numStr.substring(dotIndex);
                    result.append(intPart >= 1000 ? String.format("%,d", intPart) : String.valueOf(intPart))
                            .append(decimalPart);
                }
                else
                {
                    long value = Long.parseLong(numStr);
                    result.append(value >= 1000 ? String.format("%,d", value) : numStr);
                }
            }
            catch (NumberFormatException e)
            {
                result.append(numStr);
            }

            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    /**
     * Approximate colour grading for the Rarity text, loosely modelled on the wiki's own
     * "Always" (blue) vs numeric-rarity (green, more saturated for commoner drops) scheme.
     * This isn't pixel-identical to the wiki's own gradient (which isn't published anywhere),
     * but gives the same at-a-glance read of "how likely is this drop".
     */
    private Color rarityColor(String rarity)
    {
        if (rarity == null || rarity.isEmpty() || rarity.equals("-"))
        {
            return RARITY_UNKNOWN;
        }

        String lower = rarity.toLowerCase();
        if (lower.contains("always"))
        {
            return ALWAYS_COLOR;
        }

        // Pull the effective odds out of fraction strings like "3/128", "1/9.846", or
        // "5/128; 1/8,192" (real drop data confirmed via a live API response) - dividing
        // denominator by numerator normalizes "3/128" to the same "1-in-N" scale as "1/N"
        // so a 3/128 drop and a roughly-equivalent 1/43 drop grade to the same colour band.
        Matcher matcher = Pattern.compile("([0-9,.]+)/([0-9,.]+)").matcher(rarity);
        Double bestOdds = null;
        while (matcher.find())
        {
            try
            {
                double numerator = Double.parseDouble(matcher.group(1).replace(",", ""));
                double denominator = Double.parseDouble(matcher.group(2).replace(",", ""));
                if (numerator <= 0)
                {
                    continue;
                }
                double odds = denominator / numerator;
                if (bestOdds == null || odds > bestOdds)
                {
                    bestOdds = odds;
                }
            }
            catch (NumberFormatException ignored)
            {
            }
        }

        Double denominator = bestOdds;

        if (denominator == null)
        {
            if (lower.contains("common") && !lower.contains("uncommon"))
            {
                return RARITY_COMMON;
            }
            if (lower.contains("uncommon"))
            {
                return RARITY_UNCOMMON;
            }
            if (lower.contains("rare"))
            {
                return RARITY_RARE;
            }
            return RARITY_UNKNOWN;
        }

        // Thresholds are my own judgment call (OSRS has no official rarity-tier cutoffs
        // published anywhere) - roughly: common enough to see regularly, uncommon but not
        // unusual to still get within a session, genuinely rare (most "unique" boss drops
        // land here), and legendary/"insane rarity" tier (pets, mega-rare uniques).
        if (denominator <= 50)
        {
            return RARITY_COMMON;
        }
        if (denominator <= 500)
        {
            return RARITY_UNCOMMON;
        }
        if (denominator <= 5000)
        {
            return RARITY_RARE;
        }
        return RARITY_LEGENDARY;
    }

    public void clearInfobox()
    {
        infoTable.removeAll();
        updatePropertiesVisibility();
        revalidate();
        repaint();
    }

    public void showNonItem(String name)
    {
        ensureItemViewShown();
        nameLabel.setText("<html>" + wrapTextManually(name, 140, FontManager.getRunescapeBoldFont().deriveFont(25f)) + "</html>");
        showLoadingImage();
        infoTable.removeAll();
        updatePropertiesVisibility();
        lastFullDescription = "Loading description...";
        descriptionArea.setText(lastFullDescription);
        descriptionPanel.setVisible(true);

        resetSources();
        setSourcesLoader(null);

        revalidate();
        repaint();
        scrollToTop();
    }

    public void setDescription(String description)
    {
        lastFullDescription = description;
        refreshDescriptionText();
        revalidate();
        repaint();
        scrollToTop();
    }

    private void refreshDescriptionText()
    {
        boolean needsTruncation = lastFullDescription.length() > 300;

        if (showFullDescription || !needsTruncation)
        {
            descriptionArea.setText(lastFullDescription);
        }
        else
        {
            int cut = lastFullDescription.lastIndexOf('.', 300);
            String shortText = (cut > 0 ? lastFullDescription.substring(0, cut + 1) : lastFullDescription.substring(0, 300)) + "..";
            descriptionArea.setText(shortText);
        }

        readMoreLabel.setVisible(needsTruncation);
        updateReadMoreLabel();

        descriptionArea.setCaretPosition(0);

        // JTextArea's preferred height with word-wrap needs a known width to calculate the
        // wrapped line count - it can't just be asked for its preferred size the way a plain
        // BoxLayout panel of rows can (like Drops/Shops), since on the very first render the
        // component may not have been through a layout pass yet and would report width 0,
        // producing a wrong (single-line-ish) height. Forcing a width explicitly first side-
        // steps that: use the real width once the panel has been laid out at least once,
        // otherwise fall back to the sidebar's typical content width.
        int width = descriptionArea.getWidth();
        if (width <= 0)
        {
            width = 200;
        }
        descriptionArea.setSize(width, Short.MAX_VALUE);

        int contentHeight = descriptionArea.getPreferredSize().height;
        int cappedHeight = Math.min(contentHeight, DESCRIPTION_MAX_HEIGHT);
        descriptionScrollPane.setPreferredSize(new Dimension(10, cappedHeight));
        descriptionScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, cappedHeight));

        revalidate();
        repaint();
    }

    /**
     * Renders the read-more/read-less label with the correct text and icon for the current
     * expand state (down arrow = more to reveal, up arrow = collapse it back), plus a
     * brighter color while the mouse is hovering it.
     */
    private void updateReadMoreLabel()
    {
        String text = showFullDescription ? "Read less" : "Read more";
        Color color = readMoreHovering ? GOLD_HOVER : GOLD;
        readMoreLabel.setForeground(color);
        readMoreLabel.setText(text);
        readMoreLabel.setIcon(createTriangleIcon(showFullDescription ? DIR_UP : DIR_DOWN, color));
        readMoreLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    }

    public void setImage(BufferedImage image)
    {
        if (image == null)
        {
            iconLabel.setIcon(null);
            return;
        }

        final int boxSize = 60;
        int origWidth = image.getWidth();
        int origHeight = image.getHeight();

        double scale = Math.min((double) boxSize / origWidth, (double) boxSize / origHeight);
        int scaledWidth = Math.max(1, (int) (origWidth * scale));
        int scaledHeight = Math.max(1, (int) (origHeight * scale));

        Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);

        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        // Subtle card-style background behind the item so smaller/odd-shaped sprites
        // still sit inside a clean, consistent frame.
        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        // Clip to the rounded frame so the image itself picks up rounded corners.
        g.setClip(frameShape);
        int x = (boxSize - scaledWidth) / 2;
        int y = (boxSize - scaledHeight) / 2;
        g.drawImage(scaledImage, x, y, null);
        g.setClip(null);

        // Soft border on top to finish the frame.
        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(canvas));
        scrollToTop();
    }
}