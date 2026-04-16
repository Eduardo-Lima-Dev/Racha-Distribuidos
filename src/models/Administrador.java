package models;

/**
 * POJO que representa um administrador do sistema.
 * Administradores podem adicionar/remover jogadores e enviar
 * notas informativas via Multicast UDP para todos os clientes.
 */
public class Administrador extends Usuario {

    private String nivelAcesso;

    public Administrador() {}

    public Administrador(int id, String nome, String senha, String nivelAcesso) {
        super(id, nome, senha);
        this.nivelAcesso = nivelAcesso;
    }

    public String getNivelAcesso() { return nivelAcesso; }
    public void setNivelAcesso(String nivelAcesso) { this.nivelAcesso = nivelAcesso; }

    @Override
    public String toString() {
        return String.format("Administrador{id=%d, nome='%s', nivelAcesso='%s'}",
                getId(), getNome(), nivelAcesso);
    }
}
