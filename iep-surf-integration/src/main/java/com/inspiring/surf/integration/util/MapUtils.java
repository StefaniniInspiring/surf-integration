package com.inspiring.surf.integration.util;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class MapUtils {

    public static Map<String, Object> createMap(Object... args) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            map.put(String.valueOf(args[i]), args[++i]);
        }
        return map;
    }

    public static String toString(Map<String, Object> map, String separator) {
        if (isBlank(separator)) {
            separator = ",";
        }

        Iterator<Map.Entry<String,Object>> i = map.entrySet().iterator();
        if (! i.hasNext())
            return "";

        StringBuilder sb = new StringBuilder();
        for (;;) {
            Map.Entry<String,Object> e = i.next();
            String key = e.getKey();
            Object value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value == map ? "(this Map)" : value);
            if (! i.hasNext())
                return sb.toString();
            sb.append(separator);
        }

    }
}