package network;

import models.Administrador;
import models.Jogador;
import models.Time;
import streams.JogadorOutputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Runnable que trata uma conexao de cliente em thread dedicada.
 *
 * Fluxo:
 *   1. Autenticacao via LOGIN_REQ
 *   2. Loop de servico: despacha para tratarJogador() ou tratarAdmin()
 *      conforme o tipo de usuario autenticado
 *   3. Encerra ao receber LOGOUT (0x00) ou ao detectar desconexao
 *
 * Regra critica de streams:
 *   JogadorOutputStream e criado passando o DataOutputStream existente (dos)
 *   como destino. Nunca fecha o JOS dentro do loop — isso fecharia o socket.
 */
public class ManipuladorCliente implements Runnable {

    private final Socket socket;

    ManipuladorCliente(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket) {
            DataInputStream  dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            // ── Fase de autenticacao ─────────────────────────────────────────
            byte opcode = dis.readByte();
            if (opcode != Protocolo.LOGIN_REQ) return;

            String nome  = dis.readUTF();
            String senha = dis.readUTF();

            // Verifica admins primeiro
            Administrador admin = ServidorMultiThread.admins.values().stream()
                    .filter(a -> a.getNome().equals(nome) && a.getSenha().equals(senha))
                    .findFirst().orElse(null);

            if (admin != null) {
                dos.writeByte(Protocolo.LOGIN_OK);
                dos.writeInt(admin.getId());
                dos.writeByte(Protocolo.TIPO_ADMIN);
                dos.flush();
                System.out.println("[LOGIN] Admin: " + nome);
                tratarAdmin(dis, dos, admin);
                return;
            }

            // Verifica jogadores
            Jogador jogador = ServidorMultiThread.jogadores.values().stream()
                    .filter(j -> j.getNome().equals(nome) && j.getSenha().equals(senha))
                    .findFirst().orElse(null);

            if (jogador != null) {
                dos.writeByte(Protocolo.LOGIN_OK);
                dos.writeInt(jogador.getId());
                dos.writeByte(Protocolo.TIPO_JOGADOR);
                dos.flush();
                System.out.println("[LOGIN] Jogador: " + nome);
                tratarJogador(dis, dos, jogador);
                return;
            }

            dos.writeByte(Protocolo.LOGIN_FAIL);
            dos.writeUTF("Credenciais invalidas.");
            dos.flush();
            System.out.println("[FAIL]  Login invalido: " + nome);

        } catch (EOFException e) {
            System.out.println("[-] Cliente desconectado: " + socket.getInetAddress());
        } catch (IOException e) {
            System.out.println("[-] Erro: " + e.getMessage());
        }
    }

    // ── Handler Jogador ──────────────────────────────────────────────────────

    private void tratarJogador(DataInputStream dis, DataOutputStream dos, Jogador logado)
            throws IOException {
        byte op;
        while ((op = dis.readByte()) != Protocolo.LOGOUT) {
            switch (op) {
                case Protocolo.LIST_REQ  -> enviarLista(dos, logado.getId());
                case Protocolo.AVALIAR_REQ -> avaliar(dis, dos, logado.getId());
                default -> { return; }
            }
        }
    }

    // ── Handler Admin ────────────────────────────────────────────────────────

    private void tratarAdmin(DataInputStream dis, DataOutputStream dos, Administrador admin)
            throws IOException {
        byte op;
        while ((op = dis.readByte()) != Protocolo.LOGOUT) {
            switch (op) {
                case Protocolo.LIST_REQ     -> enviarLista(dos, -1);
                case Protocolo.ADD_REQ      -> adicionarJogador(dis, dos, admin);
                case Protocolo.REM_REQ      -> removerJogador(dis, dos, admin);
                case Protocolo.ENCERRAR_REQ -> encerrar(dis, dos, admin);
                default -> { return; }
            }
        }
    }

    // ── Operacoes compartilhadas ─────────────────────────────────────────────

    /**
     * Envia a lista de jogadores.
     * @param excluirId ID a omitir da lista (pass -1 para enviar todos)
     */
    private void enviarLista(DataOutputStream dos, int excluirId) throws IOException {
        List<Jogador> lista = new ArrayList<>(ServidorMultiThread.jogadores.values());
        if (excluirId != -1) lista.removeIf(j -> j.getId() == excluirId);

        Jogador[] arr = lista.toArray(new Jogador[0]);

        dos.writeByte(Protocolo.LIST_RESP);
        // Passa dos (DataOutputStream existente) — sem criar novo wrapper externo
        JogadorOutputStream jos = new JogadorOutputStream(arr, arr.length, dos);
        jos.enviar(); // inclui flush interno
    }

    private void avaliar(DataInputStream dis, DataOutputStream dos, int idAvaliador)
            throws IOException {
        int    idAvaliado = dis.readInt();
        double nota       = dis.readDouble();

        if (!ServidorMultiThread.sistemaAberto.get()) {
            dos.writeByte(Protocolo.AVALIAR_FAIL);
            dos.writeUTF("Avaliacoes encerradas.");
            dos.flush();
            return;
        }

        Jogador avaliado = ServidorMultiThread.jogadores.get(idAvaliado);
        if (avaliado == null) {
            dos.writeByte(Protocolo.AVALIAR_FAIL);
            dos.writeUTF("Jogador nao encontrado.");
            dos.flush();
            return;
        }

        if (idAvaliador == idAvaliado) {
            dos.writeByte(Protocolo.AVALIAR_FAIL);
            dos.writeUTF("Nao e permitido se auto-avaliar.");
            dos.flush();
            return;
        }

        String chave = idAvaliador + "-" + idAvaliado;
        if (ServidorMultiThread.avaliacoesFeitas.putIfAbsent(chave, Boolean.TRUE) != null) {
            dos.writeByte(Protocolo.AVALIAR_FAIL);
            dos.writeUTF("Voce ja avaliou este jogador.");
            dos.flush();
            return;
        }

        synchronized (avaliado) {
            avaliado.receberAvaliacao(nota);
        }

        dos.writeByte(Protocolo.AVALIAR_OK);
        dos.flush();
        System.out.printf("[AVAL]  id=%d avaliou id=%d nota=%.1f%n", idAvaliador, idAvaliado, nota);
    }

    // ── Operacoes exclusivas de admin ────────────────────────────────────────

    private void adicionarJogador(DataInputStream dis, DataOutputStream dos,
                                  Administrador admin) throws IOException {
        String nome    = dis.readUTF();
        String senha   = dis.readUTF();
        String posicao = dis.readUTF();

        // Valida nome duplicado
        boolean nomeExiste = ServidorMultiThread.jogadores.values().stream()
                .anyMatch(j -> j.getNome().equalsIgnoreCase(nome));
        if (nomeExiste) {
            dos.writeByte(Protocolo.ADD_FAIL);
            dos.writeUTF("Ja existe um jogador com o nome: " + nome);
            dos.flush();
            return;
        }

        Jogador.Posicao pos;
        try {
            pos = Jogador.Posicao.valueOf(posicao.toUpperCase());
        } catch (IllegalArgumentException e) {
            dos.writeByte(Protocolo.ADD_FAIL);
            dos.writeUTF("Posicao invalida: " + posicao
                    + ". Use: GOLEIRO, DEFENSOR, MEIO_CAMPO, ATACANTE");
            dos.flush();
            return;
        }

        int novoId = ServidorMultiThread.proximoId.getAndIncrement();
        Jogador novo = new Jogador(novoId, nome, senha, pos);
        ServidorMultiThread.jogadores.put(novoId, novo);

        dos.writeByte(Protocolo.ADD_OK);
        dos.writeInt(novoId);
        dos.flush();
        System.out.printf("[ADD]   %s adicionou jogador '%s' (id=%d)%n",
                admin.getNome(), nome, novoId);
    }

    private void removerJogador(DataInputStream dis, DataOutputStream dos,
                                Administrador admin) throws IOException {
        int id = dis.readInt();

        Jogador removido = ServidorMultiThread.jogadores.get(id);
        if (removido == null) {
            dos.writeByte(Protocolo.REM_FAIL);
            dos.writeUTF("Jogador com id=" + id + " nao encontrado.");
            dos.flush();
            return;
        }

        ServidorMultiThread.jogadores.remove(id);
        // Limpa avaliacoes envolvendo este jogador
        ServidorMultiThread.avaliacoesFeitas.keySet()
                .removeIf(k -> k.startsWith(id + "-") || k.endsWith("-" + id));

        dos.writeByte(Protocolo.REM_OK);
        dos.flush();
        System.out.printf("[REM]   %s removeu jogador '%s' (id=%d)%n",
                admin.getNome(), removido.getNome(), id);
    }

    private void encerrar(DataInputStream dis, DataOutputStream dos,
                          Administrador admin) throws IOException {
        int qtdTimes = dis.readInt();

        if (qtdTimes < 2) {
            dos.writeByte(Protocolo.ENCERRAR_FAIL);
            dos.writeUTF("Numero de times deve ser >= 2.");
            dos.flush();
            return;
        }

        int totalJogadores = ServidorMultiThread.jogadores.size();
        if (totalJogadores < qtdTimes) {
            dos.writeByte(Protocolo.ENCERRAR_FAIL);
            dos.writeUTF("Jogadores insuficientes: " + totalJogadores
                    + " jogadores para " + qtdTimes + " times.");
            dos.flush();
            return;
        }

        // Apenas o primeiro ENCERRAR_REQ gera os times; os seguintes reenviam o resultado
        boolean foiEncerrado = ServidorMultiThread.sistemaAberto.compareAndSet(true, false);
        if (foiEncerrado) {
            ServidorMultiThread.timesGerados = ServidorMultiThread.gerarTimes(qtdTimes);
            System.out.printf("[ENCER] %s encerrou as avaliacoes. %d times gerados.%n",
                    admin.getNome(), qtdTimes);
        }

        Time[] times = ServidorMultiThread.timesGerados;
        if (times == null) {
            dos.writeByte(Protocolo.ENCERRAR_FAIL);
            dos.writeUTF("Sistema ja encerrado mas resultado indisponivel.");
            dos.flush();
            return;
        }

        dos.writeByte(Protocolo.ENCERRAR_RESP);
        dos.writeInt(times.length);

        for (Time t : times) {
            dos.writeInt(t.getNumero());
            Jogador[] membros = t.getJogadores().toArray(new Jogador[0]);
            JogadorOutputStream jos = new JogadorOutputStream(membros, membros.length, dos);
            jos.enviar();
        }
        dos.flush();
    }
}
