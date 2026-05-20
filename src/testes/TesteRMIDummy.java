package testes;

import client.ClienteRMI;
import marshalling.Marshaller;
import rmi.NotificadorCliente;
import rmi.RemoteObjectRef;
import rmi.ReplyMessage;
import rmi.RequestMessage;
import rmi.ServicoRacha;
import rmi.SessaoRemota;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smoke-test ponta-a-ponta do ClienteRMI contra um servidor dummy in-memory.
 *
 * Sobe um Registry em uma porta livre, exporta um ServicoRacha que apenas
 * ecoa os argumentos (methodId=99) e uma SessaoRemota que devolve dados
 * fixos (methodId=100). Valida o round-trip e a checagem de requestId.
 */
public class TesteRMIDummy {

    private static int passou = 0;
    private static int falhou = 0;

    public static void main(String[] args) throws Exception {
        int porta = portaLivre();
        Registry registry = LocateRegistry.createRegistry(porta);

        ServicoEcoImpl servico = new ServicoEcoImpl();
        SessaoEcoImpl sessao   = new SessaoEcoImpl();
        registry.rebind("ServicoRacha", servico);
        registry.rebind("Sessao:1", sessao);

        try {
            ClienteRMI rmi = new ClienteRMI("localhost", porta);
            rmi.conectar();

            testDoOperationEco(rmi);
            testDoOperationSessao(rmi);
            testRequestIdIncrementa(rmi);
            testRefDesconhecida(rmi);
        } finally {
            UnicastRemoteObject.unexportObject(servico, true);
            UnicastRemoteObject.unexportObject(sessao, true);
            UnicastRemoteObject.unexportObject(registry, true);
        }

        System.out.println("\n=== Resultado: " + passou + " passou, " + falhou + " falhou ===");
        if (falhou > 0) System.exit(1);
    }

    // ── Casos ────────────────────────────────────────────────────────────────

    private static void testDoOperationEco(ClienteRMI rmi) throws Exception {
        Map<String, Object> args = Json.obj();
        args.put("msg", "ola");
        byte[] argsBytes = Json.toJson(args).getBytes(StandardCharsets.UTF_8);

        byte[] resp = rmi.doOperation(RemoteObjectRef.servicoRacha(), 99, argsBytes);
        Map<String, Object> result = Json.parseObj(new String(resp, StandardCharsets.UTF_8));

        eq("eco preserva msg",      "ola",          Json.getString(result, "msg"));
        eq("eco devolve methodId",  99,             Json.getInt(result, "methodId"));
        eq("eco sinaliza ok=true",  Boolean.TRUE,   result.get("ok"));
    }

    private static void testDoOperationSessao(ClienteRMI rmi) throws Exception {
        byte[] resp = rmi.doOperation(RemoteObjectRef.sessao(1), 100, new byte[0]);
        Map<String, Object> result = Json.parseObj(new String(resp, StandardCharsets.UTF_8));

        eq("sessao devolve obj",     "Sessao:1",   Json.getString(result, "via"));
        eq("sessao ok=true",         Boolean.TRUE, result.get("ok"));
    }

    private static void testRequestIdIncrementa(ClienteRMI rmi) throws Exception {
        // Duas chamadas seguidas — o servidor ecoa o requestId visto. Asserta que aumentou.
        byte[] r1 = rmi.doOperation(RemoteObjectRef.servicoRacha(), 99,
                "{}".getBytes(StandardCharsets.UTF_8));
        byte[] r2 = rmi.doOperation(RemoteObjectRef.servicoRacha(), 99,
                "{}".getBytes(StandardCharsets.UTF_8));
        int rid1 = Json.getInt(Json.parseObj(new String(r1, StandardCharsets.UTF_8)), "requestId");
        int rid2 = Json.getInt(Json.parseObj(new String(r2, StandardCharsets.UTF_8)), "requestId");
        check("requestId monotonico", rid2 > rid1);
    }

    private static void testRefDesconhecida(ClienteRMI rmi) {
        try {
            rmi.doOperation(new RemoteObjectRef("Inexistente"), 1, new byte[0]);
            falhou++;
            System.out.println("  [FAIL] ref desconhecida deveria lancar RemoteException");
        } catch (RemoteException e) {
            passou++;
            System.out.println("  [OK]   ref desconhecida lanca RemoteException");
        }
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private static int portaLivre() throws Exception {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void eq(String nome, Object esperado, Object obtido) {
        boolean ok = (esperado == null && obtido == null)
                || (esperado != null && esperado.equals(obtido));
        if (ok) { passou++; System.out.println("  [OK]   " + nome); }
        else {
            falhou++;
            System.out.println("  [FAIL] " + nome
                    + "\n         esperado: " + esperado
                    + "\n         obtido:   " + obtido);
        }
    }

    private static void check(String nome, boolean cond) {
        if (cond) { passou++; System.out.println("  [OK]   " + nome); }
        else      { falhou++; System.out.println("  [FAIL] " + nome); }
    }

    // ── Dummies ──────────────────────────────────────────────────────────────

    /** ServicoRacha de eco: devolve no result os campos recebidos + methodId/requestId. */
    static class ServicoEcoImpl extends UnicastRemoteObject implements ServicoRacha {
        ServicoEcoImpl() throws RemoteException { super(); }
        @Override public byte[] invocar(byte[] reqBytes) throws RemoteException {
            RequestMessage req = Marshaller.desempacotar(reqBytes);
            Map<String, Object> args = req.arguments.length == 0
                    ? new LinkedHashMap<>()
                    : Json.parseObj(new String(req.arguments, StandardCharsets.UTF_8));
            Map<String, Object> result = new LinkedHashMap<>(args);
            result.put("ok", true);
            result.put("methodId", req.methodId);
            result.put("requestId", req.requestId);
            byte[] payload = Json.toJson(result).getBytes(StandardCharsets.UTF_8);
            return Marshaller.empacotarReply(new ReplyMessage(1, req.requestId, payload));
        }
    }

    /** SessaoRemota dummy: devolve {via: "Sessao:1", ok: true}. */
    static class SessaoEcoImpl extends UnicastRemoteObject implements SessaoRemota {
        SessaoEcoImpl() throws RemoteException { super(); }
        @Override public byte[] invocar(byte[] reqBytes) throws RemoteException {
            RequestMessage req = Marshaller.desempacotar(reqBytes);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("via", req.objectReference);
            byte[] payload = Json.toJson(result).getBytes(StandardCharsets.UTF_8);
            return Marshaller.empacotarReply(new ReplyMessage(1, req.requestId, payload));
        }
        @Override public void registrarNotificador(NotificadorCliente notificador) throws RemoteException {
            // dummy não faz nada
        }
    }
}
