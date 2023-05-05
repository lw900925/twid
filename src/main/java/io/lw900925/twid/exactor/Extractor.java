package io.lw900925.twid.exactor;

import cn.hutool.json.JSONObject;

/**
 * 提取媒体文件URL的Extractor
 *
 * @author lw900925
 */
public interface Extractor {

    /**
     * 从JSON对象中提取媒体文件的下载链接
     *
     * @param jsonObject JSONObject
     * @return URL
     */
    String extract(JSONObject jsonObject);
}
