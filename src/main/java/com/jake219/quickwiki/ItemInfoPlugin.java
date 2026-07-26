package com.jake219.quickwiki;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import net.runelite.client.input.KeyListener;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
        name = "Quick Wiki",
        description = "Right-click any item, NPC, or object to view its wiki info, price, and stats in a compact panel",
        tags = {"item", "wiki", "prices", "npc", "object"}
)
public class ItemInfoPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ItemInfoClient itemInfoClient;

    @Inject
    private SkillIconManager skillIconManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private ItemInfoConfig config;

    @Inject
    private KeyManager keyManager;

    @Provides
    ItemInfoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ItemInfoConfig.class);
    }

    private ItemInfoPanel panel;
    private NavigationButton navButton;

    private volatile boolean hotkeyHeld = false;

    private String lastExamineCategory;
    private String lastExamineName;
    private int lastExamineMenuIdentifier;
    private int lastExamineItemId;
    private int lastExamineTick = -1;

    private final KeyListener hotkeyListener = new KeyListener()
    {
        @Override
        public void keyTyped(KeyEvent e)
        {
        }

        @Override
        public void keyPressed(KeyEvent e)
        {
            if (config.enableHotkey() && config.hotkey().matches(e))
            {
                hotkeyHeld = true;
            }
        }

        @Override
        public void keyReleased(KeyEvent e)
        {
            if (config.hotkey().matches(e))
            {
                hotkeyHeld = false;
            }
        }
    };

    private String originalCategory;
    private String originalPageName;
    private int originalGameId;
    private boolean viewingLinkedPage = false;

    private final AtomicInteger navigationGeneration = new AtomicInteger(0);

    private int currentMarketItemId = -1;
    private String currentMarketItemName;

    private void recordOriginalView(String category, String name, int gameId)
    {
        originalCategory = category;
        originalPageName = name;
        originalGameId = gameId;
        viewingLinkedPage = false;
        SwingUtilities.invokeLater(() -> panel.setBackButtonVisible(false));
    }

    private void recordLinkedView()
    {
        viewingLinkedPage = true;
        SwingUtilities.invokeLater(() -> panel.setBackButtonVisible(true));
    }

    private void fetchMarketForPanel(int itemId, String itemName, int myGen)
    {
        currentMarketItemId = itemId;
        currentMarketItemName = itemName;
        itemInfoClient.fetchMarket(itemId, itemName, panel.getGraphRange(), market ->
                SwingUtilities.invokeLater(() ->
                {
                    if (navigationGeneration.get() == myGen)
                    {
                        panel.setMarket(market);
                    }
                }));
    }

    private void refetchMarketForRange()
    {
        if (currentMarketItemId >= 0)
        {
            fetchMarketForPanel(currentMarketItemId, currentMarketItemName, navigationGeneration.get());
        }
    }

    private static final int COINS_ITEM_ID = 995;

    private void wireItemSourcesLoader(String pageName, int itemId, int myGen)
    {
        if (itemId == COINS_ITEM_ID || "Coins".equalsIgnoreCase(pageName))
        {
            panel.setSourcesLoader(() -> panel.showDropsLink(
                    "This item has too many drop sources to list here.",
                    "https://oldschool.runescape.wiki/w/Coins/Drop_sources"));
            return;
        }
        panel.setSourcesLoader(() ->
                itemInfoClient.fetchItemSources(pageName, itemId, sources ->
                        clientThread.invoke(() ->
                        {
                            for (ItemInfoClient.DropSource drop : sources.drops)
                            {
                                drop.skillIcon = skillIconForDropType(drop.dropType);
                            }
                            for (ItemInfoClient.RecipeData recipe : sources.recipes)
                            {
                                if (recipe == null || recipe.requirements == null)
                                {
                                    continue;
                                }
                                for (ItemInfoClient.SkillReq req : recipe.requirements)
                                {
                                    if (req != null && req.skill != null)
                                    {
                                        req.skillIcon = skillImageForSkillName(req.skill);
                                    }
                                }
                            }
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() != myGen)
                                {
                                    return;
                                }
                                panel.setShopsSectionVisible(!sources.isRewards);
                                panel.setNpcDropsMode(sources.isRewards, sources.isRewards ? "Rewards" : "Drops");
                            });
                            if (sources.isRewards)
                            {
                                loadNpcDropIconsAndDisplay(sources.drops, myGen);
                            }
                            else
                            {
                                SwingUtilities.invokeLater(() ->
                                {
                                    if (navigationGeneration.get() == myGen)
                                    {
                                        panel.setSources(sources.drops, sources.shops);
                                        panel.setCreation(sources.recipes);
                                        for (ItemInfoClient.RecipeData recipe : sources.recipes)
                                        {
                                            if (recipe == null || recipe.materials == null)
                                            {
                                                continue;
                                            }
                                            for (ItemInfoClient.Material mat : recipe.materials)
                                            {
                                                if (mat != null && mat.name != null && !mat.name.isEmpty())
                                                {
                                                    resolveAndApplyMaterialIcon(mat.name, myGen);
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                        })));
    }

    private void goBack()
    {
        if (!viewingLinkedPage || originalCategory == null)
        {
            return;
        }
        viewingLinkedPage = false;

        switch (originalCategory)
        {
            case "ITEM":
                showItemByName(originalPageName, originalGameId, false);
                break;
            case "NPC":
                showNpcByName(originalPageName, originalGameId, false, -1);
                break;
            case "OBJECT":
                showObjectByName(originalPageName, originalGameId, false);
                break;
            default:
                break;
        }

        SwingUtilities.invokeLater(() -> panel.setBackButtonVisible(false));
    }

    @Override
    protected void startUp()
    {
        keyManager.registerKeyListener(hotkeyListener);

        panel = new ItemInfoPanel();
        panel.setShowTooltips(config.showTooltips());

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Quick Wiki")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        panel.setDropRowClickListener((clickedName, levelStr) ->
        {
            if (panel.isNpcDropsMode())
            {
                showItemByName(clickedName, -1, true);
            }
            else
            {

                itemInfoClient.resolveExactItemIdStrict(clickedName, resolvedItemId ->
                {
                    if (resolvedItemId != null)
                    {
                        showItemByName(clickedName, resolvedItemId, true);
                        return;
                    }

                    itemInfoClient.resolveExactObjectIdStrict(clickedName, resolvedObjectId ->
                    {
                        if (resolvedObjectId != null)
                        {
                            showObjectByName(clickedName, resolvedObjectId, true);
                            return;
                        }

                        int combatLevel = -1;
                        try
                        {
                            if (levelStr != null && !levelStr.trim().isEmpty())
                            {
                                combatLevel = Integer.parseInt(levelStr.trim());
                            }
                        }
                        catch (NumberFormatException e)
                        {

                        }

                        String trailingStripped = stripTrailingParenthetical(clickedName);
                        if (trailingStripped != null)
                        {
                            final int finalCombatLevel = combatLevel;
                            itemInfoClient.resolveExactItemIdStrict(trailingStripped, retryItemId ->
                            {
                                if (retryItemId != null)
                                {
                                    showItemByName(trailingStripped, retryItemId, true);
                                    return;
                                }
                                itemInfoClient.resolveExactObjectIdStrict(trailingStripped, retryObjectId ->
                                {
                                    if (retryObjectId != null)
                                    {
                                        showObjectByName(trailingStripped, retryObjectId, true);
                                        return;
                                    }
                                    showNpcByName(stripSubLocationForNav(clickedName), -1, true, finalCombatLevel);
                                });
                            });
                            return;
                        }

                        showNpcByName(stripSubLocationForNav(clickedName), -1, true, combatLevel);
                    });
                });
            }
        });
        panel.setBackButtonListener(this::goBack);

        panel.setGraphRangeListener(this::refetchMarketForRange);

        panel.setMaterialClickListener(materialName -> showItemByName(materialName, -1, true));

        final BufferedImage geIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/ge_icon.png");
        panel.setCoinIcon(geIcon);

        final BufferedImage maxHitIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/maxhit_icon.png");
        panel.setMaxHitIcon(maxHitIcon);
        final BufferedImage poisonIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/poison_icon.png");
        panel.setPoisonIcon(poisonIcon);
        final BufferedImage questIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/quest_icon.png");
        panel.setQuestIcon(questIcon);

        final BufferedImage noteIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/note_icon.png");
        panel.setNoteIcon(noteIcon);
        final BufferedImage aggressiveIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/aggressive_icon.png");
        panel.setAggressiveIcon(aggressiveIcon);
        final BufferedImage memberIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/member_icon.png");
        panel.setMemberIcon(memberIcon);
        final BufferedImage f2pIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/f2p_icon.png");
        panel.setF2pIcon(f2pIcon);

        try
        {
            spriteManager.getSpriteAsync(SpriteID.EQUIPMENT_WEIGHT, 0, weightImage ->
                    SwingUtilities.invokeLater(() -> panel.setWeightIcon(weightImage)));
            spriteManager.getSpriteAsync(SpriteID.OPTIONS_ROUND_CHECK_BOX_CHECKED, 0, checkedImage ->
                    SwingUtilities.invokeLater(() -> panel.setYesIcon(checkedImage)));
            spriteManager.getSpriteAsync(SpriteID.OPTIONS_ROUND_CHECK_BOX_CROSSED, 0, crossedImage ->
                    SwingUtilities.invokeLater(() -> panel.setNoIcon(crossedImage)));
            spriteManager.getSpriteAsync(SpriteID.SPELL_HIGH_LEVEL_ALCHEMY, 0, highAlchImage ->
                    SwingUtilities.invokeLater(() -> panel.setHighAlchIcon(highAlchImage)));
            spriteManager.getSpriteAsync(SpriteID.SPELL_LOW_LEVEL_ALCHEMY, 0, lowAlchImage ->
                    SwingUtilities.invokeLater(() -> panel.setLowAlchIcon(lowAlchImage)));

            spriteManager.getSpriteAsync(SpriteID.EQUIPMENT_EQUIPMENT_STATS, 0, equipImage ->
                    SwingUtilities.invokeLater(() -> panel.setEquipableIcon(equipImage)));
            spriteManager.getSpriteAsync(SpriteID.GE_GUIDE_PRICE, 0, tradeImage ->
                    SwingUtilities.invokeLater(() -> panel.setTradeableIcon(tradeImage)));
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch property-row icons", e);
        }
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(hotkeyListener);
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("quickwiki".equals(event.getGroup()) && "showTooltips".equals(event.getKey()))
        {
            panel.setShowTooltips(config.showTooltips());
        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!"Examine".equals(event.getOption()))
        {
            return;
        }

        final int type = event.getType();

        final String category;
        if (type == MenuAction.EXAMINE_ITEM.getId() || type == MenuAction.EXAMINE_ITEM_GROUND.getId() || type == 1007)
        {
            category = "ITEM";
        }
        else if (type == MenuAction.EXAMINE_NPC.getId())
        {
            category = "NPC";
        }
        else if (type == MenuAction.EXAMINE_OBJECT.getId())
        {
            category = "OBJECT";
        }
        else
        {
            return;
        }

        if (config.enableHotkey() && hotkeyHeld)
        {

            lastExamineCategory = category;
            lastExamineName = event.getTarget().replaceAll("<[^>]*>", "");
            lastExamineMenuIdentifier = event.getIdentifier();
            lastExamineItemId = event.getItemId();
            lastExamineTick = client.getTickCount();
        }

        if (config.enableHotkey() && !hotkeyHeld)
        {
            return;
        }

        final String cleanName = event.getTarget().replaceAll("<[^>]*>", "");
        final int menuIdentifier = event.getIdentifier();
        final int itemId = event.getItemId();

        client.createMenuEntry(-1)
                .setOption("Wiki")
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(e -> clientThread.invoke(() -> handleWikiClick(category, cleanName, menuIdentifier, itemId)));

        if (config.enableHotkey() && hotkeyHeld)
        {
            MenuEntry[] entries = client.getMenuEntries();
            MenuEntry[] reordered = new MenuEntry[entries.length];
            System.arraycopy(entries, 0, reordered, 0, entries.length);
            for (int i = 0; i < reordered.length - 1; i++)
            {
                if ("Wiki".equals(reordered[i].getOption()) && reordered[i].getType() == MenuAction.RUNELITE)
                {
                    MenuEntry wikiEntry = reordered[i];
                    System.arraycopy(reordered, i + 1, reordered, i, reordered.length - i - 1);
                    reordered[reordered.length - 1] = wikiEntry;
                    break;
                }
            }
            client.setMenuEntries(reordered);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.enableHotkey() || !hotkeyHeld)
        {
            return;
        }

        MenuAction action = event.getMenuAction();
        String actionName = action.name();

        if (actionName.equals("WALK") && lastExamineTick == client.getTickCount() && lastExamineCategory != null)
        {

            event.consume();

            final String walkCategory = lastExamineCategory;
            final String walkName = lastExamineName;
            final int walkMenuIdentifier = lastExamineMenuIdentifier;
            final int walkItemId = lastExamineItemId;
            clientThread.invoke(() -> handleWikiClick(walkCategory, walkName, walkMenuIdentifier, walkItemId));
            return;
        }

        final String category;
        if (actionName.startsWith("NPC_") || actionName.equals("EXAMINE_NPC"))
        {
            category = "NPC";
        }
        else if (actionName.startsWith("GAME_OBJECT_") || actionName.equals("EXAMINE_OBJECT"))
        {
            category = "OBJECT";
        }
        else if (actionName.startsWith("ITEM_") || actionName.startsWith("GROUND_ITEM_")
                || actionName.equals("EXAMINE_ITEM") || actionName.equals("EXAMINE_ITEM_GROUND")
                || actionName.equals("WIDGET_TARGET")
                || (actionName.startsWith("CC_OP") && event.getItemId() >= 0))
        {

            category = "ITEM";
        }
        else
        {
            return;
        }

        event.consume();

        final String cleanName = event.getMenuTarget().replaceAll("<[^>]*>", "");
        final int menuIdentifier = event.getId();
        final int itemId = event.getItemId();

        clientThread.invoke(() -> handleWikiClick(category, cleanName, menuIdentifier, itemId));
    }

    private final Map<String, Integer> resolvedItemIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, BufferedImage> itemIconCache = new ConcurrentHashMap<>();

    private void setupCombatStatsButton(String pageName, String equipable, int itemId, int myGen)
    {
        boolean isEquipable = equipable != null && equipable.trim().equalsIgnoreCase("Yes");
        if (!isEquipable)
        {
            // Non-equipable items have no combat stats, so hide the Combat tab entirely.
            panel.setCombatStatsSectionVisible(false);
            panel.setCombatStatsAvailable(null);
            return;
        }

        panel.setCombatStatsSectionVisible(true);
        panel.setCombatStatsAvailable(() ->
                itemInfoClient.fetchCombatBonuses(pageName, itemId, bonuses ->
                        clientThread.invoke(() ->
                        {

                            Map<String, BufferedImage> skillIcons = new HashMap<>();
                            skillIcons.put("attack", skillIconManager.getSkillImage(Skill.ATTACK));
                            skillIcons.put("strength", skillIconManager.getSkillImage(Skill.STRENGTH));
                            skillIcons.put("defence", skillIconManager.getSkillImage(Skill.DEFENCE));
                            skillIcons.put("ranged", skillIconManager.getSkillImage(Skill.RANGED));
                            skillIcons.put("magic", skillIconManager.getSkillImage(Skill.MAGIC));
                            skillIcons.put("prayer", skillIconManager.getSkillImage(Skill.PRAYER));

                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.displayCombatBonuses(bonuses, skillIcons);
                                }
                            });
                        })));
    }

    private void setupNpcCombatStats(String pageName, int combatLevel, int myGen)
    {
        panel.setCombatStatsSectionVisible(true);
        panel.setCombatStatsAvailable(() ->
                itemInfoClient.fetchNpcCombatStats(pageName, combatLevel, stats ->
                        clientThread.invoke(() ->
                        {
                            Map<String, BufferedImage> skillIcons = new HashMap<>();
                            skillIcons.put("hitpoints", skillIconManager.getSkillImage(Skill.HITPOINTS));
                            skillIcons.put("attack", skillIconManager.getSkillImage(Skill.ATTACK));
                            skillIcons.put("strength", skillIconManager.getSkillImage(Skill.STRENGTH));
                            skillIcons.put("defence", skillIconManager.getSkillImage(Skill.DEFENCE));
                            skillIcons.put("ranged", skillIconManager.getSkillImage(Skill.RANGED));
                            skillIcons.put("magic", skillIconManager.getSkillImage(Skill.MAGIC));
                            skillIcons.put("prayer", skillIconManager.getSkillImage(Skill.PRAYER));

                            if (stats != null)
                            {
                                String weaknessItemName = (stats.elementalWeaknessType == null
                                        || stats.elementalWeaknessType.trim().isEmpty())
                                        ? "Pure essence"
                                        : stats.elementalWeaknessType.trim() + " rune";

                                BufferedImage weaknessIcon = null;
                                var weaknessResults = itemManager.search(weaknessItemName);
                                if (!weaknessResults.isEmpty())
                                {
                                    var bestMatch = weaknessResults.stream()
                                            .filter(r -> r.getName().equalsIgnoreCase(weaknessItemName))
                                            .findFirst()
                                            .orElse(weaknessResults.get(0));
                                    weaknessIcon = itemManager.getImage(itemManager.canonicalize(bestMatch.getId()), 1, false);
                                }
                                skillIcons.put("elemental_weakness", weaknessIcon);
                            }

                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.displayNpcCombatStats(stats, skillIcons);
                                }
                            });
                        })));
    }

    private void loadNpcDropIconsAndDisplay(List<ItemInfoClient.DropSource> drops, int myGen)
    {
        Set<String> uniqueNames = new LinkedHashSet<>();
        if (drops != null)
        {
            for (ItemInfoClient.DropSource drop : drops)
            {
                if (drop.source != null)
                {
                    uniqueNames.add(drop.source);
                }
            }
        }

        if (uniqueNames.isEmpty())
        {
            SwingUtilities.invokeLater(() ->
            {
                if (navigationGeneration.get() == myGen)
                {
                    panel.setSources(drops, null);
                }
            });
            return;
        }

        Map<String, BufferedImage> knownIcons = new ConcurrentHashMap<>();
        List<String> namesNeedingLookup = new ArrayList<>();
        for (String itemName : uniqueNames)
        {
            Integer cachedId = resolvedItemIdCache.get(itemName);
            if (cachedId != null)
            {
                if (cachedId >= 0)
                {
                    BufferedImage cachedIcon = itemIconCache.get(cachedId);
                    if (cachedIcon != null)
                    {
                        knownIcons.put(itemName, cachedIcon);
                    }
                }

            }
            else
            {
                namesNeedingLookup.add(itemName);
            }
        }

        SwingUtilities.invokeLater(() ->
        {
            if (navigationGeneration.get() == myGen)
            {
                panel.setSourcesWithLoadingIcons(drops, null, knownIcons);
            }
        });

        final int SEQUENTIAL_THRESHOLD = 75;
        if (namesNeedingLookup.size() <= SEQUENTIAL_THRESHOLD)
        {
            for (String itemName : namesNeedingLookup)
            {
                resolveAndApplyDropIcon(itemName, myGen, null);
            }
        }
        else
        {

            scheduleNextIconLookup(namesNeedingLookup, 0, myGen);
        }
    }

    private static final int ICON_LOOKUP_DELAY_MS = 150;

    private void resolveAndApplyMaterialIcon(String itemName, int myGen)
    {
        itemInfoClient.resolveItemIdByName(itemName, resolvedId ->
        {
            if (resolvedId == null)
            {
                return;
            }
            clientThread.invoke(() ->
            {
                BufferedImage image = itemManager.getImage(resolvedId, 1, false);
                if (image != null)
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.updateMaterialIcon(itemName, image);
                        }
                    });
                }
            });
        });
    }

    private void resolveAndApplyDropIcon(String itemName, int myGen, Runnable onDone)
    {
        itemInfoClient.resolveItemIdByName(itemName, resolvedId ->
        {
            resolvedItemIdCache.put(itemName, resolvedId != null ? resolvedId : -1);

            if (resolvedId != null)
            {
                clientThread.invoke(() ->
                {
                    BufferedImage image = itemManager.getImage(resolvedId, 1, false);
                    if (image != null)
                    {
                        itemIconCache.put(resolvedId, image);
                        SwingUtilities.invokeLater(() ->
                        {
                            if (navigationGeneration.get() == myGen)
                            {
                                panel.updateDropIcon(itemName, image);
                            }
                        });
                    }
                });
            }

            if (onDone != null)
            {
                onDone.run();
            }
        });
    }

    private void scheduleNextIconLookup(List<String> namesNeedingLookup, int index, int myGen)
    {
        if (index >= namesNeedingLookup.size())
        {
            return;
        }

        String itemName = namesNeedingLookup.get(index);
        resolveAndApplyDropIcon(itemName, myGen, () ->
        {

            new java.util.Timer().schedule(new java.util.TimerTask()
            {
                @Override
                public void run()
                {
                    scheduleNextIconLookup(namesNeedingLookup, index + 1, myGen);
                }
            }, ICON_LOOKUP_DELAY_MS);
        });
    }

    private BufferedImage skillIconForDropType(String dropType)
    {
        if (dropType == null)
        {
            return null;
        }

        try
        {
            Skill skill = Skill.valueOf(dropType.trim().toUpperCase());
            return skillIconManager.getSkillImage(skill);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private BufferedImage skillImageForSkillName(String skillName)
    {
        if (skillName == null)
        {
            return null;
        }

        try
        {
            Skill skill = Skill.valueOf(skillName.trim().toUpperCase());
            return skillIconManager.getSkillImage(skill);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private int resolveGameId(String category, int menuIdentifier)
    {
        if (category.equals("NPC"))
        {
            for (NPC npc : client.getNpcs())
            {
                if (npc.getIndex() == menuIdentifier)
                {
                    return npc.getId();
                }
            }
            return -1;
        }
        else if (category.equals("OBJECT"))
        {
            return menuIdentifier;
        }
        else
        {

            return menuIdentifier;
        }
    }

    private int resolveNpcCombatLevel(int menuIdentifier)
    {
        for (NPC npc : client.getNpcs())
        {
            if (npc.getIndex() == menuIdentifier)
            {
                return npc.getCombatLevel();
            }
        }
        return -1;
    }

    private int findExactNameMatchInContainer(InventoryID inventoryId, String targetName)
    {
        ItemContainer container = client.getItemContainer(inventoryId);
        if (container == null)
        {
            return -1;
        }

        for (Item item : container.getItems())
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }

            int canonicalId = itemManager.canonicalize(item.getId());
            if (itemManager.getItemComposition(canonicalId).getName().equalsIgnoreCase(targetName))
            {
                return canonicalId;
            }
        }

        return -1;
    }

    private void handleWikiClick(String category, String name, int menuIdentifier, int itemId)
    {
        final int myGen = navigationGeneration.incrementAndGet();
        final int gameId = resolveGameId(category, menuIdentifier);

        if (!category.equals("ITEM"))
        {
            recordOriginalView(category, name, gameId);
        }

        final int playerCombatLevel = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getCombatLevel() : -1;

        final String cargoTable;
        if (category.equals("NPC"))
        {
            cargoTable = "npc_id";
        }
        else if (category.equals("OBJECT"))
        {
            cargoTable = "object_id";
        }
        else
        {
            cargoTable = "item_id";
        }

        if (category.equals("ITEM"))
        {

            BufferedImage image = null;
            int price = 0;
            int highAlch = 0;
            int lowAlch = 0;
            int resolvedItemId = -1;

            if (itemId >= 0)
            {
                int candidateId = itemManager.canonicalize(itemId);
                if (itemManager.getItemComposition(candidateId).getName().equalsIgnoreCase(name))
                {
                    resolvedItemId = candidateId;
                }
            }

            if (resolvedItemId < 0)
            {
                resolvedItemId = findExactNameMatchInContainer(InventoryID.EQUIPMENT, name);
            }

            if (resolvedItemId < 0)
            {
                resolvedItemId = findExactNameMatchInContainer(InventoryID.INVENTORY, name);
            }

            if (resolvedItemId < 0)
            {
                var results = itemManager.search(name);
                if (!results.isEmpty())
                {
                    var bestMatch = results.stream()
                            .filter(r -> r.getName().equalsIgnoreCase(name))
                            .findFirst()
                            .orElse(results.get(0));
                    resolvedItemId = itemManager.canonicalize(bestMatch.getId());
                }
            }

            if (resolvedItemId >= 0)
            {
                image = itemManager.getImage(resolvedItemId, 1, false);
                price = itemManager.getItemPrice(resolvedItemId);

                var comp = itemManager.getItemComposition(resolvedItemId);
                highAlch = comp.getHaPrice();
                lowAlch = (int) (comp.getPrice() * 0.4);
            }

            final BufferedImage finalImage = image;
            final int finalPrice = price;
            final int finalHighAlch = highAlch;
            final int finalLowAlch = lowAlch;
            final int finalItemId = resolvedItemId;

            recordOriginalView("ITEM", name, finalItemId);

            SwingUtilities.invokeLater(() ->
            {
                if (navigationGeneration.get() != myGen)
                {
                    return;
                }
                clientToolbar.openPanel(navButton);
                panel.showItem(name, finalImage, finalPrice, finalHighAlch, finalLowAlch);
                panel.setPlayerCombatLevel(playerCombatLevel);

                fetchMarketForPanel(finalItemId, name, myGen);

                itemInfoClient.resolveExactPageName(cargoTable, finalItemId, exactName ->
                {
                    final String pageName = (exactName != null) ? exactName : name;

                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setWikiPageName(pageName);
                        }
                    });

                    itemInfoClient.fetchDescription(pageName, desc ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setDescription(desc);
                                }
                            }));

                    itemInfoClient.fetchInfobox(pageName, finalItemId, info ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setItemProperties(info);
                                    setupCombatStatsButton(pageName, info.equipable, finalItemId, myGen);
                                }
                            }));

                    if (finalImage == null)
                    {
                        itemInfoClient.fetchImage(pageName, wikiImage ->
                                SwingUtilities.invokeLater(() ->
                                {
                                    if (navigationGeneration.get() == myGen)
                                    {
                                        panel.setImage(wikiImage);
                                    }
                                }));
                    }

                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() != myGen)
                        {
                            return;
                        }
                        wireItemSourcesLoader(pageName, finalItemId, myGen);
                    });
                });
            });
        }
        else if (category.equals("NPC") || category.equals("OBJECT"))
        {
            SwingUtilities.invokeLater(() ->
            {
                if (navigationGeneration.get() != myGen)
                {
                    return;
                }
                clientToolbar.openPanel(navButton);
                panel.showNonItem(name);
                panel.setPlayerCombatLevel(playerCombatLevel);

                itemInfoClient.resolveExactPageName(cargoTable, gameId, exactName ->
                {
                    final String pageName = (exactName != null) ? exactName : name;

                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setWikiPageName(pageName);
                        }
                    });

                    itemInfoClient.fetchDescription(pageName, desc ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setDescription(desc);
                                }
                            }));
                    itemInfoClient.fetchImage(pageName, wikiImage ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setImage(wikiImage);
                                }
                            }));

                    if (category.equals("NPC"))
                    {
                        itemInfoClient.fetchNpcInfobox(pageName, gameId, info ->
                                SwingUtilities.invokeLater(() ->
                                {
                                    if (navigationGeneration.get() == myGen)
                                    {
                                        panel.setNpcProperties(info);
                                    }
                                }));

                        clientThread.invoke(() ->
                        {
                            int npcCombatLevel = resolveNpcCombatLevel(menuIdentifier);
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    setupNpcCombatStats(pageName, npcCombatLevel, myGen);
                                }
                            });
                        });

                        SwingUtilities.invokeLater(() ->
                        {
                            if (navigationGeneration.get() != myGen)
                            {
                                return;
                            }
                            panel.setShopsSectionVisible(false);
                            panel.setNpcDropsMode(true);
                            panel.setSourcesLoader(() ->
                                    itemInfoClient.fetchNpcDrops(pageName, drops ->
                                            loadNpcDropIconsAndDisplay(drops, myGen)));
                        });
                    }
                    else
                    {
                        itemInfoClient.fetchObjectInfobox(pageName, gameId, info ->
                                SwingUtilities.invokeLater(() ->
                                {
                                    if (navigationGeneration.get() == myGen)
                                    {
                                        panel.setObjectProperties(info);
                                    }
                                }));

                        panel.setCombatStatsSectionVisible(false);
                        panel.setCombatStatsAvailable(null);
                    }
                });
            });
        }
    }

    private String stripSubLocationForNav(String name)
    {
        int idx = name.indexOf(" (");
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private String stripTrailingParenthetical(String name)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*\\([^)]*\\))\\s*\\([^)]*\\)$").matcher(name.trim());
        if (!matcher.matches())
        {
            return null;
        }
        return matcher.group(1).trim();
    }

    private void showNpcByName(String npcName, int gameId, boolean recordHistory, int combatLevel)
    {
        if (recordHistory)
        {
            recordLinkedView();
        }
        final int myGen = navigationGeneration.incrementAndGet();

        if (gameId >= 0)
        {
            itemInfoClient.resolveExactPageName("npc_id", gameId, exactName ->
                    displayNpc((exactName != null) ? exactName : npcName, myGen, combatLevel));
        }
        else
        {
            displayNpc(npcName, myGen, combatLevel);
        }
    }

    private void displayNpc(String npcName, int myGen, int combatLevel)
    {

        SwingUtilities.invokeLater(() ->
        {
            if (navigationGeneration.get() != myGen)
            {
                return;
            }
            clientToolbar.openPanel(navButton);
            panel.showNonItem(npcName);
            panel.setWikiPageName(npcName);
            setupNpcCombatStats(npcName, combatLevel, myGen);

            clientThread.invoke(() ->
            {
                int playerCombatLevel = client.getLocalPlayer() != null
                        ? client.getLocalPlayer().getCombatLevel() : -1;
                SwingUtilities.invokeLater(() ->
                {
                    if (navigationGeneration.get() == myGen)
                    {
                        panel.setPlayerCombatLevel(playerCombatLevel);
                    }
                });
            });

            itemInfoClient.fetchDescription(npcName, desc ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setDescription(desc);
                        }
                    }));
            itemInfoClient.fetchImage(npcName, wikiImage ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setImage(wikiImage);
                        }
                    }));
            itemInfoClient.fetchNpcInfobox(npcName, -1, info ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setNpcProperties(info);
                        }
                    }));

            panel.setShopsSectionVisible(false);
            panel.setNpcDropsMode(true);
            panel.setSourcesLoader(() ->
                    itemInfoClient.fetchNpcDrops(npcName, drops ->
                            loadNpcDropIconsAndDisplay(drops, myGen)));
        });
    }

    private void showObjectByName(String objectName, int gameId, boolean recordHistory)
    {
        if (recordHistory)
        {
            recordLinkedView();
        }
        final int myGen = navigationGeneration.incrementAndGet();

        if (gameId >= 0)
        {
            itemInfoClient.resolveExactPageName("object_id", gameId, exactName ->
                    displayObject((exactName != null) ? exactName : objectName, myGen));
        }
        else
        {
            displayObject(objectName, myGen);
        }
    }

    private void displayObject(String objectName, int myGen)
    {

        SwingUtilities.invokeLater(() ->
        {
            if (navigationGeneration.get() != myGen)
            {
                return;
            }
            clientToolbar.openPanel(navButton);
            panel.showNonItem(objectName);
            panel.setWikiPageName(objectName);
            panel.setCombatStatsSectionVisible(false);
            panel.setCombatStatsAvailable(null);

            itemInfoClient.fetchDescription(objectName, desc ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setDescription(desc);
                        }
                    }));
            itemInfoClient.fetchImage(objectName, wikiImage ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setImage(wikiImage);
                        }
                    }));
            itemInfoClient.fetchObjectInfobox(objectName, -1, info ->
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() == myGen)
                        {
                            panel.setObjectProperties(info);
                        }
                    }));

            panel.setShopsSectionVisible(false);
            panel.setNpcDropsMode(true, "Rewards");
            panel.setSourcesLoader(() ->
                    itemInfoClient.fetchNpcDrops(objectName, drops ->
                            loadNpcDropIconsAndDisplay(drops, myGen)));
        });
    }

    private void showItemByName(String itemName, int gameId, boolean recordHistory)
    {
        if (recordHistory)
        {
            recordLinkedView();
        }
        final int myGen = navigationGeneration.incrementAndGet();

        if (gameId >= 0)
        {
            proceedWithItemId(gameId, itemName, myGen);
        }
        else
        {
            itemInfoClient.resolveItemIdByName(itemName, resolvedId ->
            {
                if (resolvedId == null)
                {
                    log.warn("Could not resolve item id for {}", itemName);
                    return;
                }
                proceedWithItemId(resolvedId, itemName, myGen);
            });
        }
    }

    private void proceedWithItemId(int itemId, String fallbackName, int myGen)
    {
        itemInfoClient.resolveExactPageName("item_id", itemId, exactName ->
        {
            final String pageName = (exactName != null) ? exactName : fallbackName;

            clientThread.invoke(() ->
            {
                BufferedImage image = itemManager.getImage(itemId, 1, false);
                int price = itemManager.getItemPrice(itemId);
                var comp = itemManager.getItemComposition(itemId);
                int highAlch = comp.getHaPrice();
                int lowAlch = (int) (comp.getPrice() * 0.4);
                String realName = comp.getName();

                final BufferedImage finalImage = image;
                final int finalPrice = price;
                final int finalHighAlch = highAlch;
                final int finalLowAlch = lowAlch;

                SwingUtilities.invokeLater(() ->
                {
                    if (navigationGeneration.get() != myGen)
                    {
                        return;
                    }
                    clientToolbar.openPanel(navButton);
                    panel.showItem(realName, finalImage, finalPrice, finalHighAlch, finalLowAlch);
                    panel.setWikiPageName(pageName);

                    fetchMarketForPanel(itemId, realName, myGen);

                    itemInfoClient.fetchDescription(pageName, desc ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setDescription(desc);
                                }
                            }));

                    itemInfoClient.fetchInfobox(pageName, itemId, info ->
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setItemProperties(info);
                                    setupCombatStatsButton(pageName, info.equipable, itemId, myGen);
                                }
                            }));

                    if (finalImage == null)
                    {
                        itemInfoClient.fetchImage(pageName, wikiImage ->
                                SwingUtilities.invokeLater(() ->
                                {
                                    if (navigationGeneration.get() == myGen)
                                    {
                                        panel.setImage(wikiImage);
                                    }
                                }));
                    }

                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() != myGen)
                        {
                            return;
                        }
                        wireItemSourcesLoader(pageName, itemId, myGen);
                    });
                });
            });
        });
    }
}