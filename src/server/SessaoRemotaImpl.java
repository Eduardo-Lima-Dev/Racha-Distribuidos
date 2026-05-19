package server;

import marshalling.Marshaller;
import models.Administrador;
import models.Jogador;
import models.Racha;
import models.Sessao;
import models.Time;
import rmi.NotificadorCliente;
import rmi.ReplyMessage;
import rmi.RequestMessage;
import rmi.SessaoRemota;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sessao remota de um cliente autenticado.
 *
 * Cada login cria uma instancia, exportada no Registry sob "Sessao:&lt;id&gt;".
 * Concentra todos os methodIds que exigem usuario autenticado.
 *
 * Tabela de methodIds:
 *   2  LISTAR_JOGADORES   args: {}                                 reply: {"ok":true,"jogadores":[...]}
 *   3  AVALIAR            args: {"idAvaliado":N,"nota":7.5}        reply: {"ok":true} ou erro
 *   4  ADICIONAR_JOGADOR  args: {"nome":..,"senha":..,"posicao":..}reply: {"ok":true,"id":N} ou erro    [admin]
 *   5  REMOVER_JOGADOR    args: {"id":N}                           reply: {"ok":true} ou erro          [admin]
 *   6  ENVIAR_AVISO       args: {"mensagem":..}                    reply: {"ok":true} ou erro          [admin]
 *   7  ENCERRAR_AVALIACOES args:{"qtdTimes":N}                     reply: {"ok":true,"racha":{...}}    [admin]
 *   8  LOGOUT             args: {}                                 reply: {"ok":true}
 *
 * Registro de notificadores e feito pelo metodo direto
 * {@link #registrarNotificador(NotificadorCliente)} (passagem por referencia
 * para um objeto Remote).
 */
public class SessaoRemotaImpl extends UnicastRemoteObject implements SessaoRemota {

    public static final int METHOD_LISTAR             = 2;
    public static final int METHOD_AVALIAR            = 3;
    public static final int METHOD_ADICIONAR          = 4;
    public static final int METHOD_REMOVER            = 5;
    public static final int METHOD_AVISO              = 6;
    public static final int METHOD_ENCERRAR           = 7;
    public static final int METHOD_LOGOUT             = 8;

    private final Sessao sessao;
    private final EstadoServidor estado;

