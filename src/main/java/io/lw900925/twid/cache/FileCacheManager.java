package io.lw900925.twid.cache;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.lw900925.twid.config.TwidProperties;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class FileCacheManager implements CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(FileCacheManager.class);

    public static final String SCREEN_NAME = "screen_name";
    public static final String TIMELINE_ID = "timeline_id";

    private Path cache = null;

    @Autowired
    private TwidProperties properties;

    @PostConstruct
    private void postConstruct() throws IOException {
        cache = Paths.get(properties.getCache());
        if (Files.notExists(cache)) {
            Files.createDirectories(cache.getParent());
            Files.createFile(cache);
        }
    }

    @Override
    public Map<String, String> load() {
        JSONArray timelineIds = JSONUtil.readJSONArray(cache.toFile(), StandardCharsets.UTF_8);

        return timelineIds.stream().map(it -> (JSONObject) it)
            .collect(Collectors.toMap(
                it -> it.getByPath(SCREEN_NAME, String.class).toLowerCase(),
                it -> it.getByPath(TIMELINE_ID, String.class),
                (a, b) -> {
                    // 当key相同时，取最大一个id
                    long twiId = NumberUtil.max(Long.parseLong(a), Long.parseLong(b));
                    return String.valueOf(twiId);
                }, () -> new TreeMap<>(String::compareTo)));
    }

    @Override
    public void save(Map<String, String> timelines) {
        List<Map<String, Object>> list = new ArrayList<>();

        timelines.forEach((key, value) -> list.add(ImmutableMap.of(SCREEN_NAME, key.toLowerCase(), TIMELINE_ID, value)));

        String jsonStr = JSONUtil.toJsonPrettyStr(list);

        try {
            Files.delete(cache);
            Files.write(cache, jsonStr.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            logger.error("缓存写入文件失败 - " + e.getMessage(), e);
        }
    }
}
