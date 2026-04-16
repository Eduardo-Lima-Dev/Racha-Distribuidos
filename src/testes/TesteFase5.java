package testes;

import models.Jogador;
import network.Protocolo;
import streams.JogadorInputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Teste automatizado da Fase 5: valida o protocolo TCP contra o ServidorMultiThread.
 *
 * Para rodar: inicie o servidor (servidor.bat) e entao execute este teste.
 */
public class TesteFase5 {

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=== Teste automatizado Fase 5 ===\n");

        // Aguarda o servidor estar pronto
        Thread.sleep(500);

        testarLoginInvalido();
        testarJogadorListarEAvaliar();
        testarAdminAdicionarRemover();
        testarEncerrarEGerarTimes();

        System.out.println("\n[OK] Todos os testes passaram!");
    }

    // ── Teste 1: login invalido ──────────────────────────────────────────────
    static void testarLoginInvalido() throws IOException {
        try (Socket s = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            DataInputStream  dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF("ninguem");
            dos.writeUTF("errada");
            dos.flush();

            byte resp = dis.readByte();
            assert resp == Protocolo.LOGIN_FAIL : "Esperava LOGIN_FAIL";
            String motivo = dis.readUTF();
            System.out.println("[OK] Login invalido rejeitado: " + motivo);
        }
    }

    // ── Teste 2: jogador lista e avalia ──────────────────────────────────────
    static void testarJogadorListarEAvaliar() throws IOException {
        try (Socket s = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            DataInputStream  dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

            // Login como Carlos (id=1)
            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF("Carlos");
            dos.writeUTF("senha1");
            dos.flush();

            assert dis.readByte() == Protocolo.LOGIN_OK;
            int meuId = dis.readInt();
            assert dis.readByte() == Protocolo.TIPO_JOGADOR;
            System.out.println("[OK] Login jogador ok, id=" + meuId);

            // Listar (Carlos nao deve aparecer na lista)
            dos.writeByte(Protocolo.LIST_REQ);
            dos.flush();

            assert dis.readByte() == Protocolo.LIST_RESP;
            JogadorInputStream jis = new JogadorInputStream(dis);
            Jogador[] lista = jis.receber();
            assert lista.length == 4 : "Esperava 4 jogadores (excluindo o proprio)";
            for (Jogador j : lista) {
                assert j.getId() != meuId : "Proprio jogador nao deve aparecer na lista";
            }
            System.out.println("[OK] Lista recebida: " + lista.length + " jogadores (proprio excluido)");

            // Avaliar Fernanda (id=2) nota 8.5
            dos.writeByte(Protocolo.AVALIAR_REQ);
            dos.writeInt(2);
            dos.writeDouble(8.5);
            dos.flush();

            assert dis.readByte() == Protocolo.AVALIAR_OK : "Avaliacao deveria ser aceita";
            System.out.println("[OK] Avaliacao de Fernanda registrada");

            // Tentar avaliar novamente (duplicada)
            dos.writeByte(Protocolo.AVALIAR_REQ);
            dos.writeInt(2);
            dos.writeDouble(7.0);
            dos.flush();

            assert dis.readByte() == Protocolo.AVALIAR_FAIL;
            String motivo = dis.readUTF();
            System.out.println("[OK] Avaliacao duplicada rejeitada: " + motivo);

            dos.writeByte(Protocolo.LOGOUT);
            dos.flush();
        }
    }

    // ── Teste 3: admin adiciona e remove jogador ─────────────────────────────
    static void testarAdminAdicionarRemover() throws IOException {
        try (Socket s = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            DataInputStream  dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

            // Login como admin
            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF("admin");
            dos.writeUTF("admin123");
            dos.flush();

            assert dis.readByte() == Protocolo.LOGIN_OK;
            dis.readInt(); // id
            assert dis.readByte() == Protocolo.TIPO_ADMIN;
            System.out.println("[OK] Login admin ok");

            // Adicionar jogador "Beto"
            dos.writeByte(Protocolo.ADD_REQ);
            dos.writeUTF("Beto");
            dos.writeUTF("beto123");
            dos.writeUTF("MEIO_CAMPO");
            dos.flush();

            assert dis.readByte() == Protocolo.ADD_OK;
            int betoId = dis.readInt();
            System.out.println("[OK] Jogador 'Beto' adicionado, id=" + betoId);

            // Verificar que Beto aparece na lista
            dos.writeByte(Protocolo.LIST_REQ);
            dos.flush();

            assert dis.readByte() == Protocolo.LIST_RESP;
            Jogador[] lista = new JogadorInputStream(dis).receber();
            boolean betoNaLista = false;
            for (Jogador j : lista) if (j.getId() == betoId) betoNaLista = true;
            assert betoNaLista : "Beto deveria estar na lista";
            System.out.println("[OK] Beto aparece na lista (" + lista.length + " jogadores)");

            // Remover Beto
            dos.writeByte(Protocolo.REM_REQ);
            dos.writeInt(betoId);
            dos.flush();

            assert dis.readByte() == Protocolo.REM_OK;
            System.out.println("[OK] Beto removido");

            dos.writeByte(Protocolo.LOGOUT);
            dos.flush();
        }
    }

    // ── Teste 4: admin encerra e gera times ─────────────────────────────────
    static void testarEncerrarEGerarTimes() throws IOException {
        try (Socket s = new Socket(Protocolo.HOST, Protocolo.PORTA)) {
            DataInputStream  dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

            // Login como gestor
            dos.writeByte(Protocolo.LOGIN_REQ);
            dos.writeUTF("gestor");
            dos.writeUTF("gestor123");
            dos.flush();

            assert dis.readByte() == Protocolo.LOGIN_OK;
            dis.readInt();
            assert dis.readByte() == Protocolo.TIPO_ADMIN;

            // Encerrar com 2 times
            dos.writeByte(Protocolo.ENCERRAR_REQ);
            dos.writeInt(2);
            dos.flush();

            byte resp = dis.readByte();
            if (resp == Protocolo.ENCERRAR_FAIL) {
                System.out.println("[INFO] Sistema ja encerrado anteriormente: " + dis.readUTF());
                dos.writeByte(Protocolo.LOGOUT);
                dos.flush();
                return;
            }

            assert resp == Protocolo.ENCERRAR_RESP : "Esperava ENCERRAR_RESP, obteve: " + resp;
            int qtdTimes = dis.readInt();
            System.out.println("[OK] Encerrado. Times gerados: " + qtdTimes);

            int totalJogadores = 0;
            for (int i = 0; i < qtdTimes; i++) {
                int numero = dis.readInt();
                Jogador[] membros = new JogadorInputStream(dis).receber();
                totalJogadores += membros.length;
                System.out.printf("     Time %d: %d jogadores", numero, membros.length);
                for (Jogador j : membros) {
                    System.out.printf(" [%s %.1f]", j.getNome(), j.getNotaMedia());
                }
                System.out.println();
            }
            assert totalJogadores == 5 : "Total de jogadores nos times deveria ser 5, foi " + totalJogadores;
            System.out.println("[OK] Todos os " + totalJogadores + " jogadores distribuidos nos times");

            dos.writeByte(Protocolo.LOGOUT);
            dos.flush();
        }
    }
}
