package io.lw900925.download.app;

import com.google.gson.*;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Twitter视频下载工具
 */
@Component
public class AppRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppRunner.class);

    private static final String DATE_PATTERN = "EEE MMM dd HH:mm:ss XX yyyy";

    @Autowired
    private AppProperties appProperties;
    @Autowired
    private OkHttpClient okHttpClient;
    @Autowired
    private Gson gson;

    @Override
    public void run(String... args) {
        String screenName = appProperties.getScreenName();

        // step1.获取所有推文
        Map<JsonObject, JsonArray> userTimeline = getTimelines(screenName);

        // step2.过滤并提取媒体文件URL（图片，视频）
        Map<String, List<String>> mediaUrls = extractMediaUrl(userTimeline);

        // step3.下载媒体文件
        JsonObject userInfo = userTimeline.keySet().stream().findFirst().orElseThrow(() -> new NullPointerException(String.format("%s用户信息缺失", screenName)));
        downloadMedia(userInfo, mediaUrls);
    }

    /**
     * 获取用户当前推文
     *
     * @return JsonArray
     */
    private Map<JsonObject, JsonArray> getTimelines(String screenName) {
        Map<JsonObject, JsonArray> userTimeline = new HashMap<>();

        // step1.http GET获取用户timeline，默认每次取200条
        String url = appProperties.getBaseUrl() + "/statuses/user_timeline.json";

        Map<String, String> params = new HashMap<>();
        params.put("screen_name", screenName);
        params.put("count", appProperties.getCount());
        params.put("exclude_replies", "true");

        String jsonStr = httpGet(url, params);
        JsonArray timelines = JsonParser.parseString(jsonStr).getAsJsonArray();
        if (timelines == null || timelines.size() == 0) {
            LOGGER.debug("用户{}没有推文", screenName);
            return userTimeline;
        }

        // step2.获取第一条数据，取用户信息，取用户所有推文数量
        JsonObject user = timelines.get(0).getAsJsonObject().get("user").getAsJsonObject();
        int statusesCount = user.get("statuses_count").getAsInt();
        LOGGER.debug("用户{}总共有{}条推文", screenName, statusesCount);

        // step3.循环调用，获取所有timelines
        if (statusesCount > 0) {
            // 通过用户推文数量和每次取timeline的数量来计算总共应该取多少次
            int limit = Integer.parseInt(appProperties.getCount());
            int httpGetCount = 0;
            if (statusesCount % limit == 0) {
                httpGetCount = statusesCount / limit;
            } else {
                httpGetCount = (statusesCount / limit) + 1;
            }

            // 因为开始时候已经取过一次了，所以这里i=1，表示第二次获取
            for (int i=1; i<httpGetCount; i++) {
                // 获取最后一个timeline，取id设为max_id
                JsonObject timeline = timelines.get(timelines.size() - 1).getAsJsonObject();
                String maxId = timeline.get("id_str").getAsString();
                params.put("max_id", maxId);

                String rowJsonStr = httpGet(url, params);
                JsonArray rowTimelines = JsonParser.parseString(rowJsonStr).getAsJsonArray();
                // 如果当次请求没有获取到timeline，说明所有timeline已经获取完毕，提前结束吧
                if (rowTimelines == null || rowTimelines.size() == 0) {
                    break;
                }

                // 合并到第一个结果集
                timelines.addAll(rowTimelines);
            }
        }

        userTimeline.put(user, timelines);

        // 最终结果集的数量可能和用户信息中获取的推文数量不相等，因为这里获取的timeline是包含用户转发的推文的，所以会多；
        // 也有可能他自己删掉了一些，就会变少
        LOGGER.debug("用户{}所有timeline已经获取完毕，结果集中共包含{}条", screenName, timelines.size());

        return userTimeline;
    }

    /**
     * 提取媒体文件URL
     * @param {@link Map<JsonObject, JsonArray>} userTimeline
     * @return List<String>
     */
    private Map<String, List<String>> extractMediaUrl(Map<JsonObject, JsonArray> userTimeline) {
        Map<String, List<String>> mediaUrls = new HashMap<>();

        if (userTimeline.size() == 0) {
            return mediaUrls;
        }

        LOGGER.debug("正在提取推文中的媒体链接，请稍后...");
        userTimeline.forEach((key, value) -> {
            if (value != null && value.size() > 0) {
                value.forEach(timelineJsonElement -> {
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
                                    switch (media.get("type").getAsString()) {
                                        case "video" :
                                            urls.add(extractVideos(media));
                                            break;
                                        case "photo" :
                                            urls.add(extractPhotos(media));
                                            break;
                                        default:
                                            break;
                                    }
                                });

                                mediaUrls.put(strPrettyCreationDate, urls);
                            }
                        }

                    } catch (ParseException e) {
                        LOGGER.debug("日期解析异常，creation_at: {}", strCreatedAt);
                        LOGGER.error(e.getMessage(), e);
                    }
                });
            }
        });

        LOGGER.debug("提取完毕，总共有{}个媒体", mediaUrls.values().stream().mapToLong(List::size).sum());

        return mediaUrls;
    }

    /**
     * 提取视频文件URL
     * @param media JsonObject
     * @return URL
     */
    private String extractVideos(JsonObject media) {
        String url = "";

        JsonElement videoInfoJsonElement = media.get("video_info");
        if (videoInfoJsonElement != null) {
            JsonObject videoInfo = videoInfoJsonElement.getAsJsonObject();
            JsonArray variants = videoInfo.get("variants").getAsJsonArray();
            List<JsonElement> variantJsonElements = StreamSupport.stream(variants.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(jsonObject -> jsonObject.get("content_type").getAsString().equals("video/mp4"))
                    .collect(Collectors.toList());
            // 视频文件的比特率
            List<Long> bitrates = variantJsonElements.stream()
                    .map(variant -> variant.getAsJsonObject().get("bitrate").getAsLong())
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            // 按照比特率分组
            Map<Long, List<JsonElement>> variantsGroup = variantJsonElements.stream()
                    .collect(Collectors.groupingBy(jsonElement -> jsonElement.getAsJsonObject().get("bitrate").getAsLong()));

            // 首选获得比特率最大的视频
            for (Long bitrate : bitrates) {
                List<JsonElement> bitrateVariants = variantsGroup.get(bitrate);
                url = bitrateVariants.stream().map(jsonElement -> jsonElement.getAsJsonObject().get("url").getAsString()).findFirst().orElse("");
                if (StringUtils.isNotBlank(url)) {
                    break;
                }
            }
        }

        return url;
    }

    /**
     * 提取图片文件URL
     * @param media JsonObject
     * @return url
     */
    private String extractPhotos(JsonObject media) {
        // 优先获取https链接
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(media.get("media_url_https").getAsString());

        // 按照从大到小的顺序找，找到了就跳出循环
        String[] photoSizes = {"large", "medium", "small", "thumb"};
        JsonObject sizes = media.get("sizes").getAsJsonObject();
        for (String photoSize : photoSizes) {
            JsonElement sizeJsonElement = sizes.get(photoSize);
            if (sizeJsonElement != null) {
                urlBuilder.append("?format=jpg&name=").append(photoSize);
                // 图片大小：宽 x 高
//                JsonObject size = sizeJsonElement.getAsJsonObject();
//                String strSize = size.get("w").getAsString() + "x" + size.get("h").getAsString();
//                LOGGER.debug("图片URL解析成功，size:{}, URL:{}", strSize, urlBuilder);
                break;
            }
        }

        return urlBuilder.toString();
    }

    /**
     * 下载推文中的媒体
     * @param userInfo JsonArray
     * @param creationDateMediaUrl 媒体文件URL
     */
    private void downloadMedia(JsonObject userInfo, Map<String, List<String>> creationDateMediaUrl) {
        List<String> flatUrls = creationDateMediaUrl.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        int count = flatUrls.size();
        AtomicInteger indexAI = new AtomicInteger(0);

        LOGGER.debug("开始下载媒体文件...");

        // 获取用户名
        String userName = userInfo.get("name").getAsString();
        String screenName = userInfo.get("screen_name").getAsString();

        // 处理媒体文件URL，key是推文的创建日期，value是媒体文件URL集合
        creationDateMediaUrl.forEach((key, value) -> {
            value.forEach(url -> {
                Request request = new Request.Builder().url(url).get().build();

                try {
                    Response response = okHttpClient.newCall(request).execute();
                    if (response.isSuccessful()) {
                        try (InputStream inputStream = Objects.requireNonNull(response.body()).byteStream()) {

                            // 文件路径规则：用户名 / 推文创建时间 + 文件名 + 文件后缀
                            String filename = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("?"));
                            filename = key + "_" + filename;
                            String path = appProperties.getMediaDownloadPath() + File.separator + userName + "@" + screenName + File.separator + filename;

                            // 创建目录并保存文件
                            Files.createDirectories(Paths.get(path).getParent());
                            Files.copy(inputStream, Paths.get(path));

                            LOGGER.debug("第（{}/{}）个文件下载完成：{}", indexAI.incrementAndGet(), count, path);
                        }
                    } else {
                        LOGGER.error("文件下载失败，status: {}, body: {}, URL：{}", response.code(), Objects.requireNonNull(response.body()).string(), url);
                    }
                } catch (IOException e) {
                    LOGGER.error("文件下载出错 - " + e.getMessage(), e);
                }
            });
        });
    }

    /**
     * 创建GET请求参数
     * @param params Map对象
     * @return http uri query string
     */
    private String buildQueryString(Map<String, String> params) {
        String[] keyValuePairs = params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        return String.join("&", keyValuePairs);
    }

    /**
     * 发送http GET请求
     * @param url 请求URL
     * @param params 参数
     * @return JSON String
     */
    private String httpGet(String url, Map<String, String> params) {
        String jsonStr = "";

        String queryString = buildQueryString(params);
        url = url + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", appProperties.getAccessToken())
                .get()
                .build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                jsonStr = response.body().string();
            } else {
                LOGGER.error("HTTP GET请求失败，url: {}, status_code: {}, message: {}", url, response.code(), response.message());
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return jsonStr;
    }
}
