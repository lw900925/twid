package io.lw900925.twid.exactor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VideoExtractor implements Extractor {

    @SuppressWarnings("unchecked")
    @Override
    public String extract(JSONObject jsonObject) {
        String url = null;
        JSONObject videoInfo = jsonObject.getByPath("video_info", JSONObject.class);
        if (videoInfo != null) {
            List<JSONObject> variants = videoInfo.getByPath("variants", List.class);
            // 过滤出mp4视频文件
            variants = variants.stream()
                .filter(it -> it.getByPath("content_type", String.class).equals("video/mp4"))
                .collect(Collectors.toList());
            // 视频文件的比特率，从大到小排序
            List<Long> bitrates = variants.stream()
                .map(it -> it.getByPath("bitrate", Long.class))
                .sorted(Comparator.reverseOrder())
                .toList();
            // 按照比特率分组
            Map<Long, List<JSONObject>> variantsGroup = variants.stream().collect(Collectors.groupingBy(it -> it.getByPath("bitrate", Long.class)));
            // 首选获得比特率最大的视频
            for (Long bitrate : bitrates) {
                List<JSONObject> bitrateVariants = variantsGroup.get(bitrate);
                url = bitrateVariants.stream().map(it -> it.getByPath("url", String.class)).findFirst().orElse("");
                if (StrUtil.isNotBlank(url)) {
                    break;
                }
            }
        }
        return url;
    }
}
