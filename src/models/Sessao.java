package models;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sessao de um usuario autenticado.
 *
 * Composicao (tem-um): Sessao tem-um Usuario.
 *
 * Mantida no servidor por sessao logada. O id da sessao compoe o
 * RemoteObjectRef "Sessao:<id>" devolvido no LOGIN.
 */
public class Sessao {

    private final int id;
    private final Usuario usuario;
    private final Instant criadaEm;
    private final AtomicInteger ultimoRequestId;

    public Sessao(int id, Usuario usuario) {
        this.id = id;
        this.usuario = usuario;
        this.criadaEm = Instant.now();
        this.ultimoRequestId = new AtomicInteger(0);
    }

    public int getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Instant getCriadaEm() { return criadaEm; }

    public int getUltimoRequestId() { return ultimoRequestId.get(); }
    public void registrarRequestId(int rid) { ultimoRequestId.set(rid); }

    public String getRef() {
        return "Sessao:" + id;
    }

    public boolean ehAdmin() {
        return usuario instanceof Administrador;
    }

    public boolean ehJogador() {
        return usuario instanceof Jogador;
    }

    @Override
    public String toString() {
        return String.format("Sessao{id=%d, usuario=%s, criadaEm=%s}",
                id, usuario.getNome(), criadaEm);
    }
}
