package io.lw900925.twid;

import com.google.gson.*;
import io.lw900925.twid.cache.CacheManager;
import io.lw900925.twid.config.TwidProperties;
import io.lw900925.twid.exactor.Extractor;
import io.lw900925.twid.exactor.PhotoExtractor;
import io.lw900925.twid.exactor.VideoExtractor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class TwidRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TwidRunner.class);

    private static final String USER = "user";
    private static final String TOP_TIMELINE = "top_timeline";
    private static final String TIMELINES = "timelines";
    private static final String MEDIA_URLS = "media_urls";

    private static final String DATE_PATTERN = "EEE MMM dd HH:mm:ss XX yyyy";

    @Autowired
    private TwidProperties properties;
    @Autowired
    private OkHttpClient okHttpClient;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private Gson gson;

    private List<String> LIST = new ArrayList<>();
    private List<JsonObject> PROTECTED_USERS = new ArrayList<>();
    private Map<String, String> TIMELINE_ID = new TreeMap<>(String::compareTo);
    private Map<String, Extractor> MEDIA_EXTRACTOR = new HashMap<String, Extractor>() {{
        put("video", new VideoExtractor());
        put("photo", new PhotoExtractor());
    }};

    @PostConstruct
    private void postConstruct() {
        try {
            LIST = Files.readAllLines(Paths.get(properties.getList()));
        } catch (IOException e) {
            logger.error("???????????????????????? - " + e.getMessage(), e);
        }

        TIMELINE_ID = cacheManager.load();
    }

    @Override
    public void run(String... args) throws Exception {
        LIST.forEach(screenName -> {
            // step1.????????????
            Map<String, Object> map = getTimelines(screenName);

            // step2.??????????????????????????????
            map = extract(map);

            // step3.??????????????????
            download(map);
        });
    }

    public Map<String, Object> getTimelines(String screenName) {
        JsonObject user = getUserInfo(screenName);
        screenName = user.get("screen_name").getAsString();

        // ???????????????????????????
        if (user.get("protected").getAsBoolean()) {
            PROTECTED_USERS.add(user);
            logger.debug("{} - ?????????????????????????????????", screenName);
            return null;
        }

        // ????????????
        int count = user.get("statuses_count").getAsInt();
        if (count == 0) {
            return null;
        }
        logger.debug("{} - ?????????{}?????????", screenName, count);

        // ???????????????????????????
        int page = 0;
        int size = properties.getTwitter().getSize();
        if (count % size == 0) {
            page = count / size;
        } else {
            page = (count / size) + 1;
        }

        // ????????????
        String url = properties.getTwitter().getApi().getBaseUrl() + "/statuses/user_timeline.json";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("screen_name", screenName);
        parameters.put("count", String.valueOf(size));
        parameters.put("exclude_replies", "true");
        parameters.put("tweet_mode", "extended");
        // ?????????????????????????????????data????????????????????????????????????id
        if (TIMELINE_ID.containsKey(screenName) && properties.getTwitter().getIncrement()) {
            parameters.put("since_id", TIMELINE_ID.get(screenName));
        }

        JsonArray timelines = new JsonArray();
        // ??????????????????timeline
        for (int i = 0; i < page; i++) {

            // ??????????????????timeline??????id??????max_id
            // max_id?????????????????????offset
            if (timelines.size() > 0) {
                JsonObject timeline = timelines.get(timelines.size() - 1).getAsJsonObject();
                String maxId = timeline.get("id_str").getAsString();
                parameters.put("max_id", maxId);
            }

            // ??????timeline
            String jsonStr = httpGet(url, parameters);
            JsonArray pageTimelines = JsonParser.parseString(jsonStr).getAsJsonArray();

            logger.debug("{} - ???{}????????????????????????{}???timeline", screenName, i + 1, pageTimelines.size());

            // ??????????????????????????????1?????????????????????
            // ???????????????1?????????????????????max_id????????????????????????twitter api????????????????????????1500???/15min???????????????????????????????????????http 429??????
            // ????????????size???1???????????????????????????
            if (pageTimelines.size() <= 1) {
                break;
            }

            // ??????????????????
            timelines.addAll(pageTimelines);
        }

        if (timelines.size() == 0) {
            return null;
        }

        // ????????????????????????????????????????????????????????????????????????????????????????????????timeline???????????????????????????????????????????????????
        // ???????????????????????????????????????????????????
        logger.debug("{} - ??????timeline??????????????????????????????????????????{}???", screenName, timelines.size());

        Map<String, Object> map = new HashMap<>();
        map.put(USER, user);
        map.put(TOP_TIMELINE, timelines.get(0).getAsJsonObject());
        map.put(TIMELINES, timelines);
        return map;
    }

    public JsonObject getUserInfo(String screenName) {
        String url = properties.getTwitter().getApi().getBaseUrl() + "/users/show.json";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("screen_name", screenName);
        String jsonStr = httpGet(url, parameters);
        return JsonParser.parseString(jsonStr).getAsJsonObject();
    }

    public String httpGet(String url, Map<String, String> parameters) {
        String jsonStr = null;

        String[] keyValuePairs = parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        String strQueryParam = String.join("&", keyValuePairs);
        url = url + "?" + strQueryParam;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", properties.getTwitter().getApi().getAccessToken())
                .get()
                .build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                jsonStr = response.body().string();
            } else {
                logger.debug("GET????????????\nURL: {}\nstatus_code:{}\nmessage:{}", url, response.code(), response.message());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsonStr;
    }

    public Map<String, Object> extract(Map<String, Object> map) {
        Map<String, List<String>> mediaUrls = new LinkedHashMap<>();

        if (map == null) {
            return null;
        }

        JsonObject user = (JsonObject) map.get(USER);
        JsonArray timelines = (JsonArray) map.get(TIMELINES);

        String screenName = user.get("screen_name").getAsString();
        logger.debug("{} - ????????????????????????????????????????????????...", screenName);

        // ??????????????????
        if (timelines != null && timelines.size() > 0) {
            timelines.forEach(timelineJsonElement -> {
                JsonObject timeline = timelineJsonElement.getAsJsonObject();

                // ?????????????????????
                String strCreatedAt = timeline.get("created_at").getAsString();
                try {
                    String strPrettyCreationDate = DateFormatUtils.format(DateUtils.parseDate(strCreatedAt, Locale.US, DATE_PATTERN), "yyyyMMdd_HHmmss");

                    JsonElement extendedEntitiesJsonElement = timeline.get("extended_entities");
                    if (extendedEntitiesJsonElement != null) {
                        JsonArray medias = extendedEntitiesJsonElement.getAsJsonObject().get("media").getAsJsonArray();
                        if (medias != null && medias.size() > 0) {
                            List<String> urls = new ArrayList<>();

                            // ??????????????????
                            medias.forEach(mediaJsonElement -> {
                                JsonObject media = mediaJsonElement.getAsJsonObject();
                                // ??????????????????Extractor
                                String type = media.get("type").getAsString();
                                Extractor extractor = MEDIA_EXTRACTOR.get(type);
                                if (extractor != null) {
                                    urls.add(extractor.extract(media));
                                }
                            });

                            mediaUrls.put(strPrettyCreationDate, urls);
                        }
                    }

                } catch (ParseException e) {
                    logger.debug("?????????????????????creation_at: {}", strCreatedAt);
                    logger.error(e.getMessage(), e);
                }
            });
        }

        map.put(MEDIA_URLS, mediaUrls);
        return map;
    }

    @SuppressWarnings("unchecked")
    public void download(Map<String, Object> map) {

        if (map == null || ((JsonArray) map.get(TIMELINES)).size() == 0) {
            return;
        }

        JsonObject user = (JsonObject) map.get(USER);
        JsonObject topTimeline = (JsonObject) map.get(TOP_TIMELINE);
        Map<String, List<String>> mediaUrls = (Map<String, List<String>>) map.get(MEDIA_URLS);

        List<String> flatUrls = mediaUrls.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        int count = flatUrls.size();
        AtomicInteger indexAI = new AtomicInteger(0);

        String name = user.get("name").getAsString();
        String screenName = user.get("screen_name").getAsString();

        // ??????????????????
        String[] chars = {"<", ">", "/", "\\", "|", ":", "*", "?"};
        if (StringUtils.containsAny(name, chars)) {
            name = StringUtils.replaceEach(name, chars, new String[] {"", "", "", "", "", "", "", ""});
        }
        Path downloadPath = Paths.get(properties.getLocation(), name + "@" + screenName);

        logger.debug("{} - ????????????????????????...", screenName);
        // ??????????????????URL???key???????????????????????????value???????????????URL??????
        mediaUrls.entrySet().stream().parallel().forEach(entry -> {
            String key = entry.getKey();
            List<String> value = entry.getValue();

            value.forEach(url -> {
                Request request = new Request.Builder().url(url).get().build();
                try {
                    Response response = okHttpClient.newCall(request).execute();
                    String filename = "";
                    if (response.isSuccessful()) {
                        try (InputStream inputStream = Objects.requireNonNull(response.body()).byteStream()) {

                            // ?????????????????????????????? / ?????????????????? + ????????? + ????????????

                            if (StringUtils.contains(url, "?")) {
                                filename = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("?"));
                            } else {
                                filename = url.substring(url.lastIndexOf("/") + 1);
                            }
                            filename = key + "_" + filename;
                            Path path = Paths.get(downloadPath.toString(), filename);

                            // ???????????????????????????
                            Files.createDirectories(path.getParent());
                            Files.copy(inputStream, path);

                            logger.debug("{} - ???[{}/{}]????????????????????????{}", screenName, indexAI.incrementAndGet(), count, path);
                        } catch (Exception e) {
                            logger.error("?????????????????? - url: {}, filename: {}", url, filename);
                            logger.error(e.getMessage(), e);
                        }
                    } else {
                        logger.error("{} - ?????????????????????status: {}, body: {}, URL???{}", screenName, response.code(), Objects.requireNonNull(response.body()).string(), url);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        // ?????????????????????????????????timeline_id
        TIMELINE_ID.put(screenName, topTimeline.get("id_str").getAsString());

        // ??????????????????
        Path path = Paths.get(downloadPath.toString(), "_user_info.json");
        String jsonStr = gson.toJson(user);
        try {
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.write(path, jsonStr.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("???????????????????????? - " + e.getMessage(), e);
        }
    }

    @PreDestroy
    private void preDestroy() {
        // ??????????????????
        cacheManager.save(TIMELINE_ID);

        // ???????????????
        if (!CollectionUtils.isEmpty(PROTECTED_USERS)) {
            logger.debug("????????????????????????");
            PROTECTED_USERS.forEach(user -> {
                logger.debug("{}({})", user.get("name").getAsString(), user.get("screen_name").getAsString());
            });
        }
    }
}
