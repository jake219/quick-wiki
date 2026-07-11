package com.jake219.quickwiki;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
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

    public void fetchInfobox(String itemName, int targetItemId, Consumer<InfoboxData> callback)
    {
        fetchInfoboxInternal(itemName, targetItemId, callback, false);
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
