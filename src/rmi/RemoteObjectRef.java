package rmi;

import java.util.Objects;

public final class RemoteObjectRef {

    public static final String SERVICO_RACHA       = "ServicoRacha";
    public static final String PREFIXO_SESSAO      = "Sessao:";
    public static final String PREFIXO_NOTIFICADOR = "Notificador:";

    private final String nome;

    public RemoteObjectRef(String nome) {
        this.nome = Objects.requireNonNull(nome);
    }

    public static RemoteObjectRef servicoRacha() {
        return new RemoteObjectRef(SERVICO_RACHA);
    }

    public static RemoteObjectRef sessao(int idUsuario) {
        return new RemoteObjectRef(PREFIXO_SESSAO + idUsuario);
    }

    public static RemoteObjectRef notificador(String uuid) {
        return new RemoteObjectRef(PREFIXO_NOTIFICADOR + uuid);
    }

    public String getNome() {
        return nome;
    }

    public boolean isSessao()      { return nome.startsWith(PREFIXO_SESSAO); }
    public boolean isNotificador() { return nome.startsWith(PREFIXO_NOTIFICADOR); }

    @Override public boolean equals(Object o) {
        return o instanceof RemoteObjectRef r && nome.equals(r.nome);
    }
    @Override public int hashCode() { return nome.hashCode(); }
    @Override public String toString() { return nome; }
}
