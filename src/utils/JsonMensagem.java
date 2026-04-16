package utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Representacao externa de dados (JSON) para mensagens UDP multicast.
 *
 * Implementacao minima sem dependencias externas — suficiente para
 * o formato fixo das notificacoes do sistema.
 *
 * Formato: {"tipo":"...","de":"...","mensagem":"...","hora":"HH:mm:ss"}
 */
public class JsonMensagem {

    public String tipo;
    public String de;
    public String mensagem;
    public String hora;

    public JsonMensagem() {}

    public JsonMensagem(String tipo, String de, String mensagem) {
        this.tipo     = tipo;
        this.de       = de;
        this.mensagem = mensagem;
        this.hora     = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // ── Serializacao ─────────────────────────────────────────────────────────

    public String toJson() {
        return "{"
                + "\"tipo\":"     + esc(tipo)     + ","
                + "\"de\":"       + esc(de)        + ","
                + "\"mensagem\":" + esc(mensagem)  + ","
                + "\"hora\":"     + esc(hora)
                + "}";
    }

    /** Envolve o valor em aspas e escapa caracteres especiais. */
    private static String esc(String s) {
        if (s == null) return "\"\"";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }

    // ── Desserializacao ──────────────────────────────────────────────────────

    public static JsonMensagem fromJson(String json) {
        JsonMensagem m = new JsonMensagem();
        m.tipo     = extrair(json, "tipo");
        m.de       = extrair(json, "de");
        m.mensagem = extrair(json, "mensagem");
        m.hora     = extrair(json, "hora");
        return m;
    }

    /** Extrai o valor de uma chave string simples no JSON. */
    private static String extrair(String json, String chave) {
        String k = "\"" + chave + "\":\"";
        int start = json.indexOf(k);
        if (start == -1) return "";
        start += k.length();
        // Avanca ignorando aspas escapadas
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    default   -> sb.append(next);
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