    public SessaoRemotaImpl(Sessao sessao, EstadoServidor estado) throws RemoteException {
        super();
        this.sessao = sessao;
        this.estado = estado;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Despacho principal
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public byte[] invocar(byte[] requestBytes) throws RemoteException {
        RequestMessage req = Marshaller.desempacotar(requestBytes);
        int methodId  = req.getMethodId();
        int requestId = req.getRequestId();
        sessao.registrarRequestId(requestId);

        Map<String, Object> args;
        try {
            args = (req.getArguments() == null || req.getArguments().length == 0)
                    ? Json.obj()
                    : Json.parseObj(new String(req.getArguments(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return responder(requestId, erro("Argumentos JSON invalidos: " + e.getMessage()));
        }

        try {
            Map<String, Object> resultado = switch (methodId) {
                case METHOD_LISTAR    -> listarJogadores();
                case METHOD_AVALIAR   -> avaliar(args);
                case METHOD_ADICIONAR -> exigeAdmin() ? adicionarJogador(args) : negado();
                case METHOD_REMOVER   -> exigeAdmin() ? removerJogador(args)   : negado();
                case METHOD_AVISO     -> exigeAdmin() ? enviarAviso(args)      : negado();
                case METHOD_ENCERRAR  -> exigeAdmin() ? encerrar(args)         : negado();
                case METHOD_LOGOUT    -> logout();
                default -> erro("methodId " + methodId + " nao suportado na sessao.");
            };
            return responder(requestId, resultado);
        } catch (Exception e) {
            System.err.println("[Sessao " + sessao.getId() + "] erro em methodId=" + methodId + ": " + e.getMessage());
            return responder(requestId, erro(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registro de callback (passagem por referencia)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void registrarNotificador(NotificadorCliente notificador) throws RemoteException {
        estado.notificadores.put(sessao.getId(), notificador);
        System.out.printf("[NOTIF] Notificador registrado para sessao=%d%n", sessao.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes compartilhadas
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> listarJogadores() {
        List<Object> arr = Json.arr();
        for (Jogador j : estado.jogadores.values()) {
            // Omite o proprio jogador autenticado da lista (espelha o comportamento do Trab 1)
            if (sessao.ehJogador() && j.getId() == sessao.getUsuario().getId()) continue;
            arr.add(jogadorToJson(j));
        }
        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        reply.put("jogadores", arr);
        reply.put("sistemaAberto", estado.sistemaAberto.get());
        return reply;
    }

    private Map<String, Object> avaliar(Map<String, Object> args) {
        if (!estado.sistemaAberto.get()) return erro("Avaliacoes encerradas.");
        if (!sessao.ehJogador())         return erro("Apenas jogadores podem avaliar.");

        int    idAvaliado  = Json.getInt(args, "idAvaliado");
        double nota        = Json.getDouble(args, "nota");
        int    idAvaliador = sessao.getUsuario().getId();

        if (nota < 0.0 || nota > 10.0) return erro("Nota deve estar entre 0.0 e 10.0.");

        Jogador avaliado = estado.jogadores.get(idAvaliado);
        if (avaliado == null) return erro("Jogador nao encontrado.");
        if (idAvaliador == idAvaliado) return erro("Nao e permitido se auto-avaliar.");

        String chave = idAvaliador + "-" + idAvaliado;
        if (estado.avaliacoesFeitas.putIfAbsent(chave, Boolean.TRUE) != null) {
            return erro("Voce ja avaliou este jogador.");
        }

        synchronized (avaliado) {
            avaliado.receberAvaliacao(nota);
        }

        System.out.printf("[AVAL]  id=%d avaliou id=%d nota=%.1f%n", idAvaliador, idAvaliado, nota);
        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        return reply;
    }

    private Map<String, Object> logout() {
        estado.sessoes.remove(sessao.getId());
        estado.notificadores.remove(sessao.getId());
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {}
        System.out.printf("[LOGOUT] sessao=%d (%s)%n", sessao.getId(), sessao.getUsuario().getNome());
        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        return reply;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operacoes de administrador
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> adicionarJogador(Map<String, Object> args) {
        String nome    = Json.getString(args, "nome");
        String senha   = Json.getString(args, "senha");
        String posicao = Json.getString(args, "posicao");
        if (nome == null || senha == null || posicao == null) {
            return erro("Campos 'nome', 'senha' e 'posicao' sao obrigatorios.");
        }

        boolean existe = estado.jogadores.values().stream()
                .anyMatch(j -> j.getNome().equalsIgnoreCase(nome));
        if (existe) return erro("Ja existe um jogador com o nome: " + nome);

        Jogador.Posicao pos;
        try {
            pos = Jogador.Posicao.valueOf(posicao.toUpperCase());
        } catch (IllegalArgumentException e) {
            return erro("Posicao invalida: " + posicao + ". Use GOLEIRO, DEFENSOR, MEIO_CAMPO ou ATACANTE.");
        }

        int novoId = estado.proximoIdJogador.getAndIncrement();
        Jogador novo = new Jogador(novoId, nome, senha, pos);
        estado.jogadores.put(novoId, novo);

        System.out.printf("[ADD]   %s adicionou jogador '%s' (id=%d)%n",
                sessao.getUsuario().getNome(), nome, novoId);

        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        reply.put("id", novoId);
        return reply;
    }

    private Map<String, Object> removerJogador(Map<String, Object> args) {
        int id = Json.getInt(args, "id");
        Jogador removido = estado.jogadores.remove(id);
        if (removido == null) return erro("Jogador com id=" + id + " nao encontrado.");

        estado.avaliacoesFeitas.keySet()
                .removeIf(k -> k.startsWith(id + "-") || k.endsWith("-" + id));

        System.out.printf("[REM]   %s removeu jogador '%s' (id=%d)%n",
                sessao.getUsuario().getNome(), removido.getNome(), id);

        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        return reply;
    }

    private Map<String, Object> enviarAviso(Map<String, Object> args) {
        String mensagem = Json.getString(args, "mensagem");
        if (mensagem == null || mensagem.isBlank()) return erro("Mensagem vazia.");

        String de = sessao.getUsuario().getNome();
        notificarTodos("AVISO", de, mensagem);
        System.out.printf("[AVISO] %s: %s%n", de, mensagem);

        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        return reply;
    }

    private Map<String, Object> encerrar(Map<String, Object> args) {
        int qtdTimes = Json.getInt(args, "qtdTimes");
        if (qtdTimes < 2) return erro("Numero de times deve ser >= 2.");

        int total = estado.jogadores.size();
        if (total < qtdTimes) {
            return erro("Jogadores insuficientes: " + total + " jogadores para " + qtdTimes + " times.");
        }

        boolean foiEncerrado = estado.sistemaAberto.compareAndSet(true, false);
        if (foiEncerrado) {
            Time[] times = estado.gerarTimes(qtdTimes);
            estado.rachaAtual = new Racha(estado.proximoIdRacha.getAndIncrement(), times);
            String de = sessao.getUsuario().getNome();
            notificarTodos("AVISO", "Sistema",
                    "Avaliacoes encerradas por " + de + ". " + qtdTimes + " times gerados.");
            notificarTodos("TIMES", "Sistema", rachaToJson(estado.rachaAtual));
            System.out.printf("[ENCER] %s encerrou as avaliacoes. %d times gerados.%n", de, qtdTimes);
        }

        if (estado.rachaAtual == null) return erro("Sistema ja encerrado mas resultado indisponivel.");

        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        reply.put("ja_encerrado", !foiEncerrado);
        reply.put("racha", rachaParaMapa(estado.rachaAtual));
        return reply;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean exigeAdmin() {
        return sessao.getUsuario() instanceof Administrador;
    }

    private static Map<String, Object> negado() {
        Map<String, Object> m = Json.obj();
        m.put("ok", false);
        m.put("erro", "Operacao restrita a administradores.");
        return m;
    }

    private static Map<String, Object> erro(String motivo) {
        Map<String, Object> m = Json.obj();
        m.put("ok", false);
        m.put("erro", motivo);
        return m;
    }

    private byte[] responder(int requestId, Map<String, Object> resultado) {
        byte[] resultJson = Json.toJson(resultado).getBytes(StandardCharsets.UTF_8);
        ReplyMessage reply = new ReplyMessage(1, requestId, resultJson);
        return Marshaller.empacotarReply(reply);
    }

    // ── Conversao para JSON ─────────────────────────────────────────────────

    private static Map<String, Object> jogadorToJson(Jogador j) {
        Map<String, Object> m = Json.obj();
        m.put("id", j.getId());
        m.put("nome", j.getNome());
        m.put("posicao", j.getPosicao().name());
        m.put("media", j.getNotaMedia());
        m.put("qtdAvaliacoes", j.getQtdNotas());
        return m;
    }

    private static Map<String, Object> rachaParaMapa(Racha racha) {
        Map<String, Object> m = Json.obj();
        m.put("id", racha.getId());
        m.put("qtdTimes", racha.getQuantidadeTimes());
        m.put("mediaGeral", racha.getMediaGeral());

        List<Object> times = Json.arr();
        for (Time t : racha.getTimes()) {
            Map<String, Object> tm = Json.obj();
            tm.put("numero", t.getNumero());
            tm.put("media", t.getMediaDoTime());
            List<Object> membros = Json.arr();
            for (Jogador j : t.getJogadores()) {
                membros.add(jogadorToJson(j));
            }
            tm.put("jogadores", membros);
            times.add(tm);
        }
        m.put("times", times);
        return m;
    }

    private static String rachaToJson(Racha racha) {
        return Json.toJson(rachaParaMapa(racha));
    }

    // ── Notificacao por callback (substitui o multicast UDP) ────────────────

    private void notificarTodos(String tipo, String de, String mensagem) {
        Map<String, Object> payload = Json.obj();
        payload.put("tipo", tipo);
        payload.put("de", de);
        payload.put("mensagem", mensagem);
        payload.put("hora", java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        byte[] dados = Json.toJson(payload).getBytes(StandardCharsets.UTF_8);

        List<Integer> mortos = new ArrayList<>();
        for (Map.Entry<Integer, NotificadorCliente> e : estado.notificadores.entrySet()) {
            try {
                e.getValue().notificar(dados);
            } catch (RemoteException ex) {
                System.err.println("[NOTIF] Falha ao notificar sessao=" + e.getKey() + ": " + ex.getMessage());
                mortos.add(e.getKey());
            }
        }
        mortos.forEach(estado.notificadores::remove);
    }
}
