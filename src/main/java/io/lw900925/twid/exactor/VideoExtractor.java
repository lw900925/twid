package io.lw900925.twid.exactor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class VideoExtractor implements Extractor {

    @Override
    public String extract(JsonObject jsonObject) {
        String url = null;
        JsonElement videoInfoJsonElement = jsonObject.get("video_info");
        if (videoInfoJsonElement != null) {
            JsonObject videoInfo = videoInfoJsonElement.getAsJsonObject();
            JsonArray variants = videoInfo.get("variants").getAsJsonArray();
            List<JsonElement> variantJsonElements = StreamSupport.stream(variants.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(variantJsonObject -> variantJsonObject.get("content_type").getAsString().equals("video/mp4"))
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
}
