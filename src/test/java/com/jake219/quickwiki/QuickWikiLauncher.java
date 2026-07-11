package com.jake219.quickwiki;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.RuneLite;

public class QuickWikiLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ItemInfoPlugin.class);
        RuneLite.main(args);
    }
}