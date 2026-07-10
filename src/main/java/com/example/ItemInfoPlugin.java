package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
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

    @Provides
    ItemInfoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ItemInfoConfig.class);
    }

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
        g.setColor(new Color(255, 152, 31));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("i", 6, 12);
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
        final int fallbackId = event.getIdentifier();

        client.createMenuEntry(-1)
                .setOption("Wiki")
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(e -> clientThread.invoke(() -> handleWikiClick(category, cleanName, fallbackId)));
    }

    private void handleWikiClick(String category, String name, int fallbackId)
    {
        if (category.equals("ITEM"))
        {
            var results = itemManager.search(name);
            BufferedImage image = null;
            int price = 0;
            int highAlch = 0;
            int lowAlch = 0;

            if (!results.isEmpty())
            {
                var bestMatch = results.stream()
                        .filter(r -> r.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(results.get(0));

                int realId = itemManager.canonicalize(bestMatch.getId());
                image = itemManager.getImage(realId, 1, false);
                price = itemManager.getItemPrice(realId);

                var comp = itemManager.getItemComposition(realId);
                highAlch = comp.getHaPrice();
                lowAlch = (int) (comp.getPrice() * 0.4);
            }

            final BufferedImage finalImage = image;
            final int finalPrice = price;
            final int finalHighAlch = highAlch;
            final int finalLowAlch = lowAlch;

            SwingUtilities.invokeLater(() ->
            {
                clientToolbar.openPanel(navButton);
                panel.showItem(name, finalImage, finalPrice, finalHighAlch, finalLowAlch);
                itemInfoClient.fetchDescription(name, desc ->
                        SwingUtilities.invokeLater(() -> panel.setDescription(desc)));

                itemInfoClient.fetchInfobox(name, info ->
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
                    itemInfoClient.fetchImage(name, wikiImage ->
                            SwingUtilities.invokeLater(() -> panel.setImage(wikiImage)));
                }
            });
        }
        else if (category.equals("NPC") || category.equals("OBJECT"))
        {
            SwingUtilities.invokeLater(() ->
            {
                clientToolbar.openPanel(navButton);
                panel.showNonItem(name);
                itemInfoClient.fetchDescription(name, desc ->
                        SwingUtilities.invokeLater(() -> panel.setDescription(desc)));
                itemInfoClient.fetchImage(name, wikiImage ->
                        SwingUtilities.invokeLater(() -> panel.setImage(wikiImage)));
            });
        }
    }
}