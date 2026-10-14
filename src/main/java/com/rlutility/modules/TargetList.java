package com.rlutility.modules;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A user-editable list of registry ids, persisted as one comma-separated string.
 *
 * <p>{@link FeatureConfig} only knows how to persist primitives and Strings, so every configurable
 * list (XRay blocks, ESP entities, item-magnet filters) is stored as a plain string and parsed
 * through here. The parsed {@link Set} is cached and only rebuilt when the backing string actually
 * changes, so {@link #contains} stays cheap enough to call from a render or tick loop.</p>
 *
 * <p>Matching is deliberately forgiving: entries are lower-cased and trimmed, a bare {@code stone}
 * is treated as {@code minecraft:stone}, and a trailing {@code *} makes the entry a prefix match so
 * {@code iceandfire:*} covers every block or mob from that mod.</p>
 */
public final class TargetList {

    private final String label;
    private String cachedRaw = null;
    private Set<String> exact = Collections.emptySet();
    private Set<String> prefixes = Collections.emptySet();
    private Set<String> contains = Collections.emptySet();

    public TargetList(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    private void refresh(String raw) {
        if (raw == null) raw = "";
        if (raw.equals(cachedRaw)) return;

        Set<String> newExact = new LinkedHashSet<>();
        Set<String> newPrefixes = new LinkedHashSet<>();
        Set<String> newContains = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String entry = normalizeKeepWildcards(token);
            if (entry.isEmpty()) continue;
            // "*skull*" matches anywhere in the id, which is how you catch every dragon skull
            // variant without knowing each registry name.
            if (entry.length() > 2 && entry.startsWith("*") && entry.endsWith("*")) {
                newContains.add(entry.substring(1, entry.length() - 1));
            } else if (entry.endsWith("*")) {
                newPrefixes.add(normalize(entry.substring(0, entry.length() - 1)));
            } else {
                newExact.add(normalize(entry));
            }
        }
        exact = newExact;
        prefixes = newPrefixes;
        contains = newContains;
        cachedRaw = raw;
    }

    private static String normalize(String token) {
        String entry = token.trim().toLowerCase();
        if (entry.isEmpty()) return "";
        // "diamond_ore" and "minecraft:diamond_ore" should mean the same thing. Wildcard entries
        // are left alone - "*skull*" must not become "minecraft:*skull*".
        if (entry.indexOf(':') < 0 && entry.indexOf('*') < 0) entry = "minecraft:" + entry;
        return entry;
    }

    /** Trim and lower-case without adding a namespace, so wildcards survive. */
    private static String normalizeKeepWildcards(String token) {
        return token.trim().toLowerCase();
    }

    public boolean contains(String raw, String id) {
        refresh(raw);
        if (id == null) return false;
        String key = id.toLowerCase();
        if (exact.contains(key)) return true;
        for (String prefix : prefixes) {
            if (key.startsWith(prefix)) return true;
        }
        for (String needle : contains) {
            if (key.contains(needle)) return true;
        }
        return false;
    }

    public boolean isEmpty(String raw) {
        refresh(raw);
        return exact.isEmpty() && prefixes.isEmpty() && contains.isEmpty();
    }

    public int size(String raw) {
        refresh(raw);
        return exact.size() + prefixes.size() + contains.size();
    }

    /** Entries in declaration order, for the GUI list. */
    public java.util.List<String> entries(String raw) {
        refresh(raw);
        java.util.List<String> out = new java.util.ArrayList<>(exact);
        for (String prefix : prefixes) out.add(prefix + "*");
        for (String needle : contains) out.add("*" + needle + "*");
        return out;
    }

    // ------------------------------------------------------------ mutation

    /** Returns the new raw string with {@code id} appended, or the original if already present. */
    public static String add(String raw, String id) {
        if (id == null || id.trim().isEmpty()) return raw;
        String entry = normalize(normalizeKeepWildcards(id));
        if (raw == null) raw = "";
        for (String token : raw.split(",")) {
            if (normalize(token).equals(entry)) return raw;
        }
        return raw.trim().isEmpty() ? entry : raw.trim() + "," + entry;
    }

    /** Returns the new raw string with every occurrence of {@code id} removed. */
    public static String remove(String raw, String id) {
        if (raw == null || id == null) return raw;
        String entry = normalize(id);
        StringBuilder out = new StringBuilder();
        for (String token : raw.split(",")) {
            String candidate = normalize(token);
            if (candidate.isEmpty() || candidate.equals(entry)) continue;
            if (out.length() > 0) out.append(',');
            out.append(candidate);
        }
        return out.toString();
    }

    public static String toggle(String raw, String id) {
        String entry = normalize(id);
        for (String token : (raw == null ? "" : raw).split(",")) {
            if (normalize(token).equals(entry)) return remove(raw, id);
        }
        return add(raw, id);
    }
}
