package com.jake219.quickwiki;

import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ItemInfoPanel extends PluginPanel implements Scrollable
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
    private final JLabel materialsHeaderLabel = new JLabel();
    private final JLabel combatStatsHeaderLabel = new JLabel();

    private final JPanel combatStatsContent = new WidthTrackingPanel();
    private JScrollPane combatScrollPane;
    private JScrollPane sourcesScrollPane;
    private static final int DROP_DISPLAY_CAP = 100;
    private static final int MIN_CARD_HEIGHT = 100;

    private final JPanel viewContainer = new JPanel(new BorderLayout(0, 10));
    private JPanel mainView;
    private JPanel iconNamePanel;
    private JPanel emptyStatePanel;
    private JPanel footerPanel;
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
    private JPanel priceGraphPanel;
    private Sparkline sparkline;
    private Ticker priceTicker;

    private static final String[] GRAPH_RANGES = {"1D", "1W", "1M", "1Y"};
    private final java.util.Map<String, JLabel> rangeButtons = new java.util.HashMap<>();
    private JLabel graphTitle;
    private String graphRange = "1M";
    private Runnable graphRangeListener;
    private JPanel propertiesPanel;
    private JPanel descriptionPanel;
    private JPanel descriptionContent;
    private final JPanel descriptionTail = new JPanel();
    private JPanel itemSourcesPanel;
    private JPanel dropsContent;
    private JPanel shopsContent;
    private JScrollPane descriptionScrollPane;

    private String lastFullDescription = "";

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

    private boolean npcDropsMode = false;

    private String singleSectionLabel = "Drops";

    private BiConsumer<String, String> dropRowClickListener;
    private java.util.function.Consumer<String> materialClickListener;
    private final Map<String, List<JLabel>> materialIconLabels = new HashMap<>();
    private BufferedImage facilityIcon;

    private Runnable backButtonListener;
    private boolean itemSourcesHovering = false;
    private boolean dropsExpanded = false;
    private boolean dropsHovering = false;
    private boolean shopsExpanded = false;
    private boolean shopsHovering = false;

    private boolean sourcesRequested = false;

    private List<ItemInfoClient.DropSource> cachedDrops;

    private final Map<String, List<JLabel>> dropIconLabels = new HashMap<>();
    private List<ItemInfoClient.ShopSource> cachedShops;

    private int playerCombatLevel = -1;
    private boolean showTooltips = true;

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
    private Icon realTradeableIcon;
    private Icon realEquipableIcon;
    private Icon realStackableIcon;

    private Runnable sourcesLoader;

    private static final Color GOLD = new Color(224, 168, 58);
    private static final Color GOLD_HOVER = new Color(240, 195, 110);
    private static final Color BLUE = new Color(90, 184, 224);
    private static final Color GREEN = new Color(90, 214, 130);
    private static final Color RED = new Color(214, 100, 90);
    private static final Color NEUTRAL = new Color(160, 160, 160);
    private static final Color NEUTRAL_HOVER = new Color(215, 215, 215);

    private static final Color ORANGE = new Color(255, 152, 31);
    private static final Color BG_WARM = new Color(33, 30, 25);
    private static final Color CARD_BG = new Color(43, 40, 33);
    private static final Color CARD_BORDER = new Color(60, 54, 42);
    private static final Color METHOD_BG = new Color(52, 46, 36);
    private static final Color TAB_BG = new Color(27, 24, 19);
    private static final Color TAB_ACTIVE_BG = new Color(52, 45, 33);
    private static final Color TAB_INACTIVE_FG = new Color(150, 137, 106);

    private static final String TAB_STATS = "STATS";
    private static final String TAB_COMBAT = "COMBAT";
    private static final String TAB_SOURCES = "SOURCES";
    private static final String TAB_INFO = "INFO";

    private JPanel tabBar;
    private JPanel contentHolder;
    private final JLabel priceSubLabel = new JLabel();
    private String currentTab = TAB_STATS;
    private boolean combatTabVisible = true;
    private boolean sourcesTabVisible = false;
    private JPanel dropsCard;
    private JPanel shopsCard;
    private JPanel materialsCard;
    private JPanel materialsContent;
    private final Map<String, JComponent> tabContent = new HashMap<>();
    private final Map<String, JLabel> tabButtons = new HashMap<>();

    public ItemInfoPanel()
    {
        setLayout(new BorderLayout());

        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(25f));
        nameLabel.setForeground(GOLD);

        descriptionHeaderLabel.setFont(FontManager.getRunescapeBoldFont());
        descriptionHeaderLabel.setForeground(new Color(150, 150, 150));
        descriptionHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        descriptionArea.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        descriptionArea.setForeground(Color.WHITE);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(255, 255, 255, 40)),
                BorderFactory.createEmptyBorder(0, 8, 0, 0)
        ));

        readMoreLabel.setFont(FontManager.getRunescapeFont().deriveFont(16f));
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

        backToTopLabel.setFont(FontManager.getRunescapeFont());
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

        backButtonLabel.setFont(FontManager.getRunescapeFont().deriveFont(16f));
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
        wikiLinkLabel.setFont(FontManager.getRunescapeFont().deriveFont(16f));
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

        iconNamePanel = new JPanel();
        iconNamePanel.setLayout(new BoxLayout(iconNamePanel, BoxLayout.Y_AXIS));
        iconNamePanel.setOpaque(false);
        iconNamePanel.add(topRow);
        iconNamePanel.add(Box.createVerticalStrut(6));

        JPanel iconNameRow = new JPanel();
        iconNameRow.setLayout(new BoxLayout(iconNameRow, BoxLayout.X_AXIS));
        iconNameRow.setOpaque(false);
        iconNameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

        JPanel nameStack = new JPanel();
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        nameStack.setOpaque(false);
        nameStack.setAlignmentY(Component.CENTER_ALIGNMENT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceSubLabel.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        priceSubLabel.setForeground(GOLD);
        priceSubLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameStack.add(nameLabel);
        nameStack.add(priceSubLabel);

        iconNameRow.add(iconLabel);
        iconNameRow.add(Box.createHorizontalStrut(10));
        iconNameRow.add(nameStack);

        iconNamePanel.add(iconNameRow);

        priceTicker = new Ticker();
        priceTicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceTicker.setVisible(false);
        iconNamePanel.add(Box.createVerticalStrut(5));
        iconNamePanel.add(priceTicker);

        iconNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconNamePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        infoTable = new JPanel();
        infoTable.setLayout(new GridBagLayout());
        infoTable.setOpaque(false);
        infoTable.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoTable.setVisible(false);

        sparkline = new Sparkline();
        sparkline.setAlignmentX(Component.LEFT_ALIGNMENT);

        sparkline.setPreferredHeight(210);
        sparkline.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        graphTitle = new JLabel("PAST MONTH");
        graphTitle.setFont(FontManager.getRunescapeSmallFont());
        graphTitle.setForeground(TAB_INACTIVE_FG);
        graphTitle.setVerticalAlignment(SwingConstants.CENTER);

        JPanel graphHeader = new JPanel(new BorderLayout());
        graphHeader.setOpaque(false);
        graphHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        graphHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        graphHeader.add(graphTitle, BorderLayout.WEST);
        graphHeader.add(buildGraphRangeBar(), BorderLayout.EAST);

        priceGraphPanel = new JPanel();
        priceGraphPanel.setLayout(new BoxLayout(priceGraphPanel, BoxLayout.Y_AXIS));
        priceGraphPanel.setOpaque(false);
        priceGraphPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceGraphPanel.add(createDivider());
        priceGraphPanel.add(Box.createVerticalStrut(7));
        priceGraphPanel.add(graphHeader);
        priceGraphPanel.add(Box.createVerticalStrut(5));
        priceGraphPanel.add(sparkline);
        priceGraphPanel.setVisible(false);

        JPanel graphWrapper = new JPanel(new BorderLayout());
        graphWrapper.setOpaque(false);
        graphWrapper.add(priceGraphPanel, BorderLayout.NORTH);

        JPanel propertiesBody = new JPanel(new BorderLayout(0, 10));
        propertiesBody.setOpaque(false);
        propertiesBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        propertiesBody.add(infoTable, BorderLayout.NORTH);
        propertiesBody.add(graphWrapper, BorderLayout.CENTER);

        propertiesPanel = cardFilling(propertiesHeaderLabel, "Properties", propertiesBody);

        itemSourcesPanel = buildItemSourcesSection();

        JPanel actionsRow = new JPanel(new BorderLayout());
        actionsRow.setOpaque(false);
        actionsRow.add(readMoreLabel, BorderLayout.WEST);
        actionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        descriptionScrollPane = new JScrollPane(descriptionArea);
        descriptionScrollPane.setOpaque(false);
        descriptionScrollPane.getViewport().setOpaque(false);
        descriptionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        descriptionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(descriptionScrollPane);
        descriptionScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Small preferred height so the description fills the space under the graph and scrolls
        // internally, keeping the panel viewport-sized and the footer pinned.
        descriptionScrollPane.setPreferredSize(new Dimension(10, 90));

        descriptionContent = new JPanel();
        descriptionContent.setLayout(new BoxLayout(descriptionContent, BoxLayout.Y_AXIS));
        descriptionContent.setOpaque(false);
        descriptionContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionContent.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        descriptionContent.add(descriptionScrollPane);
        descriptionContent.add(Box.createVerticalStrut(6));
        descriptionContent.add(actionsRow);

        descriptionTail.setOpaque(false);
        descriptionTail.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionTail.setPreferredSize(new Dimension(0, 0));
        descriptionContent.add(descriptionTail);

        descriptionPanel = cardFilling(descriptionHeaderLabel, "Description", descriptionContent);

        // The Info tab holds the properties + price graph on top and the description below it,
        // filling the rest and scrolling on its own.
        JPanel infoTab = new JPanel(new BorderLayout(0, 10));
        infoTab.setOpaque(false);
        infoTab.add(propertiesPanel, BorderLayout.NORTH);
        infoTab.add(descriptionPanel, BorderLayout.CENTER);

        tabContent.put(TAB_STATS, infoTab);
        tabContent.put(TAB_COMBAT, buildCombatStatsSection());
        tabContent.put(TAB_SOURCES, itemSourcesPanel);

        tabBar = new JPanel();
        tabBar.setOpaque(false);
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        contentHolder = new JPanel(new BorderLayout());
        contentHolder.setOpaque(false);

        mainView = new JPanel(new BorderLayout(0, 8));
        mainView.setOpaque(false);
        mainView.add(tabBar, BorderLayout.NORTH);
        mainView.add(contentHolder, BorderLayout.CENTER);

        refreshTabBar();
        selectTab(TAB_STATS);

        emptyStatePanel = buildEmptyStatePanel();
        footerPanel = buildFooter();

        viewContainer.setOpaque(true);
        viewContainer.setBackground(BG_WARM);
        viewContainer.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        viewContainer.add(emptyStatePanel, BorderLayout.CENTER);
        add(viewContainer, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight()
    {
        Container parent = getParent();
        if (parent instanceof JViewport)
        {
            return parent.getHeight() > getPreferredSize().height;
        }
        return false;
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension size = super.getPreferredSize();
        Container c = getParent();
        while (c != null && !(c instanceof JViewport))
        {
            c = c.getParent();
        }
        if (c != null && c.getHeight() > size.height)
        {
            return new Dimension(size.width, c.getHeight());
        }
        return size;
    }

    private static final String PLUGIN_REPO_URL = "https://github.com/jake219/quick-wiki/blob/main/README.md";
    private static final String PLUGIN_VERSION = "2.0.2";

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

        JComponent openWiki = footerButton("Open Wiki  ↗", false,
                () -> openInBrowser("https://oldschool.runescape.wiki/"));
        openWiki.setAlignmentX(Component.CENTER_ALIGNMENT);
        openWiki.setMaximumSize(new Dimension(230, 40));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(48, 16, 16, 16));

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(version);
        panel.add(Box.createVerticalStrut(10));
        panel.add(body);
        panel.add(Box.createVerticalStrut(18));
        panel.add(openWiki);
        panel.add(Box.createVerticalStrut(18));
        panel.add(buildSupportRow());
        return panel;
    }


    private JPanel buildSupportRow()
    {
        JLabel icon = new JLabel(createInfoIcon(NEUTRAL));
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        icon.setVerticalAlignment(SwingConstants.TOP);

        Font textFont = FontManager.getRunescapeFont();
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

    private JPanel buildFooter()
    {
        JPanel footer = new JPanel(new BorderLayout(6, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 4));
        footer.add(homeFooterButton(), BorderLayout.WEST);
        footer.add(footerButton("Open Wiki  ↗", false,
                () -> {
                    if (currentWikiPageName != null)
                    {
                        openInBrowser(officialWikiUrl(currentWikiPageName));
                    }
                }), BorderLayout.CENTER);
        footer.setVisible(false);
        return footer;
    }

    private JComponent homeFooterButton()
    {
        RoundedPanel button = new RoundedPanel(new Color(52, 45, 33), CARD_BORDER, 8);
        button.setLayout(new BorderLayout());
        button.setBorder(BorderFactory.createEmptyBorder(7, 11, 7, 11));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Back to the Quick Wiki menu");

        JLabel label = new JLabel(createHomeIcon(GOLD));
        button.add(label, BorderLayout.CENTER);

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                showHome();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                label.setIcon(createHomeIcon(GOLD_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                label.setIcon(createHomeIcon(GOLD));
            }
        });
        return button;
    }

    private Icon createHomeIcon(Color color)
    {
        final int size = 15;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                Polygon roof = new Polygon();
                roof.addPoint(x + size / 2, y + 1);
                roof.addPoint(x + 1, y + 7);
                roof.addPoint(x + size - 1, y + 7);
                g2.fillPolygon(roof);

                g2.fillRect(x + 3, y + 7, size - 6, size - 8);

                g2.setColor(color.darker().darker());
                g2.fillRect(x + size / 2 - 1, y + 10, 3, size - 11);
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

    private JComponent footerButton(String text, boolean compact, Runnable onClick)
    {
        RoundedPanel button = new RoundedPanel(new Color(52, 45, 33), CARD_BORDER, 8);
        button.setLayout(new BorderLayout());
        button.setBorder(BorderFactory.createEmptyBorder(7, compact ? 10 : 12, 7, compact ? 10 : 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(GOLD);
        button.add(label, BorderLayout.CENTER);

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                label.setForeground(GOLD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                label.setForeground(GOLD);
            }
        });
        return button;
    }

    private JPanel buildGraphRangeBar()
    {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        bar.setOpaque(false);
        for (String r : GRAPH_RANGES)
        {
            JLabel b = makeRangeButton(r);
            rangeButtons.put(r, b);
            bar.add(b);
        }
        styleRangeButtons();
        return bar;
    }

    private JLabel makeRangeButton(String range)
    {
        JLabel b = new JLabel(range, SwingConstants.CENTER);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                setGraphRange(range);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (!range.equals(graphRange))
                {
                    b.setForeground(GOLD_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                styleRangeButtons();
            }
        });
        return b;
    }

    private void styleRangeButtons()
    {
        for (java.util.Map.Entry<String, JLabel> e : rangeButtons.entrySet())
        {
            boolean active = e.getKey().equals(graphRange);
            e.getValue().setForeground(active ? ORANGE : TAB_INACTIVE_FG);
        }
    }

    public String getGraphRange()
    {
        return graphRange;
    }

    public void setGraphRangeListener(Runnable listener)
    {
        this.graphRangeListener = listener;
    }

    private void setGraphRange(String range)
    {
        if (range == null || range.equals(graphRange))
        {
            return;
        }
        graphRange = range;
        styleRangeButtons();
        if (graphTitle != null)
        {
            graphTitle.setText(graphRangeTitle(range));
        }
        if (graphRangeListener != null)
        {
            graphRangeListener.run();
        }
    }

    private String graphRangeTitle(String range)
    {
        switch (range)
        {
            case "1D":
                return "PAST DAY";
            case "1W":
                return "PAST WEEK";
            case "1Y":
                return "PAST YEAR";
            case "1M":
            default:
                return "PAST MONTH";
        }
    }

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
            /*x*/;
        }
    }

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

    public void setWikiPageName(String pageName)
    {
        this.currentWikiPageName = pageName;
        wikiLinkLabel.setVisible(pageName != null);
        if (footerPanel != null)
        {
            footerPanel.setVisible(pageName != null);
        }
    }

    public void setMarket(ItemInfoClient.Market m)
    {
        java.util.List<Integer> prices = m != null ? m.prices : null;
        java.util.List<Long> timestamps = m != null ? m.timestamps : null;
        boolean hasData = prices != null && prices.size() >= 2;

        sparkline.setData(hasData ? prices : null, hasData ? timestamps : null);
        priceGraphPanel.setVisible(hasData);

        java.util.List<Ticker.Seg> segs = new ArrayList<>();
        if (m != null)
        {
            addChangeSeg(segs, "1D", m.change1D);
            addChangeSeg(segs, "1W", m.change1W);
            addChangeSeg(segs, "1M", m.change1M);
            addChangeSeg(segs, "1Y", m.change1Y);
        }
        if (!segs.isEmpty())
        {
            priceTicker.setSegments(segs);
            priceTicker.setVisible(true);
        }
        else
        {
            priceTicker.setSegments(null);
            priceTicker.setVisible(false);
        }

        if (pendingPrice <= 0 && m != null)
        {
            Integer p = (m.instaBuy != null && m.instaSell != null)
                    ? Math.max(m.instaBuy, m.instaSell)
                    : (m.instaBuy != null ? m.instaBuy : m.instaSell);
            if (p != null)
            {
                priceSubLabel.setIcon(coinIcon());
                priceSubLabel.setIconTextGap(5);
                priceSubLabel.setText(formatPrice(p) + " gp");
            }
        }

        revalidate();
        repaint();
    }

    private void addChangeSeg(java.util.List<Ticker.Seg> segs, String label, Double pct)
    {
        if (pct != null)
        {
            Ticker.Seg s = new Ticker.Seg();
            s.label = label;
            s.pct = pct;
            segs.add(s);
        }
    }

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

    private void ensureItemViewShown()
    {
        if (!hasShownFirstItem)
        {
            hasShownFirstItem = true;
            viewContainer.removeAll();
            viewContainer.add(iconNamePanel, BorderLayout.NORTH);
            viewContainer.add(mainView, BorderLayout.CENTER);
            viewContainer.add(footerPanel, BorderLayout.SOUTH);
            revalidate();
            repaint();
        }
    }

    public void showHome()
    {
        hasShownFirstItem = false;
        setBackButtonVisible(false);
        viewContainer.removeAll();
        viewContainer.add(emptyStatePanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setCombatStatsAvailable(Runnable loader)
    {
        combatStatsLoader = loader;
        combatStatsRequested = false;
        combatStatsContent.removeAll();

        if (TAB_COMBAT.equals(currentTab) && combatTabVisible)
        {
            ensureCombatLoaded();
        }
        revalidate();
        repaint();
    }

    public void setCombatStatsSectionVisible(boolean visible)
    {
        combatTabVisible = visible;
        refreshTabBar();

        if (!visible && TAB_COMBAT.equals(currentTab))
        {
            selectTab(TAB_STATS);
        }
        revalidate();
        repaint();
    }

    private JPanel buildCombatStatsSection()
    {
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
        combatStatsContent.setVisible(true);

        combatScrollPane = new JScrollPane(combatStatsContent);
        combatScrollPane.setOpaque(false);
        combatScrollPane.getViewport().setOpaque(false);
        combatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        combatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        combatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        combatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(combatScrollPane);
        combatScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        combatScrollPane.setPreferredSize(new Dimension(10, 90));
        combatScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        return cardFilling(combatStatsHeaderLabel, "Combat Stats", combatScrollPane);
    }

    private JLabel makeSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        label.setForeground(GOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public void displayCombatBonuses(ItemInfoClient.CombatBonuses bonuses, Map<String, BufferedImage> skillIcons)
    {
        combatStatsContent.removeAll();

        if (bonuses == null)
        {
            JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
            noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
            combatStatsContent.add(noCombatStatsLabel);
        }
        else
        {
            JPanel attack = grid3();
            attack.add(buildBonusCell("Stab", bonuses.stabAttack, skillIcons.get("attack"), false, "Increases accuracy for stab attacks"));
            attack.add(buildBonusCell("Slash", bonuses.slashAttack, skillIcons.get("attack"), false, "Increases accuracy for slash attacks"));
            attack.add(buildBonusCell("Crush", bonuses.crushAttack, skillIcons.get("attack"), false, "Increases accuracy for crush attacks"));
            attack.add(buildBonusCell("Magic", bonuses.magicAttack, skillIcons.get("magic"), false, "Increases accuracy for magic attacks"));
            attack.add(buildBonusCell("Ranged", bonuses.rangeAttack, skillIcons.get("ranged"), false, "Increases accuracy for ranged attacks"));

            JPanel defence = grid3();
            defence.add(buildBonusCell("Stab", bonuses.stabDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by stab attacks"));
            defence.add(buildBonusCell("Slash", bonuses.slashDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by slash attacks"));
            defence.add(buildBonusCell("Crush", bonuses.crushDefence, skillIcons.get("defence"), false, "Reduces chance of being hit by crush attacks"));
            defence.add(buildBonusCell("Magic", bonuses.magicDefence, skillIcons.get("magic"), false, "Reduces chance of being hit by magic attacks"));
            defence.add(buildBonusCell("Ranged", bonuses.rangeDefence, skillIcons.get("ranged"), false, "Reduces chance of being hit by ranged attacks"));

            JPanel other = grid3();
            other.add(buildBonusCell("Strength", bonuses.strength, skillIcons.get("strength"), false, "Increases max hit with melee weapons"));
            other.add(buildBonusCell("Ranged Str", bonuses.rangedStrength, skillIcons.get("ranged"), false, "Increases max hit with ranged weapons"));
            other.add(buildBonusCell("Magic Dmg", bonuses.magicDamage, skillIcons.get("magic"), true, "Increases damage dealt by magic spells"));
            other.add(buildBonusCell("Prayer", bonuses.prayer, skillIcons.get("prayer"), false, "Increases max Prayer points while worn"));

            addCombatSection("Attack bonuses", attack);
            addCombatSection("Defence bonuses", defence);
            addCombatSection("Other bonuses", other);
        }

        revalidate();
        repaint();
    }

    private JPanel grid3()
    {
        JPanel p = new JPanel(new GridLayout(0, 3, 6, 8));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }

    private void addCombatSection(String title, JPanel grid)
    {
        if (combatStatsContent.getComponentCount() > 0)
        {
            combatStatsContent.add(Box.createVerticalStrut(8));
            combatStatsContent.add(createDivider());
            combatStatsContent.add(Box.createVerticalStrut(8));
        }
        combatStatsContent.add(makeSectionLabel(title));
        combatStatsContent.add(Box.createVerticalStrut(5));
        combatStatsContent.add(grid);
    }

    public void displayNpcCombatStats(ItemInfoClient.NpcCombatStats stats, Map<String, BufferedImage> skillIcons)
    {
        combatStatsContent.removeAll();

        if (stats == null)
        {
            JLabel noCombatStatsLabel = makeSourcesInfoLabel("No combat stats available.");
            noCombatStatsLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
            combatStatsContent.add(noCombatStatsLabel);
        }
        else
        {
            JPanel levels = grid3();
            levels.add(buildLevelCell("HP", stats.hitpoints, skillIcons.get("hitpoints"), "Hitpoints - how much damage this monster can take before dying"));
            levels.add(buildLevelCell("Attack", stats.attackLevel, skillIcons.get("attack"), "Attack level - affects this monster's melee accuracy"));
            levels.add(buildLevelCell("Strength", stats.strengthLevel, skillIcons.get("strength"), "Strength level - affects this monster's melee max hit"));
            levels.add(buildLevelCell("Defence", stats.defenceLevel, skillIcons.get("defence"), "Defence level - affects how often this monster is hit"));
            levels.add(buildLevelCell("Magic", stats.magicLevel, skillIcons.get("magic"), "Magic level - affects this monster's magic accuracy and max hit"));
            levels.add(buildLevelCell("Ranged", stats.rangedLevel, skillIcons.get("ranged"), "Ranged level - affects this monster's ranged accuracy and max hit"));

            JPanel aggressive = grid3();
            aggressive.add(buildBonusCell("Attack", stats.attackBonus, skillIcons.get("attack"), false, "Increases this monster's melee accuracy"));
            aggressive.add(buildBonusCell("Strength", stats.strengthBonus, skillIcons.get("strength"), false, "Increases this monster's melee max hit"));
            aggressive.add(buildBonusCell("Magic", stats.magicAttackBonus, skillIcons.get("magic"), false, "Increases this monster's magic accuracy"));
            aggressive.add(buildBonusCell("Magic Dmg", stats.magicDamageBonus, skillIcons.get("magic"), true, "Increases this monster's magic max hit"));
            aggressive.add(buildBonusCell("Ranged", stats.rangeAttackBonus, skillIcons.get("ranged"), false, "Increases this monster's ranged accuracy"));
            aggressive.add(buildBonusCell("Ranged Str", stats.rangedStrengthBonus, skillIcons.get("ranged"), false, "Increases this monster's ranged max hit"));

            JPanel melee = grid3();
            melee.add(buildBonusCell("Stab", stats.stabDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by stab attacks"));
            melee.add(buildBonusCell("Slash", stats.slashDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by slash attacks"));
            melee.add(buildBonusCell("Crush", stats.crushDefenceBonus, skillIcons.get("defence"), false, "Reduces chance of being hit by crush attacks"));

            String weaknessLabel = (stats.elementalWeaknessType == null || stats.elementalWeaknessType.trim().isEmpty())
                    ? "No Weakness"
                    : stats.elementalWeaknessType.trim().substring(0, 1).toUpperCase()
                      + stats.elementalWeaknessType.trim().substring(1).toLowerCase() + " Weakness";
            JPanel magic = grid3();
            magic.add(buildBonusCell("Magic", stats.magicDefenceBonus, skillIcons.get("magic"), false, "Reduces chance of being hit by magic attacks"));
            magic.add(buildBonusCell(weaknessLabel, stats.elementalWeaknessPercent, skillIcons.get("elemental_weakness"), true, "Extra damage taken from this element's spells"));

            JPanel ranged = grid3();
            ranged.add(buildBonusCell("Light", stats.lightRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by darts and similar light ammo"));
            ranged.add(buildBonusCell("Standard", stats.standardRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by arrows and similar standard ammo"));
            ranged.add(buildBonusCell("Heavy", stats.heavyRangeDefenceBonus, skillIcons.get("ranged"), false, "Reduces chance of being hit by bolts and similar heavy ammo"));

            addCombatSection("Combat stats", levels);
            addCombatSection("Aggressive stats", aggressive);
            addCombatSection("Melee defence", melee);
            addCombatSection("Magic defence", magic);
            addCombatSection("Ranged defence", ranged);
        }

        revalidate();
        repaint();
    }

    private JPanel buildBonusCell(String label, int value, BufferedImage icon)
    {
        return buildBonusCell(label, value, icon, false, null);
    }

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

        Font nameFont = FontManager.getRunescapeFont();
        JLabel nameLabel = new JLabel("<html><div style='text-align:center;'>"
                + wrapTextManually(label, 58, nameFont) + "</div></html>");
        nameLabel.setFont(nameFont);
        nameLabel.setForeground(NEUTRAL);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
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

        Font nameFont = FontManager.getRunescapeFont();
        JLabel nameLabel = new JLabel("<html><div style='text-align:center;'>"
                + wrapTextManually(label, 58, nameFont) + "</div></html>");
        nameLabel.setFont(nameFont);
        nameLabel.setForeground(NEUTRAL);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
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

    private JComponent createDivider()
    {
        JPanel divider = new JPanel();
        divider.setBackground(new Color(255, 255, 255, 35));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(10, 1));
        return divider;
    }

    private JPanel buildItemSourcesSection()
    {
        dropsContent = new JPanel();
        dropsContent.setLayout(new BoxLayout(dropsContent, BoxLayout.Y_AXIS));
        dropsContent.setOpaque(false);
        dropsContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        shopsContent = new JPanel();
        shopsContent.setLayout(new BoxLayout(shopsContent, BoxLayout.Y_AXIS));
        shopsContent.setOpaque(false);
        shopsContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        materialsContent = new WidthTrackingPanel();
        materialsContent.setLayout(new BoxLayout(materialsContent, BoxLayout.Y_AXIS));
        materialsContent.setOpaque(false);
        materialsContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        materialsContent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        // The three sections SHARE the panel height. The stack is sized to the viewport, and the
        // EqualShareLayout splits that height into equal parts - so when all three are full they
        // each get a third and scroll internally, with no outer scrollbar. A section that needs
        // less than its share just takes what it needs and hands the surplus back to the others,
        // so short sections aren't stretched into blank boxes and leftover space falls to the
        // bottom.
        JScrollPane materialsScroll = makeSourcesScroll(materialsContent);
        JScrollPane dropsScroll = makeSourcesScroll(dropsContent);
        JScrollPane shopsScroll = makeSourcesScroll(shopsContent);

        materialsCard = buildCollapsibleCard(materialsHeaderLabel, "Creation", materialsScroll);
        dropsCard = buildCollapsibleCard(dropsHeaderLabel, "Drops", dropsScroll);
        shopsCard = buildCollapsibleCard(shopsHeaderLabel, "Shops", shopsScroll);
        materialsCard.setVisible(false);

        SharedHeightPanel stack = new SharedHeightPanel();
        stack.setLayout(new EqualShareLayout(10));
        stack.setOpaque(false);
        stack.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(materialsCard);
        stack.add(dropsCard);
        stack.add(shopsCard);

        sourcesScrollPane = new JScrollPane(stack);
        sourcesScrollPane.setOpaque(false);
        sourcesScrollPane.getViewport().setOpaque(false);
        sourcesScrollPane.setBorder(BorderFactory.createEmptyBorder());
        sourcesScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sourcesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sourcesScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        enableScrollChaining(sourcesScrollPane);
        sourcesScrollPane.setPreferredSize(new Dimension(10, 120));

        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.add(sourcesScrollPane, BorderLayout.CENTER);
        return section;
    }

    private JScrollPane makeSourcesScroll(JComponent content)
    {
        JScrollPane sp = new JScrollPane(content);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        // Small minimum so the card can shrink and this pane scrolls its overflow internally.
        sp.setMinimumSize(new Dimension(10, 10));
        enableScrollChaining(sp);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sp;
    }

    public void setMaterialClickListener(java.util.function.Consumer<String> listener)
    {
        this.materialClickListener = listener;
    }

    public void setFacilityIcon(BufferedImage icon)
    {
        this.facilityIcon = icon;
    }

    public void setCreation(List<ItemInfoClient.RecipeData> recipes)
    {
        materialsContent.removeAll();
        materialIconLabels.clear();

        List<ItemInfoClient.RecipeData> methods = new ArrayList<>();
        if (recipes != null)
        {
            for (ItemInfoClient.RecipeData r : recipes)
            {
                if (r == null)
                {
                    continue;
                }
                boolean any = (r.materials != null && !r.materials.isEmpty())
                        || (r.requirements != null && !r.requirements.isEmpty())
                        || (r.facility != null && !r.facility.isEmpty());
                if (any)
                {
                    methods.add(r);
                }
            }
        }

        if (methods.isEmpty())
        {
            materialsCard.setVisible(false);
            materialsContent.revalidate();
            materialsContent.repaint();
            return;
        }

        boolean multiple = methods.size() > 1;
        for (int i = 0; i < methods.size(); i++)
        {
            ItemInfoClient.RecipeData method = methods.get(i);
            if (multiple)
            {
                if (i > 0)
                {
                    materialsContent.add(Box.createVerticalStrut(8));
                }
                String header = method.name != null && !method.name.isEmpty()
                        ? method.name
                        : "Method " + (i + 1);
                materialsContent.add(buildMethodBox(header, method));
            }
            else
            {
                addMethodRows(materialsContent, method);
            }
        }

        materialsCard.setVisible(true);
        materialsContent.revalidate();
        materialsContent.repaint();
        revalidate();
        repaint();
    }

    private JPanel buildMethodBox(String title, ItemInfoClient.RecipeData method)
    {
        RoundedPanel box = new RoundedPanel(METHOD_BG, CARD_BORDER, 8);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JLabel header = new JLabel(title);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setForeground(ORANGE);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        box.add(header);
        box.add(Box.createVerticalStrut(6));

        addMethodRows(box, method);

        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        return box;
    }

    private void addMethodRows(JPanel target, ItemInfoClient.RecipeData method)
    {
        boolean hasReqs = method.requirements != null && !method.requirements.isEmpty();
        boolean headerIsFacility = method.name != null && method.facility != null
                && method.name.equalsIgnoreCase(method.facility);
        boolean hasFacility = method.facility != null && !method.facility.isEmpty() && !headerIsFacility;
        boolean hasMats = method.materials != null && !method.materials.isEmpty();

        if (hasReqs || hasFacility)
        {
            target.add(makeCreationSubHeader("Requirements"));
            target.add(Box.createVerticalStrut(3));
            if (hasReqs)
            {
                for (ItemInfoClient.SkillReq req : method.requirements)
                {
                    if (req == null || req.skill == null || req.skill.isEmpty())
                    {
                        continue;
                    }
                    target.add(buildRequirementRow(req));
                }
            }
            if (hasFacility)
            {
                target.add(buildFacilityRow(method.facility));
            }
            if (hasMats)
            {
                target.add(Box.createVerticalStrut(9));
            }
        }

        if (hasMats)
        {
            target.add(makeCreationSubHeader("Materials"));
            target.add(Box.createVerticalStrut(3));
            for (ItemInfoClient.Material material : method.materials)
            {
                if (material == null || material.name == null || material.name.isEmpty())
                {
                    continue;
                }
                target.add(buildMaterialRow(material));
            }
        }
    }

    private JPanel buildRequirementRow(ItemInfoClient.SkillReq req)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

        final int iconBox = 22;
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(iconBox, iconBox));
        iconLabel.setMinimumSize(new Dimension(iconBox, iconBox));
        iconLabel.setMaximumSize(new Dimension(iconBox, iconBox));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        if (req.skillIcon != null)
        {
            Image scaled = req.skillIcon.getScaledInstance(iconBox, iconBox, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaled));
        }
        row.add(iconLabel);
        row.add(Box.createHorizontalStrut(8));

        JLabel nameLabel = new JLabel(req.skill);
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(nameLabel);

        row.add(Box.createHorizontalGlue());

        if (req.level != null && !req.level.isEmpty())
        {
            JLabel levelLabel = new JLabel("Lvl " + req.level);
            levelLabel.setFont(FontManager.getRunescapeFont());
            levelLabel.setForeground(GOLD);
            levelLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            row.add(levelLabel);
        }

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private JLabel makeCreationSubHeader(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(GOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }


    private JPanel buildFacilityRow(String facility)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

        final int iconBox = 22;
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(iconBox, iconBox));
        iconLabel.setMinimumSize(new Dimension(iconBox, iconBox));
        iconLabel.setMaximumSize(new Dimension(iconBox, iconBox));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        if (facilityIcon != null)
        {
            Image scaled = facilityIcon.getScaledInstance(iconBox, iconBox, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaled));
        }
        row.add(iconLabel);
        row.add(Box.createHorizontalStrut(8));

        final String firstFacility = facility.split(",")[0].trim().replaceAll("(?i)\\bor\\b.*$", "").trim();
        JLabel nameLabel = new JLabel("<html>" + wrapTextManually("Made at: " + facility, 150, FontManager.getRunescapeFont()) + "</html>");
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setForeground(ALWAYS_COLOR);
        nameLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (!firstFacility.isEmpty())
                {
                    openInBrowser(officialWikiUrl(firstFacility));
                }
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
        row.add(nameLabel);

        row.add(Box.createHorizontalGlue());

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private JPanel buildMaterialRow(ItemInfoClient.Material material)
    {
        final String name = material.name;

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 20)),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)
        ));

        final int iconBox = 24;
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(iconBox, iconBox));
        iconLabel.setMinimumSize(new Dimension(iconBox, iconBox));
        iconLabel.setMaximumSize(new Dimension(iconBox, iconBox));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        materialIconLabels.computeIfAbsent(name, k -> new ArrayList<>()).add(iconLabel);
        row.add(iconLabel);
        row.add(Box.createHorizontalStrut(8));

        JLabel nameLabel = new JLabel("<html>" + wrapTextManually(name, 120, FontManager.getRunescapeFont()) + "</html>");
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setForeground(ALWAYS_COLOR);
        nameLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (materialClickListener != null)
                {
                    materialClickListener.accept(name);
                }
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
        row.add(nameLabel);

        row.add(Box.createHorizontalGlue());

        if (material.quantity != null && !material.quantity.isEmpty())
        {
            JLabel qtyLabel = new JLabel(material.quantity);
            qtyLabel.setFont(FontManager.getRunescapeFont());
            qtyLabel.setForeground(Color.WHITE);
            qtyLabel.setAlignmentY(Component.TOP_ALIGNMENT);
            row.add(qtyLabel);
        }

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    public void updateMaterialIcon(String name, BufferedImage icon)
    {
        List<JLabel> labels = materialIconLabels.get(name);
        if (labels == null || labels.isEmpty() || icon == null)
        {
            return;
        }
        final int iconBox = 24;
        Image scaled = icon.getScaledInstance(iconBox, iconBox, Image.SCALE_SMOOTH);
        ImageIcon imageIcon = new ImageIcon(scaled);
        for (JLabel label : labels)
        {
            label.setIcon(imageIcon);
        }
        revalidate();
        repaint();
    }

    private JPanel card(JLabel titleLabel, String title, JComponent content)
    {
        return buildCard(titleLabel, title, content, false);
    }

    private JPanel cardFilling(JLabel titleLabel, String title, JComponent content)
    {
        return buildCard(titleLabel, title, content, true);
    }

    private JPanel buildCard(JLabel titleLabel, String title, JComponent content, boolean fill)
    {
        styleCardTitle(titleLabel, title);

        RoundedPanel c = new RoundedPanel(CARD_BG, CARD_BORDER, 10);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setBorder(BorderFactory.createEmptyBorder(8, 11, 9, 11));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.add(titleLabel);
        c.add(Box.createVerticalStrut(7));
        c.add(content);
        if (!fill)
        {

            c.add(Box.createVerticalGlue());
        }
        return c;
    }

    private JPanel buildCollapsibleCard(JLabel titleLabel, String title, JComponent content)
    {
        styleCardTitle(titleLabel, title);

        RoundedPanel c = new RoundedPanel(CARD_BG, CARD_BORDER, 10)
        {
            @Override
            public Dimension getMaximumSize()
            {
                // Never taller than the content, so a short section isn't stretched into a blank
                // box; the cards stack top-down and leftover space falls to the bottom.
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize()
            {
                // Allow shrinking (content scrolls internally) so several tall sections can share
                // the panel height, but not below a small floor or the card's own content.
                return new Dimension(10, Math.min(getPreferredSize().height, MIN_CARD_HEIGHT));
            }
        };
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setBorder(BorderFactory.createEmptyBorder(8, 11, 9, 11));

        final JLabel chevron = new JLabel(createTriangleIcon(DIR_DOWN, NEUTRAL));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.add(titleLabel);
        header.add(Box.createHorizontalGlue());
        header.add(chevron);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));

        final Component gap = Box.createVerticalStrut(7);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                boolean show = !content.isVisible();
                content.setVisible(show);
                gap.setVisible(show);
                chevron.setIcon(createTriangleIcon(show ? DIR_DOWN : DIR_RIGHT, NEUTRAL));
                c.revalidate();
                c.repaint();
                ItemInfoPanel.this.revalidate();
                ItemInfoPanel.this.repaint();
            }
        });

        c.add(header);
        c.add(gap);
        c.add(content);
        return c;
    }

    private void styleCardTitle(JLabel label, String title)
    {
        label.setText(title);
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        label.setForeground(ORANGE);
        label.setIcon(null);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void refreshTabBar()
    {
        if (tabBar == null)
        {
            return;
        }
        tabBar.removeAll();
        tabButtons.clear();

        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[]{TAB_STATS, "Info"});
        if (combatTabVisible)
        {
            tabs.add(new String[]{TAB_COMBAT, "Combat"});
        }
        if (sourcesTabVisible)
        {
            tabs.add(new String[]{TAB_SOURCES, "Sources"});
        }

        tabBar.setLayout(new GridLayout(1, tabs.size(), 3, 0));
        for (String[] t : tabs)
        {
            JLabel button = makeTabButton(t[0], t[1]);
            tabButtons.put(t[0], button);
            tabBar.add(button);
        }
        styleTabs();
        tabBar.revalidate();
        tabBar.repaint();
    }

    private JLabel makeTabButton(String key, String text)
    {

        JLabel button = new JLabel(text, SwingConstants.CENTER);
        button.setFont(FontManager.getRunescapeFont());
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(2);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                selectTab(key);
            }
        });
        return button;
    }

    private void styleTabs()
    {
        for (Map.Entry<String, JLabel> entry : tabButtons.entrySet())
        {
            boolean active = entry.getKey().equals(currentTab);
            JLabel button = entry.getValue();
            button.setBackground(active ? TAB_ACTIVE_BG : TAB_BG);
            button.setForeground(active ? GOLD : TAB_INACTIVE_FG);
            button.setIcon(createTabIcon(entry.getKey(), active ? GOLD : TAB_INACTIVE_FG));

            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, active ? ORANGE : TAB_BG),
                    BorderFactory.createEmptyBorder(6, 2, 5, 2)));
        }
    }

    private Icon createTabIcon(String key, Color color)
    {
        final int size = 15;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                switch (key)
                {
                    case TAB_COMBAT:

                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(x + 2, y + 13, x + 12, y + 3);
                        g2.drawLine(x + 13, y + 13, x + 3, y + 3);
                        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(x + 1, y + 10, x + 4, y + 13);
                        g2.drawLine(x + 14, y + 10, x + 11, y + 13);
                        break;
                    case TAB_SOURCES:

                        g2.fillOval(x + 5, y + 1, 5, 5);
                        g2.fillRoundRect(x + 2, y + 7, 11, 7, 6, 6);
                        break;
                    default:
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.drawOval(x + 2, y + 2, 11, 11);
                        g2.fillOval(x + 7, y + 4, 2, 2);
                        g2.drawLine(x + 8, y + 7, x + 8, y + 11);
                        break;
                }
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

    private void selectTab(String key)
    {
        currentTab = key;

        if (TAB_SOURCES.equals(key))
        {
            ensureSourcesLoaded();
        }
        else if (TAB_COMBAT.equals(key))
        {
            ensureCombatLoaded();
        }

        if (contentHolder != null)
        {
            contentHolder.removeAll();
            JComponent page = tabContent.get(key);
            if (page != null)
            {

                contentHolder.add(page, BorderLayout.CENTER);
            }
            contentHolder.revalidate();
            contentHolder.repaint();
        }
        styleTabs();
        scrollToTop();
    }

    private void ensureSourcesLoaded()
    {
        if (!sourcesRequested && sourcesLoader != null)
        {
            sourcesRequested = true;
            dropsContent.removeAll();
            dropsContent.add(makeLoadingSpinner());
            shopsContent.removeAll();
            shopsContent.add(makeSourcesInfoLabel("Loading..."));
            sourcesLoader.run();
        }
    }

    private void ensureCombatLoaded()
    {
        if (!combatStatsRequested)
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
                combatStatsContent.add(makeSourcesInfoLabel("No combat stats available."));
            }
            combatStatsContent.revalidate();
            combatStatsContent.repaint();
        }
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

    private static final long SHOP_PRICE_SANITY_CAP = 1_000_000_000L;

    private String formatShopPrice(String rawPrice)
    {
        try
        {
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

                for (int i = 2; i >= 1; i--)
                {
                    int cy = y + 3 + i * 3;
                    g2.setColor(rim);
                    g2.fillOval(x, cy, discW, discH);
                }

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

    private Icon createTradeableIcon(Color color)
    {
        final int size = 13;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                Color shade = color.darker();

                g2.setColor(shade);
                g2.fillOval(x + 1, y, 7, 7);
                g2.setColor(color);
                g2.fillOval(x + 2, y + 1, 5, 5);

                g2.setColor(shade);
                g2.fillOval(x + 5, y + 5, 8, 8);
                g2.setColor(color);
                g2.fillOval(x + 6, y + 6, 6, 6);

                g2.setColor(shade);
                g2.fillRect(x + 8, y + 8, 2, 2);
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

    private Icon createEquipableIcon(Color color)
    {
        final int size = 13;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                Color shade = color.darker();

                g2.setColor(shade);
                g2.fillArc(x, y, 12, 12, 0, 180);
                g2.fillRect(x, y + 6, 12, 5);

                g2.setColor(color);
                g2.fillArc(x + 1, y + 1, 10, 10, 0, 180);
                g2.fillRect(x + 1, y + 6, 4, 4);
                g2.fillRect(x + 7, y + 6, 4, 4);

                g2.setColor(shade);
                g2.fillRect(x + 1, y + 5, 10, 1);
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

    private Icon createStackableIcon(Color color)
    {
        final int size = 16;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color shade = color.darker();
                Color hi = color.brighter();
                final int w = 14;

                for (int i = 0; i < 4; i++)
                {
                    int cy = y + 12 - i * 3;
                    g2.setColor(shade);
                    g2.fillOval(x + 1, cy, w, 4);
                    g2.setColor(color);
                    g2.fillOval(x + 1, cy, w, 3);
                    g2.setColor(hi);
                    g2.fillRect(x + 4, cy + 1, 3, 1);
                }
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

    private boolean isYesNo(String value)
    {
        return value != null && ("Yes".equalsIgnoreCase(value)
                || "No".equalsIgnoreCase(value) || "Unknown".equalsIgnoreCase(value));
    }

    private void addTableRow(int row, String label, String value, Icon icon, Color accentColor)
    {
        Font labelFont = FontManager.getRunescapeFont();
        Font valueFont = FontManager.getRunescapeFont();

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);
        labelComp.setForeground(Color.WHITE);
        if (icon != null)
        {

            labelComp.setIcon(fixedWidthIcon(icon, 20));
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

    private Icon fixedWidthIcon(final Icon base, final int width)
    {
        if (base == null)
        {
            return null;
        }
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                base.paintIcon(c, g, x, y);
            }

            @Override
            public int getIconWidth()
            {
                return width;
            }

            @Override
            public int getIconHeight()
            {
                return base.getIconHeight();
            }
        };
    }

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
        priceSubLabel.setIcon(price > 0 ? coinIcon() : null);
        priceSubLabel.setIconTextGap(5);
        priceSubLabel.setText(price > 0 ? formatPrice(price) + " gp" : "");
        if (image != null)
        {
            setImage(image);
        }
        else
        {
            showLoadingImage();
        }

        infoTable.removeAll();
        priceGraphPanel.setVisible(false);
        sparkline.setData(null, null);
        priceTicker.setSegments(null);
        priceTicker.setVisible(false);
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

    public void setItemProperties(ItemInfoClient.InfoboxData info)
    {
        infoTable.removeAll();
        int row = 0;

        if (info != null)
        {
            addTableRow(row++, "Released:", info.released, createCalendarIcon(BLUE), BLUE);
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
            addTableRow(row++, "Value:", formatValueString(info.value) + " gp", coinIcon(), GOLD);
            addTableRow(row++, "Weight:", info.weight + " kg", weightIcon(), BLUE);
            addTableRow(row++, "Members:", info.members, membersRowIcon(info.members), yesNoColor(info.members));
            addTableRow(row++, "Tradeable:", info.tradeable, tradeableIcon(), yesNoColor(info.tradeable));
            addTableRow(row++, "Equipable:", info.equipable, equipableIcon(), yesNoColor(info.equipable));
            addTableRow(row++, "Stackable:", info.stackable, stackableIcon(), yesNoColor(info.stackable));
            addTableRow(row++, "Noteable:", info.noteable, noteIcon(), yesNoColor(info.noteable));

            Color questColor = isYesNo(info.questItem) ? yesNoColor(info.questItem) : BLUE;
            addTableRow(row++, "Quest:", info.questItem, questIcon(), questColor);
            addTableRow(row++, "Options:", info.options, createListIcon(NEUTRAL), NEUTRAL);
        }

        updatePropertiesVisibility();
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setNpcProperties(ItemInfoClient.NpcInfoboxData data)
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
        return infoTable.getComponentCount() / 2;
    }

    public void setSourcesLoader(Runnable loader)
    {
        this.sourcesLoader = loader;
        sourcesTabVisible = (loader != null);
        refreshTabBar();

        if (!sourcesTabVisible && TAB_SOURCES.equals(currentTab))
        {
            selectTab(TAB_STATS);
        }
        else if (TAB_SOURCES.equals(currentTab))
        {
            ensureSourcesLoaded();
        }
    }

    public void setShopsSectionVisible(boolean visible)
    {
        if (shopsCard != null)
        {
            shopsCard.setVisible(visible);
        }
        revalidate();
        repaint();
    }

    public void setNpcDropsMode(boolean npcMode)
    {
        setNpcDropsMode(npcMode, "Drops");
    }

    public void setNpcDropsMode(boolean npcMode, String label)
    {
        this.npcDropsMode = npcMode;
        this.singleSectionLabel = label;

        styleCardTitle(dropsHeaderLabel, npcMode ? label : "Drops");
        revalidate();
        repaint();
    }

    public void setDropRowClickListener(BiConsumer<String, String> listener)
    {
        this.dropRowClickListener = listener;
    }

    public void setBackButtonListener(Runnable listener)
    {
        this.backButtonListener = listener;
    }

    public void setBackButtonVisible(boolean visible)
    {
        backButtonLabel.setVisible(visible);
        revalidate();
        repaint();
    }

    public boolean isNpcDropsMode()
    {
        return npcDropsMode;
    }

    public void setPlayerCombatLevel(int level)
    {
        this.playerCombatLevel = level;
    }

    public void setShowTooltips(boolean showTooltips)
    {
        this.showTooltips = showTooltips;
    }

    public void setCoinIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realCoinIcon = new ImageIcon(scaled);
        }
    }

    public void setMaxHitIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realMaxHitIcon = new ImageIcon(scaled);
        }
    }

    public void setPoisonIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realPoisonIcon = new ImageIcon(scaled);
        }
    }

    public void setQuestIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realQuestIcon = new ImageIcon(scaled);
        }
    }

    public void setNoteIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realNoteIcon = new ImageIcon(scaled);
        }
    }

    public void setAggressiveIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realAggressiveIcon = new ImageIcon(scaled);
        }
    }

    public void setMemberIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realMemberIcon = new ImageIcon(scaled);
        }
    }

    public void setF2pIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realF2pIcon = new ImageIcon(scaled);
        }
    }

    public void setWeightIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realWeightIcon = new ImageIcon(scaled);
        }
    }

    public void setYesIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realYesIcon = new ImageIcon(scaled);
        }
    }

    public void setNoIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realNoIcon = new ImageIcon(scaled);
        }
    }

    public void setTradeableIcon(BufferedImage image)
    {

        if (image != null && !isBlankImage(image))
        {
            Image scaled = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            realTradeableIcon = new ImageIcon(scaled);
        }
    }

    private boolean isBlankImage(BufferedImage image)
    {
        if (!image.getColorModel().hasAlpha())
        {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) != 0)
                {
                    return false;
                }
            }
        }
        return true;
    }

    public void setEquipableIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            realEquipableIcon = new ImageIcon(scaled);
        }
    }

    public void setStackableIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            realStackableIcon = new ImageIcon(scaled);
        }
    }

    public void setHighAlchIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realHighAlchIcon = new ImageIcon(scaled);
        }
    }

    public void setLowAlchIcon(BufferedImage image)
    {
        if (image != null)
        {
            Image scaled = image.getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            realLowAlchIcon = new ImageIcon(scaled);
        }
    }

    private Icon coinIcon()
    {
        return realCoinIcon != null ? realCoinIcon : createCoinIcon(GOLD);
    }

    private Icon maxHitIcon()
    {
        return realMaxHitIcon != null ? realMaxHitIcon : createListIcon(RED);
    }

    private Icon poisonIcon()
    {
        return realPoisonIcon != null ? realPoisonIcon : createCheckIcon(GREEN);
    }

    private Icon questIcon()
    {
        return realQuestIcon != null ? realQuestIcon : createListIcon(NEUTRAL);
    }

    private Icon noteIcon()
    {
        return realNoteIcon != null ? realNoteIcon : createListIcon(NEUTRAL);
    }

    private Icon aggressiveIcon()
    {
        return realAggressiveIcon != null ? realAggressiveIcon : createListIcon(RED);
    }

    private Icon memberIcon()
    {
        return realMemberIcon != null ? realMemberIcon : createCheckIcon(GREEN);
    }

    private Icon f2pIcon()
    {
        return realF2pIcon != null ? realF2pIcon : createCrossIcon(RED);
    }

    private Icon tradeableIcon()
    {
        return realTradeableIcon != null ? realTradeableIcon : createTradeableIcon(GOLD);
    }

    private Icon equipableIcon()
    {
        return realEquipableIcon != null ? realEquipableIcon : createEquipableIcon(new Color(150, 160, 175));
    }

    private Icon stackableIcon()
    {
        return realStackableIcon != null ? realStackableIcon : createStackableIcon(GOLD);
    }

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

    private Icon weightIcon()
    {
        return realWeightIcon != null ? realWeightIcon : createScaleIcon(BLUE);
    }

    private Icon highAlchIcon()
    {
        return realHighAlchIcon != null ? realHighAlchIcon : createCoinIcon(GOLD);
    }

    private Icon lowAlchIcon()
    {
        return realLowAlchIcon != null ? realLowAlchIcon : createCoinIcon(GOLD);
    }

    private void resetSources()
    {
        sourcesRequested = false;
        cachedDrops = null;
        cachedShops = null;
        dropsContent.removeAll();
        shopsContent.removeAll();
        materialIconLabels.clear();
        if (materialsCard != null)
        {
            materialsContent.removeAll();
            materialsCard.setVisible(false);
        }
    }

    private static final int DIR_RIGHT = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_UP = 2;
    private static final int DIR_LEFT = 3;

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

    private void updatePropertiesVisibility()
    {

        infoTable.setVisible(true);
    }

    private JLabel makeSourcesInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeFont());
        label.setForeground(NEUTRAL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel makeSourcesLink(String text, String url)
    {
        JLabel link = new JLabel(text);
        link.setFont(FontManager.getRunescapeFont());
        link.setForeground(GOLD);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                openInBrowser(url);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                link.setForeground(GOLD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                link.setForeground(GOLD);
            }
        });
        return link;
    }

    public void showDropsLink(String message, String url)
    {
        cachedDrops = new ArrayList<>();
        cachedShops = new ArrayList<>();
        dropIconLabels.clear();

        dropsContent.removeAll();
        Font sourcesFont = FontManager.getRunescapeFont();
        JLabel info = makeSourcesInfoLabel("<html>" + wrapTextManually(message, 165, sourcesFont) + "</html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        dropsContent.add(info);
        dropsContent.add(makeSourcesLink(
                "<html>" + wrapTextManually("View all drop sources on the wiki  ↗", 165, sourcesFont) + "</html>", url));
        dropsContent.add(Box.createVerticalGlue());
        dropsContent.revalidate();

        shopsContent.removeAll();
        shopsContent.add(makeSourcesInfoLabel("Not sold in any shops."));
        shopsContent.revalidate();

        revalidate();
        repaint();
    }

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
    private static final Color RARITY_COMMON = new Color(80, 220, 100);
    private static final Color RARITY_UNCOMMON = new Color(230, 210, 60);
    private static final Color RARITY_RARE = new Color(225, 80, 80);
    private static final Color RARITY_LEGENDARY = new Color(190, 110, 235);
    private static final Color RARITY_UNKNOWN = NEUTRAL;

    public void setSources(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops)
    {
        setSourcesInternal(drops, shops, null, false);
    }

    public void setSources(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops, Map<String, BufferedImage> dropIcons)
    {
        setSourcesInternal(drops, shops, dropIcons, false);
    }

    public void setSourcesWithLoadingIcons(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops)
    {
        setSourcesInternal(drops, shops, null, true);
    }

    public void setSourcesWithLoadingIcons(List<ItemInfoClient.DropSource> drops, List<ItemInfoClient.ShopSource> shops, Map<String, BufferedImage> knownIcons)
    {
        setSourcesInternal(drops, shops, knownIcons, true);
    }

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
            String emptyMessage = npcDropsMode
                    ? "No " + singleSectionLabel.toLowerCase() + " available."
                    : "No known drop sources.";
            dropsContent.add(makeSourcesInfoLabel(emptyMessage));
        }
        else
        {
            int shown = 0;
            for (ItemInfoClient.DropSource drop : cachedDrops)
            {
                if (shown >= DROP_DISPLAY_CAP)
                {
                    break;
                }
                BufferedImage icon = (dropIcons != null && drop.source != null) ? dropIcons.get(drop.source) : null;
                dropsContent.add(buildDropRow(drop, icon, iconsLoading));
                shown++;
            }
            if (cachedDrops.size() > DROP_DISPLAY_CAP)
            {
                JLabel more = makeSourcesInfoLabel("<html>" + wrapTextManually(
                        "Showing " + DROP_DISPLAY_CAP + " of " + cachedDrops.size()
                                + " sources - open the wiki for the full list.",
                        165, FontManager.getRunescapeFont()) + "</html>");
                more.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
                dropsContent.add(more);
            }
        }

        dropsContent.add(Box.createVerticalGlue());
        dropsContent.revalidate();

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

        revalidate();
        repaint();
    }

    private JPanel buildDropRow(ItemInfoClient.DropSource drop, BufferedImage icon, boolean iconLoading)
    {
        JPanel row = new JPanel();
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

        int wrapWidth = showIcon ? 130 : 160;
        String rawName = drop.source != null ? drop.source : "Unknown";
        JLabel nameLabel = new JLabel("<html>" + wrapTextManually(rawName, wrapWidth, FontManager.getRunescapeFont()) + "</html>");
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (dropRowClickListener != null)
        {
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

        boolean levelAlreadyShown = drop.level != null
                && rawName.toLowerCase().contains("level " + drop.level.toLowerCase());

        if (drop.level != null && !drop.level.isEmpty() && !levelAlreadyShown)
        {
            String levelText = "(Lvl " + formatDropLevel(drop.level) + ")";
            JLabel levelLabel = new JLabel("<html>" + wrapTextManually(levelText, wrapWidth, FontManager.getRunescapeFont()) + "</html>");
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
            for (Component c : textStack.getComponents())
            {
                row.add(c);
            }
        }

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private String formatQuantity(String rawQuantity)
    {
        if (rawQuantity == null)
        {
            return "-";
        }
        String normalized = rawQuantity.replace("–", "-").replace("—", "-");

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

    private String formatDropLevel(String level)
    {
        if (level == null)
        {
            return "";
        }
        boolean haveNum = false;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int count = 0;
        for (String part : level.split("[,/]"))
        {
            String t = part.trim();
            if (t.isEmpty())
            {
                continue;
            }
            try
            {
                int v = (int) Math.round(Double.parseDouble(t));
                min = Math.min(min, v);
                max = Math.max(max, v);
                count++;
                haveNum = true;
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        if (!haveNum || count <= 1)
        {
            return level.trim();
        }
        return min == max ? String.valueOf(min) : (min + "-" + max);
    }

    private Color combatLevelColor(ItemInfoClient.DropSource drop)
    {
        if (drop.level == null || !"combat".equalsIgnoreCase(drop.dropType))
        {
            return NEUTRAL;
        }
        return combatLevelColorForLevel(drop.level);
    }

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

    private String wrapTextManually(String text, int maxWidthPx, Font font)
    {
        FontMetrics metrics = getFontMetrics(font);
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" "))
        {
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

        if (shop.location != null && !shop.location.trim().isEmpty())
        {
            JLabel locationLabel = new JLabel("<html>"
                    + wrapTextManually("Location: " + shop.location.trim(), 160, FontManager.getRunescapeFont())
                    + "</html>");
            locationLabel.setFont(FontManager.getRunescapeFont());
            locationLabel.setForeground(NEUTRAL);
            locationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(Box.createVerticalStrut(2));
            row.add(locationLabel);
        }

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
            priceText = "Price: " + formattedPrice;
        }
        else if (isCoins)
        {
            priceText = "Price: " + formattedPrice + " gp";
        }
        else
        {
            priceText = "Price: " + formattedPrice + " " + shop.currency;
        }

        JLabel priceLabel = new JLabel(priceText);
        priceLabel.setFont(FontManager.getRunescapeFont());
        priceLabel.setForeground(isFlagged ? NEUTRAL : GOLD);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (isCoins && !isFlagged)
        {
            priceLabel.setIcon(coinIcon());
            priceLabel.setIconTextGap(6);
        }
        row.add(Box.createVerticalStrut(2));
        row.add(priceLabel);

        if (shop.stock != null && !shop.stock.trim().isEmpty())
        {
            boolean noStock = "0".equals(shop.stock.trim());
            JLabel stockLabel = new JLabel("Stock: " + shop.stock.trim());
            stockLabel.setFont(FontManager.getRunescapeFont());
            stockLabel.setForeground(noStock ? RED : Color.WHITE);
            stockLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(Box.createVerticalStrut(2));
            row.add(stockLabel);
        }

        return row;
    }

    private JLabel buildRarityLabel(String rarity, int wrapWidth)
    {
        String display = (rarity != null && !rarity.isEmpty()) ? rarity : "-";
        display = addThousandsCommas(display);

        Font rarityFont = FontManager.getRunescapeBoldFont();
        String wrappedValue = wrapTextManually(display, wrapWidth, rarityFont);
        JLabel label = new JLabel("<html>" + wrappedValue + "</html>");
        label.setFont(rarityFont);
        label.setForeground(rarityColor(display));
        return label;
    }

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
        priceSubLabel.setIcon(null);
        priceSubLabel.setText("");
        showLoadingImage();
        infoTable.removeAll();
        priceGraphPanel.setVisible(false);
        sparkline.setData(null, null);
        priceTicker.setSegments(null);
        priceTicker.setVisible(false);
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

        int width = descriptionArea.getWidth();
        if (width <= 0)
        {
            width = 200;
        }
        descriptionArea.setSize(width, Short.MAX_VALUE);

        int textH = descriptionArea.getPreferredSize().height;
        if (showFullDescription)
        {

            descriptionScrollPane.setPreferredSize(new Dimension(10, Math.min(textH + 4, 160)));
            descriptionScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            descriptionTail.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
        }
        else
        {

            int boxH = Math.max(24, textH + 4);
            descriptionScrollPane.setPreferredSize(new Dimension(10, boxH));
            descriptionScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, boxH));
            descriptionTail.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        revalidate();
        repaint();
    }

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

        double scale = Math.min(1.5, Math.min((double) boxSize / origWidth, (double) boxSize / origHeight));
        int scaledWidth = Math.max(1, (int) (origWidth * scale));
        int scaledHeight = Math.max(1, (int) (origHeight * scale));

        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        g.setClip(frameShape);
        int x = (boxSize - scaledWidth) / 2;
        int y = (boxSize - scaledHeight) / 2;
        g.drawImage(image, x, y, scaledWidth, scaledHeight, null);
        g.setClip(null);

        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(canvas));
        scrollToTop();
    }

    private static class WidthTrackingPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    /**
     * Sizes itself to the viewport height whenever its sections can fit (i.e. the viewport is at
     * least the minimum height), so the BoxLayout distributes that height among the sections -
     * shrinking the tall ones (which then scroll internally) instead of overflowing into an outer
     * scrollbar. Only when the viewport is smaller than the minimum does it fall back to its
     * preferred height and let the outer scroll take over.
     */
    private static class SharedHeightPanel extends WidthTrackingPanel
    {
        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            Container parent = getParent();
            if (parent instanceof JViewport)
            {
                return parent.getHeight() >= getMinimumSize().height;
            }
            return false;
        }
    }

    /**
     * Stacks its visible children vertically and splits the container height into equal shares.
     * A child that needs less than its share takes only what it needs and returns the surplus,
     * which is redistributed equally among the children that still want more (equal water-fill).
     * So when every section is full each gets a third; when some are short the tall ones split the
     * rest evenly; and when everything fits, each keeps its natural height with the leftover left
     * at the bottom.
     */
    private static class EqualShareLayout implements LayoutManager
    {
        private final int vgap;

        EqualShareLayout(int vgap)
        {
            this.vgap = vgap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(Component comp)
        {
        }

        private java.util.List<Component> visible(Container parent)
        {
            java.util.List<Component> list = new ArrayList<>();
            for (Component c : parent.getComponents())
            {
                if (c.isVisible())
                {
                    list.add(c);
                }
            }
            return list;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent)
        {
            Insets in = parent.getInsets();
            java.util.List<Component> vis = visible(parent);
            int h = in.top + in.bottom;
            int w = 0;
            for (Component c : vis)
            {
                Dimension d = c.getPreferredSize();
                h += d.height;
                w = Math.max(w, d.width);
            }
            if (vis.size() > 1)
            {
                h += (vis.size() - 1) * vgap;
            }
            return new Dimension(w + in.left + in.right, h);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent)
        {
            Insets in = parent.getInsets();
            java.util.List<Component> vis = visible(parent);
            int h = in.top + in.bottom;
            int w = 0;
            for (Component c : vis)
            {
                h += c.getMinimumSize().height;
                w = Math.max(w, c.getPreferredSize().width);
            }
            if (vis.size() > 1)
            {
                h += (vis.size() - 1) * vgap;
            }
            return new Dimension(w + in.left + in.right, h);
        }

        @Override
        public void layoutContainer(Container parent)
        {
            Insets in = parent.getInsets();
            java.util.List<Component> vis = visible(parent);
            int n = vis.size();
            if (n == 0)
            {
                return;
            }
            int width = parent.getWidth() - in.left - in.right;
            int available = parent.getHeight() - in.top - in.bottom - (n - 1) * vgap;

            int[] heights = new int[n];
            boolean[] fixed = new boolean[n];
            int remaining = available;
            int count = n;
            boolean changed = true;
            while (changed && count > 0)
            {
                changed = false;
                int share = remaining / Math.max(count, 1);
                for (int i = 0; i < n; i++)
                {
                    if (fixed[i])
                    {
                        continue;
                    }
                    int natural = vis.get(i).getPreferredSize().height;
                    if (natural <= share)
                    {
                        heights[i] = natural;
                        fixed[i] = true;
                        remaining -= natural;
                        count--;
                        changed = true;
                    }
                }
            }
            if (count > 0)
            {
                int share = remaining / count;
                int extra = remaining - share * count;
                for (int i = 0; i < n; i++)
                {
                    if (!fixed[i])
                    {
                        heights[i] = share + (extra > 0 ? 1 : 0);
                        if (extra > 0)
                        {
                            extra--;
                        }
                    }
                }
            }

            int y = in.top;
            for (int i = 0; i < n; i++)
            {
                vis.get(i).setBounds(in.left, y, width, heights[i]);
                y += heights[i] + vgap;
            }
        }
    }

    private static class RoundedPanel extends JPanel
    {
        private final Color fill;
        private final Color borderColor;
        private final int radius;

        RoundedPanel(Color fill, Color borderColor, int radius)
        {
            this.fill = fill;
            this.borderColor = borderColor;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(fill);
            g2.fill(shape);
            g2.setColor(borderColor);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Sparkline extends JComponent
    {
        private java.util.List<Integer> data;
        private java.util.List<Long> times;
        private int hover = -1;
        private int prefH = 210;
        private static final Color LINE = new Color(255, 184, 63);
        private static final int DATE_H = 14;

        Sparkline()
        {
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
            {
                @Override
                public void mouseMoved(MouseEvent e)
                {
                    hover = indexAt(e.getX());
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover = -1;
                    repaint();
                }
            });
        }

        void setData(java.util.List<Integer> data, java.util.List<Long> times)
        {
            this.data = data;
            this.times = times;
            this.hover = -1;
            repaint();
        }

        private int indexAt(int px)
        {
            if (data == null || data.size() < 2)
            {
                return -1;
            }
            int n = data.size();
            int w = Math.max(1, getWidth() - 1);
            int idx = (int) Math.round((double) px / w * (n - 1));
            return Math.max(0, Math.min(n - 1, idx));
        }

        void setPreferredHeight(int h)
        {
            this.prefH = h;
            revalidate();
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(100, prefH);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if (data == null || data.size() < 2)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int graphH = h - DATE_H;
            int n = data.size();
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int v : data)
            {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double range = Math.max(1, max - min);

            int topPad = 24;
            int yBottom = graphH - 3;
            double plotSpan = Math.max(1, yBottom - topPad);
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++)
            {
                xs[i] = (int) ((double) i / (n - 1) * (w - 1));
                ys[i] = (int) (yBottom - (data.get(i) - min) / range * plotSpan);
            }

            Polygon area = new Polygon();
            area.addPoint(0, graphH);
            for (int i = 0; i < n; i++)
            {
                area.addPoint(xs[i], ys[i]);
            }
            area.addPoint(w - 1, graphH);
            g2.setColor(new Color(255, 184, 63, 45));
            g2.fillPolygon(area);

            g2.setColor(LINE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < n; i++)
            {
                g2.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            }

            if (hover >= 0 && hover < n)
            {
                int hx = xs[hover];
                int hy = ys[hover];
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(hx, 0, hx, graphH);
                g2.setColor(LINE);
                g2.fillOval(hx - 2, hy - 2, 5, 5);

                Font f = FontManager.getRunescapeSmallFont();
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();

                String priceStr = String.format("%,d gp", data.get(hover));
                int pw = fm.stringWidth(priceStr);
                int px = Math.max(1, Math.min(w - pw - 1, hx - pw / 2));
                g2.setColor(Color.WHITE);
                g2.drawString(priceStr, px, 13);

                if (times != null && hover < times.size())
                {

                    long span = times.size() >= 2 ? (times.get(times.size() - 1) - times.get(0)) : 0;
                    String pattern = (span > 0 && span <= 129600) ? "HH:mm" : "d MMM";
                    String dateStr = new java.text.SimpleDateFormat(pattern).format(new java.util.Date(times.get(hover) * 1000L));
                    int dw = fm.stringWidth(dateStr);
                    int dx = Math.max(1, Math.min(w - dw - 1, hx - dw / 2));
                    g2.setColor(new Color(180, 168, 140));
                    g2.drawString(dateStr, dx, h - 3);
                }
            }
            g2.dispose();
        }
    }

    private static class Ticker extends JComponent
    {
        static class Seg
        {
            String label;
            double pct;
        }

        private java.util.List<Seg> segs = new java.util.ArrayList<>();
        private double offset = 0;
        private final javax.swing.Timer timer;
        private static final int GAP = 26;
        private static final Color UP = new Color(95, 220, 99);
        private static final Color DOWN = new Color(255, 81, 64);
        private static final Color LABEL = new Color(179, 166, 132);

        Ticker()
        {
            setPreferredSize(new Dimension(100, 20));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            timer = new javax.swing.Timer(33, e ->
            {
                offset += 0.7;
                repaint();
            });
        }

        void setSegments(java.util.List<Seg> segments)
        {
            this.segs = segments != null ? segments : new java.util.ArrayList<>();
            offset = 0;

            if (this.segs.isEmpty())
            {
                timer.stop();
            }
            else
            {
                timer.start();
            }
            repaint();
        }

        @Override
        public void addNotify()
        {
            super.addNotify();
            if (!segs.isEmpty())
            {
                timer.start();
            }
        }

        @Override
        public void removeNotify()
        {
            timer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if (segs.isEmpty())
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font f = FontManager.getRunescapeBoldFont();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();

            int total = 0;
            for (Seg s : segs)
            {
                total += segWidth(s, fm) + GAP;
            }
            if (total <= 0)
            {
                g2.dispose();
                return;
            }

            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            int x = (int) (-(offset % total));
            while (x < getWidth())
            {
                for (Seg s : segs)
                {
                    x = drawSeg(g2, s, x, baseline, fm);
                    x += GAP;
                }
            }
            g2.dispose();
        }

        private int segWidth(Seg s, FontMetrics fm)
        {
            return fm.stringWidth(s.label + "  " + arrowText(s) + fmtPct(s));
        }

        private String arrowText(Seg s)
        {
            return s.pct >= 0 ? "▲ " : "▼ ";
        }

        private String fmtPct(Seg s)
        {
            return String.format("%+.1f%%", s.pct);
        }

        private int drawSeg(Graphics2D g2, Seg s, int x, int baseline, FontMetrics fm)
        {
            String label = s.label + "  ";
            g2.setColor(LABEL);
            g2.drawString(label, x, baseline);
            x += fm.stringWidth(label);

            String move = arrowText(s) + fmtPct(s);
            g2.setColor(s.pct >= 0 ? UP : DOWN);
            g2.drawString(move, x, baseline);
            x += fm.stringWidth(move);
            return x;
        }
    }

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