package server;

import models.Administrador;
import models.Jogador;
import models.Racha;
import models.Sessao;
import models.Time;
import rmi.NotificadorCliente;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado global do servidor, compartilhado entre as instancias de
 * SessaoRemotaImpl e o ServicoRachaImpl. Substitui os campos estaticos
 * que existiam em network.ServidorMultiThread.
 *
 * Thread-safety:
 *   - mapas concorrentes para colecoes que sofrem mutacao por varias threads
 *   - AtomicInteger/AtomicBoolean para contadores e flags
 *   - bloco synchronized apenas para a geracao de times (operacao composta)
 */
public final class EstadoServidor {

    public final ConcurrentHashMap<Integer, Jogador>       jogadores        = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, Administrador> admins           = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String,  Boolean>       avaliacoesFeitas = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<Integer, Sessao>              sessoes      = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, NotificadorCliente>  notificadores = new ConcurrentHashMap<>();

    public final AtomicBoolean sistemaAberto = new AtomicBoolean(true);
    public final AtomicInteger proximoIdJogador = new AtomicInteger(1);
    public final AtomicInteger proximoIdSessao  = new AtomicInteger(1);
    public final AtomicInteger proximoIdRacha   = new AtomicInteger(1);

    public volatile Racha rachaAtual = null;

    public EstadoServidor() {
        carregarDadosIniciais();
    }

    // ── Inicializacao do "banco" em memoria ─────────────────────────────────

    private void carregarDadosIniciais() {
        Administrador a1 = new Administrador(101, "admin",  "admin123", "SUPER");
        Administrador a2 = new Administrador(102, "gestor", "gestor123", "NORMAL");
        admins.put(a1.getId(), a1);
        admins.put(a2.getId(), a2);

        Jogador j1 = new Jogador(1, "Carlos",   "senha1", Jogador.Posicao.ATACANTE);
        Jogador j2 = new Jogador(2, "Fernanda", "senha2", Jogador.Posicao.GOLEIRO);
        Jogador j3 = new Jogador(3, "Rodrigo",  "senha3", Jogador.Posicao.DEFENSOR);
        Jogador j4 = new Jogador(4, "Ana",      "senha4", Jogador.Posicao.MEIO_CAMPO);
        Jogador j5 = new Jogador(5, "Pedro",    "senha5", Jogador.Posicao.ATACANTE);

        for (Jogador j : new Jogador[]{j1, j2, j3, j4, j5}) {
            jogadores.put(j.getId(), j);
        }
        proximoIdJogador.set(6);
    }

    // ── Snake draft ─────────────────────────────────────────────────────────

    /**
     * Distribui os jogadores em times balanceados via snake draft.
     * Ordena por media de avaliacao decrescente e atribui em zigue-zague
     * para que as medias dos times fiquem proximas.
     */
    public synchronized Time[] gerarTimes(int qtdTimes) {
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
}
