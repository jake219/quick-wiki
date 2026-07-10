package com.example;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.RuneLite;

public class ItemInfoPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ItemInfoPlugin.class);
        RuneLite.main(args);
    }
}