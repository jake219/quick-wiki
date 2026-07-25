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

    public void resolveExactPageName(String cargoTable, int id, Consumer<String> callback)
    {
        if (id < 0)
        {
            callback.accept(null);
            return;
        }

        String query = "bucket('" + cargoTable + "').select('page_name').where('id','" + id + "').run()";

        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "bucket")
                .addQueryParameter("query", query)
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

    public static class DropSource
    {
        public String source;
        public String level;
        public String rarity;
        public String quantity;

        public String dropType;

        public java.awt.image.BufferedImage skillIcon;
    }

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

        public int lightRangeDefenceBonus;
        public int standardRangeDefenceBonus;
        public int heavyRangeDefenceBonus;

        public String elementalWeaknessType;
        public int elementalWeaknessPercent;
    }

    public static class ShopSource
    {
        public String shopName;
        public String price;

        public String currency;
    }

    public static class Material
    {
        public String name;
        public String quantity;
    }

    public static class SkillReq
    {
        public String skill;
        public String level;
        public BufferedImage skillIcon;
    }

    public static class RecipeData
    {
        public String name;
        public List<Material> materials = new ArrayList<>();
        public List<SkillReq> requirements = new ArrayList<>();
        public String facility;
    }

    public static class ItemSourcesData
    {
        public List<DropSource> drops = new ArrayList<>();
        public List<ShopSource> shops = new ArrayList<>();
        public List<RecipeData> recipes = new ArrayList<>();

        public boolean isRewards = false;
    }

    public void fetchItemSources(String pageName, int itemId, Consumer<ItemSourcesData> callback)
    {
        if (itemId >= 0)
        {
            String resolveQuery = "bucket('infobox_item').select('item_name','page_name')"
                    + ".where('item_id'," + itemId + ").limit(1).run()";

            runBucketQuery(resolveQuery, resolveRoot ->
            {
                String resolvedName = null;
                String resolvedPage = null;
                try
                {
                    if (resolveRoot != null && resolveRoot.has("bucket"))
                    {
                        JsonArray bucket = resolveRoot.getAsJsonArray("bucket");
                        if (bucket.size() > 0)
                        {
                            JsonObject row = bucket.get(0).getAsJsonObject();
                            String name = firstString(row, "item_name");
                            if (name != null && !name.isEmpty())
                            {
                                resolvedName = name;
                            }
                            // The wiki page can be disambiguated (e.g. "Teleport to house
                            // (tablet)") while the item name is plain; recipes are keyed by
                            // the page, so capture it for the creation lookup.
                            String page = firstString(row, "page_name");
                            if (page != null && !page.isEmpty())
                            {
                                resolvedPage = page;
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to resolve variant-specific item_name for {} (id={})", pageName, itemId, e);
                }

                String nameToUse = resolvedName != null ? resolvedName : pageName;
                String pageToUse = resolvedPage != null ? resolvedPage : nameToUse;
                fetchItemSourcesByName(nameToUse, pageToUse, callback);
            });
        }
        else
        {
            fetchItemSourcesByName(pageName, pageName, callback);
        }
    }

    private void fetchItemSourcesByName(String itemName, String pageName, Consumer<ItemSourcesData> callback)
    {
        fetchItemSourcesByName(itemName, pageName, false, callback);
    }

    private void fetchItemSourcesByName(String itemName, String pageName, boolean isBaseFallback, Consumer<ItemSourcesData> callback)
    {
        ItemSourcesData data = new ItemSourcesData();
        AtomicInteger remaining = new AtomicInteger(3);
        Runnable finishOne = () ->
        {
            if (remaining.decrementAndGet() == 0)
            {
                // Degraded variants (e.g. "Dharok's greataxe 75") are their own item ids with no
                // sources of their own - the base item is what actually drops. When nothing turns
                // up for such a variant, fall back to the base item's sources once.
                if (!isBaseFallback && sourcesAreEmpty(data))
                {
                    String base = baseNameForDegraded(itemName, pageName);
                    if (base != null)
                    {
                        fetchItemSourcesByName(base, base, true, callback);
                        return;
                    }
                }
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

        fetchCreationMaterials(itemName, pageName, recipes ->
        {
            data.recipes = recipes;
            finishOne.run();
        });
    }

    private boolean sourcesAreEmpty(ItemSourcesData data)
    {
        return (data.drops == null || data.drops.isEmpty())
                && (data.shops == null || data.shops.isEmpty())
                && (data.recipes == null || data.recipes.isEmpty());
    }

    /**
     * Returns the base item name for a partially degraded variant, or null if the name is not a
     * degrade state. Barrows and similar equipment degrade into separate items named "<Base> <N>"
     * where N is a wear percentage (0/25/50/75/100); the base item is what carries the sources.
     */
    private String baseNameForDegraded(String itemName, String pageName)
    {
        String base = stripDegradeSuffix(itemName);
        if (base != null)
        {
            return base;
        }
        return stripDegradeSuffix(pageName);
    }

    private String stripDegradeSuffix(String name)
    {
        if (name == null)
        {
            return null;
        }
        Matcher matcher = Pattern.compile("^(.*\\S)\\s+(?:0|25|50|75|100)$").matcher(name.trim());
        if (matcher.matches())
        {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void fetchCreationMaterials(String itemName, String pageName, Consumer<List<RecipeData>> callback)
    {
        // Recipes are keyed by the wiki PAGE. Try the item's exact page first (it may be
        // disambiguated, e.g. "Teleport to house (tablet)"), then the plain item name, then
        // the base name with any "(charge)"/suffix stripped (for charged items whose recipe
        // lives on the base page). The output is always matched against the item itself.
        java.util.List<String> candidates = new ArrayList<>();
        addCandidate(candidates, pageName);
        addCandidate(candidates, itemName);
        addCandidate(candidates, itemName.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim());
        tryRecipeCandidates(candidates, 0, itemName, callback);
    }

    private void addCandidate(java.util.List<String> list, String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return;
        }
        String v = value.trim();
        for (String existing : list)
        {
            if (existing.equalsIgnoreCase(v))
            {
                return;
            }
        }
        list.add(v);
    }

    private void tryRecipeCandidates(java.util.List<String> candidates, int index, String matchName, Consumer<List<RecipeData>> callback)
    {
        if (index >= candidates.size())
        {
            callback.accept(new ArrayList<>());
            return;
        }
        queryRecipeMaterials(candidates.get(index), matchName, recipes ->
        {
            if (!recipes.isEmpty())
            {
                callback.accept(recipes);
                return;
            }
            tryRecipeCandidates(candidates, index + 1, matchName, callback);
        });
    }

    private void queryRecipeMaterials(String queryPage, String matchName, Consumer<List<RecipeData>> callback)
    {
        String query = "bucket('recipe').select('uses_material','production_json')"
                + ".where('page_name','" + escapeForBucketQuery(queryPage) + "').limit(20).run()";

        runBucketQuery(query, root ->
        {
            List<RecipeData> methods = new ArrayList<>();
            try
            {
                if (root != null && root.has("bucket"))
                {
                    JsonArray rows = root.getAsJsonArray("bucket");
                    boolean anyProduction = false;

                    // A page can hold several recipes: per dose, per creation method, or ones
                    // that merely USE this item. Collect every recipe whose output matches the
                    // item. Prefer exact output matches (dose and all) so a "prayer potion(4)"
                    // never pulls in the (1)/(2)/(3) recipes - but keep ALL of the exact ones,
                    // because an item can genuinely be made several different ways.
                    List<JsonObject> exact = new ArrayList<>();
                    List<JsonObject> loose = new ArrayList<>();
                    for (JsonElement el : rows)
                    {
                        if (!el.isJsonObject())
                        {
                            continue;
                        }
                        String productionJson = firstString(el.getAsJsonObject(), "production_json");
                        if (productionJson == null)
                        {
                            continue;
                        }
                        anyProduction = true;
                        try
                        {
                            JsonObject prod = gson.fromJson(productionJson, JsonObject.class);
                            if (prod == null || !recipeOutputMatches(prod, matchName, queryPage))
                            {
                                continue;
                            }
                            if (recipeOutputExact(prod, matchName))
                            {
                                exact.add(prod);
                            }
                            else
                            {
                                loose.add(prod);
                            }
                        }
                        catch (Exception ignored)
                        {
                        }
                    }

                    List<JsonObject> chosen;
                    if (!exact.isEmpty())
                    {
                        chosen = exact;
                    }
                    else if (!loose.isEmpty())
                    {
                        // No exact-dose match (e.g. a charged item whose recipe lives on the
                        // base page). Fall back to a single loose match to avoid listing every
                        // variant's method.
                        chosen = loose.subList(0, 1);
                    }
                    else
                    {
                        chosen = new ArrayList<>();
                    }

                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (JsonObject prod : chosen)
                    {
                        RecipeData recipe = parseRecipe(prod);
                        if (recipe.materials.isEmpty() && recipe.requirements.isEmpty())
                        {
                            continue;
                        }
                        String signature = recipeSignature(recipe);
                        if (seen.add(signature))
                        {
                            methods.add(recipe);
                        }
                    }

                    // Fallback: a single recipe row with no production_json is unambiguously
                    // this item's own recipe, so use its material names (no quantities).
                    if (methods.isEmpty() && !anyProduction && rows.size() == 1
                            && rows.get(0).isJsonObject())
                    {
                        JsonObject row = rows.get(0).getAsJsonObject();
                        RecipeData recipe = new RecipeData();
                        if (row.has("uses_material") && row.get("uses_material").isJsonArray())
                        {
                            for (JsonElement m : row.getAsJsonArray("uses_material"))
                            {
                                if (m == null || m.isJsonNull())
                                {
                                    continue;
                                }
                                String name = m.getAsString();
                                if (name != null && !name.trim().isEmpty())
                                {
                                    Material mat = new Material();
                                    mat.name = name.trim();
                                    recipe.materials.add(mat);
                                }
                            }
                        }
                        if (!recipe.materials.isEmpty())
                        {
                            methods.add(recipe);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to parse recipe materials for {}", matchName, e);
            }
            callback.accept(methods);
        });
    }

    private RecipeData parseRecipe(JsonObject prod)
    {
        RecipeData recipe = new RecipeData();
        if (prod == null)
        {
            return recipe;
        }
        if (prod.has("materials") && prod.get("materials").isJsonArray())
        {
            for (JsonElement mEl : prod.getAsJsonArray("materials"))
            {
                if (!mEl.isJsonObject())
                {
                    continue;
                }
                JsonObject m = mEl.getAsJsonObject();
                String name = plainString(m, "name");
                if (name == null || name.isEmpty())
                {
                    continue;
                }
                Material mat = new Material();
                mat.name = name;
                mat.quantity = plainString(m, "quantity");
                recipe.materials.add(mat);
            }
        }
        if (prod.has("skills") && prod.get("skills").isJsonArray())
        {
            for (JsonElement sEl : prod.getAsJsonArray("skills"))
            {
                if (!sEl.isJsonObject())
                {
                    continue;
                }
                JsonObject s = sEl.getAsJsonObject();
                String skillName = plainString(s, "name");
                if (skillName == null || skillName.isEmpty())
                {
                    continue;
                }
                SkillReq req = new SkillReq();
                req.skill = skillName;
                req.level = plainString(s, "level");
                recipe.requirements.add(req);
            }
        }
        String facility = plainString(prod, "facilities");
        if (facility != null && !facility.isEmpty() && !"None".equalsIgnoreCase(facility))
        {
            recipe.facility = facility;
        }

        // The wiki labels each creation method in the recipe output's "subtxt" (e.g. "Blast
        // Furnace", "Superheat", "Catalysed"), which also covers methods with no facility. Use
        // it as the method name, falling back to the facility, then to a bare "Method N".
        JsonObject output = recipeOutput(prod);
        String methodName = output != null ? plainString(output, "subtxt") : null;
        if (methodName == null || methodName.isEmpty())
        {
            methodName = recipe.facility;
        }
        if (methodName != null && !methodName.isEmpty())
        {
            recipe.name = methodName;
        }
        return recipe;
    }

    private String recipeSignature(RecipeData recipe)
    {
        StringBuilder sb = new StringBuilder();
        for (Material m : recipe.materials)
        {
            sb.append(m.name).append('#').append(m.quantity).append('|');
        }
        sb.append("::");
        for (SkillReq r : recipe.requirements)
        {
            sb.append(r.skill).append('#').append(r.level).append('|');
        }
        sb.append("::").append(recipe.facility);
        sb.append("::").append(recipe.name);
        return sb.toString().toLowerCase();
    }

    private JsonObject recipeOutput(JsonObject prod)
    {
        if (prod == null || !prod.has("output"))
        {
            return null;
        }
        JsonElement out = prod.get("output");
        if (out.isJsonObject())
        {
            return out.getAsJsonObject();
        }
        if (out.isJsonArray() && out.getAsJsonArray().size() > 0
                && out.getAsJsonArray().get(0).isJsonObject())
        {
            return out.getAsJsonArray().get(0).getAsJsonObject();
        }
        return null;
    }

    /** True when the recipe's output name/txt exactly equals the item (dose and all). */
    private boolean recipeOutputExact(JsonObject prod, String matchName)
    {
        if (matchName == null)
        {
            return false;
        }
        JsonObject output = recipeOutput(prod);
        if (output == null)
        {
            return false;
        }
        String n = plainString(output, "name");
        String t = plainString(output, "txt");
        String target = matchName.trim();
        return (n != null && n.equalsIgnoreCase(target)) || (t != null && t.equalsIgnoreCase(target));
    }

    private boolean recipeOutputMatches(JsonObject prod, String matchName, String queryPage)
    {
        JsonObject output = recipeOutput(prod);
        if (output == null)
        {
            return false;
        }
        // The output can be stored either as the plain item name (output.name = "Ring of
        // wealth") or the disambiguated page title (output.name = "Teleport to house
        // (tablet)", with the clean name in output.txt). Match either against the item name
        // or the page that was queried, so both storage styles resolve correctly.
        String[] sources = {plainString(output, "name"), plainString(output, "txt")};
        String[] targets = {matchName, queryPage};
        for (String source : sources)
        {
            if (source == null)
            {
                continue;
            }
            String normalizedSource = normalizeChargeName(source);
            for (String target : targets)
            {
                if (target != null && normalizedSource.equals(normalizeChargeName(target)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Normalizes an item name for recipe-output matching by stripping a trailing charge-state
     * suffix - a number, or "(empty)"/"(uncharged)" - so charge/state variants match the one
     * recipe that creates them (e.g. "Burning amulet(1)"/"Burning amulet(5)" -> "burning
     * amulet", "Toxic blowpipe (empty)" -> "toxic blowpipe"). Other suffixes like "(p)" are
     * kept, so genuinely different items (Steel arrow vs Steel arrow(p)) still don't match.
     */
    private String normalizeChargeName(String name)
    {
        if (name == null)
        {
            return "";
        }
        return name.replaceAll("(?i)\\s*\\((?:\\d+|empty|uncharged)\\)\\s*$", "").trim().toLowerCase();
    }

    private String plainString(JsonObject o, String field)
    {
        if (o == null || !o.has(field) || o.get(field).isJsonNull())
        {
            return null;
        }
        try
        {
            return o.get(field).getAsString().trim();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public void fetchNpcDrops(String npcName, Consumer<List<DropSource>> callback)
    {

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
                                            ds.rarity = rolls + " × " + ds.rarity;
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

    private static class DropSourcesResult
    {
        List<DropSource> drops;
        boolean isRewards;
    }

    private void fetchDropSources(String itemName, Consumer<DropSourcesResult> callback)
    {

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

    private void fetchNormalDropSources(String itemName, Consumer<DropSourcesResult> callback)
    {

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

            String baseName = itemName.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
            fetchDropSourcesInertFallback(baseName, callback);
        });
    }

    private String scrollBoxToClueScrollName(String itemName)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)^Scroll box \\(([^)]+)\\)$").matcher(itemName.trim());
        if (!matcher.matches())
        {
            return null;
        }
        return "Clue scroll (" + matcher.group(1) + ")";
    }

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
                                            ds.rarity = rolls + " × " + ds.rarity;
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

        results.sort(Comparator.comparingDouble(d -> rarityWeight(d.rarity)));
        return results;
    }

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

                int resolvedCombatLevel = firstInt(bestRow, "combat_level");
                fetchNpcExtraStats(npcName, resolvedCombatLevel, stats, callback);
            }
            else
            {
                callback.accept(null);
            }
        });
    }

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

    private void runBucketQuery(String query, Consumer<JsonObject> callback)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "bucket")
                .addQueryParameter("query", query)
                .addQueryParameter("format", "json")
                .build();

        log.debug("Quick Wiki bucket query: {}", url);

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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
        Request request = new Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

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

        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", ", ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^,\\s*|,\\s*$", "").replaceAll(",\\s*,", ",");
        return cleaned.isEmpty() ? "Unknown" : cleaned;
    }

    private final java.util.Map<Integer, Integer> buyLimitCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> nameToIdCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean mappingLoaded = false;
    private volatile boolean mappingLoading = false;

    public static class Market
    {
        public List<Integer> prices = new ArrayList<>();
        public List<Long> timestamps = new ArrayList<>();
        public Integer instaBuy;
        public Integer instaSell;
        public Integer buyLimit;
        public long todayVolume;
        public long avgVolume;

        public Double change1D;
        public Double change1W;
        public Double change1M;
        public Double change1Y;
    }

    public void fetchMarket(int itemId, String itemName, Consumer<Market> callback)
    {
        fetchMarket(itemId, itemName, "1M", callback);
    }

    public void fetchMarket(int itemId, String itemName, String range, Consumer<Market> callback)
    {
        if (itemId < 0)
        {
            callback.accept(null);
            return;
        }

        final String r = range == null ? "1M" : range;

        final boolean graphFromReference = "1M".equals(r) || "1Y".equals(r);
        final int refGraphKeep = "1Y".equals(r) ? 365 : 31;

        ensureMappingLoaded(() ->
        {
            int geId = resolveGeId(itemId, itemName);
            Market m = new Market();
            Integer bl = buyLimitCache.get(geId);
            m.buyLimit = (bl != null && bl > 0) ? bl : null;

            AtomicInteger remaining = new AtomicInteger(graphFromReference ? 2 : 3);
            Runnable done = () ->
            {
                if (remaining.decrementAndGet() == 0)
                {
                    boolean hasAnything = m.prices.size() >= 2 || m.instaBuy != null || m.instaSell != null;
                    callback.accept(hasAnything ? m : null);
                }
            };

            fetchReferenceInto(geId, m, graphFromReference, refGraphKeep, done);
            if (!graphFromReference)
            {
                fetchTimeseriesInto(geId, timestepForRange(r), keepForRange(r), m, done);
            }
            fetchLatestInto(geId, m, done);
        });
    }

    private static String timestepForRange(String range)
    {
        if (range == null)
        {
            return "6h";
        }
        switch (range)
        {
            case "1D":
                return "5m";
            case "1W":
                return "1h";
            case "1Y":
                return "24h";
            case "1M":
            default:
                return "6h";
        }
    }

    private static int keepForRange(String range)
    {
        if (range == null)
        {
            return 120;
        }
        switch (range)
        {
            case "1D":
                return 288;
            case "1W":
                return 168;
            case "1Y":
                return 365;
            case "1M":
            default:
                return 120;
        }
    }

    private int resolveGeId(int itemId, String itemName)
    {
        if (buyLimitCache.containsKey(itemId))
        {
            return itemId;
        }
        if (itemName == null || itemName.isEmpty())
        {
            return itemId;
        }
        String base = itemName.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        String[] candidates = {itemName, base, base + " (empty)", base + " (uncharged)", base + " (Empty)"};
        for (String c : candidates)
        {
            Integer id = nameToIdCache.get(c.toLowerCase());
            if (id != null)
            {
                return id;
            }
        }
        return itemId;
    }

    private void ensureMappingLoaded(Runnable onReady)
    {
        if (mappingLoaded || mappingLoading)
        {
            onReady.run();
            return;
        }
        mappingLoading = true;

        HttpUrl url = HttpUrl.parse("https://prices.runescape.wiki/api/v1/osrs/mapping").newBuilder().build();
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                log.warn("Failed to fetch GE item mapping", e);
                mappingLoaded = true;
                mappingLoading = false;
                onReady.run();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);
                        if (arr != null)
                        {
                            for (JsonElement el : arr)
                            {
                                if (!el.isJsonObject())
                                {
                                    continue;
                                }
                                JsonObject o = el.getAsJsonObject();
                                Integer id = optInt(o, "id");
                                if (id == null)
                                {
                                    continue;
                                }
                                Integer limit = optInt(o, "limit");

                                buyLimitCache.put(id, limit != null ? limit : -1);
                                String nm = firstString(o, "name");
                                if (nm != null)
                                {
                                    nameToIdCache.put(nm.toLowerCase(), id);
                                }
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse GE item mapping", e);
                }
                mappingLoaded = true;
                mappingLoading = false;
                onReady.run();
            }
        });
    }

    private void fetchTimeseriesInto(int itemId, String timestep, int keep, Market m, Runnable done)
    {
        HttpUrl url = HttpUrl.parse("https://prices.runescape.wiki/api/v1/osrs/timeseries").newBuilder()
                .addQueryParameter("timestep", timestep)
                .addQueryParameter("id", String.valueOf(itemId))
                .build();
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                done.run();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                        if (root != null && root.has("data") && root.get("data").isJsonArray())
                        {
                            List<Integer> prices = new ArrayList<>();
                            List<Long> times = new ArrayList<>();
                            List<Long> vols = new ArrayList<>();
                            parseSeries(root, prices, times, vols);

                            int from = Math.max(0, prices.size() - keep);
                            m.prices = new ArrayList<>(prices.subList(from, prices.size()));
                            m.timestamps = new ArrayList<>(times.subList(from, times.size()));
                            List<Long> keptVols = new ArrayList<>(vols.subList(from, vols.size()));
                            if (!keptVols.isEmpty())
                            {
                                m.todayVolume = keptVols.get(keptVols.size() - 1);
                                long sum = 0;
                                for (Long v : keptVols)
                                {
                                    sum += v;
                                }
                                m.avgVolume = sum / keptVols.size();
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse timeseries for item {}", itemId, e);
                }
                done.run();
            }
        });
    }

    private void fetchReferenceInto(int itemId, Market m, boolean setGraph, int graphKeep, Runnable done)
    {
        HttpUrl url = HttpUrl.parse("https://prices.runescape.wiki/api/v1/osrs/timeseries").newBuilder()
                .addQueryParameter("timestep", "24h")
                .addQueryParameter("id", String.valueOf(itemId))
                .build();
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                done.run();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                        List<Integer> prices = new ArrayList<>();
                        List<Long> times = new ArrayList<>();
                        List<Long> vols = new ArrayList<>();
                        parseSeries(root, prices, times, vols);
                        computeChanges(prices, m);
                        if (setGraph && !prices.isEmpty())
                        {
                            int from = Math.max(0, prices.size() - graphKeep);
                            m.prices = new ArrayList<>(prices.subList(from, prices.size()));
                            m.timestamps = new ArrayList<>(times.subList(from, times.size()));
                            List<Long> keptVols = new ArrayList<>(vols.subList(from, vols.size()));
                            if (!keptVols.isEmpty())
                            {
                                m.todayVolume = keptVols.get(keptVols.size() - 1);
                                long sum = 0;
                                for (Long v : keptVols)
                                {
                                    sum += v;
                                }
                                m.avgVolume = sum / keptVols.size();
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse reference series for item {}", itemId, e);
                }
                done.run();
            }
        });
    }

    private void parseSeries(JsonObject root, List<Integer> prices, List<Long> times, List<Long> vols)
    {
        if (root == null || !root.has("data") || !root.get("data").isJsonArray())
        {
            return;
        }
        for (JsonElement el : root.getAsJsonArray("data"))
        {
            if (!el.isJsonObject())
            {
                continue;
            }
            JsonObject point = el.getAsJsonObject();
            Integer high = optInt(point, "avgHighPrice");
            Integer low = optInt(point, "avgLowPrice");
            Long ts = optLong(point, "timestamp");
            Long hv = optLong(point, "highPriceVolume");
            Long lv = optLong(point, "lowPriceVolume");
            Integer price;
            if (high != null && low != null)
            {
                price = (high + low) / 2;
            }
            else if (high != null)
            {
                price = high;
            }
            else
            {
                price = low;
            }
            if (price != null && price > 0 && ts != null)
            {
                prices.add(price);
                times.add(ts);
                vols.add((hv != null ? hv : 0) + (lv != null ? lv : 0));
            }
        }
    }

    private void computeChanges(List<Integer> prices, Market m)
    {
        int n = prices.size();
        if (n < 2)
        {
            return;
        }
        int last = prices.get(n - 1);
        m.change1D = pctBack(prices, 1, last);
        m.change1W = pctBack(prices, 7, last);
        m.change1M = pctBack(prices, 30, last);

        m.change1Y = n >= 350 ? pct(last, prices.get(0)) : null;
    }

    private Double pctBack(List<Integer> prices, int daysBack, int last)
    {
        int idx = prices.size() - 1 - daysBack;
        return idx >= 0 ? pct(last, prices.get(idx)) : null;
    }

    private Double pct(int last, int ref)
    {
        return ref > 0 ? (last - ref) * 100.0 / ref : null;
    }

    private void fetchLatestInto(int itemId, Market m, Runnable done)
    {
        HttpUrl url = HttpUrl.parse("https://prices.runescape.wiki/api/v1/osrs/latest").newBuilder()
                .addQueryParameter("id", String.valueOf(itemId))
                .build();
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Cache-Control", "no-store").build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
        {
            @Override
            public void onFailure(okhttp3.Call call, IOException e)
            {
                done.run();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                        if (root != null && root.has("data"))
                        {
                            JsonObject data = root.getAsJsonObject("data");
                            String key = String.valueOf(itemId);
                            if (data.has(key))
                            {
                                JsonObject o = data.getAsJsonObject(key);
                                m.instaBuy = optInt(o, "high");
                                m.instaSell = optInt(o, "low");
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse latest price for item {}", itemId, e);
                }
                done.run();
            }
        });
    }

    private Integer optInt(JsonObject obj, String field)
    {
        if (!obj.has(field) || obj.get(field).isJsonNull())
        {
            return null;
        }
        try
        {
            return obj.get(field).getAsInt();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Long optLong(JsonObject obj, String field)
    {
        if (!obj.has(field) || obj.get(field).isJsonNull())
        {
            return null;
        }
        try
        {
            return obj.get(field).getAsLong();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}