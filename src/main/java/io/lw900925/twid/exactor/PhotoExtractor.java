package io.lw900925.twid.exactor;

import cn.hutool.json.JSONObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PhotoExtractor implements Extractor {

    @Override
    public String extract(JSONObject jsonObject) {
        // 优先获取https链接
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(jsonObject.getByPath("media_url_https", String.class));

        // 按照从大到小的顺序找，找到了就跳出循环
        String[] photoSizes = {"orig", "large", "medium", "small", "thumb"};
        JSONObject sizes = jsonObject.getByPath("sizes", JSONObject.class);
        for (String photoSize : photoSizes) {
            JSONObject size = sizes.getByPath(photoSize, JSONObject.class);
            if (size != null) {
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
}
