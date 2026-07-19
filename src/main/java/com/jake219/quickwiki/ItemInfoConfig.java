package com.jake219.quickwiki;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ModifierlessKeybind;

/**
 * Added directly in response to real feedback from the r/2007scape launch post: "Would u
 * be able to make this hotkeyable? Like if I press tilde and click something itll open the
 * wiki page? Potentially by temporarily making its left click the wiki option while key is
 * held" (37 upvotes, gherbow replied "That's doable!").
 * <p>
 * When enabled, the "Wiki" option no longer appears in the right-click menu at all during
 * normal use - it's only accessible by holding the hotkey and left-clicking directly.
 * Defaults to tilde/backtick as originally suggested - Ctrl was tried as the default
 * first, but RuneLite's own Keybind constructor asserts against a bare modifier key
 * (Ctrl/Shift/Alt alone, no secondary key) as a keybind value, confirmed via a real crash
 * when that was attempted. Backtick has no such restriction, so a single straightforward
 * Keybind config item works cleanly here with no extra workaround needed.
 */
@ConfigGroup("quickwiki")
public interface ItemInfoConfig extends Config
{
    @ConfigItem(
            keyName = "enableHotkey",
            name = "Enable Hotkey + Click lookup",
            description = "Hold the hotkey below and left-click an item/NPC/object to open it in Quick Wiki "
                    + "directly. While this is on, the 'Wiki' right-click option is hidden during normal use, "
                    + "since it's only reachable via the hotkey instead.",
            position = 0
    )
    default boolean enableHotkey()
    {
        return false;
    }

    @ConfigItem(
            keyName = "hotkey",
            name = "Hotkey",
            description = "The key to hold for hotkey-based lookup (see 'Enable Hotkey + Click lookup' above).",
            position = 1
    )
    default ModifierlessKeybind hotkey()
    {
        return new ModifierlessKeybind(KeyEvent.VK_BACK_QUOTE, 0);
    }

    @ConfigItem(
            keyName = "showTooltips",
            name = "Tooltips",
            description = "Show a short explanation when hovering over a stat in the Combat Stats section "
                    + "(e.g. what Strength bonus or Elemental weakness actually do).",
            position = 2
    )
    default boolean showTooltips()
    {
        return true;
    }
}