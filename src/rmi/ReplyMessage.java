package rmi;

import java.nio.charset.StandardCharsets;

/**
 * Mensagem de resposta do protocolo req-resp (Fig. 5.4 do livro-texto).
 *
 * O servidor encapsula o payload de sucesso/erro de negócio dentro de
 * {@code arguments} (JSON com chave {@code ok}). O campo booleano {@code ok}
 * aqui denota apenas o sucesso a nível de protocolo (envelope bem-formado).
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

    /** Construtor usado pelos dispatchers do servidor (B's convention). */
    public ReplyMessage(int messageType, int requestId, byte[] arguments) {
        this.messageType = (byte) messageType;
        this.requestId   = requestId;
        this.ok          = true;
        this.arguments   = arguments == null ? new byte[0] : arguments;
    }

    public static ReplyMessage ok(int requestId, byte[] resultJsonBytes) {
        return new ReplyMessage(requestId, true, resultJsonBytes);
    }

    public static ReplyMessage erro(int requestId, String motivo) {
        byte[] bytes = motivo == null ? new byte[0] : motivo.getBytes(StandardCharsets.UTF_8);
        return new ReplyMessage(requestId, false, bytes);
    }

    public int    getRequestId() { return requestId; }
    public byte[] getArguments() { return arguments; }
    public byte   getMessageType() { return messageType; }
    public boolean isOk() { return ok; }

    public String motivo() {
        if (ok) return null;
        return new String(arguments, StandardCharsets.UTF_8);
    }
}
