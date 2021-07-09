package io.lw900925.twid.exactor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PhotoExtractor implements Extractor {

    @Override
    public String extract(JsonObject jsonObject) {
        // 优先获取https链接
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(jsonObject.get("media_url_https").getAsString());

        // 按照从大到小的顺序找，找到了就跳出循环
        String[] photoSizes = {"large", "medium", "small", "thumb"};
        JsonObject sizes = jsonObject.get("sizes").getAsJsonObject();
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
}
