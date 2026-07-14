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
    }

    /**
     * Looks up where an item can be obtained: monster drops (Bucket:Dropsline) and shop
     * stock (Bucket:Storeline). Both bucket queries run concurrently and the combined
     * result is handed to the callback once both have finished (each individually falls
     * back to an empty list on any failure, so one bad query never blocks the other).
     * <p>
     * NOTE ON FIELD NAMES: the wiki's Bucket API doesn't have official per-table schema
     * docs at the time of writing, so the field names below ("item_page" for the dropped
     * item's page on the dropsline bucket, "sold_item"/"sold_by"/"store_sell_price" on the
     * storeline bucket) are inferred from the wiki's own Lua modules and third-party query
     * examples rather than a live-tested response. If this comes back empty for items you
     * know have sources, open the generated query URL (logged at debug level below) in a
     * browser to see the raw field names actually returned and adjust the select()/where()
     * calls accordingly.
     *
     * @param pageName the exact wiki page name of the item (as resolved via resolveExactPageName)
     * @param callback receives the combined drop/shop source lists (empty lists if nothing found)
     */
    public void fetchItemSources(String pageName, Consumer<ItemSourcesData> callback)
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

        fetchDropSources(pageName, drops ->
        {
            data.drops = drops;
            finishOne.run();
        });

        fetchShopSources(pageName, shops ->
        {
            data.shops = shops;
            finishOne.run();
        });
    }

    /**
     * Fetches a monster/NPC's own drop table - the reverse of fetchItemSources: instead of
     * "which monsters drop this item", this is "which items does this monster drop".
     * Confirmed working via a live query: filtering dropsline by page_name (the monster)
     * instead of item_name (the item) returns that monster's real drop table.
     * <p>
     * Reuses the same DropSource class as item lookups, just populated the other way
     * around - source holds the dropped item's name instead of a monster's name, and
     * level/dropType/skillIcon are left null (not applicable here; buildDropRow already
     * handles those being absent gracefully). This keeps the display identical to the
     * item-side drop rows, as requested.
     *
     * @param npcName the exact wiki page name of the monster/NPC
     * @param callback receives the drop list, sorted most-common-first (empty if none found)
     */
    public void fetchNpcDrops(String npcName, Consumer<List<DropSource>> callback)
    {
        String query = "bucket('dropsline').select('item_name','drop_json')"
                + ".where('page_name','" + escapeForBucketQuery(npcName) + "').limit(50).run()";

        runBucketQuery(query, root -> callback.accept(parseNpcDropRows(root, npcName)));
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
                    ds.source = firstString(row, "item_name");

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

    private void fetchDropSources(String itemName, Consumer<List<DropSource>> callback)
    {
        // Confirmed working end-to-end against the live API: 'item_name' is the correct
        // where() field (not 'item_page', which errored), and 'page_name'/'drop_json' are
        // valid select() fields. Rarity/quantity/level all live inside the drop_json blob
        // rather than as flattened top-level fields - see parseDropRows below for the
        // confirmed blob key names.
        String query = "bucket('dropsline').select('page_name','drop_json')"
                + ".where('item_name','" + escapeForBucketQuery(itemName) + "').limit(50).run()";

        runBucketQuery(query, root -> callback.accept(parseDropRows(root, itemName)));
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

                    // Field names confirmed by inspecting an actual live drop_json blob
                    // (e.g. for Rune arrow): {"Rarity":"3/128","Drop level":"304",
                    // "Drop Quantity":"8",...}. Note it's "Drop Quantity", not "Quantity" -
                    // that mismatch was the actual bug that made every item show up empty.
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
                                    // The raw value here is often an unreduced fraction like
                                    // "6/134.35" or "7/131" - the wiki's own page always
                                    // displays these reduced to "1 in N" form (verified against
                                    // a live page: 131/7 -> "1/18.71", 100/5 -> "1/20",
                                    // 134.35/6 -> "1/22.39", matching exactly), so reduce here
                                    // rather than showing the raw fraction as-is.
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

                    if (ds.source != null)
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
                + ".where('sold_item','" + escapeForBucketQuery(itemName) + "').limit(50).run()";

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
                        // Logging the raw error object (not just the query) is the whole point
                        // here - this is what tells us whether a select()/where() field name
                        // was wrong, since the Bucket API doesn't publish a field schema we can
                        // check against ahead of time.
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
     * Resolves an item's real game ID from its wiki page name alone - the reverse of the
     * usual flow (which starts from an in-game right-click and already has the ID).
     * Confirmed via a live query that 'page_name' works as a where() filter on the
     * 'item_id' bucket. Note the result's "id" field comes back as a JSON array (e.g.
     * {"id":["300"]}), not a plain value - presumably to support multi-version items.
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
                            if (idArray.size() > 0)
                            {
                                callback.accept(idArray.get(0).getAsInt());
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to resolve item id for {}", itemName, e);
            }
            callback.accept(null);
        });
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
                    // Deliberately NOT falling back to "level" here - confirmed via real
                    // data (Tool store 5, a player-owned house shop) that some non-monster
                    // pages reuse "level" for an unrelated skill requirement (Construction
                    // level to build the room), not a combat level at all. Only "combat"
                    // (the dedicated Infobox Monster field) is unambiguous enough to trust.
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
        Pattern pattern = Pattern.compile("\\|\\s*" + Pattern.quote(fieldName) + "\\s*=\\s*([^\\|\\n\\}]+)");
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
        cleaned = cleaned.replaceAll("\\{\\{[^}]*\\}\\}", "").trim();
        return cleaned.isEmpty() ? "Unknown" : cleaned;
    }
}