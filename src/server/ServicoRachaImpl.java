package server;

import marshalling.Marshaller;
import models.Administrador;
import models.Jogador;
import models.Sessao;
import models.Usuario;
import rmi.ReplyMessage;
import rmi.RequestMessage;
import rmi.ServicoRacha;
import utils.Json;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

/**
 * Servico remoto principal. Implementa o unico metodo de despacho
 * {@code byte[] invocar(byte[])} previsto na Fig. 5.4 do Coulouris.
 *
 * Trata apenas o LOGIN (methodId = 1). Apos autenticar o usuario,
 * cria uma Sessao e exporta uma SessaoRemotaImpl no Registry sob o
 * nome "Sessao:<id>". O cliente recebe esse nome no campo
 * {@code sessaoRef} e faz lookup para obter o stub da SessaoRemota,
 * por onde fluem todas as demais chamadas.
 *
 * methodId reservado:
 *   1  LOGIN   args: {"nome":..,"senha":..}
 *               reply ok:    {"ok":true,"sessaoRef":"Sessao:N","tipo":"ADMIN|JOGADOR","idUsuario":N}
 *               reply fail:  {"ok":false,"erro":"motivo"}
 */
public class ServicoRachaImpl extends UnicastRemoteObject implements ServicoRacha {

    public static final int METHOD_LOGIN = 1;
    private static final String SENHA_MESTRA = "rachao2025";

    private final EstadoServidor estado;
    private final Registry registry;

    public ServicoRachaImpl(EstadoServidor estado, Registry registry) throws RemoteException {
        super();
        this.estado = estado;
        this.registry = registry;
    }

    @Override
    public byte[] invocar(byte[] requestBytes) throws RemoteException {
        RequestMessage req = Marshaller.desempacotar(requestBytes);
        int methodId  = req.getMethodId();
        int requestId = req.getRequestId();

        try {
            if (methodId == METHOD_LOGIN) {
                return responder(requestId, login(req.getArguments()));
            }
            return responder(requestId, erro("methodId " + methodId + " nao suportado pelo ServicoRacha"));
        } catch (Exception e) {
            System.err.println("[ServicoRacha] erro em methodId=" + methodId + ": " + e.getMessage());
            return responder(requestId, erro(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> login(byte[] argsBytes) throws RemoteException {
        Map<String, Object> args = Json.parseObj(new String(argsBytes, java.nio.charset.StandardCharsets.UTF_8));
        String nome  = Json.getString(args, "nome");
        String senha = Json.getString(args, "senha");

        if (nome == null || senha == null) {
            return erro("Campos 'nome' e 'senha' sao obrigatorios.");
        }

        boolean senhaMestra = SENHA_MESTRA.equals(senha);

        Usuario autenticado = estado.admins.values().stream()
                .filter(a -> a.getNome().equals(nome) && (a.getSenha().equals(senha) || senhaMestra))
                .map(a -> (Usuario) a)
                .findFirst()
                .orElseGet(() -> estado.jogadores.values().stream()
                        .filter(j -> j.getNome().equals(nome) && (j.getSenha().equals(senha) || senhaMestra))
                        .map(j -> (Usuario) j)
                        .findFirst()
                        .orElse(null));

        if (autenticado == null) {
            System.out.println("[FAIL]  Login invalido: " + nome);
            return erro("Credenciais invalidas.");
        }

        int idSessao = estado.proximoIdSessao.getAndIncrement();
        Sessao sessao = new Sessao(idSessao, autenticado);
        estado.sessoes.put(idSessao, sessao);

        // Exporta a SessaoRemotaImpl no Registry sob o ref "Sessao:<id>"
        SessaoRemotaImpl sessaoImpl = new SessaoRemotaImpl(sessao, estado);
        registry.rebind(sessao.getRef(), sessaoImpl);

        String tipo = (autenticado instanceof Administrador) ? "ADMIN" : "JOGADOR";
        System.out.printf("[LOGIN] %s '%s' (sessao=%s)%n", tipo, nome, sessao.getRef());

        Map<String, Object> reply = Json.obj();
        reply.put("ok", true);
        reply.put("sessaoRef", sessao.getRef());
        reply.put("tipo", tipo);
        reply.put("idUsuario", autenticado.getId());
        if (autenticado instanceof Jogador j) {
            reply.put("posicao", j.getPosicao().name());
        }
        return reply;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de empacotamento
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] responder(int requestId, Map<String, Object> resultado) {
        byte[] resultJson = Json.toJson(resultado).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ReplyMessage reply = new ReplyMessage(1, requestId, resultJson);
        return Marshaller.empacotarReply(reply);
    }

    private static Map<String, Object> erro(String motivo) {
        Map<String, Object> m = Json.obj();
        m.put("ok", false);
        m.put("erro", motivo);
        return m;
    }
}
