package io.lw900925.twid;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.net.URLEncodeUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.lw900925.twid.cache.CacheManager;
import io.lw900925.twid.config.TwidProperties;
import io.lw900925.twid.config.TwidProperties.Twitter.API;
import io.lw900925.twid.config.TwidProperties.Twitter.ApiInfo;
import io.lw900925.twid.exactor.Extractor;
import io.lw900925.twid.exactor.PhotoExtractor;
import io.lw900925.twid.exactor.VideoExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class TwidRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TwidRunner.class);

    private static final String USER = "user";
    private static final String TOP_TIMELINE = "top_timeline";
    private static final String TIMELINES = "timelines";
    private static final String MEDIA_URLS = "media_urls";

    private static final String DATE_PATTERN = "EEE MMM dd HH:mm:ss XX yyyy";
    private static final Pattern CSRF_PATTERN = Pattern.compile("ct0=(.+?)(?:;|$)");

    @Autowired
    private TwidProperties properties;
    @Autowired
    private OkHttpClient okHttpClient;
    @Autowired
    private CacheManager cacheManager;

    private List<String> LIST = new ArrayList<>();
    private Map<String, String> TIMELINE_ID = new TreeMap<>(String::compareTo);
    private final Map<String, Extractor> MEDIA_EXTRACTOR = new HashMap<String, Extractor>() {{
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
        for (String screenName : LIST) {
            if (screenName.startsWith("#")) {
                continue;
            }

            // step1.获取推文
            Map<String, Object> map = getTimelines(screenName);

            // step2.抽取推文中的媒体链接
            map = extract(map);

            // step3.下载媒体文件
            download(map);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTimelines(String screenName) {
        JSONObject user = getUserInfo(screenName);
        List<JSONObject> errors = user.getByPath("errors", List.class);
        if (CollUtil.isNotEmpty(errors)) {
            logger.error("获取用户信息失败: {}", JSONUtil.toJsonStr(errors));
            return null;
        }

        JSONObject userInfo = user.getByPath("data.user", JSONObject.class);

        // 推文数量
        int count = userInfo.getByPath("legacy.media_count", int.class);
        if (count == 0) {
            return null;
        }
        logger.debug("{} - 总共有{}条媒体推文", screenName, count);

        // 请求参数
        ApiInfo mediaApi = properties.getTwitter().getApi().get(API.media);
        String url = mediaApi.getUrl();
        Map<String, Object> parameter = parseParameter(mediaApi.getParam());
        Map<String, Object> features = (Map<String, Object>) parameter.get("features");
        Map<String, Object> variables = (Map<String, Object>) parameter.get("variables");
        // 填入user_id和count
        variables.put("userId", userInfo.getByPath("rest_id", String.class));
        variables.put("count", properties.getTwitter().getSize());

        // 当前用户抓取的timeline
        List<JSONObject> timelines = Lists.newArrayList();
        int index = 0;
        while (true) {
            // 组织参数，获取timeline
            String jsonStr = httpGet(url, ImmutableMap.<String, String>builder()
                .put("variables", JSONUtil.toJsonStr(variables))
                .put("features", JSONUtil.toJsonStr(features))
                .build());
            JSONObject response = JSONUtil.parseObj(jsonStr);

            List<JSONObject> timelineEntries = response.getByPath("data.user.result.timeline_v2.timeline.instructions[0].entries", List.class);
            logger.debug("{} - 第{}次抓取，本次返回{}条timeline", screenName, index + 1, timelineEntries.size());
            if (CollUtil.isEmpty(timelineEntries)) {
                break;
            }

            // 是否抓取完毕，请求结果中没有timeline，只有翻页游标时，结束循环
            boolean isNonTimeline = timelineEntries.stream().noneMatch(it -> it.getByPath("entryId", String.class).startsWith("tweet-"));
            if (isNonTimeline) {
                break;
            }

            boolean lastTimelineMatched = false;
            for (JSONObject timelineEntry : timelineEntries) {
                // 过滤掉非timeline
                if (!timelineEntry.getByPath("entryId", String.class).startsWith("tweet-")) {
                    continue;
                }

                JSONObject result = getResult(timelineEntry);
                if (result == null || result.isEmpty()) {
                    continue;
                }

                String timelineId = result.getByPath("rest_id", String.class);
                String lastTimelineId = TIMELINE_ID.get(screenName.toLowerCase());
                if (StrUtil.isNotBlank(lastTimelineId) && lastTimelineId.equals(timelineId)) {
                    // 本次抓取结果中是否包含上次最新的一条媒体推文，如果是，后面的timeline就不用再处理了
                    lastTimelineMatched = true;
                    break;
                }

                // 添加到结果集中
                timelines.add(timelineEntry);
            }

            // 如果匹配导缓存中上次抓取的最新推文，后面的不需要处理了，提前结束循环
            if (lastTimelineMatched) {
                break;
            }

            // 判断后面是否还有数据，是否再次查询
            String cursor = timelineEntries.stream()
                .filter(it -> it.getByPath("entryId", String.class).startsWith("cursor-bottom"))
                .map(it -> it.getByPath("content.value", String.class))
                .findFirst().orElse(null);
            if (StrUtil.isNotBlank(cursor)) {
                variables.put("cursor", cursor); // 分页的游标
            } else {
                break;
            }

            index++;
        }

        if (CollUtil.isEmpty(timelines)) {
            return null;
        }

        logger.debug("{} - 所有timeline已经获取完毕，结果集中共包含{}条", screenName, timelines.size());

        Map<String, Object> map = new HashMap<>();
        map.put(USER, userInfo);
        map.put(TOP_TIMELINE, timelines.get(0));
        map.put(TIMELINES, timelines);
        return map;
    }

    public JSONObject getUserInfo(String screenName) {
        ApiInfo userInfoApi = properties.getTwitter().getApi().get(API.user_info);
        String url = userInfoApi.getUrl();

        // 解析参数文件，填入screen_name
        Map<String, Object> parameter = parseParameter(userInfoApi.getParam());
        parameter.put("screen_name", screenName);

        // 组织参数
        String jsonStr = httpGet(url, ImmutableMap.<String, String>builder()
            .put("variables", JSONUtil.toJsonStr(parameter))
            .build());
        return JSONUtil.parseObj(jsonStr);
    }

    public Map<String, Object> parseParameter(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            String content = IoUtil.read(inputStream, StandardCharsets.UTF_8);
            return JSONUtil.toBean(content, new TypeReference<Map<String, Object>>() {
            }, false);

        } catch (IOException e) {
            logger.error(String.format("获取用户信息出错，读取param内容失败：%s", e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    public String httpGet(String url, Map<String, String> parameters) {
        String jsonStr = null;

        String cookie = properties.getTwitter().getCookie();
        List<String> strs = ReUtil.findAll(CSRF_PATTERN, cookie, 0);
        if (CollUtil.isEmpty(strs)) {
            throw new RuntimeException("token中未包含csrf_token信息");
        }
        String csrf = strs.get(0);
        csrf = csrf.split("=")[1];
        csrf = StrUtil.replace(csrf, ";", StrUtil.EMPTY);

        String[] keyValuePairs = parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        String strQueryParam = String.join("&", keyValuePairs);
        url = url + "?" + encodeQuery(strQueryParam);
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", properties.getTwitter().getAccessToken())
            .addHeader("Cookie", properties.getTwitter().getCookie())
            .addHeader("X-CSRF-TOKEN", csrf)
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

    public String encodeQuery(String queryStr) {
        queryStr = URLEncodeUtil.encodeQuery(queryStr);
        queryStr = StrUtil.replace(queryStr, ":", "%3A"); // 对:特殊处理
        return queryStr;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(Map<String, Object> map) {
        Map<String, List<String>> mediaUrls = new LinkedHashMap<>();

        if (map == null) {
            return null;
        }

        JSONObject userInfo = (JSONObject) map.get(USER);
        List<JSONObject> timelines = (List<JSONObject>) map.get(TIMELINES);
        String screenName = userInfo.getByPath("legacy.screen_name", String.class);
        logger.debug("{} - 正在提取推文中的媒体链接，请稍后...", screenName);

        // 提取媒体链接
        if (CollUtil.isNotEmpty(timelines)) {
            for (JSONObject timeline : timelines) {
                JSONObject result = getResult(timeline).getByPath("legacy", JSONObject.class);
                String strCreatedAt = result.getByPath("created_at", String.class);
                String strPrettyCreationDate = DateUtil.format(DateUtil.parse(strCreatedAt, DATE_PATTERN, Locale.US), "yyyyMMdd_HHmmss");
                JSONObject extendedEntities = result.getByPath("extended_entities", JSONObject.class);
                if (extendedEntities != null) {
                    List<JSONObject> media = extendedEntities.getByPath("media", List.class);
                    if (CollUtil.isNotEmpty(media)) {
                        List<String> urls = new ArrayList<>();

                        for (JSONObject mediaEntry : media) {
                            String type = mediaEntry.getByPath("type", String.class);
                            Extractor extractor = MEDIA_EXTRACTOR.get(type);
                            if (extractor != null) {
                                urls.add(extractor.extract(mediaEntry));
                            }
                        }

                        mediaUrls.put(strPrettyCreationDate, urls);
                    }
                }
            }
        }

        map.put(MEDIA_URLS, mediaUrls);
        return map;
    }

    @SuppressWarnings("unchecked")
    public void download(Map<String, Object> map) {

        if (map == null || ((List<JSONObject>) map.get(TIMELINES)).isEmpty()) {
            return;
        }

        JSONObject userInfo = (JSONObject) map.get(USER);
        JSONObject topTimeline = (JSONObject) map.get(TOP_TIMELINE);
        Map<String, List<String>> mediaUrls = (Map<String, List<String>>) map.get(MEDIA_URLS);

        List<String> flatUrls = mediaUrls.values().stream().flatMap(Collection::stream).toList();
        int count = flatUrls.size();
        AtomicInteger indexAI = new AtomicInteger(0);

        String name = userInfo.getByPath("legacy.name", String.class);
        String screenName = userInfo.getByPath("legacy.screen_name", String.class);

        // 用户下载目录
        char[] chars = {'<', '>', '/', '\\', '|', ':', '*', '?'};
        name = StrUtil.replaceChars(name, chars, StrUtil.EMPTY);
        Path downloadPath = Paths.get(properties.getLocation(), name + "@" + screenName);

        logger.debug("{} - 开始下载媒体文件...", screenName);
        // 处理媒体文件URL，key是推文的创建日期，value是媒体文件URL集合

        mediaUrls.entrySet().stream().parallel().forEach(entry -> {
            String key = entry.getKey();
            List<String> value = entry.getValue();

            for (String url : value) {
                httpDownload(url, 3, response -> {
                    String filename = "";
                    try (InputStream inputStream = Objects.requireNonNull(response.body()).byteStream()) {
                        // 文件路径规则：用户名 / 推文创建时间 + 文件名 + 文件后缀

                        if (StrUtil.contains(url, "?")) {
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
                    } catch (FileAlreadyExistsException e) {
                        logger.debug("{} - 第[{}/{}]个文件[{}]已存在，跳过下载", screenName, indexAI.incrementAndGet(), count, filename);
                    } catch (Exception e) {
                        logger.error("文件下载出错 - url: {}, filename: {}", url, filename);
                        logger.error(e.getMessage(), e);
                    }
                });
            }
        });

        // 所有媒体下载完成，更新timeline_id
        String lastTimelineId = getResult(topTimeline).getByPath("rest_id", String.class);
        TIMELINE_ID.put(screenName, lastTimelineId);

        // 保存用户信息
        Path path = Paths.get(downloadPath.toString(), "_user_info.json");
        String jsonStr = JSONUtil.toJsonStr(userInfo);
        try {
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.writeString(path, jsonStr);
        } catch (IOException e) {
            logger.error("用户信息写入失败 - " + e.getMessage(), e);
        }
    }

    public JSONObject getResult(JSONObject timeline) {
        JSONObject result = timeline.getByPath("content.itemContent.tweet_results.result", JSONObject.class);
        if (result != null && result.containsKey("legacy")) {
            return result;
        }

        result = timeline.getByPath("content.itemContent.tweet_results.result.tweet", JSONObject.class);
        if (result != null && result.containsKey("legacy")) {
            return result;
        }

        result = timeline.getByPath("content.itemContent.tweet_results", JSONObject.class);
        if (result == null || result.isEmpty()) {
            return result;
        }

        throw new RuntimeException(String.format("获取timeline内容失败：\n%s", JSONUtil.toJsonPrettyStr(timeline)));
    }

    public void httpDownload(String url, int maxRetry, Consumer<Response> action) {
        if (maxRetry <= 0) {
            return;
        }

        try {
            Request request = new Request.Builder().url(url).get().build();
            Response response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                logger.error("下载文件连接出错：url = {}, code = {}, message = {}, 剩余重试次数 = {}", response.request().url(), response.code(), response.message(), maxRetry);
                httpDownload(url, maxRetry - 1, action);
            }

            // 交给外部实现
            action.accept(response);
        } catch (IOException e) {
            logger.error("下载文件连接出错：url = {}, message = {}，剩余重试次数 = {}", url, e.getMessage(), maxRetry);
            httpDownload(url, maxRetry - 1, action);
        }
    }

    @PreDestroy
    private void preDestroy() {
        // 缓存写回文件
        cacheManager.save(TIMELINE_ID);
    }
}
