package com.example.gsb.delimited;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Maps column names to field indexes, built from the header record.
 */
public final class Header {

    private final List<String> names;
    private final Map<String, Integer> indexByName;

    Header(List<String> names) {
        this.names = List.copyOf(names);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            map.putIfAbsent(names.get(i), i);
        }
        this.indexByName = Collections.unmodifiableMap(map);
    }

    public int size() {
        return names.size();
    }

    public List<String> names() {
        return names;
    }

    public boolean contains(String name) {
        return indexByName.containsKey(name);
    }

    /** Index of the first column with this name, or empty when absent. */
    public OptionalInt indexOf(String name) {
        Integer index = indexByName.get(name);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }
}
