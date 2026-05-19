package client;

import rmi.RemoteObjectRef;
import rmi.SessaoRemota;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Cliente interativo do Trabalho 2.
 *
 * Substitui o cliente TCP do Trabalho 1: todas as chamadas remotas passam pela
 * camada {@link ClienteRMI#doOperation(RemoteObjectRef, int, byte[])} (primitiva
 * da Fig. 5.4 do Coulouris). Os tipos de payload sao definidos em JSON.
 *
 * Fluxo:
 *   1. Resolve o stub do ServicoRacha via lookup no RMI Registry.
 *   2. Envia LOGIN (methodId=1) e obtem sessaoRef ("Sessao:N").
 *   3. Resolve o stub da SessaoRemota e registra um NotificadorImpl (callback).
 *   4. Executa o menu (jogador ou admin) invocando metodos da sessao.
 *   5. Envia LOGOUT (methodId=8) antes de sair.
 */
public class ClienteInterativo {

    public static final String HOST_PADRAO = "localhost";
    public static final int PORTA_PADRAO   = 1099;

    private static final int METHOD_LOGIN     = 1;
    private static final int METHOD_LISTAR    = 2;
    private static final int METHOD_AVALIAR   = 3;
    private static final int METHOD_ADICIONAR = 4;
    private static final int METHOD_REMOVER   = 5;
    private static final int METHOD_AVISO     = 6;
    private static final int METHOD_ENCERRAR  = 7;
    private static final int METHOD_LOGOUT    = 8;

    private static final RemoteObjectRef REF_SERVICO = new RemoteObjectRef("ServicoRacha");

    private static final Scanner scanner = new Scanner(System.in);

    private static ClienteRMI rmi;
    private static RemoteObjectRef refSessao;
    private static String tipoUsuario;
    private static int idUsuario;
    private static String nomeUsuario;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : HOST_PADRAO;
        int porta   = args.length > 1 ? Integer.parseInt(args[1]) : PORTA_PADRAO;

        limparTela();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   Divisao de Times - Cliente RMI         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("Conectando a " + host + ":" + porta + "...");

