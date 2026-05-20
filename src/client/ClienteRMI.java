package client;

import marshalling.Marshaller;
import rmi.RemoteObjectRef;
import rmi.ReplyMessage;
import rmi.RequestMessage;
import rmi.ServicoRacha;
import rmi.SessaoRemota;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stub lado-cliente do protocolo req-resp do Coulouris.
 *
 * Responsabilidades:
 *   - localizar o stub do ServicoRacha no Registry e cachear;
 *   - resolver stubs de SessaoRemota sob demanda (após o login);
 *   - implementar a primitiva {@code doOperation} (Fig. 5.3) — empacota
 *     a RequestMessage, invoca o stub correto, desempacota a reply, valida
 *     o requestId e devolve apenas o payload do {@code result} ao chamador.
 *
 * Threads: doOperation é seguro para uso concorrente (AtomicInteger).
 */
public class ClienteRMI {

    private final String host;
    private final int    porta;
    private final AtomicInteger proximoRequestId = new AtomicInteger(1);

    private Registry      registry;
    private ServicoRacha  servico;

    public ClienteRMI(String host, int porta) {
        this.host  = host;
        this.porta = porta;
    }

    public void conectar() throws RemoteException {
        try {
            registry = LocateRegistry.getRegistry(host, porta);
            servico  = (ServicoRacha) registry.lookup(RemoteObjectRef.SERVICO_RACHA);
        } catch (Exception e) {
            throw new RemoteException("Falha ao conectar ao Registry " + host + ":" + porta, e);
        }
    }

    public SessaoRemota obterSessao(RemoteObjectRef refSessao) throws RemoteException {
        if (registry == null) {
            throw new RemoteException("ClienteRMI nao conectado — chame conectar() antes.");
        }
        if (!refSessao.isSessao()) {
            throw new RemoteException("Ref invalida para sessao: " + refSessao);
        }
        try {
            return (SessaoRemota) registry.lookup(refSessao.getNome());
        } catch (Exception e) {
            throw new RemoteException("Lookup falhou para " + refSessao, e);
        }
    }

    /**
     * Primitiva doOperation do protocolo req-resp (Fig. 5.3 do Coulouris).
     * Devolve o payload interno do campo {@code result} da ReplyMessage —
     * NÃO inclui o envelope ({messageType, requestId, ok, result/erro}).
     */
    public byte[] doOperation(RemoteObjectRef ref, int methodId, byte[] arguments) throws RemoteException {
        if (servico == null) {
            throw new RemoteException("ClienteRMI nao conectado — chame conectar() antes.");
        }

        int requestId = proximoRequestId.getAndIncrement();
        RequestMessage req = new RequestMessage(requestId, ref, methodId, arguments);
        byte[] reqBytes = Marshaller.empacotar(req);

        byte[] respBytes;
        if (RemoteObjectRef.SERVICO_RACHA.equals(ref.getNome())) {
            respBytes = servico.invocar(reqBytes);
        } else if (ref.isSessao()) {
            respBytes = obterSessao(ref).invocar(reqBytes);
        } else {
            throw new RemoteException("Ref nao suportada por doOperation: " + ref);
        }

        ReplyMessage reply = Marshaller.desempacotarReply(respBytes);
        if (reply.requestId != requestId) {
            throw new RemoteException("requestId divergente: enviado=" + requestId
                    + " recebido=" + reply.requestId);
        }
        if (!reply.ok) {
            throw new RemoteException("Falha de protocolo: " + reply.motivo());
        }
        return reply.arguments;
    }
}
