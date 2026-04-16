package network;

import models.Jogador;
import streams.JogadorInputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * Cliente TCP interativo da Fase 5.
 *
 * Exibe um menu adaptado ao tipo de usuario autenticado:
 *   - Jogador   : listar jogadores, avaliar, sair
 *   - Administrador: listar, adicionar, remover, encerrar avaliacoes, sair
 *
 * Regra critica de streams:
 *   JogadorInputStream e criado passando o DataInputStream existente (dis),
 *   nunca socket.getInputStream() diretamente — evita criar um segundo
 *   DataInputStream concorrente sobre o mesmo socket.
 *   Nunca chama jis.close() dentro do loop — isso fecharia o socket.
 */
public class ClienteInterativo {

    private static DataInputStream  dis;
    private static DataOutputStream dos;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws IOException {
        System.out.println("=== Sistema de Divisao de Times ===");
        System.out.println("Conectando a " + Protocolo.HOST + ":" + Protocolo.PORTA + "...");

        try (Socket socket = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            // ── Login ────────────────────────────────────────────────────────
            System.out.print("Nome  : ");
            String nome  = scanner.nextLine().trim();
            System.out.print("Senha : ");
            String senha = scanner.nextLine().trim();

            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF(nome);
            dos.writeUTF(senha);
            dos.flush();

            byte resp = dis.readByte();
            if (resp == Protocolo.LOGIN_FAIL) {
                System.out.println("[ERRO] " + dis.readUTF());
                return;
            }

            int  meuId = dis.readInt();
            byte tipo  = dis.readByte();

            if (tipo == Protocolo.TIPO_ADMIN) {
                System.out.println("Autenticado como Administrador. Bem-vindo, " + nome + "!");
                menuAdmin();
            } else {
                System.out.println("Autenticado como Jogador. Bem-vindo, " + nome + "!");
                menuJogador(meuId);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu Jogador
    // ─────────────────────────────────────────────────────────────────────────

    static void menuJogador(int meuId) throws IOException {
        while (true) {
            System.out.println("\n--- Menu do Jogador ---");
            System.out.println("1. Listar jogadores");
            System.out.println("2. Avaliar jogador");
            System.out.println("0. Sair");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> listarJogadores();
                case "2" -> avaliar(meuId);
                case "0" -> { logout(); return; }
                default  -> System.out.println("Opcao invalida.");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu Administrador
    // ─────────────────────────────────────────────────────────────────────────

    static void menuAdmin() throws IOException {
        while (true) {
            System.out.println("\n--- Menu do Administrador ---");
            System.out.println("1. Listar jogadores");
            System.out.println("2. Adicionar jogador");
            System.out.println("3. Remover jogador");
            System.out.println("4. Encerrar avaliacoes e gerar times");
            System.out.println("0. Sair");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> listarJogadores();
                case "2" -> adicionarJogador();
                case "3" -> removerJogador();
                case "4" -> encerrar();
                case "0" -> { logout(); return; }
                default  -> System.out.println("Opcao invalida.");
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

        // Passa dis existente — nao cria novo DataInputStream sobre o socket
        JogadorInputStream jis = new JogadorInputStream(dis);
        Jogador[] lista = jis.receber();

        if (lista.length == 0) {
            System.out.println("Nenhum jogador disponivel.");
            return;
        }

        System.out.println();
        System.out.printf("%-5s %-14s %-12s %s%n", "ID", "Nome", "Posicao", "Media");
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
            System.out.println("[OK] Avaliacao registrada com sucesso!");
        } else {
            System.out.println("[ERRO] " + dis.readUTF());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes do Administrador
    // ─────────────────────────────────────────────────────────────────────────

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
            System.out.println("[OK] Jogador '" + nome + "' adicionado com id=" + novoId);
        } else {
            System.out.println("[ERRO] " + dis.readUTF());
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
            System.out.println("[OK] Jogador removido.");
        } else {
            System.out.println("[ERRO] " + dis.readUTF());
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
            System.out.println("[ERRO] " + dis.readUTF());
            return;
        }

        if (resp == Protocolo.ENCERRAR_RESP) {
            int qtd = dis.readInt();
            System.out.println("\n==============================");
            System.out.println(" AVALIACOES ENCERRADAS        ");
            System.out.println("==============================");

            for (int i = 0; i < qtd; i++) {
                int numero = dis.readInt();

                // Passa dis existente — nao cria novo DataInputStream
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
            System.out.println("\n==============================");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    static void logout() throws IOException {
        dos.writeByte(Protocolo.LOGOUT);
        dos.flush();
        System.out.println("Ate logo!");
    }
}
