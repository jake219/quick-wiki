# Quick Wiki

[![Buy Me a Coffee](https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=jacob6444&button_colour=5F7FFF&font_colour=ffffff&font_family=Poppins&outline_colour=000000&coffee_colour=FFDD00)](https://buymeacoffee.com/jacob6444)

A RuneLite plugin that adds a "Wiki" option to the right-click menu on items, NPCs, and objects. Click it and the wiki entry shows up in a side panel — description, stats, drop sources, shop locations, and more, without ever leaving the game.

## Why

Examining an item, NPC, or object in-game just gives you a one-line description — no price, no stats, no drop info. So you end up alt-tabbing out to a browser to look it up on the wiki. That means a separate window, breaking out of fullscreen, losing sight of the game while it's still running in the background — just to check a price or a drop rate.

This puts all of that directly in a side panel inside the client itself. No alt-tab, no second window, no losing your place. You stay in the game the whole time.

## Features

- **Items** — description, GE price, high/low alch, release date, members status, quest item flag, tradeable/equipable/stackable/noteable, right-click options, value, weight, and the item's image
- **NPCs** — description, image, combat level (color-coded relative to your own), race, attack style, max hit, aggressive/poisonous flags, slayer level, and their full drop table
- **Objects** — description, image, release date, members status, quest requirement, interaction options, and reward contents for containers like raid chests
- **Click-through navigation** — click any monster in an item's drop sources, or any item in a monster's drop table, to jump straight to that page's own info. A back button returns you to wherever you started
- **Shops** — see every shop that sells an item and at what price, including non-GP currencies like Slayer Reward points
- **Rewards** — reward caskets and raid reward chests (e.g. Tombs of Amascut) show their full contents, not just an empty drop list
- **Official wiki, one click away** — a "Wiki" button in the top-right of the panel opens the exact page you're looking at on the real, official wiki
- **Hotkey mode** — hold a configurable key (default: backtick) and left-click anything to open its Wiki panel instantly, no right-click menu needed

<div align="center">
<table>
<tr>
<td align="center"><b>Item sources</b><br><img src="item-sources.gif" width="1000"></td>
</tr>
</table>
</div>

## Item sources

Expand "Item Sources" on any item to see every monster that drops it and every shop that sells it, sorted most-common-first with color-coded drop rates.

## NPC drops and stats

Right-click any monster to see its full combat stats and its own drop table — no need to look up the item first to find out what drops it.

## Reward caskets and raid chests

Reward caskets (e.g. Reward casket (hard)) and raid reward containers (e.g. Chest (Tombs of Amascut)) show their actual contents under a Rewards section, the same way an NPC's drop table works — rather than an empty, unhelpful list.

## Jump between pages without leaving the panel

Click any item or monster name inside a drop table to go straight to its own page. Use the back button (top-left) to return to whatever you originally looked up.

## Official wiki, one click away

The Wiki button in the top-right of the panel opens the real oldschool.runescape.wiki page for whatever's currently shown, for anyone who wants the full page rather than just the panel's summary.

## Hotkey mode

Turn on "Enable Hotkey" in the plugin's settings, then hold the configured key (backtick by default, changeable in settings) and left-click any item, NPC, or object to open its Wiki panel directly — skips the right-click menu entirely, for faster lookups while you're actively playing.

## Accuracy

A lot of items and NPCs share the same name (there are several NPCs named "Alan", for example, and items like the toxic blowpipe have charged/uncharged versions with different stats). Instead of just searching the wiki by name, this plugin looks up the exact in-game ID of whatever you clicked and matches it against the wiki's structured data, so you get the right page instead of a random namesake.

## Changelog

### 2026-07-19

**Added**
- "Wiki" button (top-right of the panel) linking straight to the official wiki page for whatever's currently shown
- "Report Issues or Support the Developer" link on the empty-state screen
- Reward caskets now show their real contents under a Rewards section, instead of an empty drop list
- Raid reward chests (e.g. Chest (Tombs of Amascut)) show their full reward table the same way

**Fixed**
- Multi-form monsters (e.g. Dark wizard, Skeleton) sometimes showed wrong or missing combat stats
- Clicking a monster's name inside an item's drop table could show no combat stats at all
- Some item icons (e.g. Coin pouch) failed to load due to naming mismatches
- Clicking a reward casket or raid chest inside a drop table could incorrectly try to look it up as an NPC
- Scroll boxes (e.g. Scroll box (hard)) now show the real sources of the equivalent clue scroll
- Reward caskets could incorrectly show the monsters that lead to them instead of their own contents
- Large reward lists (150+ items) could trigger rate limiting when loading icons — now throttled automatically
- Hotkey mode didn't work on "Use"-default items (e.g. Zulrah scales, Marks of grace) — now works on the first click


## Install

Search "Quick Wiki" in the RuneLite Plugin Hub.

## Usage

Right-click something → Wiki → panel opens with the info. Or, with Hotkey mode enabled: hold the configured key and left-click anything for the same result.

## Issues / feedback

Open an issue on this repo if something's broken, missing, or if the info shown for an item, NPC, or object looks wrong.

## License

See [LICENSE](LICENSE).
