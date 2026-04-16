package network;

/**
 * Constantes do protocolo binario TCP da Fase 5.
 *
 * Formato geral de cada mensagem:
 *   [opcode: byte(1)] [payload especifico do opcode...]
 *
 * Opcodes agrupados por familia:
 *   0x0x - autenticacao
 *   0x1x - listagem
 *   0x2x - avaliacao
 *   0x3x - adicionar jogador (admin)
 *   0x4x - remover jogador  (admin)
 *   0x5x - encerrar sistema  (admin)
 */
public class Protocolo {

    // ── Conexao ──────────────────────────────────────────────────────────────
    public static final String HOST = "localhost";
    public static final int    PORTA = 5000;

    // ── Logout ───────────────────────────────────────────────────────────────
    /** Cliente -> Servidor: [0x00]  (sem payload — encerra a sessao) */
    public static final byte LOGOUT = 0x00;

    // ── Autenticacao ─────────────────────────────────────────────────────────
    /** Cliente -> Servidor: [0x01][nome: UTF][senha: UTF] */
    public static final byte LOGIN_REQ  = 0x01;

    /** Servidor -> Cliente: [0x02][id: int(4)][tipo: byte(1)] */
    public static final byte LOGIN_OK   = 0x02;

    /** Servidor -> Cliente: [0x03][motivo: UTF] */
    public static final byte LOGIN_FAIL = 0x03;

    // ── Listagem ─────────────────────────────────────────────────────────────
    /** Cliente -> Servidor: [0x10]  (sem payload) */
    public static final byte LIST_REQ  = 0x10;

    /**
     * Servidor -> Cliente: [0x11] seguido do payload de JogadorOutputStream.
     * O cliente deve usar JogadorInputStream.receber() apos consumir este opcode.
     */
    public static final byte LIST_RESP = 0x11;

    // ── Avaliacao ─────────────────────────────────────────────────────────────
    /** Cliente -> Servidor: [0x20][idAvaliado: int(4)][nota: double(8)] */
    public static final byte AVALIAR_REQ  = 0x20;

    /** Servidor -> Cliente: [0x21]  (sem payload) */
    public static final byte AVALIAR_OK   = 0x21;

    /** Servidor -> Cliente: [0x22][motivo: UTF] */
    public static final byte AVALIAR_FAIL = 0x22;

    // ── Adicionar jogador (admin) ─────────────────────────────────────────────
    /** Cliente -> Servidor: [0x30][nome: UTF][senha: UTF][posicao: UTF] */
    public static final byte ADD_REQ  = 0x30;

    /** Servidor -> Cliente: [0x31][idGerado: int(4)] */
    public static final byte ADD_OK   = 0x31;

    /** Servidor -> Cliente: [0x32][motivo: UTF] */
    public static final byte ADD_FAIL = 0x32;

    // ── Remover jogador (admin) ──────────────────────────────────────────────
    /** Cliente -> Servidor: [0x40][id: int(4)] */
    public static final byte REM_REQ  = 0x40;

    /** Servidor -> Cliente: [0x41]  (sem payload) */
    public static final byte REM_OK   = 0x41;

    /** Servidor -> Cliente: [0x42][motivo: UTF] */
    public static final byte REM_FAIL = 0x42;

    // ── Encerrar avaliacoes (admin) ──────────────────────────────────────────
    /** Cliente -> Servidor: [0x50][qtdTimes: int(4)] */
    public static final byte ENCERRAR_REQ  = 0x50;

    /**
     * Servidor -> Cliente: [0x51][qtdTimes: int(4)]
     *   Para cada time:
     *     [numeroTime: int(4)] [payload JogadorOutputStream dos membros]
     */
    public static final byte ENCERRAR_RESP = 0x51;

    /** Servidor -> Cliente: [0x52][motivo: UTF] */
    public static final byte ENCERRAR_FAIL = 0x52;

    // ── Tipos de usuario ──────────────────────────────────────────────────────
    public static final byte TIPO_JOGADOR = 0x00;
    public static final byte TIPO_ADMIN   = 0x01;
}
