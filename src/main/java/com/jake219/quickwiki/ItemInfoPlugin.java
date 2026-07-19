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

    /**
     * Tracks whether Ctrl is currently held down - checked in onMenuEntryAdded, both to
     * decide whether to add the "Wiki" entry at all, and to reorder it to the front of the
     * menu array (RuneLite's default left-click action) immediately after adding it.
     */
    private volatile boolean hotkeyHeld = false;

    /**
     * Tracks the most recent "Examine" target seen in onMenuEntryAdded, tagged with the
     * game tick it was seen on. Exists specifically for examine-only objects (decorative
     * scenery with no other interactions) - holding the hotkey and clicking one used to
     * do nothing, because the object has no real interactive menu entry to fall back to:
     * the actual default left-click action for these is a plain "Walk here" tile click
     * (actionName=WALK), not GAME_OBJECT_* or EXAMINE_OBJECT the way objects with real
     * options produce. Since menu entries are rebuilt every tick based on current hover,
     * an Examine entry for the object under the cursor fires on the same tick as the
     * click that follows it - so onMenuOptionClicked can treat a WALK click as "clicked
     * this object" if (and only if) this record is from the current tick, avoiding
     * hijacking an unrelated plain walk-to-empty-ground click using stale hover data.
     */
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

    /**
     * Tracks the ORIGINAL item/NPC/object the user right-clicked "Wiki" on, so the back
     * button can return to it regardless of how many Drops-list links were clicked since -
     * this is one-level "back to start", not a full multi-page history.
     */
    private String originalCategory;
    private String originalPageName;
    private int originalGameId;
    private boolean viewingLinkedPage = false;

    /**
     * Incremented at the start of every navigation (fresh right-click, Drops-list link
     * click, or Back). Async wiki fetches capture the generation that was current when they
     * were fired; if it's no longer current by the time a response arrives, the response is
     * discarded instead of being applied to the panel. Without this, clicking Back (or
     * clicking another link) before a previous page's fetches finish could let a late-
     * arriving response from the page you already left overwrite the page you're now on.
     */
    private final AtomicInteger navigationGeneration = new AtomicInteger(0);

    /**
     * Called at the start of every fresh right-click "Wiki" action - this always resets
     * what "original" means, and hides the back button since a brand new examine has no
     * "back" target yet. gameId is kept so goBack() can re-resolve the exact wiki page
     * name (handling redirects/capitalization) the same way the original right-click did,
     * rather than assuming the raw examine-text name is already an exact match.
     */
    private void recordOriginalView(String category, String name, int gameId)
    {
        originalCategory = category;
        originalPageName = name;
        originalGameId = gameId;
        viewingLinkedPage = false;
        SwingUtilities.invokeLater(() -> panel.setBackButtonVisible(false));
    }

    /**
     * Called when a Drops-list name is clicked (navigating away from the original view via
     * a link) - shows the back button, since there's now somewhere to return to.
     */
    private void recordLinkedView()
    {
        viewingLinkedPage = true;
        SwingUtilities.invokeLater(() -> panel.setBackButtonVisible(true));
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

        // Drop-table rows (both directions - monster names under an item, item names under
        // an NPC) are clickable to navigate straight to that thing's own wiki info, the
        // same as if it had been examined directly. The panel only knows the clicked name
        // and which mode it was in when clicked; the actual resolve-and-display logic needs
        // client/item-manager access the panel doesn't have, so it lives here.
        panel.setDropRowClickListener((clickedName, levelStr) ->
        {
            if (panel.isNpcDropsMode())
            {
                showItemByName(clickedName, -1, true);
            }
            else
            {
                // Some "sources" in an item's own drop table are themselves items, not
                // monsters - Reward caskets, Mystery box, and similar containers are
                // tracked in the same dropsline bucket as actual monsters. Checking
                // whether the name resolves as a real item first - rather than
                // hardcoding a list of known container names, which would inevitably
                // miss some - handles this whole category generally.
                itemInfoClient.resolveExactItemIdStrict(clickedName, resolvedItemId ->
                {
                    if (resolvedItemId != null)
                    {
                        showItemByName(clickedName, resolvedItemId, true);
                        return;
                    }

                    // Some sources are world objects/scenery rather than items or
                    // monsters - e.g. "Chest (Tombs of Amascut)" for raid uniques.
                    // Checked before falling back to NPC, same reasoning as the item
                    // check above: without this, an object source falls through to an
                    // NPC search that doesn't match anything real.
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
                            // Some drop rows' "level" field isn't a plain number (e.g.
                            // "-" for a universal drop with no level restriction) -
                            // fall back to -1 (unknown).
                        }
                        // Only strip a sub-location suffix (e.g. "Cyclops (Warriors'
                        // Guild Basement)" -> "Cyclops") now that the raw, unstripped
                        // name has already been tried as a real item and object and
                        // failed both - stripping earlier would cut "Reward casket
                        // (hard)" down to just "Reward casket", which isn't a real,
                        // resolvable item on its own.
                        showNpcByName(stripSubLocationForNav(clickedName), -1, true, combatLevel);
                    });
                });
            }
        });
        panel.setBackButtonListener(this::goBack);

        // Bundled resource, not fetched at runtime - loads instantly and reliably, unlike
        // an async itemManager.getImage() fetch which could show the hand-drawn fallback
        // for the first item or two examined right after the plugin loads.
        final BufferedImage geIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/ge_icon.png");
        panel.setCoinIcon(geIcon);

        // Same bundled-resource approach as the GE icon above - these three didn't have a
        // safe sprite constant to fetch instead, so real cropped screenshots are used.
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

        // The equipment-weight and checkbox/spell sprites are static - the same every time -
        // so fetch them once here rather than re-fetching per item. All hop to the client
        // thread since they touch game resources, then back to Swing to hand off to the
        // panel; if any comes back null the panel just keeps its existing hand-drawn
        // fallback icon instead of showing nothing. Wrapped defensively so a failure here
        // can never affect anything else in the plugin.
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
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch weight/checkbox icons", e);
        }
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(hotkeyListener);
        clientToolbar.removeNavigation(navButton);
    }

    /**
     * Keeps the panel's tooltip setting in sync when the user changes it live in the
     * plugin's config panel, rather than requiring a client restart for it to take
     * effect. Only affects the "Tooltips" option here, but checks the group/key so this
     * doesn't fire needless work for unrelated config changes from other plugins.
     */
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

        // When hotkey mode is on, "Wiki" is only added to the menu while Ctrl is actively
        // held - during normal use it's skipped entirely, per the original Reddit request
        // to remove menu clutter once a hotkey exists as an alternative.
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

        // Reordering happens right here, immediately after adding our entry, rather than
        // in a separate MenuOpened subscriber - MenuOpened only fires when a menu is
        // actually visually opened via right-click, not for a plain left-click, which
        // never shows a popup and just directly executes whatever's last in the array.
        // Doing it here means the array is already in its final default-action order by
        // the time any click happens, regardless of whether MenuOpened ever fires.
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

    /**
     * Fallback safety net for hotkey mode - the primary mechanism above (add a "Wiki"
     * entry, reorder it to the end of the array) can be raced by another plugin adding its
     * own entry afterward on the same tick, which would push "Wiki" back out of the
     * default-action position before the actual click happens.
     * <p>
     * This catches whatever actually got left-clicked instead (which may not be "Wiki" if
     * the reorder got undone) and, if the hotkey is held and it's a real item/NPC/object
     * interaction, redirects it to Quick Wiki instead of letting the original action
     * happen. Matches by MenuAction name prefix rather than an exhaustive enum list, since
     * the actual default action varies a lot by target (Walk here, Attack, Talk-to, Take,
     * Use, etc.).
     */
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
            // The examine-only-object case: this target has no real interactive menu
            // entry at all, so the actual default left-click action is a plain "Walk
            // here" tile click rather than anything object-specific. Since an Examine
            // entry for the object under the cursor was seen on this exact same tick
            // (see lastExamineTick's own comment), treat this WALK as "clicked that
            // object" rather than a real walk, using the info captured then rather than
            // anything from this WALK event itself (which doesn't carry object identity
            // the way GAME_OBJECT_* or EXAMINE_OBJECT clicks do).

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
            // CC_OP ("Client Component" - a widget/interface interaction) is the real
            // action type for clicking an item inside a widget like the inventory, as
            // opposed to something in the game world. CC_OP is also used for lots of
            // other non-item widget interactions (spells, prayers, settings toggles), so
            // this requires a valid item ID too, rather than matching CC_OP alone -
            // otherwise holding the hotkey could hijack unrelated interface clicks.
            // WIDGET_TARGET is the real action type for "Use"-default items (e.g. Zulrah
            // scales, Marks of grace) - clicking one normally starts a "Use X on Y"
            // target-selection mode rather than doing anything immediately, which this
            // consumes before that mode ever starts.
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

    /**
     * Caches item name -> resolved ID across the whole plugin session, not just within one
     * drop table load. Common items (Coins, runes, bones) show up in dozens of different
     * monsters' drop tables, so without this every examine re-asks the wiki for something
     * already resolved. -1 is used as a sentinel for "confirmed unresolvable" (e.g. Clue
     * scroll (elite), which the wiki's bucket has no clean canonical ID for), so we don't
     * keep re-attempting a lookup we already know won't succeed.
     */
    private final Map<String, Integer> resolvedItemIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, BufferedImage> itemIconCache = new ConcurrentHashMap<>();

    /**
     * Sets up the lazy Combat Stats loader for an equipable item - the accordion section
     * is always present regardless of what this determines, and shows "No combat stats
     * available" itself if the fetch comes back empty, so this only needs a cheap
     * pre-filter (skip the query entirely for obviously non-equipable items like food or
     * potions) rather than eagerly fetching just to decide whether to show a button. The
     * actual fetch only fires when the user expands the accordion, not on every examine.
     */
    private void setupCombatStatsButton(String pageName, String equipable, int itemId, int myGen)
    {
        panel.setCombatStatsSectionVisible(true);
        boolean isEquipable = equipable != null && equipable.trim().equalsIgnoreCase("Yes");
        if (!isEquipable)
        {
            panel.setCombatStatsAvailable(null);
            return;
        }

        panel.setCombatStatsAvailable(() ->
                itemInfoClient.fetchCombatBonuses(pageName, itemId, bonuses ->
                        clientThread.invoke(() ->
                        {
                            // getSkillImage() is a local lookup (unlike the drop-table
                            // item icons), so no async/loading-state handling is needed.
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

    /**
     * NPC version of setupCombatStatsButton - always sets up a loader rather than
     * pre-filtering like the item side does with "equipable", since there's no similarly
     * cheap, already-fetched signal for "is this NPC attackable" available at this point.
     * The fetch itself is still lazy (only fires when the accordion is expanded), and
     * displayNpcCombatStats already shows "No combat stats available" for non-combat NPCs
     * where the query comes back empty, so the cost of skipping a pre-filter here is just
     * one query for non-attackable NPCs a user actually chooses to expand this for, not
     * one fired automatically on every NPC examine.
     */
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

                            // Resolved via itemManager.search rather than a hardcoded item
                            // ID, since rune item IDs found from a quick search weren't
                            // from an authoritative source - this reuses the same
                            // search-and-match pattern already used elsewhere in this
                            // plugin for resolving real item icons (see handleWikiClick).
                            // Guarded by stats != null - this NPEs for any NPC with no
                            // combat data at all (stats is null in that case).
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

    /**
     * Resolves and fetches an icon for every unique item name in an NPC's drop list, then
     * displays the drops with those icons attached. Deduped by name first since a drop
     * table can list the same item many times at different quantities/rarities (e.g.
     * Goblin's "Coins" appears 5 times across its two drop tables). Uses the same
     * navigationGeneration guard as every other async panel update, so a slow-resolving
     * icon batch from a page the user has already left can't overwrite the current one.
     */
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

        // Split into "already known" (cache hit, either a real icon or a confirmed
        // failure) vs "needs an actual lookup" - cached icons are shown immediately below,
        // only genuinely new lookups get a per-row spinner.
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
                // cachedId == -1 (confirmed unresolvable) needs nothing further
            }
            else
            {
                namesNeedingLookup.add(itemName);
            }
        }

        // Rows render right away - cached icons appear immediately, anything still being
        // looked up shows a spinner instead of leaving the whole table waiting on the
        // slowest item. Each remaining icon gets applied individually via updateDropIcon()
        // as its own lookup finishes, rather than one big wait before anything appears.
        SwingUtilities.invokeLater(() ->
        {
            if (navigationGeneration.get() == myGen)
            {
                panel.setSourcesWithLoadingIcons(drops, null, knownIcons);
            }
        });

        // Small lists (a normal NPC's own drop table) never had a rate-limiting problem
        // with firing every lookup at once - that's fast, no reason to slow it down.
        // Only large lists (a reward casket's ~150-190 unique reward items) risk
        // flooding the API, so only those pay the sequential-queue slowdown below.
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
            // Fully sequential rather than batched - resolveItemIdByName's own internal
            // fallback chain (exact match -> infobox_item -> case-toggle -> bare-base-
            // name) fires each subsequent step immediately with no delay, so batching
            // "outer" items still let far more requests burst out than the batch size
            // implied. Processing one item's entire resolution to completion before
            // starting the next - with a short delay after - guarantees at most one
            // request in flight at any moment.
            scheduleNextIconLookup(namesNeedingLookup, 0, myGen);
        }
    }

    private static final int ICON_LOOKUP_DELAY_MS = 150;

    /**
     * The fast-path version used for small lists - resolves one item's icon and applies
     * it, with no throttling relative to any other call. Shared with the sequential queue
     * below (via the onDone callback) so the actual resolution logic isn't duplicated
     * between the two paths.
     *
     * @param onDone called once this item's resolution (including all of its own internal
     *               fallback attempts) has fully finished - null for the fast path, which
     *               doesn't need to know when to start the next one since it starts them
     *               all immediately.
     */
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
            // Only schedules the next lookup once this one (including all of its own
            // internal fallback attempts) has fully finished - this is what actually
            // guarantees sequential, non-overlapping requests, rather than just spacing
            // out when each "top-level" lookup starts.
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

    /**
     * Maps the wiki's "Drop type" field (e.g. "mining", "hunter", "fishing") to the
     * matching RuneLite {@link Skill} and returns its real game icon, for skilling-type
     * item sources where a bare level number doesn't mean much without knowing which
     * skill it's for (e.g. a Coal rock's "level 30" is a Mining level). Returns null for
     * "combat", "reward", or anything else that isn't an actual skill name.
     */
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

    /**
     * Resolves the precise in-game ID for the thing that was actually clicked, so we can
     * do an exact wiki lookup instead of guessing from the (often ambiguous) display name.
     * <p>
     * For NPCs, the menu identifier is the NPC's index in the world's NPC list, not its
     * composition ID - so we look the NPC up by index to get its real ID. For objects, the
     * menu identifier is already the object's ID directly.
     *
     * @return the resolved game ID, or -1 if it couldn't be determined
     */
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
            // Items resolve their real ID via event.getItemId() in handleWikiClick instead,
            // since the menu identifier here isn't reliably the item's real ID.
            return menuIdentifier;
        }
    }

    /**
     * Resolves the actual in-game combat level of the specific NPC instance that was
     * clicked, using the same live client.getNpcs() lookup as resolveGameId above. Exists
     * to fix incorrect combat stats for multi-form NPCs (e.g. Dark wizard - 5 different
     * combat-level variants sharing one wiki page): without this, fetchNpcCombatStats's
     * bucket query had no way to tell the forms apart. Returns -1 if the NPC can't be
     * found live (e.g. reached via "Back" or a Drops-list link, where there's no live NPC
     * object to query) - fetchNpcCombatStats treats -1 as "unknown".
     */
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

    /**
     * Searches a specific item container (equipment or inventory) for an item whose real
     * composition name exactly matches what was clicked, returning its canonicalized ID.
     * This is a defensive cross-check for cases where the menu event's reported item ID
     * doesn't match reality (observed with equipped chargeable gear).
     *
     * @return the matching item's canonicalized ID, or -1 if no exact match was found
     */
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
        // Items are recorded later, once resolvedItemId is known (see below) - gameId here
        // is just the raw menu identifier for items, which is NOT reliably the item's real
        // ID (see resolveGameId's own comment on this). Recording it here caused "Back" to
        // sometimes land on a completely unrelated item.
        if (!category.equals("ITEM"))
        {
            recordOriginalView(category, name, gameId);
        }

        // Reading the player's combat level here (rather than later, e.g. inside the lazy
        // sources loader) is important: this method already runs on the client thread via
        // clientThread.invoke, so it's safe to touch client state directly - unlike the
        // sources loader itself, which runs later on the Swing/EDT thread, where touching
        // client state would not be safe.
        // <p>
        // This used to be computed only inside the ITEM branch below, so
        // panel.setPlayerCombatLevel() was never called when viewing an NPC - meaning the
        // NPC Properties "Combat level" row only got proper color-coding if the player had
        // already looked up an item earlier in the same session. Moved here so it's set
        // consistently regardless of category.
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
            // itemId comes straight from MenuEntryAdded.getItemId(). This is reliable for
            // inventory items, but for equipped gear it can sometimes report the item's base
            // "display" ID rather than the actual charged/uncharged variant sitting in the
            // equipment slot (e.g. a charged Pendant of ates resolving as the uncharged one).
            // We verify the resolved item's real name matches what was actually clicked, and
            // if not, search the player's equipment and inventory directly for an exact name
            // match before falling back to a broader text search.
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

            // playerCombatLevel is now computed once at the top of handleWikiClick,
            // shared across all categories - see that comment for the bug this fixes.

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

                    // Lazily wired up: only fires the drop/shop bucket queries the first
                    // time the user actually expands the "item sources" section.
                    SwingUtilities.invokeLater(() ->
                    {
                        if (navigationGeneration.get() != myGen)
                        {
                            return;
                        }
                        boolean isRewardCasket = itemInfoClient.isRewardCasketName(pageName);
                        panel.setShopsSectionVisible(!isRewardCasket);
                        panel.setNpcDropsMode(isRewardCasket, isRewardCasket ? "Rewards" : "Drops");
                        panel.setSourcesLoader(() ->
                                itemInfoClient.fetchItemSources(pageName, finalItemId, sources ->
                                        clientThread.invoke(() ->
                                        {
                                            for (ItemInfoClient.DropSource drop : sources.drops)
                                            {
                                                drop.skillIcon = skillIconForDropType(drop.dropType);
                                            }
                                            if (isRewardCasket)
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
                                                    }
                                                });
                                            }
                                        })));
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

                        // showNonItem() clears the sources loader by default (Objects don't
                        // have drops), so it's re-enabled here specifically for NPCs - same
                        // Item Sources > Drops UI as items use, just fetching the monster's
                        // own drop table instead of "which monsters drop this item". Shops
                        // is hidden entirely (not just empty) since NPCs aren't sold anywhere.
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

    /**
     * Strips a sub-location suffix like " (Wilderness Slayer Cave)" before using a clicked
     * drop-row name for NPC navigation - that suffix was artificially added by
     * ItemInfoPanel's formatSourceName for monster names with regional variants (e.g.
     * "Cyclops" in the Warriors' Guild Basement), not part of the monster's own real name.
     * <p>
     * Only applied as a fallback after the raw, unstripped name has already been tried as
     * a real item and failed - stripping unconditionally, before that item check, cuts
     * "Reward casket (hard)" down to just "Reward casket", which isn't a real, resolvable
     * item, causing container-type drop sources to get misidentified as monsters.
     */
    private String stripSubLocationForNav(String name)
    {
        int idx = name.indexOf(" (");
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /**
     * Shows an NPC's wiki info by name - either directly (gameId == -1, e.g. reached via a
     * Drops-list link click, where the name is already the wiki's exact page_name) or by
     * first resolving the exact page name from a real gameId (e.g. going back to an
     * originally right-clicked NPC).
     */
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

    /**
     * @param combatLevel a real, known combat level if available (e.g. from a drop-table
     *                    row's own "level" field - see ItemInfoPanel's dropRowClickListener
     *                    for why that's a reliable proxy), or -1 if unknown (e.g. reached
     *                    via "Back", where no such context exists). Threading a real value
     *                    through here, rather than always passing -1, fixes multi-form
     *                    monsters (e.g. Thermonuclear smoke devil, Iron dragons) showing no
     *                    combat stats when reached via a drop-table link - the old
     *                    always-(-1) behavior skipped the combat-level-filtered query and
     *                    fell back to a page_name-only lookup that can't disambiguate
     *                    between a monster's multiple forms.
     */
    private void displayNpc(String npcName, int myGen, int combatLevel)
    {
        // This is called from resolveExactPageName's callback, which fires on OkHttp's
        // background dispatcher thread, not the EDT - clientToolbar.openPanel() and the
        // panel.* calls below all require the EDT (openPanel throws an AssertionError
        // otherwise), which is why this whole body needs to be wrapped here rather than
        // just the individual fetch callbacks below (which were already correctly wrapped
        // - it was specifically the immediate synchronous calls that were missing this).
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

            // Same fix as handleWikiClick's shared playerCombatLevel computation - this
            // path (reached via "Back" or a Drops-list link) needs it set here too, so
            // the Properties "Combat level" row's color coding is always correct rather
            // than depending on whether an item was viewed earlier in the session.
            // Wrapped in clientThread.invoke since this method doesn't already run on
            // the client thread the way handleWikiClick does.
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

    /**
     * Shows an object/scenery's wiki info by name - same gameId-aware resolution as
     * showNpcByName above. Only ever reached via goBack() (objects have no Drops list of
     * their own to click a link from), but kept consistent with the other two flows.
     */
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
        // Same EDT-threading fix as displayNpc above - this is called from
        // resolveExactPageName's callback, which fires on OkHttp's background thread.
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

            // Objects had no sources loader wired up at all before this - most objects
            // (trees, doors, decorative scenery) genuinely have nothing here, but some
            // (e.g. "Chest (Tombs of Amascut)", a raid reward chest) are containers with
            // real contents tracked the same way in dropsline. Reuses fetchNpcDrops,
            // which just queries by page_name regardless of whether the "source" is an
            // NPC or an object, plus the same icon-loading path already used for reward
            // casket contents.
            panel.setShopsSectionVisible(false);
            panel.setNpcDropsMode(true, "Rewards");
            panel.setSourcesLoader(() ->
                    itemInfoClient.fetchNpcDrops(objectName, drops ->
                            loadNpcDropIconsAndDisplay(drops, myGen)));
        });
    }

    /**
     * Shows an item's wiki info - either from a real gameId already known (going back to an
     * originally right-clicked item) or by resolving one from the name first (reached via a
     * Drops-list link click). Either way, once an ID is known, the exact wiki page name is
     * resolved from it before any content fetches.
     */
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
                        boolean isRewardCasket = itemInfoClient.isRewardCasketName(pageName);
                        panel.setShopsSectionVisible(!isRewardCasket);
                        panel.setNpcDropsMode(isRewardCasket, isRewardCasket ? "Rewards" : "Drops");
                        panel.setSourcesLoader(() ->
                                itemInfoClient.fetchItemSources(pageName, itemId, sources ->
                                        clientThread.invoke(() ->
                                        {
                                            for (ItemInfoClient.DropSource drop : sources.drops)
                                            {
                                                drop.skillIcon = skillIconForDropType(drop.dropType);
                                            }
                                            if (isRewardCasket)
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
                                                    }
                                                });
                                            }
                                        })));
                    });
                });
            });
        });
    }
}