package rmi;

import java.util.Objects;

/** Mensagem de requisição do protocolo req-resp (Fig. 5.4 do livro-texto). */
public final class RequestMessage {

    public static final byte TYPE_REQUEST = 0;

    public final byte    messageType;
    public final int     requestId;
    public final String  objectReference;
    public final int     methodId;
    public final byte[]  arguments;

    public RequestMessage(int requestId, String objectReference, int methodId, byte[] arguments) {
        this.messageType     = TYPE_REQUEST;
        this.requestId       = requestId;
        this.objectReference = Objects.requireNonNull(objectReference);
        this.methodId        = methodId;
        this.arguments       = arguments == null ? new byte[0] : arguments;
    }

    public RequestMessage(int requestId, RemoteObjectRef ref, int methodId, byte[] arguments) {
        this(requestId, ref.getNome(), methodId, arguments);
    }

    public int    getRequestId()       { return requestId; }
    public int    getMethodId()        { return methodId; }
    public String getObjectReference() { return objectReference; }
    public byte[] getArguments()       { return arguments; }
    public byte   getMessageType()     { return messageType; }
}
