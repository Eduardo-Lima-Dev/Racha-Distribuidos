package testes;

import client.ClienteRMI;
import rmi.NotificadorCliente;
import rmi.RemoteObjectRef;
import server.EstadoServidor;
import server.ServicoRachaImpl;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste de integração ponta-a-ponta self-contained: sobe o servidor real
 * (server.MainServidor) in-process em uma porta livre, executa todos os
 * methodIds do contrato e valida o callback de notificação.
 *
 * Cobre os métodos remotos:
 *   1 LOGIN  | 2 LISTAR | 3 AVALIAR  | 4 ADICIONAR | 5 REMOVER
 *   6 AVISO  | 7 ENCERRAR | 8 LOGOUT  | + registrarNotificador
 */
public class TesteIntegracao {

    private static int passou = 0;
    private static int falhou = 0;

    // methodIds (espelham as constantes do servidor)
    private static final int LOGIN = 1, LISTAR = 2, AVALIAR = 3,
                             ADICIONAR = 4, REMOVER = 5,
                             AVISO = 6, ENCERRAR = 7, LOGOUT = 8;

    public static void main(String[] args) throws Exception {
        int porta = portaLivre();
        Registry registry = LocateRegistry.createRegistry(porta);
        EstadoServidor estado = new EstadoServidor();
        ServicoRachaImpl servico = new ServicoRachaImpl(estado, registry);
        registry.rebind(RemoteObjectRef.SERVICO_RACHA, servico);
        System.out.println("[INTEG] Servidor in-process na porta " + porta);

        ClienteRMI rmi = new ClienteRMI("localhost", porta);
        rmi.conectar();

        try {
            // ── Fluxo principal ─────────────────────────────────────────────
            String sessaoAdmin  = login(rmi, "admin",  "admin123", "ADMIN");
            String sessaoCarlos = login(rmi, "Carlos", "senha1",   "JOGADOR");

            CountDownLatch avisoChegou = new CountDownLatch(1);
            AtomicInteger  totalNotifs = new AtomicInteger(0);
            registrarCallback(rmi, sessaoCarlos, totalNotifs, avisoChegou);

            listarComoJogador(rmi, sessaoCarlos);
            avaliarJogador(rmi, sessaoCarlos);
            duplicarAvaliacao(rmi, sessaoCarlos);
            autoAvaliacao(rmi, sessaoCarlos);

            adicionarJogador(rmi, sessaoAdmin);
            removerJogador(rmi, sessaoAdmin);

            enviarAviso(rmi, sessaoAdmin);
            check("callback recebeu o aviso", avisoChegou.await(2, TimeUnit.SECONDS));

            encerrarAvaliacoes(rmi, sessaoAdmin);
            avaliarAposEncerrado(rmi, sessaoCarlos);

            check("callback recebeu aviso + TIMES (>=2 notificacoes)", totalNotifs.get() >= 2);

            logout(rmi, sessaoCarlos);
            logout(rmi, sessaoAdmin);

            // ── Negativos ───────────────────────────────────────────────────
            loginInvalido(rmi);
            jogadorNaoPodeAdministrar(rmi);

        } finally {
            try { UnicastRemoteObject.unexportObject(servico, true); } catch (Exception ignored) {}
            try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
        }

        System.out.println("\n=== Resultado: " + passou + " passou, " + falhou + " falhou ===");
        System.exit(falhou > 0 ? 1 : 0);
    }

    // ── Casos ────────────────────────────────────────────────────────────────

    private static String login(ClienteRMI rmi, String nome, String senha, String tipoEsperado) throws Exception {
        Map<String, Object> reply = chamar(rmi, RemoteObjectRef.servicoRacha(), LOGIN,
                obj("nome", nome, "senha", senha));
        eq("LOGIN " + nome + " ok",     Boolean.TRUE,   reply.get("ok"));
        eq("LOGIN " + nome + " tipo",   tipoEsperado,   Json.getString(reply, "tipo"));
        String ref = Json.getString(reply, "sessaoRef");
        check("LOGIN " + nome + " sessaoRef populado", ref != null && ref.startsWith("Sessao:"));
        return ref;
    }

    private static void loginInvalido(ClienteRMI rmi) throws Exception {
        Map<String, Object> reply = chamar(rmi, RemoteObjectRef.servicoRacha(), LOGIN,
                obj("nome", "naoexiste", "senha", "xxx"));
        eq("LOGIN invalido ok=false", Boolean.FALSE, reply.get("ok"));
        check("LOGIN invalido tem motivo", Json.getString(reply, "erro") != null);
    }

