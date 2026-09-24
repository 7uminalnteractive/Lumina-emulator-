package org.json;

/** Parser JSON mínimo, só para o teste. */
class MiniParser {
    private final String s; private int p = 0;
    MiniParser(String s) { this.s = s; }
    Object parseTop() throws JSONException { Object v = value(); ws(); if (p != s.length()) throw new JSONException("lixo no fim"); return v; }
    private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    private Object value() throws JSONException {
        ws(); if (p >= s.length()) throw new JSONException("fim inesperado");
        char c = s.charAt(p);
        if (c == '{') return obj(); if (c == '[') return arr(); if (c == '"') return str();
        if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p)) { p += 4; return JSONObject.NULL; }
        int st = p; while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
        if (st == p) throw new JSONException("valor inválido na posição " + p);
        String n = s.substring(st, p);
        try { return n.matches("-?\\d+") ? (Object) Long.parseLong(n) : (Object) Double.parseDouble(n); }
        catch (NumberFormatException e) { throw new JSONException("número inválido " + n); }
    }
    private JSONObject obj() throws JSONException {
        JSONObject o = new JSONObject(); p++; ws();
        if (s.charAt(p) == '}') { p++; return o; }
        while (true) { ws(); String k = str(); ws(); if (s.charAt(p++) != ':') throw new JSONException("esperava ':'"); o.map.put(k, value()); ws();
            char c = s.charAt(p++); if (c == '}') return o; if (c != ',') throw new JSONException("esperava ',' ou '}'"); }
    }
    private JSONArray arr() throws JSONException {
        JSONArray a = new JSONArray(); p++; ws();
        if (s.charAt(p) == ']') { p++; return a; }
        while (true) { a.list.add(value()); ws(); char c = s.charAt(p++); if (c == ']') return a; if (c != ',') throw new JSONException("esperava ',' ou ']'"); }
    }
    private String str() throws JSONException {
        if (s.charAt(p) != '"') throw new JSONException("esperava string"); p++;
        StringBuilder b = new StringBuilder();
        while (true) { char c = s.charAt(p++);
            if (c == '"') return b.toString();
            if (c == '\\') { char e = s.charAt(p++);
                switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break; default: b.append(e); }
            } else b.append(c); }
    }
}
