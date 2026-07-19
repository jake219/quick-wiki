package com.jake219.quickwiki;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.JsonElement;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ItemInfoClient
{
    private static final String WIKI_API_BASE = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "Quick Wiki RuneLite Plugin - github.com/jake219/quick-wiki";

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    /**
     * Resolves the exact wiki page name for a given in-game NPC/object/item ID by
     * querying the wiki's Cargo database (structured infobox data). This avoids
     * name-collision problems (e.g. "Alan" referring to 4 different NPCs) by
     * matching on the precise ID stored in each page's infobox rather than guessing
     * from a text search on the display name.
     * <p>
     * If no exact match is found (some pages aren't in Cargo tables), the callback
     * receives null so callers can fall back to name-based search.
     *
     * @param cargoTable the ID-lookup bucket to query: "npc_id", "object_id", or "item_id"
     * @param id         the exact in-game ID to match against
     * @param callback   receives the exact page name, or null if no match was found
     */
    public void resolveExactPageName(String cargoTable, int id, Consumer<String> callback)
    {
        if (id < 0)
        {
            callback.accept(null);
            return;
        }

        // The wiki retired the old Cargo "cargoquery" action in favor of a new "Bucket" API,
        // which uses a query-string DSL: bucket('table').select('field').where('field','value').run()
        // 'page_name' is a reserved column present on every bucket, referring to the page the row belongs to.
        String query = "bucket('" + cargoTable + "').select('page_name').where('id','" + id + "').run()";

        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "bucket")
                .addQueryParameter("query", query)
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                log.warn("Failed to resolve exact page for id {} in {}", id, cargoTable, e);
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    String bodyStr = response.body().string();
                    JsonObject root = gson.fromJson(bodyStr, JsonObject.class);

                    if (!root.has("bucket"))
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonArray results = root.getAsJsonArray("bucket");
                    if (results.size() == 0)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject firstResult = results.get(0).getAsJsonObject();
                    if (!firstResult.has("page_name"))
                    {
                        callback.accept(null);
                        return;
                    }

                    com.google.gson.JsonElement pageNameElement = firstResult.get("page_name");
                    String resolvedPage = pageNameElement.isJsonArray()
                            ? pageNameElement.getAsJsonArray().get(0).getAsString()
                            : pageNameElement.getAsString();
                    callback.accept(resolvedPage);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse bucket query result for id {} in {}", id, cargoTable, e);
                    callback.accept(null);
                }
            }
        });
    }

    /**
     * A single row describing a monster (or other kill-based source) that can drop the item.
     */
    public static class DropSource
    {
        public String source;
        public String level;
        public String rarity;
        public String quantity;
        /** "combat", "reward", "hunter", "mining", etc. from the wiki's own Drop type field -
         * lets callers tell a monster combat level apart from a skilling level or "N/A". */
        public String dropType;
        /** Set by the plugin (not this class - it has no access to game resources) when
         * dropType matches a skill name, e.g. Mining's pickaxe icon for a Coal rock's
         * "level 30". Null for combat/reward sources or anything that isn't a skill. */
        public java.awt.image.BufferedImage skillIcon;
    }

    /**
     * Attack/Defence/Other combat bonuses for a wieldable weapon or piece of equipment.
     * Lives in the wiki's 'infobox_bonuses' bucket (not infobox_item), keyed by
     * 'page_name_sub' rather than 'item_name' or 'page_name'.
     */
    public static class CombatBonuses
    {
        public int stabAttack;
        public int slashAttack;
        public int crushAttack;
        public int magicAttack;
        public int rangeAttack;
        public int stabDefence;
        public int slashDefence;
        public int crushDefence;
        public int magicDefence;
        public int rangeDefence;
        public int strength;
        public int rangedStrength;
        public int magicDamage;
        public int prayer;
        public String attackSpeed;
        public String attackRange;
    }

    /**
     * Combat levels and bonuses for an NPC. Lives in the 'infobox_monster' bucket,
     * filtered by 'page_name' (page_name_sub, used on the item side, returns nothing
     * here). Unlike items, NPCs have levels as well as bonuses, and only one combined
     * attack bonus rather than separate stab/slash/crush - monsters don't get a choice
     * of attack style the way weapons do.
     */
    public static class NpcCombatStats
    {
        public int hitpoints;
        public int attackLevel;
        public int strengthLevel;
        public int defenceLevel;
        public int magicLevel;
        public int rangedLevel;
        public int attackBonus;
        public int strengthBonus;
        public int magicAttackBonus;
        public int magicDamageBonus;
        public int rangeAttackBonus;
        public int rangedStrengthBonus;
        public int stabDefenceBonus;
        public int slashDefenceBonus;
        public int crushDefenceBonus;
        public int magicDefenceBonus;
        public int rangeDefenceBonus;
        /** Separate defence values against light (darts), standard (arrows), and heavy
         * (bolts) ranged ammo - the wiki tracks these as three distinct fields rather
         * than one combined value (rangeDefenceBonus above is the older, legacy field
         * being phased out in favor of this split). */
        public int lightRangeDefenceBonus;
        public int standardRangeDefenceBonus;
        public int heavyRangeDefenceBonus;
        /** Null/empty means no elemental weakness (matches the wiki's "Pure essence" icon
         * case) - otherwise one of Air/Water/Earth/Fire. */
        public String elementalWeaknessType;
        public int elementalWeaknessPercent;
    }

    /**
     * A single row describing a shop that stocks the item.
     */
    public static class ShopSource
    {
        public String shopName;
        public String price;
        /** "Coins", "Points", etc. from the wiki's store_currency field - lets callers tell
         * a normal GP shop apart from points-based ones (Slayer Rewards, minigame shops). */
        public String currency;
    }

    /**
     * Combined result of the drop-source and shop-source bucket lookups for one item.
     */
    public static class ItemSourcesData
    {
        public List<DropSource> drops = new ArrayList<>();
        public List<ShopSource> shops = new ArrayList<>();
        /** True when drops represents this item's own contents (a reward container -
         * casket, chest, coffin, crate, whatever it's called) rather than normal "what
         * drops/sells this item" sources. */
        public boolean isRewards = false;
    }

    /**
     * Looks up where an item can be obtained: monster drops (dropsline bucket) and shop
     * stock (storeline bucket). Both queries run concurrently, combined result goes to
     * the callback once both finish (each falls back to an empty list on failure).
     * <p>
     * Some items have multiple variants sharing one generic page name (e.g. Pendant of
     * ates has Inert and Charged forms), and the drop table is filed under the specific
     * variant's name, not the generic one. So if we know the item's actual ID, resolve
     * the variant-specific name via infobox_item first and query with that instead.
     *
     * @param pageName the exact wiki page name of the item
     * @param itemId the item's actual resolved in-game ID if known, or -1 if unknown
     * @param callback receives the combined drop/shop source lists (empty lists if nothing found)
     */
    public void fetchItemSources(String pageName, int itemId, Consumer<ItemSourcesData> callback)
    {
        if (itemId >= 0)
        {
            String resolveQuery = "bucket('infobox_item').select('item_name')"
                    + ".where('item_id'," + itemId + ").limit(1).run()";

            runBucketQuery(resolveQuery, resolveRoot ->
            {
                String resolvedName = null;
                try
                {
                    if (resolveRoot != null && resolveRoot.has("bucket"))
                    {
                        JsonArray bucket = resolveRoot.getAsJsonArray("bucket");
                        if (bucket.size() > 0)
                        {
                            String name = firstString(bucket.get(0).getAsJsonObject(), "item_name");
                            if (name != null && !name.isEmpty())
                            {
                                resolvedName = name;
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to resolve variant-specific item_name for {} (id={})", pageName, itemId, e);
                }

                String nameToUse = resolvedName != null ? resolvedName : pageName;
                fetchItemSourcesByName(nameToUse, callback);
            });
        }
        else
        {
            fetchItemSourcesByName(pageName, callback);
        }
    }

    private void fetchItemSourcesByName(String itemName, Consumer<ItemSourcesData> callback)
    {
        ItemSourcesData data = new ItemSourcesData();
        AtomicInteger remaining = new AtomicInteger(2);
        Runnable finishOne = () ->
        {
            if (remaining.decrementAndGet() == 0)
            {
                callback.accept(data);
            }
        };

        fetchDropSources(itemName, result ->
        {
            data.drops = result.drops;
            data.isRewards = result.isRewards;
            finishOne.run();
        });

        fetchShopSources(itemName, shops ->
        {
            data.shops = shops;
            finishOne.run();
        });
    }

    /**
     * Fetches a monster/NPC's own drop table - the reverse of fetchItemSources: instead of
     * "which monsters drop this item", this is "which items does this monster drop".
     * Filters dropsline by page_name (the monster) instead of item_name (the item).
     * <p>
     * Reuses the same DropSource class as item lookups, just populated the other way
     * around - source holds the dropped item's name instead of a monster's name, and
     * level/dropType/skillIcon are left null (not applicable here).
     *
     * @param npcName the exact wiki page name of the monster/NPC
     * @param callback receives the drop list, sorted most-common-first (empty if none found)
     */
    public void fetchNpcDrops(String npcName, Consumer<List<DropSource>> callback)
    {
        // limit(500), not 50 - Zulrah's drop table (unique drops + tertiary + common loot
        // across overlapping phase tables) exceeds 50 entries, silently truncating items
        // that fall past whatever the bucket's ordering puts in the first 50.
        String query = "bucket('dropsline').select('item_name','drop_json')"
                + ".where('page_name','" + escapeForBucketQuery(npcName) + "').limit(500).run()";

        runBucketQuery(query, root ->
        {
            callback.accept(parseNpcDropRows(root, npcName));
        });
    }

    private List<DropSource> parseNpcDropRows(JsonObject root, String npcName)
    {
        List<DropSource> results = new ArrayList<>();
        try
        {
            if (root != null && root.has("bucket"))
            {
                for (JsonElement el : root.getAsJsonArray("bucket"))
                {
                    JsonObject row = el.getAsJsonObject();

                    DropSource ds = new DropSource();
                    String rawItemName = firstString(row, "item_name");
                    // Convert the wiki's "#Section" page-anchor syntax (e.g. "Pendant of
                    // ates#Inert") into the real in-game display name format instead of
                    // just stripping it - stripping would resolve the icon to the wrong
                    // variant. See convertWikiSectionSuffix for the exact format.
                    ds.source = convertWikiSectionSuffix(rawItemName);

                    if (row.has("drop_json"))
                    {
                        String rawJson = firstString(row, "drop_json");
                        if (rawJson != null)
                        {
                            try
                            {
                                JsonObject blob = gson.fromJson(rawJson, JsonObject.class);
                                if (blob.has("Rarity"))
                                {
                                    ds.rarity = reduceRarityFraction(blob.get("Rarity").getAsString());
                                }
                                if (blob.has("Drop Quantity"))
                                {
                                    ds.quantity = blob.get("Drop Quantity").getAsString();
                                }
                                if (blob.has("Rolls") && ds.rarity != null)
                                {
                                    try
                                    {
                                        int rolls = blob.get("Rolls").getAsInt();
                                        if (rolls > 1)
                                        {
                                            ds.rarity = rolls + " \u00d7 " + ds.rarity;
                                        }
                                    }
                                    catch (Exception ignored)
                                    {
                                    }
                                }
                            }
                            catch (Exception ignored)
                            {
                            }
                        }
                    }

                    if (ds.source != null)
                    {
                        results.add(ds);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to parse NPC drops for {}", npcName, e);
        }

        // A monster's own drop table can legitimately list the exact same item/quantity/
        // rarity twice - once per monster variant (e.g. "Rat#Regular" and "Rat#Stronghold
        // of Security" both drop "Rat's tail - Always"). Since we don't display which
        // variant each row is from, showing both looks like a plain duplicate rather than
        // meaningful extra info, so exact duplicates are merged into one row here.
        List<DropSource> deduped = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DropSource ds : results)
        {
            String key = ds.source + "|" + ds.quantity + "|" + ds.rarity;
            if (seen.add(key))
            {
                deduped.add(ds);
            }
        }

        deduped.sort(Comparator.comparingDouble(d -> rarityWeight(d.rarity)));
        return deduped;
    }

    /**
     * Converts the wiki's "#Section" page-anchor syntax (used throughout this file for
     * page_name_sub values like "Dragon defender#Normal", "Toxic blowpipe#Charged") into
     * the "(section)" parenthetical format real in-game item names actually use, so the
     * result is searchable rather than a wiki-internal string with a literal "#" that
     * won't match any real item. Returns names with no "#" unchanged.
     */
    private String convertWikiSectionSuffix(String rawName)
    {
        if (rawName == null || !rawName.contains("#"))
        {
            return rawName;
        }

        int hashIndex = rawName.indexOf('#');
        String base = rawName.substring(0, hashIndex).trim();
        String section = rawName.substring(hashIndex + 1).trim();
        return base + " (" + section.toLowerCase() + ")";
    }

    /**
     * Small holder so the reward-vs-normal distinction can travel back through the same
     * callback chain as the drops list itself, rather than needing a second round-trip.
     */
    private static class DropSourcesResult
    {
        List<DropSource> drops;
        boolean isRewards;
    }

    private void fetchDropSources(String itemName, Consumer<DropSourcesResult> callback)
    {
        // Always try "what's inside this container" first, for any item - rather than
        // checking whether the name looks like a known reward-container pattern. There's
        // no consistent naming across these: "Reward casket (hard)", "Rewards Chest
        // (Fortis Colosseum)", "Chest (Barrows)", "Lunar chest", "Supply crate", "Coffin
        // (Hallowed Sepulchre)" all use completely different words, so pattern-matching
        // would always be chasing an incomplete list. A normal, non-container item (e.g.
        // "Rune platebody") simply has no page_name rows in dropsline at all - nothing
        // "drops from" a platebody - so this query is safe to try unconditionally and
        // will just come back empty for the vast majority of items.
        String rewardsQuery = "bucket('dropsline').select('item_name','drop_json')"
                + ".where('page_name','" + escapeForBucketQuery(itemName) + "').limit(500).run()";

        runBucketQuery(rewardsQuery, rewardsRoot ->
        {
            List<DropSource> rewards = parseNpcDropRows(rewardsRoot, itemName);
            if (!rewards.isEmpty())
            {
                DropSourcesResult result = new DropSourcesResult();
                result.drops = rewards;
                result.isRewards = true;
                callback.accept(result);
                return;
            }

            fetchNormalDropSources(itemName, callback);
        });
    }

    /**
     * The original "what drops/where does this item come from" lookup - only reached
     * once the reverse-direction "what's inside this" query above came back empty, i.e.
     * this item isn't itself a reward container.
     */
    private void fetchNormalDropSources(String itemName, Consumer<DropSourcesResult> callback)
    {
        // 'item_name' is the correct where() field (not 'item_page'). Rarity/quantity/
        // level all live inside the drop_json blob rather than as flattened fields.
        String query = "bucket('dropsline').select('page_name','drop_json')"
                + ".where('item_name','" + escapeForBucketQuery(itemName) + "').limit(500).run()";

        runBucketQuery(query, root ->
        {
            List<DropSource> drops = parseDropRows(root, itemName);
            if (!drops.isEmpty())
            {
                DropSourcesResult result = new DropSourcesResult();
                result.drops = drops;
                result.isRewards = false;
                callback.accept(result);
                return;
            }

            String clueScrollEquivalent = scrollBoxToClueScrollName(itemName);
            if (clueScrollEquivalent != null)
            {
                fetchDropSourcesForClueScrollEquivalent(clueScrollEquivalent, callback);
                return;
            }

            // Strip any existing "(Something)" qualifier before trying the #Inert
            // fallback - infobox_item can resolve this item to either the plain name or
            // a parenthetical variant depending on which item_id was queried, but
            // neither form is what dropsline actually uses ("Pendant of ates#Inert",
            // with a literal #) - so the fallback needs the bare base name either way.
            String baseName = itemName.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
            fetchDropSourcesInertFallback(baseName, callback);
        });
    }

    /**
     * Scroll boxes (e.g. "Scroll box (easy)") aren't directly dropped by anything - they're
     * obtained "in place of" clue scrolls upon completing X Marks the Spot, a mechanical
     * substitution rather than a real monster/shop source, so they have no dropsline entry
     * of their own. The useful info for a player is the equivalent clue scroll's real
     * sources (e.g. "Clue scroll (easy)"). Returns null for anything that isn't a
     * "Scroll box (tier)" name.
     */
    private String scrollBoxToClueScrollName(String itemName)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)^Scroll box \\(([^)]+)\\)$").matcher(itemName.trim());
        if (!matcher.matches())
        {
            return null;
        }
        return "Clue scroll (" + matcher.group(1) + ")";
    }

    /**
     * Fetches the equivalent clue scroll's own drop sources on a scroll box's behalf - see
     * scrollBoxToClueScrollName for why this substitution makes sense.
     */
    private void fetchDropSourcesForClueScrollEquivalent(String clueScrollName, Consumer<DropSourcesResult> callback)
    {
        String query = "bucket('dropsline').select('page_name','drop_json')"
                + ".where('item_name','" + escapeForBucketQuery(clueScrollName) + "').limit(500).run()";

        runBucketQuery(query, root ->
        {
            DropSourcesResult result = new DropSourcesResult();
            result.drops = parseDropRows(root, clueScrollName);
            result.isRewards = false;
            callback.accept(result);
        });
    }

    /**
     * Fallback for chargeable items whose drop-table entry is filed under a "#Inert"
     * section suffix that infobox_item's own item_name field doesn't reflect. Pendant of
     * ates resolves to either "Pendant of ates" or "Pendant of ates (inert)" via
     * infobox_item depending on which item_id was queried, but the actual drop table entry
     * uses "Pendant of ates#Inert" - the same "#Section" anchor convention used elsewhere
     * for page_name_sub values (e.g. "Dragon defender#Normal"). Only tried when the
     * plain-name query comes back empty, so this can't override a real result.
     */
    private void fetchDropSourcesInertFallback(String itemName, Consumer<DropSourcesResult> callback)
    {
        String inertName = itemName + "#Inert";
        String query = "bucket('dropsline').select('page_name','drop_json')"
                + ".where('item_name','" + escapeForBucketQuery(inertName) + "').limit(500).run()";

        runBucketQuery(query, root ->
        {
            DropSourcesResult result = new DropSourcesResult();
            result.drops = parseDropRows(root, inertName);
            result.isRewards = false;
            callback.accept(result);
        });
    }

    /**
     * Converts a raw drop-rate fraction like "6/134.35" or "7/131" into the "1 in N" form
     * the wiki's own pages actually display (e.g. "1/22.39", "1/18.71"). Verified against a
     * live wiki page value-by-value: 131/7 -> 18.71, 100/5 -> 20, 134.35/6 -> 22.39,
     * 134.39/6 -> 22.4 (note the stripped trailing zero), 134.43/6 -> 22.41, 134.5/6 -> 22.42.
     * Anything that isn't a plain numeric fraction (e.g. "Always", "Varies", or text with
     * extra notes) is returned unchanged rather than risk mangling something unrecognized.
     */
    private String reduceRarityFraction(String raw)
    {
        if (raw == null)
        {
            return null;
        }

        Matcher matcher = Pattern.compile("^([0-9,.]+)/([0-9,.]+)$").matcher(raw.trim());
        if (!matcher.matches())
        {
            return raw;
        }

        try
        {
            double numerator = Double.parseDouble(matcher.group(1).replace(",", ""));
            double denominator = Double.parseDouble(matcher.group(2).replace(",", ""));
            if (numerator <= 0)
            {
                return raw;
            }

            double reducedValue = denominator / numerator;
            BigDecimal rounded = BigDecimal.valueOf(reducedValue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return "1/" + rounded.toPlainString();
        }
        catch (NumberFormatException e)
        {
            return raw;
        }
    }

    /**
     * Turns the raw "Page#SubLocation" format used by "Dropped from" (e.g.
     * "Mystery box#Members", "Cyclops#Warriors' Guild Basement") into a more readable
     * "Page (SubLocation)" display string. Left as-is if there's no "#" to split on.
     */
    private String formatSourceName(String raw)
    {
        int hashIndex = raw.indexOf('#');
        if (hashIndex > 0 && hashIndex < raw.length() - 1)
        {
            String main = raw.substring(0, hashIndex);
            String sub = raw.substring(hashIndex + 1);
            return main + " (" + sub + ")";
        }
        return raw;
    }

    private List<DropSource> parseDropRows(JsonObject root, String itemName)
    {
        List<DropSource> results = new ArrayList<>();
        try
        {
            if (root != null && root.has("bucket"))
            {
                for (JsonElement el : root.getAsJsonArray("bucket"))
                {
                    JsonObject row = el.getAsJsonObject();

                    DropSource ds = new DropSource();
                    ds.source = firstString(row, "page_name");

                    // Field names from the actual drop_json blob (e.g. for Rune arrow):
                    // {"Rarity":"3/128","Drop level":"304","Drop Quantity":"8",...}.
                    // Note it's "Drop Quantity", not "Quantity".
                    if (row.has("drop_json"))
                    {
                        String rawJson = firstString(row, "drop_json");
                        if (rawJson != null)
                        {
                            try
                            {
                                JsonObject blob = gson.fromJson(rawJson, JsonObject.class);
                                if (blob.has("Drop level"))
                                {
                                    ds.level = blob.get("Drop level").getAsString();
                                }
                                if (blob.has("Drop type"))
                                {
                                    ds.dropType = blob.get("Drop type").getAsString();
                                }
                                // "page_name" alone collapses distinct sub-sources that
                                // share the same page - e.g. Mystery box has separate
                                // #Members and #Entrana entries with identical rarity, which
                                // otherwise look like an exact duplicate row. "Dropped from"
                                // is more specific (includes the "#SubLocation" suffix when
                                // there is one), so prefer it when present.
                                if (blob.has("Dropped from"))
                                {
                                    String droppedFrom = blob.get("Dropped from").getAsString();
                                    if (droppedFrom != null && !droppedFrom.isEmpty())
                                    {
                                        ds.source = formatSourceName(droppedFrom);
                                    }
                                }
                                if (blob.has("Rarity"))
                                {
                                    // Raw value is often an unreduced fraction like
                                    // "6/134.35" - the wiki's own pages show these
                                    // reduced to "1 in N" form, so reduce here too.
                                    ds.rarity = reduceRarityFraction(blob.get("Rarity").getAsString());
                                }
                                if (blob.has("Drop Quantity"))
                                {
                                    ds.quantity = blob.get("Drop Quantity").getAsString();
                                }
                                // The wiki displays "N x rarity" (e.g. "7 x 1/2,448") when a
                                // monster gets multiple independent rolls at this drop per
                                // kill - without this, a 7-roll 1/2,448 drop looks identical
                                // to a single-roll one even though it's ~7x more likely overall.
                                if (blob.has("Rolls") && ds.rarity != null)
                                {
                                    try
                                    {
                                        int rolls = blob.get("Rolls").getAsInt();
                                        if (rolls > 1)
                                        {
                                            ds.rarity = rolls + " \u00d7 " + ds.rarity;
                                        }
                                    }
                                    catch (Exception ignored)
                                    {
                                        // Rolls wasn't a plain integer - just skip the multiplier.
                                    }
                                }
                            }
                            catch (Exception ignored)
                            {
                                // drop_json wasn't parseable JSON - just skip the extra detail.
                            }
                        }
                    }

                    // Ammo-recycling equipment mechanics (Ranging cape#assembler, Ava's
                    // assembler, Assembler max cape, etc.) get tracked in this same
                    // dropsline bucket, but they're equipment mechanics (recovering ammo
                    // on hit), not monster/reward drops. All variants share the word
                    // "assembler" in their source name, which catches this family without
                    // needing to hardcode every exact page name.
                    if (ds.source != null && !ds.source.toLowerCase().contains("assembler"))
                    {
                        results.add(ds);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to parse drop sources for {}", itemName, e);
        }

        // Most common first: "Always" sorts before any numeric rarity, then ascending by
        // the effective "1 in N" value (smaller N = more common). Rows with an unreadable
        // rarity sort to the end rather than disrupting the rest of the ordering.
        results.sort(Comparator.comparingDouble(d -> rarityWeight(d.rarity)));
        return results;
    }

    /**
     * Converts a rarity string into a sortable weight - 0 for "Always", the effective 1-in-N
     * value for numeric fractions (a "7 x 1/2,448" roll-multiplier prefix is stripped before
     * parsing), or Double.MAX_VALUE if it can't be read at all, so unknown rarities sort last.
     */
    private double rarityWeight(String rarity)
    {
        if (rarity == null || rarity.isEmpty())
        {
            return Double.MAX_VALUE;
        }
        if (rarity.toLowerCase().contains("always"))
        {
            return 0;
        }

        String cleaned = rarity.replaceAll("^[0-9]+\\s*\\u00d7\\s*", "");
        Matcher matcher = Pattern.compile("1/([0-9,.]+)").matcher(cleaned);
        if (matcher.find())
        {
            try
            {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            }
            catch (NumberFormatException ignored)
            {
                return Double.MAX_VALUE;
            }
        }
        return Double.MAX_VALUE;
    }

    private void fetchShopSources(String itemName, Consumer<List<ShopSource>> callback)
    {
        String query = "bucket('storeline').select('sold_by','store_sell_price','store_currency')"
                + ".where('sold_item','" + escapeForBucketQuery(itemName) + "').limit(500).run()";

        runBucketQuery(query, root ->
        {
            List<ShopSource> results = new ArrayList<>();
            try
            {
                if (root != null && root.has("bucket"))
                {
                    for (JsonElement el : root.getAsJsonArray("bucket"))
                    {
                        JsonObject row = el.getAsJsonObject();

                        ShopSource ss = new ShopSource();
                        ss.shopName = firstString(row, "sold_by");
                        ss.price = firstString(row, "store_sell_price");
                        ss.currency = firstString(row, "store_currency");

                        if (ss.shopName != null)
                        {
                            results.add(ss);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to parse shop sources for {}", itemName, e);
            }
            callback.accept(results);
        });
    }

    /**
     * Fetches Attack/Defence/Other combat bonuses for a wieldable weapon or equipment
     * piece. Lives in the 'infobox_bonuses' bucket, not 'infobox_item', filtered by
     * 'page_name_sub' rather than 'item_name'/'page_name'. Values come back as plain
     * numbers here, not the single-element JSON arrays seen elsewhere (e.g. item_id's
     * "id" field).
     * <p>
     * The plain item name often isn't the real page_name_sub value - e.g. "Toxic
     * blowpipe" returns nothing, the actual value is "Toxic blowpipe#Charged" (multi-
     * version items use a "#Section" suffix). So this first resolves the real
     * page_name_sub via infobox_item (queried by item_name), then uses that for the
     * bonuses lookup - falling back to the plain name if resolution fails, since some
     * items (e.g. Rune scimitar) match directly with no resolution needed.
     * <p>
     * Items with multiple variants sharing one item_name (e.g. Pendant of ates - Inert
     * and Charged forms) can have several infobox_item rows for that name, and a
     * name-only resolve query can pick the wrong variant. So when the caller knows the
     * item's actual ID, this queries by item_id alone instead (each variant has its own
     * distinct page_name_sub, so combining the correct ID with the wrong item_name would
     * just never match anything). Falls back to the name-only query if the ID-only query
     * comes back empty for some other reason.
     *
     * @param itemId the item's actual resolved in-game ID if known, or -1 if unknown
     * @param callback receives the parsed bonuses, or null if the item has none (not
     *                  equipment, or no data found)
     */
    public void fetchCombatBonuses(String itemName, int itemId, Consumer<CombatBonuses> callback)
    {
        if (itemId >= 0)
        {
            String filteredResolveQuery = "bucket('infobox_item').select('page_name_sub')"
                    + ".where('item_id'," + itemId + ").limit(1).run()";

            runBucketQuery(filteredResolveQuery, filteredRoot ->
            {
                String resolved = extractPageNameSub(filteredRoot);
                if (resolved != null)
                {
                    fetchCombatBonusesByPageNameSub(resolved, callback);
                }
                else
                {
                    fetchCombatBonusesUnfiltered(itemName, callback);
                }
            });
        }
        else
        {
            fetchCombatBonusesUnfiltered(itemName, callback);
        }
    }

    /**
     * Original name-only resolve + fetch, used when the item ID is unknown or the
     * ID-filtered attempt above didn't find a match. Can still pick the wrong variant for
     * items with multiple forms sharing one item_name, but is a safety net over losing
     * bonus data entirely.
     */
    private void fetchCombatBonusesUnfiltered(String itemName, Consumer<CombatBonuses> callback)
    {
        String resolveQuery = "bucket('infobox_item').select('page_name_sub')"
                + ".where('item_name','" + escapeForBucketQuery(itemName) + "').limit(1).run()";

        runBucketQuery(resolveQuery, resolveRoot ->
        {
            String resolved = extractPageNameSub(resolveRoot);
            fetchCombatBonusesByPageNameSub(resolved != null ? resolved : itemName, callback);
        });
    }

    private String extractPageNameSub(JsonObject root)
    {
        try
        {
            if (root != null && root.has("bucket"))
            {
                JsonArray bucket = root.getAsJsonArray("bucket");
                if (bucket.size() > 0)
                {
                    String resolved = firstString(bucket.get(0).getAsJsonObject(), "page_name_sub");
                    if (resolved != null && !resolved.isEmpty())
                    {
                        return resolved;
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to extract page_name_sub", e);
        }
        return null;
    }

    private void fetchCombatBonusesByPageNameSub(String pageNameSub, Consumer<CombatBonuses> callback)
    {
        String query = "bucket('infobox_bonuses').select("
                + "'stab_attack_bonus','slash_attack_bonus','crush_attack_bonus','magic_attack_bonus','range_attack_bonus',"
                + "'stab_defence_bonus','slash_defence_bonus','crush_defence_bonus','magic_defence_bonus','range_defence_bonus',"
                + "'strength_bonus','ranged_strength_bonus','magic_damage_bonus','prayer_bonus','weapon_attack_speed','weapon_attack_range')"
                + ".where('page_name_sub','" + escapeForBucketQuery(pageNameSub) + "').limit(1).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    if (bucket.size() > 0)
                    {
                        JsonObject row = bucket.get(0).getAsJsonObject();
                        CombatBonuses bonuses = new CombatBonuses();
                        bonuses.stabAttack = firstInt(row, "stab_attack_bonus");
                        bonuses.slashAttack = firstInt(row, "slash_attack_bonus");
                        bonuses.crushAttack = firstInt(row, "crush_attack_bonus");
                        bonuses.magicAttack = firstInt(row, "magic_attack_bonus");
                        bonuses.rangeAttack = firstInt(row, "range_attack_bonus");
                        bonuses.stabDefence = firstInt(row, "stab_defence_bonus");
                        bonuses.slashDefence = firstInt(row, "slash_defence_bonus");
                        bonuses.crushDefence = firstInt(row, "crush_defence_bonus");
                        bonuses.magicDefence = firstInt(row, "magic_defence_bonus");
                        bonuses.rangeDefence = firstInt(row, "range_defence_bonus");
                        bonuses.strength = firstInt(row, "strength_bonus");
                        bonuses.rangedStrength = firstInt(row, "ranged_strength_bonus");
                        bonuses.magicDamage = firstInt(row, "magic_damage_bonus");
                        bonuses.prayer = firstInt(row, "prayer_bonus");
                        bonuses.attackSpeed = firstString(row, "weapon_attack_speed");
                        bonuses.attackRange = firstString(row, "weapon_attack_range");
                        callback.accept(bonuses);
                        return;
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to fetch combat bonuses for {}", pageNameSub, e);
            }
            callback.accept(null);
        });
    }

    /**
     * Fetches combat levels and bonuses for an NPC. Lives in the 'infobox_monster' bucket,
     * filtered by 'page_name' (page_name_sub, used on the item side, returns nothing
     * here). A monster with multiple combat-level forms (e.g. Dark wizard - level
     * 7/11/20/22/23, all sharing one wiki page) can return several rows for one page_name.
     * <p>
     * Fetches every row sharing that page_name (limit 20) and picks the closest
     * combat_level match itself, rather than trusting a plain page_name-only query with
     * limit(1) to happen to return the right form - that approach picked arbitrary
     * (visibly wrong) forms for several multi-form monsters.
     *
     * @param combatLevel the NPC's actual in-game combat level if known, or -1 if unknown
     *                    (e.g. reached via "Back" with no drop-row level context available)
     * @param callback receives the parsed stats, or null if the NPC has none (not
     *                  attackable, or no data found)
     */
    public void fetchNpcCombatStats(String npcName, int combatLevel, Consumer<NpcCombatStats> callback)
    {
        String query = "bucket('infobox_monster').select("
                + "'combat_level','hitpoints','attack_level','strength_level','defence_level','magic_level','ranged_level',"
                + "'attack_bonus','strength_bonus','magic_attack_bonus','range_attack_bonus',"
                + "'stab_defence_bonus','slash_defence_bonus','crush_defence_bonus','magic_defence_bonus','range_defence_bonus',"
                + "'light_range_defence_bonus','standard_range_defence_bonus','heavy_range_defence_bonus')"
                + ".where('page_name','" + escapeForBucketQuery(npcName) + "').limit(20).run()";

        runBucketQuery(query, root ->
        {
            JsonObject bestRow = pickBestNpcCombatStatsRow(root, combatLevel);
            if (bestRow != null)
            {
                NpcCombatStats stats = parseNpcCombatStatsFields(bestRow);
                // Uses the chosen row's own actual combat_level, not the original
                // (possibly non-matching) combatLevel parameter - important when no exact
                // match existed, or combatLevel was unknown (-1) to begin with, so the
                // enrichment query below stays consistent with whichever row was actually
                // picked rather than filtering for a different, mismatched level.
                int resolvedCombatLevel = firstInt(bestRow, "combat_level");
                fetchNpcExtraStats(npcName, resolvedCombatLevel, stats, callback);
            }
            else
            {
                callback.accept(null);
            }
        });
    }

    /**
     * Picks the best-matching row (not yet parsed into NpcCombatStats) out of every
     * infobox_monster row sharing this page_name - an exact combat_level match if one
     * exists and combatLevel is known, otherwise the row whose own combat_level is
     * numerically closest to it, or just the first row if combatLevel is unknown (-1).
     * Returns the raw row rather than parsed stats so the caller can also read its
     * actual combat_level.
     */
    private JsonObject pickBestNpcCombatStatsRow(JsonObject root, int combatLevel)
    {
        try
        {
            if (root == null || !root.has("bucket"))
            {
                return null;
            }
            JsonArray bucket = root.getAsJsonArray("bucket");
            if (bucket.size() == 0)
            {
                return null;
            }

            JsonObject bestRow = null;
            int bestDiff = Integer.MAX_VALUE;
            for (JsonElement rowElement : bucket)
            {
                JsonObject row = rowElement.getAsJsonObject();
                if (combatLevel < 0)
                {
                    bestRow = row;
                    break;
                }
                int rowLevel = firstInt(row, "combat_level");
                int diff = Math.abs(rowLevel - combatLevel);
                if (diff < bestDiff)
                {
                    bestDiff = diff;
                    bestRow = row;
                    if (diff == 0)
                    {
                        break;
                    }
                }
            }

            if (bestRow == null)
            {
                return null;
            }
            return bestRow;
        }
        catch (Exception e)
        {
            log.warn("Failed to pick best NPC combat stats row", e);
            return null;
        }
    }

    private NpcCombatStats parseNpcCombatStatsFields(JsonObject row)
    {
        NpcCombatStats stats = new NpcCombatStats();
        stats.hitpoints = firstInt(row, "hitpoints");
        stats.attackLevel = firstInt(row, "attack_level");
        stats.strengthLevel = firstInt(row, "strength_level");
        stats.defenceLevel = firstInt(row, "defence_level");
        stats.magicLevel = firstInt(row, "magic_level");
        stats.rangedLevel = firstInt(row, "ranged_level");
        stats.attackBonus = firstInt(row, "attack_bonus");
        stats.strengthBonus = firstInt(row, "strength_bonus");
        stats.magicAttackBonus = firstInt(row, "magic_attack_bonus");
        stats.rangeAttackBonus = firstInt(row, "range_attack_bonus");
        stats.stabDefenceBonus = firstInt(row, "stab_defence_bonus");
        stats.slashDefenceBonus = firstInt(row, "slash_defence_bonus");
        stats.crushDefenceBonus = firstInt(row, "crush_defence_bonus");
        stats.magicDefenceBonus = firstInt(row, "magic_defence_bonus");
        stats.rangeDefenceBonus = firstInt(row, "range_defence_bonus");
        stats.lightRangeDefenceBonus = firstInt(row, "light_range_defence_bonus");
        stats.standardRangeDefenceBonus = firstInt(row, "standard_range_defence_bonus");
        stats.heavyRangeDefenceBonus = firstInt(row, "heavy_range_defence_bonus");
        return stats;
    }

    /**
     * Enrichment query for a handful of field names that took some digging to pin down
     * (per the Module:Infobox Monster Lua source):
     * - rngbns_bucket = 'range_strength_bonus' (not 'ranged_strength_bonus')
     * - mbns_bucket = 'magic_damage_bonus'
     * - elementalweaknesstype_bucket = 'elemental_weakness'
     * - elementalweaknesspercent_bucket = 'elemental_weakness_percent'
     * <p>
     * Kept as its own separate query (not merged into fetchNpcCombatStats above) so the
     * main query stays isolated if anything here turns out wrong.
     */
    private void fetchNpcExtraStats(String npcName, int combatLevel, NpcCombatStats stats, Consumer<NpcCombatStats> callback)
    {
        String query = "bucket('infobox_monster').select("
                + "'magic_damage_bonus','range_strength_bonus','elemental_weakness','elemental_weakness_percent')"
                + ".where('page_name','" + escapeForBucketQuery(npcName) + "')"
                + ".where('combat_level'," + combatLevel + ").limit(1).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    if (bucket.size() > 0)
                    {
                        JsonObject row = bucket.get(0).getAsJsonObject();
                        stats.magicDamageBonus = firstInt(row, "magic_damage_bonus");
                        stats.rangedStrengthBonus = firstInt(row, "range_strength_bonus");
                        stats.elementalWeaknessType = firstString(row, "elemental_weakness");
                        stats.elementalWeaknessPercent = firstInt(row, "elemental_weakness_percent");
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to fetch extra NPC combat stats for {}", npcName, e);
            }
            callback.accept(stats);
        });
    }

    /**
     * Like firstString, but for the plain-integer bonus fields in infobox_bonuses (not
     * wrapped in a JSON array the way e.g. item_id's "id" field is). Missing/null fields
     * default to 0 rather than null, since callers display these as plain numbers
     * (e.g. "+44") where a missing value should just read as no bonus.
     */
    private int firstInt(JsonObject row, String field)
    {
        if (!row.has(field) || row.get(field).isJsonNull())
        {
            return 0;
        }
        try
        {
            return row.get(field).getAsInt();
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /**
     * Shared plumbing for a Bucket API call: builds the request, fires it, and hands the
     * parsed JSON root object back to the callback (or null on any failure). Individual
     * callers are responsible for interpreting the "bucket" array inside the root object.
     */
    private void runBucketQuery(String query, Consumer<JsonObject> callback)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "bucket")
                .addQueryParameter("query", query)
                .addQueryParameter("format", "json")
                .build();

        log.debug("Quick Wiki bucket query: {}", url);

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                log.warn("Failed to run bucket query: {}", query, e);
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        log.warn("Bucket query got HTTP {} for: {}", response.code(), url);
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    if (root != null && root.has("error"))
                    {
                        // Log the raw error object - the Bucket API doesn't publish a
                        // field schema, so this is how a wrong select()/where() field
                        // name gets caught.
                        log.warn("Bucket query returned an error for {} - response: {}", url, root.get("error"));
                        callback.accept(null);
                        return;
                    }

                    callback.accept(root);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse bucket query response for: {}", query, e);
                    callback.accept(null);
                }
            }
        });
    }

    /**
     * Bucket fields can come back either as a bare scalar or as a single-element array
     * (RuneLite's existing resolveExactPageName has the same quirk with page_name), so this
     * normalizes either shape into a plain string, returning null if the field is absent,
     * empty, or JSON null.
     */
    private String firstString(JsonObject row, String field)
    {
        if (!row.has(field) || row.get(field).isJsonNull())
        {
            return null;
        }

        JsonElement el = row.get(field);
        if (el.isJsonArray())
        {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() == 0)
            {
                return null;
            }
            el = arr.get(0);
        }

        String value = el.getAsString();
        return value.isEmpty() ? null : value;
    }

    /**
     * Strict, fallback-free version of resolveItemIdByName - only the exact page_name
     * match on the item_id bucket, none of that method's looser fallback chain (case-
     * toggle, then stripping the parenthetical entirely). Needed for "is this exact name
     * actually a real item?" navigation decisions, as opposed to icon-resolution
     * best-effort lookups where a looser match is an acceptable tradeoff - reusing the
     * full fallback chain here once caused a monster's sub-location-qualified name (e.g.
     * "Cyclops (Warriors' Guild Basement)") to incorrectly resolve as an item, since the
     * bare-base-name fallback ("Cyclops" alone) matched some unrelated real item.
     */
    public void resolveExactItemIdStrict(String itemName, Consumer<Integer> callback)
    {
        String query = "bucket('item_id').select('id')"
                + ".where('page_name','" + escapeForBucketQuery(itemName) + "').limit(1).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    if (bucket.size() > 0)
                    {
                        JsonObject row = bucket.get(0).getAsJsonObject();
                        if (row.has("id"))
                        {
                            JsonArray idArray = row.getAsJsonArray("id");
                            int bestId = -1;
                            for (JsonElement idElement : idArray)
                            {
                                String idStr = idElement.getAsString();
                                if (idStr.matches("\\d+"))
                                {
                                    int id = Integer.parseInt(idStr);
                                    if (id > bestId)
                                    {
                                        bestId = id;
                                    }
                                }
                            }
                            if (bestId >= 0)
                            {
                                callback.accept(bestId);
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to strictly resolve item id for {}", itemName, e);
            }
            callback.accept(null);
        });
    }

    /**
     * Same idea as resolveExactItemIdStrict, but checks the 'object_id' bucket instead -
     * some drop-table "sources" are actually world objects/scenery, not items or monsters
     * (e.g. "Chest (Tombs of Amascut)", a raid reward chest). Without this check, a
     * drop-row click routing between "is this an item?" and "is this an NPC?" has no way
     * to correctly identify an object, and falls through to an NPC search that doesn't
     * match anything real.
     */
    public void resolveExactObjectIdStrict(String objectName, Consumer<Integer> callback)
    {
        String query = "bucket('object_id').select('id')"
                + ".where('page_name','" + escapeForBucketQuery(objectName) + "').limit(1).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    if (bucket.size() > 0)
                    {
                        JsonObject row = bucket.get(0).getAsJsonObject();
                        if (row.has("id"))
                        {
                            JsonArray idArray = row.getAsJsonArray("id");
                            int bestId = -1;
                            for (JsonElement idElement : idArray)
                            {
                                String idStr = idElement.getAsString();
                                if (idStr.matches("\\d+"))
                                {
                                    int id = Integer.parseInt(idStr);
                                    if (id > bestId)
                                    {
                                        bestId = id;
                                    }
                                }
                            }
                            if (bestId >= 0)
                            {
                                callback.accept(bestId);
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to strictly resolve object id for {}", objectName, e);
            }
            callback.accept(null);
        });
    }

    /**
     * Resolves an item's real game ID from its wiki page name alone - the reverse of the
     * usual flow (which starts from an in-game right-click and already has the ID).
     * Uses 'page_name' as a where() filter on the 'item_id' bucket. Note the result's
     * "id" field comes back as a JSON array (e.g. {"id":["300"]}), not a plain value -
     * presumably to support multi-version items.
     * <p>
     * If the direct lookup finds nothing, falls back to the 'infobox_item' bucket, queried
     * by 'item_name' instead of 'page_name' - this resolves cases 'item_id' misses:
     * - "Antidote++(4)" isn't its own page (it redirects to a shared "Antidote++" article
     *   with four dose variants) - item_id only indexes canonical page titles, but
     *   infobox_item is indexed by item_name and finds it directly.
     * - "Key (medium)" resolves to eleven different IDs (different guards/contexts drop
     *   different underlying item variants that all display as "Key (medium)") - since
     *   they're all the same icon visually, the first one is used.
     *
     * @param callback receives the resolved ID, or null if the item couldn't be resolved
     */
    public void resolveItemIdByName(String itemName, Consumer<Integer> callback)
    {
        String query = "bucket('item_id').select('id')"
                + ".where('page_name','" + escapeForBucketQuery(itemName) + "').limit(1).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    if (bucket.size() > 0)
                    {
                        JsonObject row = bucket.get(0).getAsJsonObject();
                        if (row.has("id"))
                        {
                            JsonArray idArray = row.getAsJsonArray("id");
                            // Some items (e.g. Clue scroll (elite)) don't have one single
                            // canonical ID the wiki considers definitive, and the bucket
                            // returns the literal string "N/A" instead of a number in that
                            // case - confirmed via a real runtime log. This array can also
                            // hold multiple historical IDs for a single page (same root
                            // cause as infobox_item's multi-ID case below) - scanning for
                            // the largest valid numeric value rather than trusting the
                            // first element handles both cases the same way.
                            int bestId = -1;
                            for (JsonElement idElement : idArray)
                            {
                                String idStr = idElement.getAsString();
                                if (idStr.matches("\\d+"))
                                {
                                    int id = Integer.parseInt(idStr);
                                    if (id > bestId)
                                    {
                                        bestId = id;
                                    }
                                }
                            }
                            if (bestId >= 0)
                            {
                                callback.accept(bestId);
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to resolve item id for {}", itemName, e);
                callback.accept(null);
                return;
            }
            resolveItemIdViaInfoboxItem(itemName, callback);
        });
    }

    /**
     * Resolves an item's ID via infobox_item, trying up to three name variants in order:
     * the name as given, then (if it has a parenthetical qualifier) that qualifier's case
     * toggled, then finally the bare base name with the qualifier stripped entirely. See
     * tryBareBaseName for why that last resort exists - Coin pouch (dropped by Hero)
     * showed neither case variant of the qualifier matching anything at all.
     */
    private void resolveItemIdViaInfoboxItem(String itemName, Consumer<Integer> callback)
    {
        queryInfoboxItemForId(itemName, bestId ->
        {
            if (bestId != null)
            {
                callback.accept(bestId);
                return;
            }

            String toggledCaseName = toggleParentheticalCase(itemName);
            if (toggledCaseName != null)
            {
                queryInfoboxItemForId(toggledCaseName, toggledId ->
                {
                    if (toggledId != null)
                    {
                        callback.accept(toggledId);
                        return;
                    }

                    tryBareBaseName(itemName, callback);
                });
            }
            else
            {
                tryBareBaseName(itemName, callback);
            }
        });
    }

    /**
     * Final fallback: strips a trailing "(qualifier)" entirely and tries the bare base
     * name alone - e.g. "Coin pouch (Hero)" -> "Coin pouch". Coin pouch (dropped by Hero)
     * showed neither the lowercase nor Title-case qualifier variant matching any
     * infobox_item row, meaning the per-NPC qualifier isn't part of that item's actual
     * item_name field the way it is for e.g. Pendant of ates. Many multi-version items
     * on this wiki share one identical item_name across every
     * version (only the icon/item_id differ per version, not the display name), so this
     * is a reasonable last resort - it may not resolve to the exact NPC-specific variant's
     * own icon, but a real, generic icon for the item beats showing none at all. Skipped
     * entirely if there's no parenthetical to strip in the first place.
     */
    private void tryBareBaseName(String itemName, Consumer<Integer> callback)
    {
        String bareBaseName = itemName.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        if (bareBaseName.equals(itemName) || bareBaseName.isEmpty())
        {
            callback.accept(null);
            return;
        }

        queryInfoboxItemForId(bareBaseName, callback);
    }

    /**
     * Some items (clue scrolls especially) have gone through many graphical reworks over
     * the years, each keeping its own tracked ID under the same display name - a live
     * query can return 150+ IDs for "Clue scroll (medium)" alone, including junk values
     * ("undefined", "hist2841"). With no explicit ordering, taking the first result was
     * effectively arbitrary and could land on a defunct ID with no valid sprite. Instead,
     * this scans every candidate and takes the largest valid numeric ID, since OSRS
     * generally assigns higher IDs to more recently-added items.
     * <p>
     * Extracted as a shared helper since it's reused across three name variants
     * (original, case-toggled, bare-base-name) - the callback receives null (not an
     * exception) when no valid ID is found, so callers can chain fallback attempts.
     */
    private void queryInfoboxItemForId(String itemName, Consumer<Integer> callback)
    {
        String query = "bucket('infobox_item').select('item_id')"
                + ".where('item_name','" + escapeForBucketQuery(itemName) + "').limit(200).run()";

        runBucketQuery(query, root ->
        {
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray bucket = root.getAsJsonArray("bucket");
                    int bestId = -1;
                    for (JsonElement rowElement : bucket)
                    {
                        JsonObject row = rowElement.getAsJsonObject();
                        if (row.has("item_id"))
                        {
                            JsonArray idArray = row.getAsJsonArray("item_id");
                            for (JsonElement idElement : idArray)
                            {
                                String idStr = idElement.getAsString();
                                if (idStr.matches("\\d+"))
                                {
                                    int id = Integer.parseInt(idStr);
                                    if (id > bestId)
                                    {
                                        bestId = id;
                                    }
                                }
                            }
                        }
                    }
                    if (bestId >= 0)
                    {
                        callback.accept(bestId);
                        return;
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to resolve item id via infobox_item for {}", itemName, e);
            }
            callback.accept(null);
        });
    }

    /**
     * Toggles the case of a trailing "(qualifier)" - lowercase becomes Title-case and vice
     * versa. Returns null if the name has no such parenthetical, or if toggling would
     * produce the exact same string (nothing meaningful to retry).
     */
    private String toggleParentheticalCase(String name)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*) \\(([^)]+)\\)$").matcher(name);
        if (!matcher.matches())
        {
            return null;
        }

        String base = matcher.group(1);
        String qualifier = matcher.group(2);
        String toggled = qualifier.equals(qualifier.toLowerCase())
                ? qualifier.substring(0, 1).toUpperCase() + qualifier.substring(1)
                : qualifier.toLowerCase();

        if (toggled.equals(qualifier))
        {
            return null;
        }
        return base + " (" + toggled + ")";
    }

    /**
     * Best-effort escaping for values interpolated into the Bucket query DSL string. The
     * Bucket API doesn't document an official escaping scheme for embedded quotes, so this
     * just avoids breaking the query's own quoting for the common case of an apostrophe in
     * a page name (e.g. "Saradomin's light").
     */
    private String escapeForBucketQuery(String value)
    {
        return value.replace("'", "\\'");
    }

    public void fetchDescription(String itemName, Consumer<String> callback)
    {
        fetchExtract(itemName, extract ->
        {
            if (extract != null && !extract.isEmpty())
            {
                callback.accept(extract);
                return;
            }

            searchForPage(itemName, resolvedTitle ->
            {
                if (resolvedTitle == null)
                {
                    callback.accept("No description found.");
                    return;
                }
                fetchExtract(resolvedTitle, extract2 ->
                        callback.accept(extract2 != null && !extract2.isEmpty()
                                ? extract2
                                : "No description found.")
                );
            });
        });
    }

    private void fetchExtract(String title, Consumer<String> callback)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("prop", "extracts")
                .addQueryParameter("exintro", "1")
                .addQueryParameter("explaintext", "1")
                .addQueryParameter("redirects", "1")
                .addQueryParameter("format", "json")
                .addQueryParameter("titles", title)
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                log.warn("Failed to fetch wiki extract for {}", title, e);
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonObject pages = root.getAsJsonObject("query").getAsJsonObject("pages");

                    for (Map.Entry<String, com.google.gson.JsonElement> entry : pages.entrySet())
                    {
                        JsonObject page = entry.getValue().getAsJsonObject();

                        if (page.has("missing"))
                        {
                            callback.accept(null);
                            return;
                        }

                        if (page.has("extract"))
                        {
                            String extract = page.get("extract").getAsString();
                            callback.accept(extract);
                            return;
                        }
                    }

                    callback.accept(null);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse wiki extract for {}", title, e);
                    callback.accept(null);
                }
            }
        });
    }

    private void searchForPage(String query, Consumer<String> callback)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "search")
                .addQueryParameter("srlimit", "1")
                .addQueryParameter("format", "json")
                .addQueryParameter("srsearch", query)
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    var results = root.getAsJsonObject("query").getAsJsonArray("search");

                    if (results.size() == 0)
                    {
                        callback.accept(null);
                        return;
                    }

                    String title = results.get(0).getAsJsonObject().get("title").getAsString();
                    callback.accept(title);
                }
                catch (Exception e)
                {
                    callback.accept(null);
                }
            }
        });
    }

    public void fetchImage(String itemName, Consumer<BufferedImage> callback)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("prop", "pageimages")
                .addQueryParameter("piprop", "thumbnail")
                .addQueryParameter("pithumbsize", "100")
                .addQueryParameter("redirects", "1")
                .addQueryParameter("format", "json")
                .addQueryParameter("titles", itemName)
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonObject pages = root.getAsJsonObject("query").getAsJsonObject("pages");

                    String thumbUrl = null;
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : pages.entrySet())
                    {
                        JsonObject page = entry.getValue().getAsJsonObject();
                        if (page.has("thumbnail"))
                        {
                            thumbUrl = page.getAsJsonObject("thumbnail").get("source").getAsString();
                        }
                    }

                    if (thumbUrl == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    downloadImage(thumbUrl, callback);
                }
                catch (Exception e)
                {
                    callback.accept(null);
                }
            }
        });
    }

    private void downloadImage(String imageUrl, Consumer<BufferedImage> callback)
    {
        Request request = new Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    BufferedImage img = javax.imageio.ImageIO.read(response.body().byteStream());
                    callback.accept(img);
                }
                catch (Exception e)
                {
                    callback.accept(null);
                }
            }
        });
    }

    public static class InfoboxData
    {
        public String released;
        public String members;
        public String questItem;
        public String tradeable;
        public String equipable;
        public String stackable;
        public String noteable;
        public String options;
        public String value;
        public String weight;
    }

    /**
     * NPC properties. Covers fields from both {{Infobox Monster}} (attackable NPCs with
     * combat stats) and {{Infobox NPC}} (non-combat NPCs) - these are two different wiki
     * templates with different field sets, but since extractInfoboxBlock() just grabs
     * whichever "{{Infobox ...}}" block is actually on the page, we extract both possible
     * sets of field names and whichever ones don't apply to a given NPC's actual infobox
     * type simply come back null (left out of the display rather than shown as "Unknown" -
     * unlike items, not every field is expected to exist for every NPC).
     */
    public static class NpcInfoboxData
    {
        public String released;
        public String members;
        public String combatLevel;
        public String race;
        public String attackStyle;
        public String maxHit;
        public String aggressive;
        public String poisonous;
        public String slayerLevel;
        public String quest;
    }

    /** Object/scenery properties, from {{Infobox Scenery}} - that's the actual template
     * name the wiki uses for objects, not "Infobox Object". */
    public static class ObjectInfoboxData
    {
        public String released;
        public String members;
        public String quest;
        public String options;
    }

    public void fetchInfobox(String itemName, int targetItemId, Consumer<InfoboxData> callback)
    {
        fetchInfoboxInternal(itemName, targetItemId, callback, false);
    }

    public void fetchNpcInfobox(String npcName, int targetNpcId, Consumer<NpcInfoboxData> callback)
    {
        fetchNpcInfoboxInternal(npcName, targetNpcId, callback, false);
    }

    public void fetchObjectInfobox(String objectName, int targetObjectId, Consumer<ObjectInfoboxData> callback)
    {
        fetchObjectInfoboxInternal(objectName, targetObjectId, callback, false);
    }

    private void fetchNpcInfoboxInternal(String npcName, int targetNpcId, Consumer<NpcInfoboxData> callback, boolean isRedirectRetry)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("page", npcName)
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    if (root.has("error"))
                    {
                        callback.accept(null);
                        return;
                    }

                    String wikitext = root.getAsJsonObject("parse")
                            .getAsJsonObject("wikitext")
                            .get("*").getAsString();

                    if (!isRedirectRetry)
                    {
                        String redirectTarget = extractRedirectTarget(wikitext);
                        if (redirectTarget != null)
                        {
                            fetchNpcInfoboxInternal(redirectTarget, targetNpcId, callback, true);
                            return;
                        }
                    }

                    String infoboxBlock = extractInfoboxBlock(wikitext);
                    if (infoboxBlock == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    String versionIndex = extractVersionIndexForId(infoboxBlock, targetNpcId);

                    NpcInfoboxData data = new NpcInfoboxData();
                    data.released = extractFieldWithFallback(infoboxBlock, "release", "released", versionIndex);
                    data.members = extractFieldWithFallback(infoboxBlock, "members", null, versionIndex);
                    // Deliberately NOT falling back to "level" here - some non-monster
                    // pages reuse "level" for an unrelated skill requirement (e.g. a
                    // player-owned house shop's Construction level to build the room),
                    // not a combat level. Only "combat" is unambiguous enough to trust.
                    data.combatLevel = extractFieldWithFallback(infoboxBlock, "combat", null, versionIndex);
                    data.race = extractFieldWithFallback(infoboxBlock, "race", null, versionIndex);
                    data.attackStyle = extractFieldWithFallback(infoboxBlock, "attack style", null, versionIndex);
                    data.maxHit = extractFieldWithFallback(infoboxBlock, "max hit", null, versionIndex);
                    data.aggressive = extractFieldWithFallback(infoboxBlock, "aggressive", null, versionIndex);
                    data.poisonous = extractFieldWithFallback(infoboxBlock, "poisonous", null, versionIndex);
                    data.slayerLevel = extractFieldWithFallback(infoboxBlock, "slaylvl", null, versionIndex);
                    data.quest = extractFieldWithFallback(infoboxBlock, "quest", null, versionIndex);

                    if (data.released != null)
                    {
                        data.released = cleanWikiValue(data.released);
                    }
                    if (data.members != null)
                    {
                        data.members = cleanWikiValue(data.members);
                    }
                    if (data.combatLevel != null)
                    {
                        data.combatLevel = cleanWikiValue(data.combatLevel);
                    }
                    if (data.race != null)
                    {
                        data.race = cleanWikiValue(data.race);
                    }
                    if (data.attackStyle != null)
                    {
                        data.attackStyle = cleanWikiValue(data.attackStyle);
                    }
                    if (data.maxHit != null)
                    {
                        data.maxHit = cleanWikiValue(data.maxHit);
                    }
                    if (data.aggressive != null)
                    {
                        data.aggressive = cleanWikiValue(data.aggressive);
                    }
                    if (data.poisonous != null)
                    {
                        data.poisonous = cleanWikiValue(data.poisonous);
                    }
                    if (data.slayerLevel != null)
                    {
                        data.slayerLevel = cleanWikiValue(data.slayerLevel);
                    }
                    if (data.quest != null)
                    {
                        data.quest = cleanWikiValue(data.quest);
                    }

                    callback.accept(data);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse NPC infobox wikitext for {}", npcName, e);
                    callback.accept(null);
                }
            }
        });
    }

    private void fetchObjectInfoboxInternal(String objectName, int targetObjectId, Consumer<ObjectInfoboxData> callback, boolean isRedirectRetry)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("page", objectName)
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    if (root.has("error"))
                    {
                        callback.accept(null);
                        return;
                    }

                    String wikitext = root.getAsJsonObject("parse")
                            .getAsJsonObject("wikitext")
                            .get("*").getAsString();

                    if (!isRedirectRetry)
                    {
                        String redirectTarget = extractRedirectTarget(wikitext);
                        if (redirectTarget != null)
                        {
                            fetchObjectInfoboxInternal(redirectTarget, targetObjectId, callback, true);
                            return;
                        }
                    }

                    String infoboxBlock = extractInfoboxBlock(wikitext);
                    if (infoboxBlock == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    String versionIndex = extractVersionIndexForId(infoboxBlock, targetObjectId);

                    ObjectInfoboxData data = new ObjectInfoboxData();
                    data.released = extractFieldWithFallback(infoboxBlock, "release", "released", versionIndex);
                    data.members = extractFieldWithFallback(infoboxBlock, "members", null, versionIndex);
                    data.quest = extractFieldWithFallback(infoboxBlock, "quest", null, versionIndex);
                    data.options = extractFieldWithFallback(infoboxBlock, "options", null, versionIndex);

                    if (data.released != null)
                    {
                        data.released = cleanWikiValue(data.released);
                    }
                    if (data.members != null)
                    {
                        data.members = cleanWikiValue(data.members);
                    }
                    if (data.quest != null)
                    {
                        data.quest = cleanWikiValue(data.quest);
                    }
                    if (data.options != null)
                    {
                        data.options = cleanWikiValue(data.options);
                    }

                    callback.accept(data);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse Object infobox wikitext for {}", objectName, e);
                    callback.accept(null);
                }
            }
        });
    }

    private void fetchInfoboxInternal(String itemName, int targetItemId, Consumer<InfoboxData> callback, boolean isRedirectRetry)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("page", itemName)
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                callback.accept(null);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    String bodyStr = response.body().string();
                    JsonObject root = gson.fromJson(bodyStr, JsonObject.class);

                    if (root.has("error"))
                    {
                        callback.accept(null);
                        return;
                    }

                    String wikitext = root.getAsJsonObject("parse")
                            .getAsJsonObject("wikitext")
                            .get("*").getAsString();

                    if (!isRedirectRetry)
                    {
                        String redirectTarget = extractRedirectTarget(wikitext);
                        if (redirectTarget != null)
                        {
                            fetchInfoboxInternal(redirectTarget, targetItemId, callback, true);
                            return;
                        }
                    }

                    String infoboxBlock = extractInfoboxBlock(wikitext);
                    if (infoboxBlock == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    String versionIndex = extractVersionIndexForId(infoboxBlock, targetItemId);

                    InfoboxData data = new InfoboxData();

                    data.released = orUnknown(extractFieldWithFallback(infoboxBlock, "release", "released", versionIndex));
                    data.members = orUnknown(extractFieldWithFallback(infoboxBlock, "members", null, versionIndex));
                    data.questItem = orUnknown(extractFieldWithFallback(infoboxBlock, "quest", null, versionIndex));
                    data.tradeable = orUnknown(extractFieldWithFallback(infoboxBlock, "tradeable", null, versionIndex));
                    data.equipable = orUnknown(extractFieldWithFallback(infoboxBlock, "equipable", null, versionIndex));
                    data.stackable = orUnknown(extractFieldWithFallback(infoboxBlock, "stackable", null, versionIndex));
                    data.noteable = orUnknown(extractFieldWithFallback(infoboxBlock, "noteable", null, versionIndex));
                    data.options = orUnknown(extractFieldWithFallback(infoboxBlock, "options", null, versionIndex));
                    data.value = orUnknown(extractFieldWithFallback(infoboxBlock, "value", null, versionIndex));
                    data.weight = orUnknown(extractFieldWithFallback(infoboxBlock, "weight", null, versionIndex));

                    callback.accept(data);
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse infobox wikitext for {}", itemName, e);
                    callback.accept(null);
                }
            }
        });
    }

    private String orUnknown(String raw)
    {
        return (raw != null) ? cleanWikiValue(raw) : "Unknown";
    }

    private String extractRedirectTarget(String wikitext)
    {
        String trimmed = wikitext.trim();
        Pattern pattern = Pattern.compile("^#REDIRECT\\s*\\[\\[([^\\]#]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(trimmed);
        if (matcher.find())
        {
            return matcher.group(1).trim().replace("_", " ");
        }
        return null;
    }

    private String extractInfoboxBlock(String wikitext)
    {
        String lower = wikitext.toLowerCase();
        int start = lower.indexOf("{{infobox");

        if (start == -1)
        {
            return null;
        }

        int depth = 0;
        int i = start;
        while (i < wikitext.length() - 1)
        {
            if (wikitext.charAt(i) == '{' && wikitext.charAt(i + 1) == '{')
            {
                depth++;
                i += 2;
            }
            else if (wikitext.charAt(i) == '}' && wikitext.charAt(i + 1) == '}')
            {
                depth--;
                i += 2;
                if (depth == 0)
                {
                    return wikitext.substring(start, i);
                }
            }
            else
            {
                i++;
            }
        }

        return wikitext.substring(start);
    }

    /**
     * Determines which numbered "version" of a switch infobox matches our target item ID,
     * by scanning the id/id1/id2/... fields (which list the item ID(s) for each version) and
     * finding which version number contains our target ID. Returns null if the infobox has no
     * versions, or none match (in which case callers fall back to unsuffixed/generic fields).
     */
    private String extractVersionIndexForId(String block, int targetId)
    {
        if (targetId < 0)
        {
            return null;
        }

        Pattern pattern = Pattern.compile("\\|\\s*id(\\d*)\\s*=\\s*([^\\|\\n\\}]+)");
        Matcher matcher = pattern.matcher(block);
        while (matcher.find())
        {
            String indexStr = matcher.group(1);
            if (indexStr.isEmpty())
            {
                continue;
            }

            for (String part : matcher.group(2).split(","))
            {
                try
                {
                    if (Integer.parseInt(part.trim()) == targetId)
                    {
                        return indexStr;
                    }
                }
                catch (NumberFormatException ignored)
                {
                }
            }
        }

        return null;
    }

    private String extractFieldWithFallback(String block, String primaryName, String altName, String versionIndex)
    {
        if (versionIndex != null)
        {
            String value = extractField(block, primaryName + versionIndex);
            if (value != null)
            {
                return value;
            }

            if (altName != null)
            {
                value = extractField(block, altName + versionIndex);
                if (value != null)
                {
                    return value;
                }
            }
        }

        String value = extractField(block, primaryName);
        if (value != null)
        {
            return value;
        }

        if (altName != null)
        {
            value = extractField(block, altName);
            if (value != null)
            {
                return value;
            }
        }

        for (int i = 1; i <= 10; i++)
        {
            value = extractField(block, primaryName + i);
            if (value != null)
            {
                return value;
            }
        }

        return null;
    }

    private String extractField(String block, String fieldName)
    {
        // The lookahead stops at either "newline + next |field" or "newline + }}" (the
        // infobox's own closing) - not at a lone '}' character, since that would
        // truncate fields containing a nested template like "{{*}}" (a bullet-point
        // marker some quest lists use).
        //
        // [ \t]* (not \s*) around the "=" is deliberate - \s* matches newlines too, so
        // an empty value (e.g. "|release1 = " followed immediately by a newline) would
        // let the greedy \s* consume that newline and capture the start of the NEXT
        // field's line as if it were this field's own value.
        Pattern pattern = Pattern.compile("\\|\\s*" + Pattern.quote(fieldName)
                + "[ \\t]*=[ \\t]*(.*?)(?=\\n\\s*\\||\\n\\s*\\}\\})");
        Matcher matcher = pattern.matcher(block);
        if (matcher.find())
        {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String cleanWikiValue(String raw)
    {
        String cleaned = raw.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)\\]\\]", "$1");
        cleaned = cleaned.replaceAll("\\{\\{[^}]*\\}\\}", "");
        // Quest/reward-style fields often list multiple entries separated by <br/> tags
        // (e.g. "{{*}} [[Cook's Assistant]]<br/>{{*}} [[Recipe for Disaster]]") - treat
        // as a separator rather than showing the literal "<br/>" text.
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", ", ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^,\\s*|,\\s*$", "").replaceAll(",\\s*,", ",");
        return cleaned.isEmpty() ? "Unknown" : cleaned;
    }
}