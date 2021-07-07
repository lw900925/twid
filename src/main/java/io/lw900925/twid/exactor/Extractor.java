package io.lw900925.twid.exactor;

import com.google.gson.JsonObject;

/**
 * 提取媒体文件URL的Extractor
 *
 * @author lw900925
 */
public interface Extractor {

    /**
     * 从JSON对象中提取媒体文件的下载链接
     *
     * @param jsonObject jsonObject
     * @return URL
     */
    String extract(JsonObject jsonObject);
}
