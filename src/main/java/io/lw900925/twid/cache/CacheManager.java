package io.lw900925.twid.cache;

import java.util.List;
import java.util.Map;

/**
 * timeline缓存管理器
 *
 * @author lw900925
 */
public interface CacheManager {

    /**
     * 加载缓存
     *
     * @return Map<String, String>
     */
    Map<String, String> load();

    /**
     * 保存
     *
     * @param timelines Map<String, String>
     */
    void save(Map<String, String> timelines);
}
