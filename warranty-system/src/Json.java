import java.util.*;

/** Tiny JSON helper (no external libraries needed). */
class Json {
    static class Raw { final String s; Raw(String s) { this.s = s; } }

    static String esc(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.append('"').toString();
    }

    static String val(Object v) {
        if (v == null) return "null";
        if (v instanceof Raw) return ((Raw) v).s;
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return esc(v.toString());
    }

    /** obj("id", 1, "name", "TV") -> {"id":1,"name":"TV"} */
    static String obj(Object... kv) {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) b.append(',');
            b.append(esc((String) kv[i])).append(':').append(val(kv[i + 1]));
        }
        return b.append('}').toString();
    }

    static String arr(Collection<String> items) { return "[" + String.join(",", items) + "]"; }

    /** Parses a flat JSON object like {"a":"x","b":5} into a Map of Strings. */
    static Map<String, String> parse(String json) {
        Map<String, String> m = new HashMap<>();
        int i = 0, n = json.length();
        while (i < n) {
            int k1 = json.indexOf('"', i); if (k1 < 0) break;
            int[] r = readStr(json, k1); String key = json.substring(r[0], r[1]); i = r[1] + 1;
            i = json.indexOf(':', i) + 1;
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (json.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder(); int j = i + 1;
                while (j < n && json.charAt(j) != '"') {
                    char c = json.charAt(j);
                    if (c == '\\' && j + 1 < n) {
                        char d = json.charAt(++j);
                        sb.append(d == 'n' ? '\n' : d == 't' ? '\t' : d);
                    } else sb.append(c);
                    j++;
                }
                m.put(key, sb.toString()); i = j + 1;
            } else {
                int j = i; while (j < n && ",}".indexOf(json.charAt(j)) < 0) j++;
                m.put(key, json.substring(i, j).trim()); i = j;
            }
        }
        return m;
    }

    private static int[] readStr(String s, int open) {
        int j = open + 1; while (j < s.length() && s.charAt(j) != '"') j++;
        return new int[]{open + 1, j};
    }
}
