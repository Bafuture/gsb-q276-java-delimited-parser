package com.example.delimited;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 表头：列名到列下标（0 起始）的映射。重名列取第一次出现的位置。 */
public final class Header {

    private final List<String> names;
    private final Map<String, Integer> indexByName;

    public Header(List<String> names) {
        this.names = List.copyOf(names);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < this.names.size(); i++) {
            map.putIfAbsent(this.names.get(i), i);
        }
        this.indexByName = Collections.unmodifiableMap(map);
    }

    public List<String> names() {
        return names;
    }

    public int size() {
        return names.size();
    }

    public boolean contains(String name) {
        return indexByName.containsKey(name);
    }

    /** 列名对应的下标；不存在返回 -1。 */
    public int indexOf(String name) {
        Integer idx = indexByName.get(name);
        return idx == null ? -1 : idx;
    }
}
