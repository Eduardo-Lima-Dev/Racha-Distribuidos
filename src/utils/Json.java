package utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser/builder JSON minimo, sem dependencias externas.
 *
 * Suporta os tipos canonicos:
 *   - object   ↔ Map&lt;String,Object&gt; (LinkedHashMap, preserva ordem)
 *   - array    ↔ List&lt;Object&gt;
 *   - string   ↔ String
 *   - number   ↔ Long (se inteiro) ou Double (se fracao/expoente)
 *   - boolean  ↔ Boolean
 *   - null     ↔ null Java
 *
 * Cobre as formas de "arguments" e "result" descritas no CONTRATO do Trabalho 2.
 * Para mensagens multicast com schema fixo, ver utils.JsonMensagem.
 */
public final class Json {

    private Json() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Build
    // ─────────────────────────────────────────────────────────────────────────

    public static String toJson(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, valor);
        return sb.toString();
    }

    private static void escrever(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                escreverString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escrever(sb, e.getValue());
                primeiro = false;
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : l) {
                if (!primeiro) sb.append(',');
                escrever(sb, item);
                primeiro = false;
            }
            sb.append(']');
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
                else sb.append(d);
            } else {
                sb.append(n.toString());
            }
        } else if (v instanceof String s) {
            escreverString(sb, s);
        } else {
            escreverString(sb, v.toString());
        }
    }

    private static void escreverString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse
    // ─────────────────────────────────────────────────────────────────────────

    public static Object parse(String json) {
        if (json == null) throw new IllegalArgumentException("JSON null");
        Parser p = new Parser(json);
        Object v = p.lerValor();
        p.pularEspacos();
        if (p.fim()) return v;
        throw new IllegalArgumentException("Conteudo extra apos JSON na posicao " + p.pos);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObj(String json) {
        Object v = parse(json);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Esperava objeto JSON, recebeu " + (v == null ? "null" : v.getClass().getSimpleName()));
        }
        return (Map<String, Object>) v;
    }

    // ── Acessores tipados ───────────────────────────────────────────────────

    public static String getString(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null) return null;
        return v.toString();
    }

    public static String getString(Map<String, Object> m, String chave, String padrao) {
        String s = getString(m, chave);
        return s == null ? padrao : s;
    }

    public static int getInt(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s.trim());
        throw new IllegalArgumentException("Chave '" + chave + "' nao e inteiro: " + v);
    }

    public static double getDouble(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s.trim().replace(',', '.'));
        throw new IllegalArgumentException("Chave '" + chave + "' nao e numero: " + v);
    }

    public static boolean getBool(Map<String, Object> m, String chave, boolean padrao) {
        Object v = m.get(chave);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return padrao;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getArr(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null) return new ArrayList<>();
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new IllegalArgumentException("Chave '" + chave + "' nao e array: " + v);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObj(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null) return null;
        if (v instanceof Map<?, ?>) return (Map<String, Object>) v;
        throw new IllegalArgumentException("Chave '" + chave + "' nao e objeto: " + v);
    }

    // ── Builder fluente ─────────────────────────────────────────────────────

    public static Map<String, Object> obj() {
        return new LinkedHashMap<>();
    }

    public static List<Object> arr() {
        return new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parser interno
    // ─────────────────────────────────────────────────────────────────────────

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) { this.src = src; this.pos = 0; }

        boolean fim() { return pos >= src.length(); }

        void pularEspacos() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        Object lerValor() {
            pularEspacos();
            if (fim()) throw new IllegalArgumentException("JSON vazio");
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> lerObjeto();
                case '[' -> lerArray();
                case '"' -> lerString();
                case 't', 'f' -> lerBool();
                case 'n' -> lerNull();
                default -> lerNumero();
            };
        }

        Map<String, Object> lerObjeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // '{'
            pularEspacos();
            if (!fim() && src.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                pularEspacos();
                if (fim() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Esperava string na posicao " + pos);
                }
                String chave = lerString();
                pularEspacos();
                if (fim() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Esperava ':' na posicao " + pos);
                }
                pos++; // ':'
                Object valor = lerValor();
                m.put(chave, valor);
                pularEspacos();
                if (fim()) throw new IllegalArgumentException("Objeto JSON nao fechado");
                char d = src.charAt(pos);
                if (d == ',') { pos++; continue; }
                if (d == '}') { pos++; return m; }
                throw new IllegalArgumentException("Esperava ',' ou '}' na posicao " + pos);
            }
        }

        List<Object> lerArray() {
            List<Object> l = new ArrayList<>();
            pos++; // '['
            pularEspacos();
            if (!fim() && src.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                Object valor = lerValor();
                l.add(valor);
                pularEspacos();
                if (fim()) throw new IllegalArgumentException("Array JSON nao fechado");
                char d = src.charAt(pos);
                if (d == ',') { pos++; continue; }
                if (d == ']') { pos++; return l; }
                throw new IllegalArgumentException("Esperava ',' ou ']' na posicao " + pos);
            }
        }

        String lerString() {
            if (src.charAt(pos) != '"') {
                throw new IllegalArgumentException("Esperava '\"' na posicao " + pos);
            }
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Escape incompleto");
                    char e = src.charAt(pos++);
                    switch (e) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u'  -> {
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Escape unicode incompleto");
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Escape invalido: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String nao fechada");
        }

        Object lerNumero() {
            int inicio = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            boolean fracao = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                fracao = true; pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                fracao = true; pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String num = src.substring(inicio, pos);
            if (fracao) return Double.parseDouble(num);
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        Boolean lerBool() {
            if (src.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("Valor invalido na posicao " + pos);
        }

        Object lerNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new IllegalArgumentException("Valor invalido na posicao " + pos);
        }
    }
}
