package network;

import models.Jogador;
import streams.JogadorInputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * Cliente TCP interativo da Fase 6.
 *
 * Novidades em relacao a Fase 5:
 *   - Terminal limpo a cada opcao para evitar scroll excessivo
 *   - Thread de escuta UDP multicast (recebe avisos do admin em background)
 *   - Admin pode enviar avisos multicast para todos os clientes conectados
 */
public class ClienteInterativo {

    private static DataInputStream   dis;
    private static DataOutputStream  dos;
    private static ClienteMulticast  multicast;
    private static final Scanner     scanner = new Scanner(System.in);

    public static void main(String[] args) throws IOException {
        limparTela();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║    Sistema de Divisao de Times           ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("Conectando a " + Protocolo.HOST + ":" + Protocolo.PORTA + "...");

        try (Socket socket = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            // ── Login ────────────────────────────────────────────────────────
            System.out.print("\nNome  : ");
            String nome  = scanner.nextLine().trim();
            System.out.print("Senha : ");
            String senha = scanner.nextLine().trim();

            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF(nome);
            dos.writeUTF(senha);
            dos.flush();

            byte resp = dis.readByte();
            if (resp == Protocolo.LOGIN_FAIL) {
                System.out.println("\n[ERRO] " + dis.readUTF());
                return;
            }

            int  meuId = dis.readInt();
            byte tipo  = dis.readByte();

            // Inicia escuta de avisos multicast em background
            multicast = new ClienteMulticast();
            Thread tMulticast = new Thread(multicast);
            tMulticast.setDaemon(true);
            tMulticast.start();

            if (tipo == Protocolo.TIPO_ADMIN) {
                menuAdmin(nome);
            } else {
                menuJogador(meuId, nome);
            }

            multicast.parar();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu Jogador
    // ─────────────────────────────────────────────────────────────────────────

    static void menuJogador(int meuId, String nome) throws IOException {
        while (true) {
            limparTela();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.printf( "║  Jogador: %-32s║%n", nome);
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Listar jogadores                     ║");
            System.out.println("║  2. Avaliar jogador                      ║");
            System.out.println("║  0. Sair                                 ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> { limparTela(); listarJogadores(); aguardar(); }
                case "2" -> { limparTela(); avaliar(meuId);    aguardar(); }
                case "0" -> { logout(); return; }
                default  -> { System.out.println("Opcao invalida."); aguardar(); }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu Administrador
    // ─────────────────────────────────────────────────────────────────────────

    static void menuAdmin(String nome) throws IOException {
        while (true) {
            limparTela();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.printf( "║  Admin: %-34s║%n", nome);
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Listar jogadores                     ║");
            System.out.println("║  2. Adicionar jogador                    ║");
            System.out.println("║  3. Remover jogador                      ║");
            System.out.println("║  4. Enviar aviso (multicast)             ║");
            System.out.println("║  5. Encerrar avaliacoes e gerar times    ║");
            System.out.println("║  0. Sair                                 ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> { limparTela(); listarJogadores();  aguardar(); }
                case "2" -> { limparTela(); adicionarJogador(); aguardar(); }
                case "3" -> { limparTela(); removerJogador();   aguardar(); }
                case "4" -> { limparTela(); enviarAviso();      aguardar(); }
                case "5" -> { limparTela(); encerrar();         aguardar(); }
                case "0" -> { logout(); return; }
                default  -> { System.out.println("Opcao invalida."); aguardar(); }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes compartilhadas
    // ─────────────────────────────────────────────────────────────────────────

    static void listarJogadores() throws IOException {
        dos.writeByte(Protocolo.LIST_REQ);
        dos.flush();

        byte resp = dis.readByte();
        if (resp != Protocolo.LIST_RESP) {
            System.out.println("[ERRO] Resposta inesperada do servidor.");
            return;
        }

        JogadorInputStream jis = new JogadorInputStream(dis);
        Jogador[] lista = jis.receber();

        if (lista.length == 0) {
            System.out.println("Nenhum jogador disponivel.");
            return;
        }

        System.out.printf("%n%-5s %-14s %-12s %s%n", "ID", "Nome", "Posicao", "Media");
        System.out.println("-".repeat(42));
        for (Jogador j : lista) {
            System.out.printf("%-5d %-14s %-12s %.2f%n",
                    j.getId(), j.getNome(), j.getPosicao(), j.getNotaMedia());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes do Jogador
    // ─────────────────────────────────────────────────────────────────────────

    static void avaliar(int meuId) throws IOException {
        listarJogadores();

        System.out.print("\nID do jogador a avaliar (0 para cancelar): ");
        int idAvaliado;
        try {
            idAvaliado = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("ID invalido.");
            return;
        }
        if (idAvaliado == 0) return;

        System.out.print("Nota (0.0 a 10.0): ");
        double nota;
        try {
            nota = Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            System.out.println("Nota invalida.");
            return;
        }

        dos.writeByte(Protocolo.AVALIAR_REQ);
        dos.writeInt(idAvaliado);
        dos.writeDouble(nota);
        dos.flush();

        byte resp = dis.readByte();
        if (resp == Protocolo.AVALIAR_OK) {
            System.out.println("\n[OK] Avaliacao registrada com sucesso!");
        } else {
            System.out.println("\n[ERRO] " + dis.readUTF());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes do Administrador
    // ─────────────────────────────────────────────────────────────────────────

    static void enviarAviso() throws IOException {
        System.out.print("Mensagem para todos os clientes: ");
        String mensagem = scanner.nextLine().trim();
        if (mensagem.isEmpty()) {
            System.out.println("Mensagem vazia. Operacao cancelada.");
            return;
        }

        dos.writeByte(Protocolo.AVISO_REQ);
        dos.writeUTF(mensagem);
        dos.flush();

        byte resp = dis.readByte();
        if (resp == Protocolo.AVISO_OK) {
            System.out.println("\n[OK] Aviso enviado via multicast UDP!");
        } else {
            System.out.println("\n[ERRO] " + dis.readUTF());
        }
    }

    static void adicionarJogador() throws IOException {
        System.out.print("Nome    : ");
        String nome = scanner.nextLine().trim();
        System.out.print("Senha   : ");
        String senha = scanner.nextLine().trim();
        System.out.print("Posicao (GOLEIRO, DEFENSOR, MEIO_CAMPO, ATACANTE): ");
        String posicao = scanner.nextLine().trim();

        dos.writeByte(Protocolo.ADD_REQ);
        dos.writeUTF(nome);
        dos.writeUTF(senha);
        dos.writeUTF(posicao);
        dos.flush();

        byte resp = dis.readByte();
        if (resp == Protocolo.ADD_OK) {
            int novoId = dis.readInt();
            System.out.println("\n[OK] Jogador '" + nome + "' adicionado com id=" + novoId);
        } else {
            System.out.println("\n[ERRO] " + dis.readUTF());
        }
    }

    static void removerJogador() throws IOException {
        listarJogadores();

        System.out.print("\nID do jogador a remover (0 para cancelar): ");
        int id;
        try {
            id = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("ID invalido.");
            return;
        }
        if (id == 0) return;

        dos.writeByte(Protocolo.REM_REQ);
        dos.writeInt(id);
        dos.flush();

        byte resp = dis.readByte();
        if (resp == Protocolo.REM_OK) {
            System.out.println("\n[OK] Jogador removido.");
        } else {
            System.out.println("\n[ERRO] " + dis.readUTF());
        }
    }

    static void encerrar() throws IOException {
        System.out.print("Numero de times a gerar (>= 2): ");
        int qtdTimes;
        try {
            qtdTimes = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Numero invalido.");
            return;
        }
        if (qtdTimes < 2) {
            System.out.println("Minimo de 2 times.");
            return;
        }

        System.out.print("Confirmar encerramento das avaliacoes? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) return;

        dos.writeByte(Protocolo.ENCERRAR_REQ);
        dos.writeInt(qtdTimes);
        dos.flush();

        byte resp = dis.readByte();

        if (resp == Protocolo.ENCERRAR_FAIL) {
            System.out.println("\n[ERRO] " + dis.readUTF());
            return;
        }

        if (resp == Protocolo.ENCERRAR_RESP) {
            int qtd = dis.readInt();
            System.out.println("\n══════════════════════════════════════════");
            System.out.println("  AVALIACOES ENCERRADAS — TIMES GERADOS   ");
            System.out.println("══════════════════════════════════════════");

            for (int i = 0; i < qtd; i++) {
                int numero = dis.readInt();

                JogadorInputStream jis = new JogadorInputStream(dis);
                Jogador[] membros = jis.receber();

                double media = 0;
                for (Jogador j : membros) media += j.getNotaMedia();
                if (membros.length > 0) media /= membros.length;

                System.out.printf("%nTime %d  (media=%.2f)%n", numero, media);
                System.out.println("-".repeat(42));
                for (Jogador j : membros) {
                    System.out.printf("  %-14s %-12s  media: %.2f%n",
                            j.getNome(), j.getPosicao(), j.getNotaMedia());
                }
            }
            System.out.println("\n══════════════════════════════════════════");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitarios de UI
    // ─────────────────────────────────────────────────────────────────────────

    static void logout() throws IOException {
        dos.writeByte(Protocolo.LOGOUT);
        dos.flush();
        limparTela();
        System.out.println("Ate logo!");
    }

    /** Limpa o terminal usando codigos ANSI (suportado no Windows 10/11+). */
    static void limparTela() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();
    }

    /** Pausa a execucao ate o usuario pressionar Enter. */
    static void aguardar() {
        System.out.print("\nPressione Enter para continuar...");
        scanner.nextLine();
    }
}
