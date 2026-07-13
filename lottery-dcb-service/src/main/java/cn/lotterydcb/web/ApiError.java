package cn.lotterydcb.web;

import java.util.Map;

public record ApiError(
        String timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {

}
