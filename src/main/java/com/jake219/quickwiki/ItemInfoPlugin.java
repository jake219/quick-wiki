package com.jake219.quickwiki;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
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

    private ItemInfoPanel panel;
    private NavigationButton navButton;

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
                showNpcByName(originalPageName, originalGameId, false);
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
        panel = new ItemInfoPanel();

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
        panel.setDropRowClickListener(clickedName ->
        {
            if (panel.isNpcDropsMode())
            {
                showItemByName(clickedName, -1, true);
            }
            else
            {
                showNpcByName(clickedName, -1, true);
            }
        });
        panel.setBackButtonListener(this::goBack);

        // Bundled resource, not fetched at runtime - loads instantly and reliably, same as
        // the nav icon above, which is why this replaced the earlier itemManager.getImage()
        // approach: that was an async fetch with a real timing race (it could show the
        // hand-drawn fallback for the first item or two examined right after the plugin
        // loads, before the fetch completed). A bundled image has no such window.
        final BufferedImage geIcon = ImageUtil.loadImageResource(getClass(), "/com/jake219/quickwiki/ge_icon.png");
        panel.setCoinIcon(geIcon);

        // Same bundled-resource approach as the GE icon above - these three didn't have a
        // confirmed, safe sprite constant to fetch instead (a "standard damage" hitsplat
        // sprite ID couldn't be verified to actually exist, and guessing one risks a build
        // that doesn't compile), so real cropped screenshots are used instead.
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
        clientToolbar.removeNavigation(navButton);
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

        final String cleanName = event.getTarget().replaceAll("<[^>]*>", "");
        final int menuIdentifier = event.getIdentifier();
        final int itemId = event.getItemId();

        client.createMenuEntry(-1)
                .setOption("Wiki")
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(e -> clientThread.invoke(() -> handleWikiClick(category, cleanName, menuIdentifier, itemId)));
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

            // Reading the player's combat level here (rather than later, e.g. inside the
            // lazy sources loader) is important: this method already runs on the client
            // thread via clientThread.invoke, so it's safe to touch client state directly.
            // The sources loader itself runs later on the Swing/EDT thread when the user
            // expands the section, where touching client state would not be safe.
            final int playerCombatLevel = client.getLocalPlayer() != null
                    ? client.getLocalPlayer().getCombatLevel() : -1;

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
                        panel.setShopsSectionVisible(true);
                        panel.setNpcDropsMode(false);
                        panel.setSourcesLoader(() ->
                                itemInfoClient.fetchItemSources(pageName, sources ->
                                        clientThread.invoke(() ->
                                        {
                                            for (ItemInfoClient.DropSource drop : sources.drops)
                                            {
                                                drop.skillIcon = skillIconForDropType(drop.dropType);
                                            }
                                            SwingUtilities.invokeLater(() ->
                                            {
                                                if (navigationGeneration.get() == myGen)
                                                {
                                                    panel.setSources(sources.drops, sources.shops);
                                                }
                                            });
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

                itemInfoClient.resolveExactPageName(cargoTable, gameId, exactName ->
                {
                    final String pageName = (exactName != null) ? exactName : name;

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
                                            SwingUtilities.invokeLater(() ->
                                            {
                                                if (navigationGeneration.get() == myGen)
                                                {
                                                    panel.setSources(drops, null);
                                                }
                                            })));
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
                    }
                });
            });
        }
    }

    /**
     * Shows an NPC's wiki info by name - either directly (gameId == -1, e.g. reached via a
     * Drops-list link click, where the name is already the wiki's exact page_name from
     * bucket data) or by first resolving the exact page name from a real gameId (e.g. going
     * back to an originally right-clicked NPC) - this second step is what the original
     * right-click flow always did and what showNpcByName was missing before, which is why
     * the image (and potentially description/infobox) could silently fail to load on
     * "back" if the raw examine-text name didn't exactly match the wiki's page title.
     */
    private void showNpcByName(String npcName, int gameId, boolean recordHistory)
    {
        if (recordHistory)
        {
            recordLinkedView();
        }
        final int myGen = navigationGeneration.incrementAndGet();

        if (gameId >= 0)
        {
            itemInfoClient.resolveExactPageName("npc_id", gameId, exactName ->
                    displayNpc((exactName != null) ? exactName : npcName, myGen));
        }
        else
        {
            displayNpc(npcName, myGen);
        }
    }

    private void displayNpc(String npcName, int myGen)
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
                            SwingUtilities.invokeLater(() ->
                            {
                                if (navigationGeneration.get() == myGen)
                                {
                                    panel.setSources(drops, null);
                                }
                            })));
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
        });
    }

    /**
     * Shows an item's wiki info - either from a real gameId already known (going back to an
     * originally right-clicked item) or by resolving one from the name first (reached via a
     * Drops-list link click). Either way, once an ID is known, the exact wiki page name is
     * resolved from it before any content fetches - same fix as the NPC/Object flows above,
     * and also fixes a latent version of this bug that existed even on the initial link-
     * click path (description/infobox/sources were using the raw name directly before).
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
                        panel.setShopsSectionVisible(true);
                        panel.setNpcDropsMode(false);
                        panel.setSourcesLoader(() ->
                                itemInfoClient.fetchItemSources(pageName, sources ->
                                        clientThread.invoke(() ->
                                        {
                                            for (ItemInfoClient.DropSource drop : sources.drops)
                                            {
                                                drop.skillIcon = skillIconForDropType(drop.dropType);
                                            }
                                            SwingUtilities.invokeLater(() ->
                                            {
                                                if (navigationGeneration.get() == myGen)
                                                {
                                                    panel.setSources(sources.drops, sources.shops);
                                                }
                                            });
                                        })));
                    });
                });
            });
        });
    }
}