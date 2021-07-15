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
            logger.error("读取下载列表失败 - " + e.getMessage(), e);
        }

        TIMELINE_ID = cacheManager.load();
    }

    @Override
    public void run(String... args) throws Exception {
        LIST.forEach(screenName -> {
            // step1.获取推文
            Map<String, Object> map = getTimelines(screenName);

            // step2.抽取推文中的媒体链接
            map = extract(map);

            // step3.下载媒体文件
            download(map);
        });
    }

    public Map<String, Object> getTimelines(String screenName) {
        JsonObject user = getUserInfo(screenName);
        screenName = user.get("screen_name").getAsString();

        // 判断该用户是否锁推
        if (user.get("protected").getAsBoolean()) {
            PROTECTED_USERS.add(user);
            logger.debug("{} - 推文受保护，跳过该用户", screenName);
            return null;
        }

        // 推文数量
        int count = user.get("statuses_count").getAsInt();
        if (count == 0) {
            return null;
        }
        logger.debug("{} - 总共有{}条推文", screenName, count);

        // 一个简单的分页逻辑
        int page = 0;
        int size = properties.getTwitter().getSize();
        if (count % size == 0) {
            page = count / size;
        } else {
            page = (count / size) + 1;
        }

        // 请求参数
        String url = properties.getTwitter().getApi().getBaseUrl() + "/statuses/user_timeline.json";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("screen_name", screenName);
        parameters.put("count", String.valueOf(size));
        parameters.put("exclude_replies", "true");
        parameters.put("tweet_mode", "extended");
        // 如果是增量抓取，先读取data目录下该用户上次最新推文id
        if (TIMELINE_ID.containsKey(screenName) && properties.getTwitter().getIncrement()) {
            parameters.put("since_id", TIMELINE_ID.get(screenName));
        }

        JsonArray timelines = new JsonArray();
        // 循环获取所有timeline
        for (int i = 0; i < page; i++) {

            // 获取最后一个timeline，取id设为max_id
            // max_id相当于分页中的offset
            if (timelines.size() > 0) {
                JsonObject timeline = timelines.get(timelines.size() - 1).getAsJsonObject();
                String maxId = timeline.get("id_str").getAsString();
                parameters.put("max_id", maxId);
            }

            // 获取timeline
            String jsonStr = httpGet(url, parameters);
            JsonArray pageTimelines = JsonParser.parseString(jsonStr).getAsJsonArray();

            logger.debug("{} - 第{}次抓取，本次返回{}条timeline", screenName, i + 1, pageTimelines.size());

            // 如果获取的分页内容为1，提前结束循环
            // 分页内容为1，其实就是上面max_id查询的结果，由于twitter api有访问次数限制（1500次/15min），为避免超过最大次数导致http 429错误
            // 这里判断size为1的时候就可以结束了
            if (pageTimelines.size() <= 1) {
                break;
            }

            // 合并到结果集
            timelines.addAll(pageTimelines);
        }

        if (timelines.size() == 0) {
            return null;
        }

        // 最终结果集的数量可能和用户信息中获取的推文数量不相等，这里获取的timeline是包含用户转发的推文的，所以会多；
        // 也有可能他自己删掉了一些，就会变少
        logger.debug("{} - 所有timeline已经获取完毕，结果集中共包含{}条", screenName, timelines.size());

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
                logger.debug("GET响应失败\nURL: {}\nstatus_code:{}\nmessage:{}", url, response.code(), response.message());
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
        logger.debug("{} - 正在提取推文中的媒体链接，请稍后...", screenName);

        // 提取媒体链接
        if (timelines != null && timelines.size() > 0) {
            timelines.forEach(timelineJsonElement -> {
                JsonObject timeline = timelineJsonElement.getAsJsonObject();

                // 推文的创建日期
                String strCreatedAt = timeline.get("created_at").getAsString();
                try {
                    String strPrettyCreationDate = DateFormatUtils.format(DateUtils.parseDate(strCreatedAt, Locale.US, DATE_PATTERN), "yyyyMMdd_HHmmss");

                    JsonElement extendedEntitiesJsonElement = timeline.get("extended_entities");
                    if (extendedEntitiesJsonElement != null) {
                        JsonArray medias = extendedEntitiesJsonElement.getAsJsonObject().get("media").getAsJsonArray();
                        if (medias != null && medias.size() > 0) {
                            List<String> urls = new ArrayList<>();

                            // 解析媒体文件
                            medias.forEach(mediaJsonElement -> {
                                JsonObject media = mediaJsonElement.getAsJsonObject();
                                // 根据类型获取Extractor
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
                    logger.debug("日期解析异常，creation_at: {}", strCreatedAt);
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

        // 用户下载目录
        Path downloadPath = Paths.get(properties.getLocation(), name + "@" + screenName);

        logger.debug("{} - 开始下载媒体文件...", screenName);
        // 处理媒体文件URL，key是推文的创建日期，value是媒体文件URL集合
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

                            // 文件路径规则：用户名 / 推文创建时间 + 文件名 + 文件后缀

                            if (StringUtils.contains(url, "?")) {
                                filename = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("?"));
                            } else {
                                filename = url.substring(url.lastIndexOf("/") + 1);
                            }
                            filename = key + "_" + filename;
                            Path path = Paths.get(downloadPath.toString(), filename);

                            // 创建目录并保存文件
                            Files.createDirectories(path.getParent());
                            Files.copy(inputStream, path);

                            logger.debug("{} - 第[{}/{}]个文件下载完成：{}", screenName, indexAI.incrementAndGet(), count, path);
                        } catch (Exception e) {
                            logger.error("文件下载出错 - url: {}, filename: {}", url, filename);
                            logger.error(e.getMessage(), e);
                        }
                    } else {
                        logger.error("{} - 文件下载失败，status: {}, body: {}, URL：{}", screenName, response.code(), Objects.requireNonNull(response.body()).string(), url);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        // 所有媒体下载完成，更新timeline_id
        TIMELINE_ID.put(screenName, topTimeline.get("id_str").getAsString());

        // 保存用户信息
        Path path = Paths.get(downloadPath.toString(), "_user_info.json");
        String jsonStr = gson.toJson(user);
        try {
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.write(path, jsonStr.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("用户信息写入失败 - " + e.getMessage(), e);
        }
    }

    @PreDestroy
    private void preDestroy() {
        // 缓存写回文件
        cacheManager.save(TIMELINE_ID);

        // 锁推的用户
        if (!CollectionUtils.isEmpty(PROTECTED_USERS)) {
            logger.debug("下列用户已锁推：");
            PROTECTED_USERS.forEach(user -> {
                logger.debug("{}({})", user.get("name").getAsString(), user.get("screen_name").getAsString());
            });
        }
    }
}
