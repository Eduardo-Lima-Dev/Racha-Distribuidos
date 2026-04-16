package network;

import models.Administrador;
import models.Jogador;
import models.Time;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor multi-threaded da Fase 5.
 *
 * Estado compartilhado (thread-safe):
 *   - jogadores:       mapa de jogadores registrados
 *   - admins:          mapa de administradores (imutavel apos init)
 *   - avaliacoesFeitas: controla avaliacao duplicada (chave: "avaliadorId-avaliadoId")
 *   - sistemaAberto:   false apos ENCERRAR_REQ
 *   - timesGerados:    resultado do encerramento (null ate ser gerado)
 */
public class ServidorMultiThread {

    // ── Estado compartilhado ─────────────────────────────────────────────────
    static final ConcurrentHashMap<Integer, Jogador>       jogadores       = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, Administrador> admins          = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String,  Boolean>       avaliacoesFeitas = new ConcurrentHashMap<>();
    static final AtomicBoolean  sistemaAberto = new AtomicBoolean(true);
    static final AtomicInteger  proximoId     = new AtomicInteger(1);
    static volatile Time[] timesGerados = null;

    // ── Main ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        inicializarDados();

        System.out.println("=== Servidor de Divisao de Times (Fase 5) ===");
        System.out.println("Porta        : " + Protocolo.PORTA);
        System.out.println("Jogadores    : " + jogadores.size());
        System.out.println("Admins       : " + admins.values().stream()
                .map(a -> a.getNome() + "/" + a.getSenha()).toList());
        System.out.println("Aguardando conexoes...\n");

        try (ServerSocket serverSocket = new ServerSocket(Protocolo.PORTA)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[+] " + socket.getInetAddress() + ":" + socket.getPort());
                Thread t = new Thread(new ManipuladorCliente(socket));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // ── Algoritmo snake draft ────────────────────────────────────────────────
    /**
     * Divide os jogadores em times balanceados usando snake draft.
     *
     * Ordena por nota decrescente e distribui em zigue-zague:
     *   rodada par  (0,2,4...): time 0 -> 1 -> ... -> N-1
     *   rodada impar(1,3,5...): time N-1 -> ... -> 1 -> 0
     *
     * Resultado: a diferenca de media entre os times e minimizada.
     */
    static synchronized Time[] gerarTimes(int qtdTimes) {
        List<Jogador> lista = new ArrayList<>(jogadores.values());
        lista.sort((a, b) -> Double.compare(b.getNotaMedia(), a.getNotaMedia()));

        Time[] times = new Time[qtdTimes];
        for (int i = 0; i < qtdTimes; i++) {
            times[i] = new Time(i + 1);
        }

        for (int i = 0; i < lista.size(); i++) {
            int rodada = i / qtdTimes;
            int pos    = i % qtdTimes;
            int idx    = (rodada % 2 == 0) ? pos : (qtdTimes - 1 - pos);
            times[idx].adicionarJogador(lista.get(i));
        }

        return times;
    }

    // ── Seed de dados ────────────────────────────────────────────────────────
    static void inicializarDados() {
        Administrador a1 = new Administrador(101, "admin",  "admin123", "SUPER");
        Administrador a2 = new Administrador(102, "gestor", "gestor123", "NORMAL");
        admins.put(a1.getId(), a1);
        admins.put(a2.getId(), a2);

        Jogador j1 = new Jogador(1, "Carlos",   "senha1", Jogador.Posicao.ATACANTE);
        Jogador j2 = new Jogador(2, "Fernanda",  "senha2", Jogador.Posicao.GOLEIRO);
        Jogador j3 = new Jogador(3, "Rodrigo",   "senha3", Jogador.Posicao.DEFENSOR);
        Jogador j4 = new Jogador(4, "Ana",       "senha4", Jogador.Posicao.MEIO_CAMPO);
        Jogador j5 = new Jogador(5, "Pedro",     "senha5", Jogador.Posicao.ATACANTE);

        for (Jogador j : new Jogador[]{j1, j2, j3, j4, j5}) {
            jogadores.put(j.getId(), j);
        }
        proximoId.set(6);
    }
}
