package marshalling;

import rmi.ReplyMessage;
import rmi.RequestMessage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Empacotamento JSON UTF-8 das mensagens do protocolo req-resp.
 *
 * Envelopes:
 *   Request:   {"messageType":0,"requestId":N,"objectReference":"...","methodId":M,"args":{...}}
 *   Reply ok:  {"messageType":1,"requestId":N,"ok":true,"result":{...}}
 *   Reply err: {"messageType":1,"requestId":N,"ok":false,"erro":"motivo"}
 */
public final class Marshaller {

    private Marshaller() {}

    public static byte[] empacotar(RequestMessage msg) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("messageType",     (int) msg.messageType);
        env.put("requestId",       msg.requestId);
        env.put("objectReference", msg.objectReference);
        env.put("methodId",        msg.methodId);
        env.put("args",            argsParaArvore(msg.arguments));
        return Json.escrever(env).getBytes(StandardCharsets.UTF_8);
    }

    /** Alias para {@link #desempacotarRequest(byte[])} usado pelos dispatchers do servidor. */
    public static RequestMessage desempacotar(byte[] bytes) {
        return desempacotarRequest(bytes);
    }

    /** Alias para {@link #empacotar(ReplyMessage)} usado pelos dispatchers do servidor. */
    public static byte[] empacotarReply(ReplyMessage msg) {
        return empacotar(msg);
    }

    public static RequestMessage desempacotarRequest(byte[] bytes) {
        Map<String, Object> env = Json.lerObjeto(new String(bytes, StandardCharsets.UTF_8));

        int type = inteiro(env.get("messageType"), "messageType");
        if (type != RequestMessage.TYPE_REQUEST) {
            throw new MarshallingException("Esperado messageType=0, recebido " + type);
        }
        int requestId = inteiro(env.get("requestId"), "requestId");
        String objRef = String.valueOf(env.get("objectReference"));
        int methodId  = inteiro(env.get("methodId"), "methodId");

        Object args = env.getOrDefault("args", new LinkedHashMap<>());
        byte[] argsBytes = Json.escrever(args).getBytes(StandardCharsets.UTF_8);

        return new RequestMessage(requestId, objRef, methodId, argsBytes);
    }

    public static byte[] empacotar(ReplyMessage msg) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("messageType", (int) msg.messageType);
        env.put("requestId",   msg.requestId);
        env.put("ok",          msg.ok);
        if (msg.ok) {
            env.put("result", argsParaArvore(msg.arguments));
        } else {
            String motivo = msg.arguments == null
                    ? ""
                    : new String(msg.arguments, StandardCharsets.UTF_8);
            env.put("erro", motivo);
        }
        return Json.escrever(env).getBytes(StandardCharsets.UTF_8);
    }

    public static ReplyMessage desempacotarReply(byte[] bytes) {
        Map<String, Object> env = Json.lerObjeto(new String(bytes, StandardCharsets.UTF_8));

        int type = inteiro(env.get("messageType"), "messageType");
        if (type != ReplyMessage.TYPE_REPLY) {
            throw new MarshallingException("Esperado messageType=1, recebido " + type);
        }
        int requestId = inteiro(env.get("requestId"), "requestId");
        boolean ok = Boolean.TRUE.equals(env.get("ok"));

        if (ok) {
            Object result = env.getOrDefault("result", new LinkedHashMap<>());
            byte[] resBytes = Json.escrever(result).getBytes(StandardCharsets.UTF_8);
            return ReplyMessage.ok(requestId, resBytes);
        } else {
            String motivo = String.valueOf(env.getOrDefault("erro", ""));
            return ReplyMessage.erro(requestId, motivo);
        }
    }

    public static byte[] argsDe(Object payload) {
        if (payload == null) return Json.escrever(new LinkedHashMap<>())
                                        .getBytes(StandardCharsets.UTF_8);
        return Json.escrever(payload).getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, Object> argsParaMapa(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new LinkedHashMap<>();
        try {
            return Json.lerObjeto(new String(bytes, StandardCharsets.UTF_8));
        } catch (Json.JsonException e) {
            throw new MarshallingException("Payload nao e objeto JSON: " + e.getMessage());
        }
    }

    private static Object argsParaArvore(byte[] args) {
        if (args == null || args.length == 0) return new LinkedHashMap<>();
        return Json.ler(new String(args, StandardCharsets.UTF_8));
    }

    private static int inteiro(Object o, String campo) {
        if (o == null) throw new MarshallingException("Campo ausente: " + campo);
        if (o instanceof Number n) return n.intValue();
        throw new MarshallingException("Campo nao numerico: " + campo + " = " + o);
    }

    public static class MarshallingException extends RuntimeException {
        public MarshallingException(String m) { super(m); }
    }
}
