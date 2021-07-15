package io.lw900925.twid.cache;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.lw900925.twid.config.TwidProperties;
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
    @Autowired
    private Gson gson;

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
        Map<String, String> map = new TreeMap<>(String::compareTo);
        try (InputStream inputStream = Files.newInputStream(cache)) {
            String jsonStr = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            if (StringUtils.isNotBlank(jsonStr)) {
                JsonArray timelineIds = JsonParser.parseString(jsonStr).getAsJsonArray();
                timelineIds.forEach(timelineId -> {
                    map.put(timelineId.getAsJsonObject().get(SCREEN_NAME).getAsString(), timelineId.getAsJsonObject().get(TIMELINE_ID).getAsString());
                });
            }
        } catch (IOException e) {
            logger.error("读取缓存文件失败 - " + e.getMessage(), e);
        }

        return map;
    }

    @Override
    public void save(Map<String, String> timelines) {
        List<Map<String, Object>> list = new ArrayList<>();

        timelines.forEach((key, value) -> {
            list.add(ImmutableMap.of(SCREEN_NAME, key, TIMELINE_ID, value));
        });

        String jsonStr = gson.toJson(list);

        try {
            Files.delete(cache);
            Files.write(cache, jsonStr.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            logger.error("缓存写入文件失败 - " + e.getMessage(), e);
        }
    }
}
