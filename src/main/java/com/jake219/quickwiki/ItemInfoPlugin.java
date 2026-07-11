package com.jake219.quickwiki;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

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

    private ItemInfoPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp()
    {
        panel = new ItemInfoPanel();

        final BufferedImage icon = createIcon();

        navButton = NavigationButton.builder()
                .tooltip("Quick Wiki")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
    }

    private BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(255, 152, 31));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics metrics = g.getFontMetrics();

        String first = "Q";
        String second = "W";
        int firstWidth = metrics.stringWidth(first);
        int secondWidth = metrics.stringWidth(second);
        int overlap = 0;
        int totalWidth = firstWidth + secondWidth - overlap;

        int x = (16 - totalWidth) / 2;
        int y = (16 - metrics.getHeight()) / 2 + metrics.getAscent();

        g.drawString(first, x, y);
        g.drawString(second, x + firstWidth - overlap, y);

        g.dispose();
        return image;
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
        final int gameId = resolveGameId(category, menuIdentifier);
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

            final BufferedImage finalImage = image;
            final int finalPrice = price;
            final int finalHighAlch = highAlch;
            final int finalLowAlch = lowAlch;
            final int finalItemId = resolvedItemId;

            SwingUtilities.invokeLater(() ->
            {
                clientToolbar.openPanel(navButton);
                panel.showItem(name, finalImage, finalPrice, finalHighAlch, finalLowAlch);

                itemInfoClient.resolveExactPageName(cargoTable, finalItemId, exactName ->
                {
                    final String pageName = (exactName != null) ? exactName : name;

                    itemInfoClient.fetchDescription(pageName, desc ->
                            SwingUtilities.invokeLater(() -> panel.setDescription(desc)));

                    itemInfoClient.fetchInfobox(pageName, finalItemId, info ->
                    {
                        if (info != null)
                        {
                            SwingUtilities.invokeLater(() ->
                            {
                                panel.setInfobox(info.released, info.members, info.questItem);
                                panel.setProperties(info.tradeable, info.equipable, info.stackable, info.noteable, info.options);
                                panel.setValues(info.value, info.weight);
                            });
                        }
                    });

                    if (finalImage == null)
                    {
                        itemInfoClient.fetchImage(pageName, wikiImage ->
                                SwingUtilities.invokeLater(() -> panel.setImage(wikiImage)));
                    }
                });
            });
        }
        else if (category.equals("NPC") || category.equals("OBJECT"))
        {
            SwingUtilities.invokeLater(() ->
            {
                clientToolbar.openPanel(navButton);
                panel.showNonItem(name);

                itemInfoClient.resolveExactPageName(cargoTable, gameId, exactName ->
                {
                    final String pageName = (exactName != null) ? exactName : name;

                    itemInfoClient.fetchDescription(pageName, desc ->
                            SwingUtilities.invokeLater(() -> panel.setDescription(desc)));
                    itemInfoClient.fetchImage(pageName, wikiImage ->
                            SwingUtilities.invokeLater(() -> panel.setImage(wikiImage)));
                });
            });
        }
    }
}
