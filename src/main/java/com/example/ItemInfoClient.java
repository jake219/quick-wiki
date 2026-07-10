package com.example;

import com.google.gson.Gson;
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

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

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

        Request request = new Request.Builder().url(url).build();

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

        Request request = new Request.Builder().url(url).build();

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

        Request request = new Request.Builder().url(url).build();

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
        Request request = new Request.Builder().url(imageUrl).build();

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

    public void fetchInfobox(String itemName, Consumer<InfoboxData> callback)
    {
        fetchInfoboxInternal(itemName, callback, false);
    }

    private void fetchInfoboxInternal(String itemName, Consumer<InfoboxData> callback, boolean isRedirectRetry)
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("page", itemName)
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).build();

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
                            fetchInfoboxInternal(redirectTarget, callback, true);
                            return;
                        }
                    }

                    String infoboxBlock = extractInfoboxBlock(wikitext);
                    if (infoboxBlock == null)
                    {
                        callback.accept(null);
                        return;
                    }

                    InfoboxData data = new InfoboxData();

                    data.released = orUnknown(extractFieldWithFallback(infoboxBlock, "release", "released"));
                    data.members = orUnknown(extractFieldWithFallback(infoboxBlock, "members", null));
                    data.questItem = orUnknown(extractFieldWithFallback(infoboxBlock, "quest", null));
                    data.tradeable = orUnknown(extractFieldWithFallback(infoboxBlock, "tradeable", null));
                    data.equipable = orUnknown(extractFieldWithFallback(infoboxBlock, "equipable", null));
                    data.stackable = orUnknown(extractFieldWithFallback(infoboxBlock, "stackable", null));
                    data.noteable = orUnknown(extractFieldWithFallback(infoboxBlock, "noteable", null));
                    data.options = orUnknown(extractFieldWithFallback(infoboxBlock, "options", null));
                    data.value = orUnknown(extractFieldWithFallback(infoboxBlock, "value", null));
                    data.weight = orUnknown(extractFieldWithFallback(infoboxBlock, "weight", null));

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

    private String extractFieldWithFallback(String block, String primaryName, String altName)
    {
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