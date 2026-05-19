package marshalling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser/writer JSON sem dependências externas.
 * Árvore: Map (objeto), List (array), String, Integer/Long/Double, Boolean, null.
 */
public final class Json {

    private Json() {}

    public static String escrever(Object arvore) {
        StringBuilder sb = new StringBuilder();
        escrever(arvore, sb);
        return sb.toString();
    }

    private static void escrever(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            escString(s, sb);
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Number n) {
            sb.append(numero(n));
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                escString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) sb.append(',');
                first = false;
                escrever(x, sb);
            }
            sb.append(']');
        } else if (v.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                escrever(java.lang.reflect.Array.get(v, i), sb);
            }
            sb.append(']');
        } else {
            throw new JsonException("Tipo nao suportado: " + v.getClass().getName());
        }
    }

    private static String numero(Number n) {
        if (n instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) throw new JsonException("Numero invalido: " + d);
            return Double.toString(d);
        }
        if (n instanceof Float f) {
            if (f.isNaN() || f.isInfinite()) throw new JsonException("Numero invalido: " + f);
            return Float.toString(f);
        }
        return n.toString();
    }

    private static void escString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    public static Object ler(String texto) {
        Parser p = new Parser(texto);
        p.skipWS();
        Object v = p.parseValue();
        p.skipWS();
        if (p.pos < p.s.length()) {
            throw new JsonException("Texto extra apos JSON em pos " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lerObjeto(String texto) {
        Object o = ler(texto);
        if (!(o instanceof Map<?, ?>)) {
            throw new JsonException("Esperado objeto JSON, recebido " +
                    (o == null ? "null" : o.getClass().getSimpleName()));
        }
        return (Map<String, Object>) o;
    }

    private static class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        void skipWS() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWS();
            if (pos >= s.length()) throw new JsonException("JSON truncado");
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new JsonException("Token inesperado '" + c + "' em pos " + pos);
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWS();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWS();
                String chave = parseString();
                skipWS();
                expect(':');
                Object v = parseValue();
                m.put(chave, v);
                skipWS();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return m; }
                throw new JsonException("Esperado ',' ou '}' em pos " + pos);
            }
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWS();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                Object v = parseValue();
                list.add(v);
                skipWS();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw new JsonException("Esperado ',' ou ']' em pos " + pos);
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw new JsonException("Escape truncado");
                    char n = s.charAt(pos++);
                    switch (n) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new JsonException("escape unicode truncado");
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonException("Escape invalido: " + n);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("String nao fechada");
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new JsonException("Esperado true/false em pos " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new JsonException("Esperado null em pos " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean isReal = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isReal = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isReal = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(start, pos);
            if (isReal) return Double.parseDouble(num);
            try {
                return Integer.parseInt(num);
            } catch (NumberFormatException e1) {
                return Long.parseLong(num);
            }
        }

        char peek() {
            if (pos >= s.length()) throw new JsonException("JSON truncado");
            return s.charAt(pos);
        }

        void expect(char c) {
            skipWS();
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new JsonException("Esperado '" + c + "' em pos " + pos);
            }
            pos++;
        }
    }

    public static class JsonException extends RuntimeException {
        public JsonException(String m) { super(m); }
    }
}