    @SuppressWarnings("unchecked")
    private static void listarComoJogador(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), LISTAR, Json.obj());
        eq("LISTAR ok", Boolean.TRUE, reply.get("ok"));
        List<Object> jogs = Json.getArr(reply, "jogadores");
        check("LISTAR devolve >= 4 outros jogadores", jogs.size() >= 4);
        // Carlos é id=1; jogador autenticado deve sair da lista
        boolean omitido = jogs.stream().noneMatch(o ->
                ((Number) ((Map<?, ?>) o).get("id")).intValue() == 1);
        check("LISTAR omite o proprio jogador autenticado", omitido);
    }

    private static void avaliarJogador(ClienteRMI rmi, String sessaoRef) throws Exception {
        // Carlos (id=1) avalia Fernanda (id=2)
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), AVALIAR,
                obj("idAvaliado", 2, "nota", 8.5));
        eq("AVALIAR Fernanda ok", Boolean.TRUE, reply.get("ok"));
    }

    private static void duplicarAvaliacao(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), AVALIAR,
                obj("idAvaliado", 2, "nota", 9.0));
        eq("AVALIAR duplicado ok=false", Boolean.FALSE, reply.get("ok"));
        check("AVALIAR duplicado tem motivo", Json.getString(reply, "erro") != null);
    }

    private static void autoAvaliacao(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), AVALIAR,
                obj("idAvaliado", 1, "nota", 10.0));
        eq("AVALIAR auto ok=false", Boolean.FALSE, reply.get("ok"));
    }

    private static int idAdicionado = -1;

    private static void adicionarJogador(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), ADICIONAR,
                obj("nome", "Roberto", "senha", "senha6", "posicao", "GOLEIRO"));
        eq("ADICIONAR ok", Boolean.TRUE, reply.get("ok"));
        idAdicionado = Json.getInt(reply, "id");
        check("ADICIONAR retornou id positivo", idAdicionado > 0);
    }

    private static void removerJogador(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), REMOVER,
                obj("id", idAdicionado));
        eq("REMOVER ok", Boolean.TRUE, reply.get("ok"));
    }

    private static void enviarAviso(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), AVISO,
                obj("mensagem", "Reuniao tatica em 5 minutos"));
        eq("AVISO ok", Boolean.TRUE, reply.get("ok"));
    }

    @SuppressWarnings("unchecked")
    private static void encerrarAvaliacoes(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), ENCERRAR,
                obj("qtdTimes", 2));
        eq("ENCERRAR ok", Boolean.TRUE, reply.get("ok"));
        Map<String, Object> racha = (Map<String, Object>) reply.get("racha");
        check("ENCERRAR devolve racha", racha != null);
        eq("ENCERRAR qtdTimes=2", 2, Json.getInt(racha, "qtdTimes"));
        List<Object> times = Json.getArr(racha, "times");
        eq("ENCERRAR retornou 2 times", 2, times.size());
    }

    private static void avaliarAposEncerrado(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), AVALIAR,
                obj("idAvaliado", 3, "nota", 7.0));
        eq("AVALIAR apos encerramento ok=false", Boolean.FALSE, reply.get("ok"));
    }

    private static void jogadorNaoPodeAdministrar(ClienteRMI rmi) throws Exception {
        String sessao = login(rmi, "Fernanda", "senha2", "JOGADOR");
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessao), ADICIONAR,
                obj("nome", "X", "senha", "y", "posicao", "GOLEIRO"));
        eq("ADICIONAR como jogador ok=false", Boolean.FALSE, reply.get("ok"));
        logout(rmi, sessao);
    }

    private static void logout(ClienteRMI rmi, String sessaoRef) throws Exception {
        Map<String, Object> reply = chamar(rmi, new RemoteObjectRef(sessaoRef), LOGOUT, Json.obj());
        eq("LOGOUT " + sessaoRef + " ok", Boolean.TRUE, reply.get("ok"));
    }

    private static void registrarCallback(ClienteRMI rmi, String sessaoRef,
                                          AtomicInteger contador, CountDownLatch primeiro) throws Exception {
        NotificadorCliente notif = new NotificadorCliente() {
            @Override public void notificar(byte[] payload) throws RemoteException {
                contador.incrementAndGet();
                primeiro.countDown();
            }
        };
        NotificadorCliente stub = (NotificadorCliente) UnicastRemoteObject.exportObject(notif, 0);
        rmi.obterSessao(new RemoteObjectRef(sessaoRef)).registrarNotificador(stub);
        check("registrarNotificador concluido", true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> chamar(ClienteRMI rmi, RemoteObjectRef ref,
                                              int methodId, Map<String, Object> args) throws Exception {
        byte[] resp = rmi.doOperation(ref, methodId,
                Json.toJson(args).getBytes(StandardCharsets.UTF_8));
        return Json.parseObj(new String(resp, StandardCharsets.UTF_8));
    }

    private static Map<String, Object> obj(Object... kvs) {
        Map<String, Object> m = Json.obj();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }

    private static int portaLivre() throws Exception {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) { return s.getLocalPort(); }
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
