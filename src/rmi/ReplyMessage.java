package rmi;

import java.nio.charset.StandardCharsets;

/**
 * Mensagem de resposta do protocolo req-resp (Fig. 5.4 do livro-texto).
 *
 * Semântica do campo {@code arguments} muda conforme {@code ok}:
 *   ok=true  → bytes UTF-8 do JSON do resultado (objeto)
 *   ok=false → bytes UTF-8 da string-motivo (não é JSON)
 */
public final class ReplyMessage {

    public static final byte TYPE_REPLY = 1;

    public final byte    messageType;
    public final int     requestId;
    public final boolean ok;
    public final byte[]  arguments;

    private ReplyMessage(int requestId, boolean ok, byte[] arguments) {
        this.messageType = TYPE_REPLY;
        this.requestId   = requestId;
        this.ok          = ok;
        this.arguments   = arguments == null ? new byte[0] : arguments;
    }

    public static ReplyMessage ok(int requestId, byte[] resultJsonBytes) {
        return new ReplyMessage(requestId, true, resultJsonBytes);
    }

    public static ReplyMessage erro(int requestId, String motivo) {
        byte[] bytes = motivo == null ? new byte[0] : motivo.getBytes(StandardCharsets.UTF_8);
        return new ReplyMessage(requestId, false, bytes);
    }

    public String motivo() {
        if (ok) return null;
        return new String(arguments, StandardCharsets.UTF_8);
    }
}