        try {
            rmi = new ClienteRMI(host, porta);
            rmi.conectar();

            if (!autenticar()) return;

            registrarCallback();

            if ("ADMIN".equals(tipoUsuario)) menuAdmin();
            else                              menuJogador();

            logout();
        } catch (Exception e) {
            System.err.println("[ERRO] " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login + setup
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean autenticar() throws Exception {
        System.out.print("\nNome  : ");
        String nome  = scanner.nextLine().trim();
        System.out.print("Senha : ");
        String senha = scanner.nextLine().trim();

        Map<String, Object> args = Json.obj();
        args.put("nome", nome);
        args.put("senha", senha);

        byte[] resp = rmi.doOperation(REF_SERVICO, METHOD_LOGIN,
                Json.toJson(args).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> reply = Json.parseObj(new String(resp, StandardCharsets.UTF_8));

        if (!Json.getBool(reply, "ok", false)) {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro", "Falha no login."));
            return false;
        }

        refSessao   = new RemoteObjectRef(Json.getString(reply, "sessaoRef"));
        tipoUsuario = Json.getString(reply, "tipo");
        idUsuario   = Json.getInt(reply, "idUsuario");
        nomeUsuario = nome;
        System.out.println("\n[OK] Autenticado como " + tipoUsuario + " (" + nome + ")");
        return true;
    }

    private static void registrarCallback() {
        try {
            NotificadorImpl notif = new NotificadorImpl();
            SessaoRemota sessaoStub = rmi.obterSessao(refSessao);
            sessaoStub.registrarNotificador(notif);
        } catch (Exception e) {
            System.err.println("[WARN] Nao foi possivel registrar callback: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menus
    // ─────────────────────────────────────────────────────────────────────────

    private static void menuJogador() throws Exception {
        while (true) {
            limparTela();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.printf( "║  Jogador: %-32s║%n", nomeUsuario);
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Listar jogadores                     ║");
            System.out.println("║  2. Avaliar jogador                      ║");
            System.out.println("║  0. Sair                                 ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> { limparTela(); listarJogadores(); aguardar(); }
                case "2" -> { limparTela(); avaliar();          aguardar(); }
                case "0" -> { return; }
                default  -> { System.out.println("Opcao invalida."); aguardar(); }
            }
        }
    }

    private static void menuAdmin() throws Exception {
        while (true) {
            limparTela();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.printf( "║  Admin: %-34s║%n", nomeUsuario);
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Listar jogadores                     ║");
            System.out.println("║  2. Adicionar jogador                    ║");
            System.out.println("║  3. Remover jogador                      ║");
            System.out.println("║  4. Enviar aviso                         ║");
            System.out.println("║  5. Encerrar avaliacoes e gerar times    ║");
            System.out.println("║  0. Sair                                 ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> { limparTela(); listarJogadores();   aguardar(); }
                case "2" -> { limparTela(); adicionarJogador();  aguardar(); }
                case "3" -> { limparTela(); removerJogador();    aguardar(); }
                case "4" -> { limparTela(); enviarAviso();       aguardar(); }
                case "5" -> { limparTela(); encerrar();          aguardar(); }
                case "0" -> { return; }
                default  -> { System.out.println("Opcao invalida."); aguardar(); }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes compartilhadas
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listarJogadores() throws Exception {
        Map<String, Object> reply = chamarSessao(METHOD_LISTAR, Json.obj());
        if (!Json.getBool(reply, "ok", false)) {
            System.out.println("[ERRO] " + Json.getString(reply, "erro"));
            return List.of();
        }
        List<Object> arr = Json.getArr(reply, "jogadores");
        if (arr.isEmpty()) {
            System.out.println("Nenhum jogador disponivel.");
            return List.of();
        }
        System.out.printf("%n%-5s %-14s %-12s %-6s %s%n", "ID", "Nome", "Posicao", "Media", "#Aval");
        System.out.println("-".repeat(50));
        List<Map<String, Object>> jogadores = new java.util.ArrayList<>();
        for (Object o : arr) {
            Map<String, Object> j = (Map<String, Object>) o;
            jogadores.add(j);
            System.out.printf("%-5s %-14s %-12s %-6.2f %s%n",
                    String.valueOf(j.get("id")),
                    j.get("nome"),
                    j.get("posicao"),
                    Json.getDouble(j, "media"),
                    String.valueOf(j.get("qtdAvaliacoes")));
        }
        boolean aberto = Json.getBool(reply, "sistemaAberto", true);
        if (!aberto) System.out.println("\n[INFO] As avaliacoes ja foram encerradas.");
        return jogadores;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes do Jogador
    // ─────────────────────────────────────────────────────────────────────────

    private static void avaliar() throws Exception {
        listarJogadores();

        System.out.print("\nID do jogador a avaliar (0 para cancelar): ");
        int idAvaliado;
        try { idAvaliado = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("ID invalido."); return; }
        if (idAvaliado == 0) return;

        System.out.print("Nota (0.0 a 10.0): ");
        double nota;
        try { nota = Double.parseDouble(scanner.nextLine().trim().replace(',', '.')); }
        catch (NumberFormatException e) { System.out.println("Nota invalida."); return; }

        Map<String, Object> args = Json.obj();
        args.put("idAvaliado", idAvaliado);
        args.put("nota", nota);

        Map<String, Object> reply = chamarSessao(METHOD_AVALIAR, args);
        if (Json.getBool(reply, "ok", false)) {
            System.out.println("\n[OK] Avaliacao registrada com sucesso!");
        } else {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes do Administrador
    // ─────────────────────────────────────────────────────────────────────────

    private static void adicionarJogador() throws Exception {
        System.out.print("Nome    : ");
        String nome = scanner.nextLine().trim();
        System.out.print("Senha   : ");
        String senha = scanner.nextLine().trim();
        System.out.print("Posicao (GOLEIRO, DEFENSOR, MEIO_CAMPO, ATACANTE): ");
        String posicao = scanner.nextLine().trim();

        Map<String, Object> args = Json.obj();
        args.put("nome", nome);
        args.put("senha", senha);
        args.put("posicao", posicao);

        Map<String, Object> reply = chamarSessao(METHOD_ADICIONAR, args);
        if (Json.getBool(reply, "ok", false)) {
            System.out.println("\n[OK] Jogador '" + nome + "' adicionado com id=" + reply.get("id"));
        } else {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro"));
        }
    }

    private static void removerJogador() throws Exception {
        listarJogadores();

        System.out.print("\nID do jogador a remover (0 para cancelar): ");
        int id;
        try { id = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("ID invalido."); return; }
        if (id == 0) return;

        Map<String, Object> args = Json.obj();
        args.put("id", id);

        Map<String, Object> reply = chamarSessao(METHOD_REMOVER, args);
        if (Json.getBool(reply, "ok", false)) {
            System.out.println("\n[OK] Jogador removido.");
        } else {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro"));
        }
    }

    private static void enviarAviso() throws Exception {
        System.out.print("Mensagem para todos os clientes: ");
        String mensagem = scanner.nextLine().trim();
        if (mensagem.isEmpty()) { System.out.println("Mensagem vazia. Cancelado."); return; }

        Map<String, Object> args = Json.obj();
        args.put("mensagem", mensagem);

        Map<String, Object> reply = chamarSessao(METHOD_AVISO, args);
        if (Json.getBool(reply, "ok", false)) {
            System.out.println("\n[OK] Aviso enviado a todos os clientes via callback RMI!");
        } else {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void encerrar() throws Exception {
        System.out.print("Numero de times a gerar (>= 2): ");
        int qtdTimes;
        try { qtdTimes = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Numero invalido."); return; }
        if (qtdTimes < 2) { System.out.println("Minimo de 2 times."); return; }

        System.out.print("Confirmar encerramento das avaliacoes? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) return;

        Map<String, Object> args = Json.obj();
        args.put("qtdTimes", qtdTimes);

        Map<String, Object> reply = chamarSessao(METHOD_ENCERRAR, args);
        if (!Json.getBool(reply, "ok", false)) {
            System.out.println("\n[ERRO] " + Json.getString(reply, "erro"));
            return;
        }

        Map<String, Object> racha = (Map<String, Object>) reply.get("racha");
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  AVALIACOES ENCERRADAS — TIMES GERADOS   ");
        System.out.println("══════════════════════════════════════════");
        System.out.printf("Racha #%s | media geral=%.2f%n",
                String.valueOf(racha.get("id")), Json.getDouble(racha, "mediaGeral"));

        List<Object> times = Json.getArr(racha, "times");
        for (Object t : times) {
            Map<String, Object> tm = (Map<String, Object>) t;
            System.out.printf("%nTime %s  (media=%.2f)%n",
                    String.valueOf(tm.get("numero")), Json.getDouble(tm, "media"));
            System.out.println("-".repeat(42));
            for (Object m : Json.getArr(tm, "jogadores")) {
                Map<String, Object> j = (Map<String, Object>) m;
                System.out.printf("  %-14s %-12s media: %.2f%n",
                        j.get("nome"), j.get("posicao"), Json.getDouble(j, "media"));
            }
        }
        System.out.println("\n══════════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    private static void logout() {
        try {
            chamarSessao(METHOD_LOGOUT, Json.obj());
        } catch (Exception ignored) {}
        limparTela();
        System.out.println("Ate logo!");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: chamada com (un)marshalling do JSON
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<String, Object> chamarSessao(int methodId, Map<String, Object> args) throws Exception {
        byte[] resp = rmi.doOperation(refSessao, methodId,
                Json.toJson(args).getBytes(StandardCharsets.UTF_8));
        return Json.parseObj(new String(resp, StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    private static void limparTela() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();
    }

    private static void aguardar() {
        System.out.print("\nPressione Enter para continuar...");
        scanner.nextLine();
    }
}
