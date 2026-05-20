package testes;

import client.ClienteRMI;
import rmi.RemoteObjectRef;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Teste de integração contra o servidor real (server.MainServidor).
 *
 * Pré-condição: subir o servidor em localhost:1099 antes de rodar.
 *   java -cp out server.MainServidor 1099
 *
 * Exercita LOGIN → LISTAR_JOGADORES → LOGOUT usando as credenciais
 * pré-cadastradas em EstadoServidor (admin/admin123).
 */
public class TesteIntegracao {

    private static int passou = 0;
    private static int falhou = 0;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta   = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        ClienteRMI rmi = new ClienteRMI(host, porta);
        rmi.conectar();
        System.out.println("[INTEG] Conectado a " + host + ":" + porta);

        String sessaoRef = login(rmi, "admin", "admin123");
        if (sessaoRef == null) {
            System.out.println("[INTEG] Login falhou — abortando.");
            System.exit(1);
        }
        listar(rmi, sessaoRef);
        logout(rmi, sessaoRef);

        System.out.println("\n=== Resultado: " + passou + " passou, " + falhou + " falhou ===");
        if (falhou > 0) System.exit(1);
    }

    private static String login(ClienteRMI rmi, String nome, String senha) throws Exception {
        Map<String, Object> args = Json.obj();
        args.put("nome", nome);
        args.put("senha", senha);
        byte[] resp = rmi.doOperation(RemoteObjectRef.servicoRacha(), 1,
                Json.toJson(args).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> reply = Json.parseObj(new String(resp, StandardCharsets.UTF_8));

        eq("LOGIN ok",   Boolean.TRUE, reply.get("ok"));
        eq("LOGIN tipo", "ADMIN",      Json.getString(reply, "tipo"));
        String sessaoRef = Json.getString(reply, "sessaoRef");
        check("LOGIN sessaoRef nao-nulo", sessaoRef != null && sessaoRef.startsWith("Sessao:"));
        return sessaoRef;
    }

    @SuppressWarnings("unchecked")
    private static void listar(ClienteRMI rmi, String sessaoRef) throws Exception {
        byte[] resp = rmi.doOperation(new RemoteObjectRef(sessaoRef), 2,
                "{}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> reply = Json.parseObj(new String(resp, StandardCharsets.UTF_8));

        eq("LISTAR ok", Boolean.TRUE, reply.get("ok"));
        List<Object> jogadores = Json.getArr(reply, "jogadores");
        check("LISTAR retornou >= 5 jogadores", jogadores.size() >= 5);
    }

    private static void logout(ClienteRMI rmi, String sessaoRef) throws Exception {
        byte[] resp = rmi.doOperation(new RemoteObjectRef(sessaoRef), 8,
                "{}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> reply = Json.parseObj(new String(resp, StandardCharsets.UTF_8));
        eq("LOGOUT ok", Boolean.TRUE, reply.get("ok"));
    }

    private static void eq(String nome, Object esperado, Object obtido) {
        boolean ok = (esperado == null && obtido == null)
                || (esperado != null && esperado.equals(obtido));
        if (ok) { passou++; System.out.println("  [OK]   " + nome); }
        else {
            falhou++;
            System.out.println("  [FAIL] " + nome + " esperado=" + esperado + " obtido=" + obtido);
        }
    }

    private static void check(String nome, boolean cond) {
        if (cond) { passou++; System.out.println("  [OK]   " + nome); }
        else      { falhou++; System.out.println("  [FAIL] " + nome); }
    }
}
