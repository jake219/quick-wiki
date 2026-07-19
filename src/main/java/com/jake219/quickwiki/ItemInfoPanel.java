package com.jake219.quickwiki;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
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
    private JLabel wikiLinkLabel;
    private String currentWikiPageName;
    private final JLabel itemSourcesHeaderLabel = new JLabel();
    private final JLabel dropsHeaderLabel = new JLabel();
    private final JLabel shopsHeaderLabel = new JLabel();
    private final JLabel combatStatsHeaderLabel = new JLabel();
    private final JPanel combatStatsContent = new JPanel();

    private final JPanel viewContainer = new JPanel(new BorderLayout(0, 10));
    private JPanel mainView;
    private JPanel iconNamePanel;
    private JPanel emptyStatePanel;
    private final JPanel attackBonusRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel attackBonusRow2 = new JPanel(new GridLayout(1, 2, 4, 0));
    private final JPanel defenceBonusRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel defenceBonusRow2 = new JPanel(new GridLayout(1, 2, 4, 0));
    private final JPanel otherBonusRow = new JPanel(new GridLayout(1, 4, 4, 0));
    private final JPanel npcLevelsRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel npcLevelsRow2 = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel npcAttackRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel npcAttackRow2 = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel npcMeleeDefenceRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private final JPanel npcMagicDefenceRow = new JPanel(new GridLayout(1, 2, 4, 0));
    private final JPanel npcRangedDefenceRow = new JPanel(new GridLayout(1, 3, 4, 0));
    private boolean hasShownFirstItem = false;
    private Runnable combatStatsLoader;
    private boolean combatStatsRequested = false;
    private boolean combatStatsExpanded = false;
    private boolean combatStatsHovering = false;

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
    private static final int SOURCES_MAX_HEIGHT = 375;
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
    /** When true, the outer "Item sources" accordion relabels to singleSectionLabel and the
     * redundant nested "Drops" toggle is skipped entirely - a single-source case (a
     * monster's own drops, or a reward casket's own contents) only has the one list (no
     * shops), so nesting it under an "Item sources" umbrella is an unnecessary extra click
     * and a label that doesn't really fit. Items keep the normal two-level
     * Item Sources > Drops/Shops structure. */
    private boolean npcDropsMode = false;
    /** The label used for the outer accordion when npcDropsMode is true - "Drops" for an
     * NPC's own drop table, "Rewards" for a reward casket's contents, etc. Set via
     * setNpcDropsMode's label parameter; defaults to "Drops" for anywhere that still calls
     * the old single-argument convenience overload. */
    private String singleSectionLabel = "Drops";
    /** Registered by the plugin, since only it has access to game/client resources needed
     * to actually resolve and display a clicked drop-row name. Receives the clicked name
     * (already stripped of any sub-location suffix for monster names) and the drop row's
     * own "level" field - for NPC navigation, this is a reliable proxy for the monster's
     * actual combat level (verified against Frost Nagua's own drop_json blob, where "Drop
     * level":"104" matched its Properties "Combat level: 104" exactly), used so drop-table
     * navigation to a monster can reuse the same combat-level-filtered query direct
     * in-world clicks already get, rather than always falling back to page_name-only.
     * May be null/empty or not meaningful for item navigation (npcDropsMode) - the plugin
     * ignores it in that case since items don't have combat levels. */
    private BiConsumer<String, String> dropRowClickListener;
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
    /** item name -> every currently-visible row's icon label showing that item, so a
     * resolved icon can be applied to all matching rows at once via updateDropIcon(). */
    private final Map<String, List<JLabel>> dropIconLabels = new HashMap<>();
    private List<ItemInfoClient.ShopSource> cachedShops;

    /**
     * Set by the plugin each time an item is examined (it has to read this from the game
     * client, which the panel itself has no access to). -1 means unknown/unavailable, in
     * which case monster levels are shown in a neutral color rather than guessing.
     */
    private int playerCombatLevel = -1;
    private boolean showTooltips = true;

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

        JLabel wikiLinkLabel = new JLabel("Wiki");
        wikiLinkLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        wikiLinkLabel.setForeground(NEUTRAL);
        wikiLinkLabel.setIcon(createInfoIcon(NEUTRAL));
        wikiLinkLabel.setIconTextGap(4);
        wikiLinkLabel.setHorizontalTextPosition(SwingConstants.RIGHT);
        wikiLinkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        wikiLinkLabel.setVisible(false);
        wikiLinkLabel.setToolTipText("Open this page on the official OSRS Wiki");
        wikiLinkLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (currentWikiPageName != null)
                {
                    openInBrowser(officialWikiUrl(currentWikiPageName));
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                wikiLinkLabel.setForeground(NEUTRAL_HOVER);
                wikiLinkLabel.setIcon(createInfoIcon(NEUTRAL_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                wikiLinkLabel.setForeground(NEUTRAL);
                wikiLinkLabel.setIcon(createInfoIcon(NEUTRAL));
            }
        });
        this.wikiLinkLabel = wikiLinkLabel;

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        topRow.add(backButtonLabel, BorderLayout.WEST);
        topRow.add(wikiLinkLabel, BorderLayout.EAST);

        iconNamePanel = new JPanel();
        iconNamePanel.setLayout(new BoxLayout(iconNamePanel, BoxLayout.Y_AXIS));
        iconNamePanel.add(topRow);
        iconNamePanel.add(Box.createVerticalStrut(6));

        // Icon and name sit side-by-side rather than stacked - saves a full row of
        // vertical space that stacking used to cost, freeing up room elsewhere in the
        // panel (e.g. for larger property text) without growing the overall height.
        JPanel iconNameRow = new JPanel();
        iconNameRow.setLayout(new BoxLayout(iconNameRow, BoxLayout.X_AXIS));
        iconNameRow.setOpaque(false);
        iconNameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        nameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        iconNameRow.add(iconLabel);
        iconNameRow.add(Box.createHorizontalStrut(10));
        iconNameRow.add(nameLabel);

        iconNamePanel.add(iconNameRow);
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
        // Opaque with the standard RuneLite panel background, not transparent like most
        // panels in this file - this is specifically the panel where stale pixels have
        // been observed ghosting through after the button's presence changes. Being
        // non-opaque means Swing never guarantees a background clear before redrawing,
        // regardless of which code path (ours or something else entirely, e.g. Item
        // Sources' own independent async updates) triggers the repaint. A solid matching
        // background fixes this at the root for every trigger, not just one call site.
        propertiesPanel.setOpaque(true);
        propertiesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
        // Opaque by JPanel's default already, but the color was never explicitly set
        // before - fixing that for consistency with the other opacity fixes above.
        mainView.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainView.add(propertiesPanel);
        mainView.add(Box.createVerticalStrut(8));
        mainView.add(buildCombatStatsSection());
        mainView.add(Box.createVerticalStrut(8));
        mainView.add(itemSourcesPanel);
        mainView.add(Box.createVerticalStrut(8));
        mainView.add(descriptionPanel);

        emptyStatePanel = buildEmptyStatePanel();

        // Shows the empty-state message until the first item/NPC/object is actually
        // examined, at which point ensureItemViewShown() swaps this out for the real
        // icon/name + mainView layout (see showItem/showNonItem).
        // Opaque with the standard RuneLite panel background, not transparent - this is
        // the top-level container for the entire panel's content, sitting above
        // everything else. Given propertiesPanel becoming opaque alone didn't fully
        // resolve the ghosting bug, this ancestor is a more likely actual source, since
        // stale pixels here would affect the whole panel, not just one section.
        viewContainer.setOpaque(true);
        viewContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        viewContainer.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        viewContainer.add(emptyStatePanel, BorderLayout.CENTER);
        add(viewContainer, BorderLayout.CENTER);
    }

    /**
     * TODO: fill in your actual repo URL - this is a placeholder until then. Used by the
     * info row's click handler below.
     */
    private static final String PLUGIN_REPO_URL = "https://github.com/jake219/quick-wiki/blob/main/README.md";
    private static final String PLUGIN_VERSION = "1.0.4";

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
        panel.add(buildSupportRow());
        return panel;
    }

    /**
     * A second clickable row right below "View on GitHub", same style, linking to the same
     * repo - lets users report bugs/request features, or support the developer if they'd
     * like to, all via the one link the developer already maintains.
     */
    private JPanel buildSupportRow()
    {
        JLabel icon = new JLabel(createInfoIcon(NEUTRAL));
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        icon.setVerticalAlignment(SwingConstants.TOP);

        Font textFont = new Font("Segoe UI", Font.PLAIN, 11);
        JLabel text = new JLabel("<html>" + wrapTextManually("Report Issues or Support the Developer", 160, textFont) + "</html>");
        text.setFont(textFont);
        text.setForeground(NEUTRAL);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(230, 42));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(new Color(255, 255, 255, 30), 10),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        left.add(icon);
        left.add(Box.createHorizontalStrut(6));
        left.add(text);

        row.add(left, BorderLayout.WEST);

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
            log.warn("Quick Wiki: failed to open {}", url, e);
        }
    }

    /**
     * Builds the real, official OSRS Wiki page URL for a given page name - standard
     * MediaWiki convention (spaces become underscores in the path). Falls back to an
     * unencoded URL if UTF-8 encoding somehow isn't available, rather than failing to
     * open anything at all.
     */
    private String officialWikiUrl(String pageName)
    {
        String withUnderscores = pageName.replace(' ', '_');
        try
        {
            return "https://oldschool.runescape.wiki/w/" + java.net.URLEncoder.encode(withUnderscores, "UTF-8");
        }
        catch (Exception e)
        {
            return "https://oldschool.runescape.wiki/w/" + withUnderscores;
        }
    }

    /**
     * Called by the plugin whenever an item/NPC/object is displayed, with the exact
     * resolved wiki page name (same name already used for fetchDescription/fetchInfobox
     * etc.) - lets the top-right "Wiki" button open that exact page on the real,
     * official wiki, for users who want the full page rather than just this panel's
     * summary. Pass null to hide the button entirely (e.g. before anything's been
     * searched yet).
     */
    public void setWikiPageName(String pageName)
    {
        this.currentWikiPageName = pageName;
        wikiLinkLabel.setVisible(pageName != null);
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
     * Sets up the lazy loader for Combat Stats, called once per item (even non-equipable
     * ones - the accordion is always present, and shows "No combat stats available" if
     * the fetch comes back empty, rather than needing to know in advance whether an item
     * has bonuses). This replaced an earlier button-based design that dynamically added
     * and removed itself from propertiesPanel and caused a persistent visual ghosting
     * bug - reusing this panel's existing accordion pattern (the same one Item Sources
     * and Description already use) sidesteps that problem entirely.
     *
     * @param loader fetches combat bonuses and calls displayCombatBonuses() once ready -
     *               lazy, only runs the first time the button is actually clicked for this
     *               item, matching the same lazy-load pattern as Item Sources.
     */
    public void setCombatStatsAvailable(Runnable loader)
    {
        combatStatsLoader = loader;
        combatStatsRequested = false;
        combatStatsContent.removeAll();
        if (combatStatsExpanded)
        {
            if (loader != null)
            {
                combatStatsContent.add(makeSourcesInfoLabel("Loading..."));
                combatStatsRequested = true;
                loader.run();
            }
            else
            {
                // Confirmed via a real report: switching from an item with bonuses
                // (accordion expanded) to one without left "Loading..." stuck on screen
                // forever, since there was no loader left to ever replace it. We already
                // know upfront there's nothing to fetch, so show the real empty state
                // immediately instead.
                JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
                noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
                combatStatsContent.add(noCombatStatsLabel);
            }
        }
        revalidate();
        repaint();
    }

    private void toggleCombatStats()
    {
        combatStatsExpanded = !combatStatsExpanded;
        updateAccordionHeader(combatStatsHeaderLabel, "Combat Stats", combatStatsExpanded, combatStatsHovering);
        combatStatsContent.setVisible(combatStatsExpanded);

        if (combatStatsExpanded && !combatStatsRequested)
        {
            combatStatsRequested = true;
            combatStatsContent.removeAll();
            if (combatStatsLoader != null)
            {
                combatStatsContent.add(makeSourcesInfoLabel("Loading..."));
                combatStatsLoader.run();
            }
            else
            {
                // Same fix as setCombatStatsAvailable's null-loader case - if this item
                // was set up while the accordion was collapsed (so no content was
                // populated then), expanding it now would otherwise show a blank area
                // instead of the real empty state.
                JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
                noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
                combatStatsContent.add(noCombatStatsLabel);
            }
        }

        revalidate();
        repaint();
    }

    /**
     * Shows or hides the whole "Combat Stats" accordion entirely - used to hide it for
     * Objects, which have no combat stats at all (unlike items/NPCs, where an
     * always-empty "No combat stats available" state can still legitimately apply to some
     * of them). Same pattern as setShopsSectionVisible above, for the same reason: a
     * section that structurally can't ever apply to a category shouldn't be shown as an
     * always-empty toggle for it.
     */
    public void setCombatStatsSectionVisible(boolean visible)
    {
        combatStatsHeaderLabel.setVisible(visible);
        combatStatsContent.setVisible(visible && combatStatsExpanded);
        revalidate();
        repaint();
    }

    /**
     * Builds the Combat Stats accordion section once at construction, following the same
     * structure as Description (header + content, added once to mainView and never
     * removed) rather than a separate swap-to view. Three sections (Attack/Defence/Other
     * bonuses) each laid out as a row of icon-over-value cells, matching the wiki's own
     * Combat stats table structure as closely as reasonably fits this panel's width.
     */
    private JPanel buildCombatStatsSection()
    {
        wireAccordionHeader(combatStatsHeaderLabel, () -> "Combat Stats", () -> combatStatsExpanded,
                () -> combatStatsHovering, hovering -> combatStatsHovering = hovering, this::toggleCombatStats);

        attackBonusRow.setOpaque(false);
        attackBonusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        attackBonusRow2.setOpaque(false);
        attackBonusRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        defenceBonusRow.setOpaque(false);
        defenceBonusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        defenceBonusRow2.setOpaque(false);
        defenceBonusRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        otherBonusRow.setOpaque(false);
        otherBonusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcLevelsRow.setOpaque(false);
        npcLevelsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcLevelsRow2.setOpaque(false);
        npcLevelsRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcAttackRow.setOpaque(false);
        npcAttackRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcAttackRow2.setOpaque(false);
        npcAttackRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcMeleeDefenceRow.setOpaque(false);
        npcMeleeDefenceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcMagicDefenceRow.setOpaque(false);
        npcMagicDefenceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        npcRangedDefenceRow.setOpaque(false);
        npcRangedDefenceRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        combatStatsContent.setLayout(new BoxLayout(combatStatsContent, BoxLayout.Y_AXIS));
        combatStatsContent.setOpaque(false);
        combatStatsContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        combatStatsContent.setVisible(false);

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(combatStatsHeaderLabel);
        section.add(Box.createVerticalStrut(6));
        section.add(combatStatsContent);

        return section;
    }

    private JLabel makeSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
        label.setForeground(GOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Populates the three bonus rows with real values and icons - called once the
     * plugin's combat stats loader finishes fetching. Positive bonuses show in green,
     * negative in red, zero in neutral grey, matching how the rest of this panel colors
     * good/bad/neutral values (e.g. yesNoColor for boolean properties).
     *
     * @param skillIcons keyed by lowercase Skill enum name ("attack", "strength",
     *                   "defence", "ranged", "magic", "prayer") - built by the plugin via
     *                   skillIconManager.getSkillImage(), reusing the exact same mechanism
     *                   already used for drop-type icons elsewhere in this plugin.
     */
    public void displayCombatBonuses(ItemInfoClient.CombatBonuses bonuses, Map<String, BufferedImage> skillIcons)
    {
        combatStatsContent.removeAll();
        attackBonusRow.removeAll();
        attackBonusRow2.removeAll();
        defenceBonusRow.removeAll();
        defenceBonusRow2.removeAll();
        otherBonusRow.removeAll();

        if (bonuses == null)
        {
            JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
            noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
            combatStatsContent.add(noCombatStatsLabel);
        }
        else
        {
            // Stab/Slash/Crush share the Attack skill icon (no unique RuneLite sprite
            // equivalent for each melee style specifically), differentiated by their text
            // label instead of a unique icon - see the session notes on this tradeoff.
            attackBonusRow.add(buildBonusCell("Stab", bonuses.stabAttack, skillIcons.get("attack"), false, "Increases accuracy for stab attacks"));
            attackBonusRow.add(buildBonusCell("Slash", bonuses.slashAttack, skillIcons.get("attack"), false, "Increases accuracy for slash attacks"));
            attackBonusRow.add(buildBonusCell("Crush", bonuses.crushAttack, skillIcons.get("attack"), false, "Increases accuracy for crush attacks"));

            attackBonusRow2.add(buildBonusCell("Magic", bonuses.magicAttack, skillIcons.get("magic"), false, "Increases accuracy for magic attacks"));
            attackBonusRow2.add(buildBonusCell("Ranged", bonuses.rangeAttack, skillIcons.get("ranged"), false, "Increases accuracy for ranged attacks"));

            defenceBonusRow.add(buildBonusCell("Stab", bonuses.stabDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by stab attacks"));
            defenceBonusRow.add(buildBonusCell("Slash", bonuses.slashDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by slash attacks"));
            defenceBonusRow.add(buildBonusCell("Crush", bonuses.crushDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by crush attacks"));

            defenceBonusRow2.add(buildBonusCell("Magic", bonuses.magicDefence, skillIcons.get("magic"), false, "Reduces chance of being hit by magic attacks"));
            defenceBonusRow2.add(buildBonusCell("Ranged", bonuses.rangeDefence, skillIcons.get("ranged"), false, "Reduces chance of being hit by ranged attacks"));

            otherBonusRow.add(buildBonusCell("Strength", bonuses.strength, skillIcons.get("strength"), false, "Increases max hit with melee weapons"));
            otherBonusRow.add(buildBonusCell("Ranged Str", bonuses.rangedStrength, skillIcons.get("ranged"), false, "Increases max hit with ranged weapons"));
            otherBonusRow.add(buildBonusCell("Magic Dmg", bonuses.magicDamage, skillIcons.get("magic"), true, "Increases damage dealt by magic spells"));
            otherBonusRow.add(buildBonusCell("Prayer", bonuses.prayer, skillIcons.get("prayer"), false, "Increases max Prayer points while worn"));

            combatStatsContent.add(makeSectionLabel("Attack bonuses"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(attackBonusRow);
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(attackBonusRow2);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Defence bonuses"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(defenceBonusRow);
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(defenceBonusRow2);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Other bonuses"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(otherBonusRow);
        }

        revalidate();
        repaint();
    }

    /**
     * NPC version of displayCombatBonuses - matches the wiki's own section structure as
     * closely as this panel's width allows: "Combat stats" (6 base levels, including HP,
     * which items don't have), "Aggressive stats" (6 attack-side bonuses - items call this
     * "Attack bonuses", but the wiki's own NPC infobox specifically labels this section
     * "Aggressive stats"), and three separate defence sections (Melee/Magic/Ranged) rather
     * than one combined row - items combine all 5 into one "Defence bonuses" row since
     * items only have a single value per style, but the wiki keeps these as visually
     * distinct sections for monsters too, so this mirrors that rather than the item
     * layout. A previous version of this only showed 5 of 6 Combat stats icons and 3 of 6
     * Aggressive stats icons - a real gap, not just a labeling change.
     */
    public void displayNpcCombatStats(ItemInfoClient.NpcCombatStats stats, Map<String, BufferedImage> skillIcons)
    {
        combatStatsContent.removeAll();
        npcLevelsRow.removeAll();
        npcLevelsRow2.removeAll();
        npcAttackRow.removeAll();
        npcAttackRow2.removeAll();
        npcMeleeDefenceRow.removeAll();
        npcMagicDefenceRow.removeAll();
        npcRangedDefenceRow.removeAll();

        if (stats == null)
        {
            JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
            noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
            combatStatsContent.add(noCombatStatsLabel);
        }
        else
        {
            npcLevelsRow.add(buildLevelCell("HP", stats.hitpoints, skillIcons.get("hitpoints"), "Hitpoints - how much damage this monster can take before dying"));
            npcLevelsRow.add(buildLevelCell("Attack", stats.attackLevel, skillIcons.get("attack"), "Attack level - affects this monster's melee accuracy"));
            npcLevelsRow.add(buildLevelCell("Strength", stats.strengthLevel, skillIcons.get("strength"), "Strength level - affects this monster's melee max hit"));

            npcLevelsRow2.add(buildLevelCell("Defence", stats.defenceLevel, skillIcons.get("defence"), "Defence level - affects how often this monster is hit"));
            npcLevelsRow2.add(buildLevelCell("Magic", stats.magicLevel, skillIcons.get("magic"), "Magic level - affects this monster's magic accuracy and max hit"));
            npcLevelsRow2.add(buildLevelCell("Ranged", stats.rangedLevel, skillIcons.get("ranged"), "Ranged level - affects this monster's ranged accuracy and max hit"));

            npcAttackRow.add(buildBonusCell("Attack", stats.attackBonus, skillIcons.get("attack"), false, "Increases this monster's melee accuracy"));
            npcAttackRow.add(buildBonusCell("Strength", stats.strengthBonus, skillIcons.get("strength"), false, "Increases this monster's melee max hit"));
            npcAttackRow.add(buildBonusCell("Magic", stats.magicAttackBonus, skillIcons.get("magic"), false, "Increases this monster's magic accuracy"));

            npcAttackRow2.add(buildBonusCell("Magic Dmg", stats.magicDamageBonus, skillIcons.get("magic"), true, "Increases this monster's magic max hit"));
            npcAttackRow2.add(buildBonusCell("Ranged", stats.rangeAttackBonus, skillIcons.get("ranged"), false, "Increases this monster's ranged accuracy"));
            npcAttackRow2.add(buildBonusCell("Ranged Str", stats.rangedStrengthBonus, skillIcons.get("ranged"), false, "Increases this monster's ranged max hit"));

            npcMeleeDefenceRow.add(buildBonusCell("Stab", stats.stabDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by stab attacks"));
            npcMeleeDefenceRow.add(buildBonusCell("Slash", stats.slashDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by slash attacks"));
            npcMeleeDefenceRow.add(buildBonusCell("Crush", stats.crushDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by crush attacks"));

            npcMagicDefenceRow.add(buildBonusCell("Magic", stats.magicDefenceBonus, skillIcons.get("magic"), false, "Reduces chance of being hit by magic attacks"));

            String weaknessLabel = (stats.elementalWeaknessType == null || stats.elementalWeaknessType.trim().isEmpty())
                    ? "No Weakness"
                    : stats.elementalWeaknessType.trim().substring(0, 1).toUpperCase()
                      + stats.elementalWeaknessType.trim().substring(1).toLowerCase() + " Weakness";
            npcMagicDefenceRow.add(buildBonusCell(weaknessLabel, stats.elementalWeaknessPercent, skillIcons.get("elemental_weakness"), true, "Extra damage taken from this element's spells"));

            npcRangedDefenceRow.add(buildBonusCell("Light", stats.lightRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by darts and similar light ammo"));
            npcRangedDefenceRow.add(buildBonusCell("Standard", stats.standardRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by arrows and similar standard ammo"));
            npcRangedDefenceRow.add(buildBonusCell("Heavy", stats.heavyRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by bolts and similar heavy ammo"));

            combatStatsContent.add(makeSectionLabel("Combat stats"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcLevelsRow);
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcLevelsRow2);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Aggressive stats"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcAttackRow);
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcAttackRow2);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Melee defence"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcMeleeDefenceRow);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Magic defence"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcMagicDefenceRow);
            combatStatsContent.add(Box.createVerticalStrut(6));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(6));

            combatStatsContent.add(makeSectionLabel("Ranged defence"));
            combatStatsContent.add(Box.createVerticalStrut(4));
            combatStatsContent.add(npcRangedDefenceRow);
        }

        revalidate();
        repaint();
    }

    /**
     * Small icon+text row under Magic defence showing the monster's elemental weakness -
     * matches the wiki's own placement of this info right alongside Magic defence, rather
     * than as its own separate section, and now also matches its icon+text presentation
     * (a rune icon, or "Pure essence" for no weakness) rather than plain text alone.
     * "No elemental weakness" when stats.elementalWeaknessType is null/empty, otherwise
     * "Weak to X spells (+Y%)". The icon itself is resolved by the plugin via
     * itemManager.search (see setupNpcCombatStats) and passed in through skillIcons under
     * the "elemental_weakness" key, same mechanism as every other icon in this panel.
     */
    private JPanel buildBonusCell(String label, int value, BufferedImage icon)
    {
        return buildBonusCell(label, value, icon, false, null);
    }

    /**
     * Like buildBonusCell, but for plain base levels (the wiki's "Combat stats" section -
     * HP/Attack/Strength/Defence/Magic/Ranged) rather than bonuses - no "+" prefix and
     * always neutral coloring, since a level of 5 isn't a "good" or "bad" value the way a
     * +5 bonus is.
     */
    private JPanel buildLevelCell(String label, int value, BufferedImage icon, String tooltipText)
    {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        cell.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (icon != null)
        {
            final int iconSize = 24;
            Image scaled = icon.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaled));
        }

        JLabel valueLabel = new JLabel(String.valueOf(value));
        valueLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        valueLabel.setForeground(NEUTRAL);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Font nameFont = new Font("Segoe UI", Font.PLAIN, 11);
        JLabel nameLabel = new JLabel("<html><div style='text-align:center;'>"
                + wrapTextManually(label, 58, nameFont) + "</div></html>");
        nameLabel.setFont(nameFont);
        nameLabel.setForeground(NEUTRAL);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
        // Same reserved-2-line-height alignment fix as buildBonusCell, so this row's cells
        // line up visually with the bonus rows above/below it.
        FontMetrics nameMetrics = getFontMetrics(nameFont);
        int twoLineHeight = nameMetrics.getHeight() * 2;
        nameLabel.setPreferredSize(new Dimension(58, twoLineHeight));
        nameLabel.setMinimumSize(new Dimension(58, twoLineHeight));
        nameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, twoLineHeight));

        // Set on every sub-component, not just the cell itself - a JLabel child can
        // otherwise intercept the mouse and show no tooltip at all rather than the
        // parent's, depending on exactly where within the cell the cursor is.
        if (showTooltips && tooltipText != null)
        {
            cell.setToolTipText(tooltipText);
            iconLabel.setToolTipText(tooltipText);
            valueLabel.setToolTipText(tooltipText);
            nameLabel.setToolTipText(tooltipText);
        }

        cell.add(iconLabel);
        cell.add(Box.createVerticalStrut(2));
        cell.add(valueLabel);
        cell.add(Box.createVerticalStrut(1));
        cell.add(nameLabel);
        return cell;
    }

    private JPanel buildBonusCell(String label, int value, BufferedImage icon, boolean isPercentage)
    {
        return buildBonusCell(label, value, icon, isPercentage, null);
    }

    private JPanel buildBonusCell(String label, int value, BufferedImage icon, boolean isPercentage, String tooltipText)
    {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        cell.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (icon != null)
        {
            final int iconSize = 24;
            Image scaled = icon.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaled));
        }

        JLabel valueLabel = new JLabel((value > 0 ? "+" : "") + value + (isPercentage ? "%" : ""));
        valueLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        valueLabel.setForeground(value > 0 ? RARITY_COMMON : (value < 0 ? RARITY_RARE : NEUTRAL));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Font nameFont = new Font("Segoe UI", Font.PLAIN, 11);
        JLabel nameLabel = new JLabel("<html><div style='text-align:center;'>"
                + wrapTextManually(label, 58, nameFont) + "</div></html>");
        nameLabel.setFont(nameFont);
        nameLabel.setForeground(NEUTRAL);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
        // Reserves room for 2 lines always, regardless of whether this particular label
        // actually needs 1 or 2 lines - without this, a cell with a short single-line
        // label (e.g. "Strength") ends up shorter overall than one with a wrapped
        // two-line label (e.g. "Magic Dmg"), and centering each cell's content
        // independently (an earlier, wrong fix) shifted icons out of alignment with each
        // other instead of fixing it. A uniform reserved height keeps every cell's
        // internal structure identical, so icon+value naturally align across the row.
        FontMetrics nameMetrics = getFontMetrics(nameFont);
        int twoLineHeight = nameMetrics.getHeight() * 2;
        nameLabel.setPreferredSize(new Dimension(58, twoLineHeight));
        nameLabel.setMinimumSize(new Dimension(58, twoLineHeight));
        nameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, twoLineHeight));

        if (showTooltips && tooltipText != null)
        {
            cell.setToolTipText(tooltipText);
            iconLabel.setToolTipText(tooltipText);
            valueLabel.setToolTipText(tooltipText);
            nameLabel.setToolTipText(tooltipText);
        }

        cell.add(iconLabel);
        cell.add(Box.createVerticalStrut(2));
        cell.add(valueLabel);
        cell.add(Box.createVerticalStrut(1));
        cell.add(nameLabel);
        return cell;
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
        wireAccordionHeader(itemSourcesHeaderLabel, () -> npcDropsMode ? singleSectionLabel : "Item sources", () -> itemSourcesExpanded,
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
        Font dotsFont = FontManager.getRunescapeFont().deriveFont(16f);
        g.setFont(dotsFont);
        FontMetrics dotsMetrics = g.getFontMetrics(dotsFont);
        String dots = "...";
        int dotsX = (boxSize - dotsMetrics.stringWidth(dots)) / 2;
        int dotsY = (boxSize - dotsMetrics.getHeight()) / 2 + dotsMetrics.getAscent();
        g.drawString(dots, dotsX, dotsY);

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
     * Sanity ceiling for a single shop-sold price. No item in OSRS is legitimately sold by
     * a shop for anywhere near this much - anything above it is essentially certain to be
     * a wiki data error (one was found: "Tree (Draynor guard)" showing 10,000,000,000 gp
     * for Stew, when every other shop sells it for 20-24 gp) rather than a real price.
     */
    private static final long SHOP_PRICE_SANITY_CAP = 1_000_000_000L;

    /**
     * Adds comma thousand-separators to a shop price string (e.g. "750" -> "750",
     * "2500" -> "2,500"). Falls back to the raw string unchanged if it isn't a plain
     * integer (the wiki's price fields are usually clean numbers, but this avoids crashing
     * or mangling anything unexpected).
     */
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
     * A simple dimmed circle outline signaling "this icon is still loading" - used in NPC
     * drop rows while a specific item's icon is being resolved, before rows had per-icon
     * loading states this meant either a real icon or nothing at all. Deliberately static
     * rather than a true animated spinner (which would need a custom Icon plus a shared
     * Timer tracking every currently-visible label to repaint) - a reasonable amount of
     * complexity for what's ultimately a small polish detail.
     */
    private Icon createSmallSpinnerIcon()
    {
        final int size = 16;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x + 2, y + 2, size - 4, size - 4);
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
        // 13pt (from an earlier compacting pass) turned out too small to read comfortably.
        // Moving the item name beside the icon instead of below it freed up enough
        // vertical space that this can go back up to something more readable without
        // growing the panel taller overall.
        Font labelFont = FontManager.getRunescapeFont().deriveFont(15f);
        Font valueFont = FontManager.getRunescapeFont().deriveFont(15f);

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
        gcLabel.insets = new Insets(1, 0, 1, 8);

        GridBagConstraints gcValue = new GridBagConstraints();
        gcValue.gridx = 1;
        gcValue.gridy = row;
        gcValue.anchor = GridBagConstraints.NORTHWEST;
        gcValue.weightx = 1.0;
        gcValue.fill = GridBagConstraints.HORIZONTAL;
        gcValue.insets = new Insets(1, 0, 1, 0);

        infoTable.add(labelComp, gcLabel);
        infoTable.add(valueComp, gcValue);
    }

    private void addTableRow(int row, String label, String value)
    {
        addTableRow(row, label, value, null, null);
    }

    /**
     * Scales the header name label's font down for longer names, so it fits within the
     * fixed 110px wrap width without overflowing even after wrapping - "Thermonuclear
     * smoke devil" (25 characters) still didn't fit cleanly at the previous fixed 20f
     * size. Thresholds are a starting point based on character count, not a precise
     * pixel-width measurement, so may still need adjustment for other long names.
     */
    private Font headerNameFont(String name)
    {
        int length = name != null ? name.length() : 0;
        float size = length > 30 ? 10f : length > 24 ? 12f : length > 18 ? 16f : 20f;
        return FontManager.getRunescapeBoldFont().deriveFont(size);
    }

    public void showItem(String name, BufferedImage image, int price, int highAlch, int lowAlch)
    {
        ensureItemViewShown();
        nameLabel.setText("<html>" + wrapTextManually(name, 140, headerNameFont(name)) + "</html>");
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
            dropsContent.add(makeLoadingSpinner());
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
     * Convenience overload defaulting to "Drops" - used for the normal NPC case.
     */
    public void setNpcDropsMode(boolean npcMode)
    {
        setNpcDropsMode(npcMode, "Drops");
    }

    /**
     * @param label the outer accordion's text when npcMode is true - "Drops" for an NPC's
     *              own drop table, "Rewards" for a reward casket's contents. Ignored when
     *              npcMode is false (the outer accordion is "Item sources" in that case
     *              regardless).
     */
    public void setNpcDropsMode(boolean npcMode, String label)
    {
        this.npcDropsMode = npcMode;
        this.singleSectionLabel = label;
        dropsHeaderLabel.setVisible(!npcMode);
        updateAccordionHeader(itemSourcesHeaderLabel, npcMode ? singleSectionLabel : "Item sources", itemSourcesExpanded, itemSourcesHovering);
        dropsScrollPane.setVisible(npcMode ? itemSourcesExpanded : (itemSourcesExpanded && dropsExpanded));
        revalidate();
        repaint();
    }

    /**
     * Registers the callback fired when a name in the Drops list is clicked - the panel
     * itself has no access to game/client resources needed to actually resolve and display
     * the clicked item/NPC, so this is wired up once by the plugin at startup.
     */
    public void setDropRowClickListener(BiConsumer<String, String> listener)
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
     * Lets the plugin's drop-row click listener tell which lookup flow to use: a name
     * clicked while viewing an item's own drops is a monster name, while a name clicked
     * while viewing an NPC's own drops is an item name.
     */
    public boolean isNpcDropsMode()
    {
        return npcDropsMode;
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
     * Controls whether stat cells in the Combat Stats section get a hover tooltip
     * explaining what that stat means. Set once by the plugin at startup, and again
     * whenever the user changes the "Tooltips" config option live via onConfigChanged.
     * Tooltip text is applied once, at cell-build time in buildBonusCell/buildLevelCell,
     * so a live config change takes effect on the next item/NPC looked up - it doesn't
     * retroactively add or remove tooltips from whatever's already displayed.
     */
    public void setShowTooltips(boolean showTooltips)
    {
        this.showTooltips = showTooltips;
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
     * cropped screenshot, not a fetched sprite - there was no safe sprite constant for a
     * standard damage hitsplat to fetch instead).
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
        label.setToolTipText(expanded ? "Click to collapse" : "Click to expand");
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
        updateAccordionHeader(itemSourcesHeaderLabel, npcDropsMode ? singleSectionLabel : "Item sources", itemSourcesExpanded, itemSourcesHovering);
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
            dropsContent.add(makeLoadingSpinner());
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

    /**
     * A real loading indicator instead of static "Loading..." text - most noticeable on
     * NPC drop tables, where every unique item needs its own icon resolved before the
     * whole list renders at once, so this can sit on screen for a real, visible stretch
     * of time. Uses Swing's built-in indeterminate JProgressBar (animates on its own,
     * no custom timer/animation code needed) rather than hand-building a spinner.
     */
    private JComponent makeLoadingSpinner()
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        spinner.setForeground(GOLD);
        spinner.setBackground(new Color(255, 255, 255, 20));
        spinner.setBorderPainted(false);

        JLabel label = makeSourcesInfoLabel("Loading...");

        wrapper.add(spinner);
        wrapper.add(Box.createVerticalStrut(6));
        wrapper.add(label);
        return wrapper;
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
        setSourcesInternal(drops, shops, null, false);
    }

    /**
     * @param dropIcons item name -> icon, used only for NPC drop rows (where every row is
     *                  an item, so a matching icon makes sense). Item Sources' own drop
     *                  rows (monsters) keep using the no-icon overload above, since a
     *                  monster icon would need a much heavier per-row wiki image fetch
     *                  instead of the item-id-based lookup already built for navigation.
     */
    public void setSources(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops, Map<String, BufferedImage> dropIcons)
    {
        setSourcesInternal(drops, shops, dropIcons, false);
    }

    /**
     * Renders the drop rows immediately, with every icon slot showing a small loading
     * spinner instead of waiting for the whole batch to resolve first - the plugin then
     * calls updateDropIcon() individually as each one finishes, rather than one big wait
     * before anything appears at all.
     */
    public void setSourcesWithLoadingIcons(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops)
    {
        setSourcesInternal(drops, shops, null, true);
    }

    /**
     * Same as above, but with some icons already known upfront (e.g. from a previous
     * examine's cache) - those show immediately instead of a spinner, while anything not
     * yet in knownIcons still gets one.
     */
    public void setSourcesWithLoadingIcons(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops, Map<String, BufferedImage> knownIcons)
    {
        setSourcesInternal(drops, shops, knownIcons, true);
    }

    /**
     * Updates a single item's icon in place, once it resolves - finds every row currently
     * showing that item name (a drop table can list the same item multiple times, e.g.
     * "Coins" at several different quantities/rarities) and swaps its loading spinner for
     * the real icon, without touching or rebuilding any other row.
     */
    public void updateDropIcon(String itemName, BufferedImage icon)
    {
        List<JLabel> labels = dropIconLabels.get(itemName);
        if (labels == null || labels.isEmpty() || icon == null)
        {
            return;
        }
        final int iconBoxSize = 32;
        Image scaled = icon.getScaledInstance(iconBoxSize, iconBoxSize, Image.SCALE_SMOOTH);
        ImageIcon imageIcon = new ImageIcon(scaled);
        for (JLabel label : labels)
        {
            label.setIcon(imageIcon);
        }
        revalidate();
        repaint();
    }

    private void setSourcesInternal(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops, Map<String, BufferedImage> dropIcons, boolean iconsLoading)
    {
        cachedDrops = drops != null ? drops : new ArrayList<>();
        cachedShops = shops != null ? shops : new ArrayList<>();
        dropIconLabels.clear();

        dropsContent.removeAll();
        if (cachedDrops.isEmpty())
        {
            // Generalized from an NPC-specific message to use singleSectionLabel, so this
            // reads naturally for any single-source case, not just NPCs - "No known drop
            // sources" (the two-level Item Sources > Drops/Shops phrasing) reads oddly
            // here since in this mode the section IS the one list, with no separate Shops
            // to fall back to.
            String emptyMessage = npcDropsMode
                    ? "No " + singleSectionLabel.toLowerCase() + " available."
                    : "No known drop sources.";
            dropsContent.add(makeSourcesInfoLabel(emptyMessage));
        }
        else
        {
            for (ItemInfoClient.DropSource drop : cachedDrops)
            {
                BufferedImage icon = (dropIcons != null && drop.source != null) ? dropIcons.get(drop.source) : null;
                dropsContent.add(buildDropRow(drop, icon, iconsLoading));
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
    private JPanel buildDropRow(ItemInfoClient.DropSource drop, BufferedImage icon, boolean iconLoading)
    {
        JPanel row = new JPanel();
        // Icon+horizontal layout only applies to NPC drop rows (items), where an icon is
        // actually possible - monster rows (an item's own drop sources) never get one (see
        // loadNpcDropIconsAndDisplay's docs for why: no ID-based shortcut exists for
        // monster icons, only items), so they revert to the original plain vertical stack
        // rather than wasting a permanently-blank icon column.
        boolean showIcon = npcDropsMode;
        row.setLayout(new BoxLayout(row, showIcon ? BoxLayout.X_AXIS : BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 20)),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)
        ));

        if (showIcon)
        {
            // Fixed-size icon slot on the left, Loot Lookup style - kept blank (not hidden)
            // when this specific item's icon failed to resolve, so it still lines up with
            // sibling rows that did get one.
            JLabel iconLabel = new JLabel();
            final int iconBoxSize = 32;
            iconLabel.setPreferredSize(new Dimension(iconBoxSize, iconBoxSize));
            iconLabel.setMinimumSize(new Dimension(iconBoxSize, iconBoxSize));
            iconLabel.setMaximumSize(new Dimension(iconBoxSize, iconBoxSize));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
            if (icon != null)
            {
                Image scaled = icon.getScaledInstance(iconBoxSize, iconBoxSize, Image.SCALE_SMOOTH);
                iconLabel.setIcon(new ImageIcon(scaled));
            }
            else if (iconLoading)
            {
                // Small per-icon busy indicator instead of leaving the slot blank while
                // this specific item's icon is still resolving - rows render immediately
                // rather than the whole table waiting on the slowest icon, and this fills
                // the gap in the meantime. Swapped for the real icon (or left blank on
                // failure) via updateDropIcon() once the fetch actually completes.
                iconLabel.setIcon(createSmallSpinnerIcon());
            }
            if (drop.source != null)
            {
                dropIconLabels.computeIfAbsent(drop.source, k -> new ArrayList<>()).add(iconLabel);
            }
            row.add(iconLabel);
            row.add(Box.createHorizontalStrut(8));
        }

        JPanel textStack = new JPanel();
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.setOpaque(false);
        textStack.setAlignmentX(Component.LEFT_ALIGNMENT);
        textStack.setAlignmentY(Component.TOP_ALIGNMENT);

        // Wraps narrower when there's an icon column eating into the available width.
        int wrapWidth = showIcon ? 130 : 160;
        String rawName = drop.source != null ? drop.source : "Unknown";
        JLabel nameLabel = new JLabel("<html>" + wrapTextManually(rawName, wrapWidth, FontManager.getRunescapeFont()) + "</html>");
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (dropRowClickListener != null)
        {
            // A distinct default color (not plain white) signals this name is clickable
            // before the user even hovers over it - matches the classic "hyperlink blue"
            // convention, reusing the same light blue already used for "Always" rarity
            // rather than introducing a new arbitrary color.
            nameLabel.setForeground(ALWAYS_COLOR);
            nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nameLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    dropRowClickListener.accept(rawName, drop.level);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    nameLabel.setForeground(GOLD_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    nameLabel.setForeground(ALWAYS_COLOR);
                }
            });
        }
        else
        {
            nameLabel.setForeground(Color.WHITE);
        }
        textStack.add(nameLabel);

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

            textStack.add(Box.createVerticalStrut(2));
            textStack.add(levelLabel);
        }

        JLabel qtyLabel = new JLabel("Quantity: " + formatQuantity(drop.quantity));
        qtyLabel.setFont(FontManager.getRunescapeFont());
        qtyLabel.setForeground(Color.WHITE);
        qtyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textStack.add(Box.createVerticalStrut(4));
        textStack.add(qtyLabel);

        JLabel rarityLabel = buildRarityLabel(drop.rarity, wrapWidth);
        rarityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textStack.add(Box.createVerticalStrut(2));
        textStack.add(rarityLabel);

        if (showIcon)
        {
            row.add(textStack);
        }
        else
        {
            // No icon column, so the text stack's own components are added directly to
            // the row rather than nesting an extra panel - matches the original structure
            // exactly rather than just visually resembling it.
            for (Component c : textStack.getComponents())
            {
                row.add(c);
            }
        }

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
        String normalized = rawQuantity.replace("\u2013", "-").replace("\u2014", "-");

        // Quantities can be a single number ("2500") or a range ("1000-5000") - format
        // each numeric part with comma separators individually, rejoining with "-",
        // rather than trying to parse the whole string as one number.
        String[] parts = normalized.split("-");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                result.append("-");
            }
            String part = parts[i].trim();
            try
            {
                result.append(String.format("%,d", Integer.parseInt(part)));
            }
            catch (NumberFormatException e)
            {
                result.append(part);
            }
        }
        return result.toString();
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
            // A single space-delimited "word" can itself be wider than the whole
            // available line (e.g. a drop rate like "1/6,729.23" has no spaces inside it
            // to wrap at) - these were overflowing straight past the panel edge instead
            // of wrapping. Break it character-by-character in that case, rather than
            // relying on space-splitting alone.
            if (metrics.stringWidth(word) > maxWidthPx)
            {
                if (currentLine.length() > 0)
                {
                    if (result.length() > 0)
                    {
                        result.append("<br>");
                    }
                    result.append(escapeHtml(currentLine.toString()));
                    currentLine = new StringBuilder();
                }

                StringBuilder chunk = new StringBuilder();
                for (char ch : word.toCharArray())
                {
                    String candidateChunk = chunk.toString() + ch;
                    if (metrics.stringWidth(candidateChunk) > maxWidthPx && chunk.length() > 0)
                    {
                        if (result.length() > 0)
                        {
                            result.append("<br>");
                        }
                        result.append(escapeHtml(chunk.toString()));
                        chunk = new StringBuilder(String.valueOf(ch));
                    }
                    else
                    {
                        chunk.append(ch);
                    }
                }
                currentLine = chunk;
                continue;
            }

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
    private JLabel buildRarityLabel(String rarity, int wrapWidth)
    {
        String display = (rarity != null && !rarity.isEmpty()) ? rarity : "-";
        display = addThousandsCommas(display);

        // Bold variant at a slightly larger size, rather than the regular weight - the
        // extra stroke weight makes dense fraction numbers like "1/8,192" easier to read
        // while staying in the RuneScape font family.
        Font rarityFont = FontManager.getRunescapeBoldFont().deriveFont(15f);
        // No "Drop rate:" label anymore - the value alone is enough in context (this is
        // already inside a "Drops" section), and dropping the label frees up a full line
        // per row plus more width for the value itself, avoiding awkward wraps.
        String wrappedValue = wrapTextManually(display, wrapWidth, rarityFont);
        JLabel label = new JLabel("<html>" + wrappedValue + "</html>");
        label.setFont(rarityFont);
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
        // "5/128; 1/8,192" - dividing denominator by numerator normalizes "3/128" to the
        // same "1-in-N" scale as "1/N" so a 3/128 drop and a roughly-equivalent 1/43 drop
        // grade to the same colour band.
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
        nameLabel.setText("<html>" + wrapTextManually(name, 140, headerNameFont(name)) + "</html>");
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

        // Capped at 1.5x - unrestricted upscaling of these small pixel-art sprites was
        // what caused the pixelated look, but a hard 1.0x cap (no upscale at all) made
        // them look too small in the box. 1.5x is a middle ground: still noticeably
        // sharper than stretching all the way to fill 60px, without looking tiny.
        double scale = Math.min(1.5, Math.min((double) boxSize / origWidth, (double) boxSize / origHeight));
        int scaledWidth = Math.max(1, (int) (origWidth * scale));
        int scaledHeight = Math.max(1, (int) (origHeight * scale));

        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Bicubic interpolation for the image itself - drawing the original image
        // directly at the target size (rather than pre-scaling via the older
        // getScaledInstance() API first) avoids both a pixelated result and a subtle
        // centering mismatch that could happen when that API's returned image didn't
        // perfectly match the requested dimensions.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        // Subtle card-style background behind the item so smaller/odd-shaped sprites
        // still sit inside a clean, consistent frame.
        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        // Clip to the rounded frame so the image itself picks up rounded corners.
        g.setClip(frameShape);
        int x = (boxSize - scaledWidth) / 2;
        int y = (boxSize - scaledHeight) / 2;
        g.drawImage(image, x, y, scaledWidth, scaledHeight, null);
        g.setClip(null);

        // Soft border on top to finish the frame.
        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(canvas));
        scrollToTop();
    }

    /**
     * A simple rounded-rectangle outline border - Swing's built-in
     * BorderFactory.createMatteBorder/createLineBorder only draw sharp, square corners,
     * with no built-in option for rounded ones. Used for the "Report Issues or Support the
     * Developer" row specifically, at the user's request for a softer look than the
     * previous sharp-cornered box.
     */
    private static class RoundedLineBorder implements Border
    {
        private final Color color;
        private final int radius;

        RoundedLineBorder(Color color, int radius)
        {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c)
        {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }
    }
}