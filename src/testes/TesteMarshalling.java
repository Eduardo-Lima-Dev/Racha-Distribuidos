package testes;

import marshalling.Json;
import marshalling.Marshaller;
import rmi.RemoteObjectRef;
import rmi.ReplyMessage;
import rmi.RequestMessage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TesteMarshalling {

    private static int passou = 0;
    private static int falhou = 0;

    public static void main(String[] args) {
        System.out.println("=== TesteMarshalling ===\n");

        testJsonEscritaEEscape();
        testJsonRoundTripObjetoAninhado();
        testRequestRoundTrip();
        testReplyOkRoundTrip();
        testReplyErroRoundTrip();
        testRequestComArgsAninhados();
        testRefHelpers();

        System.out.println("\n=== Resultado: " + passou + " passou, " + falhou + " falhou ===");
        if (falhou > 0) System.exit(1);
    }

    private static void testJsonEscritaEEscape() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", "linha1\nlinha2\t\"aspas\"\\barra");
        m.put("vazio", "");
        String json = Json.escrever(m);
        eq("escape produz aspas escapadas",
           "{\"texto\":\"linha1\\nlinha2\\t\\\"aspas\\\"\\\\barra\",\"vazio\":\"\"}", json);

        Map<String, Object> v = Json.lerObjeto(json);
        eq("round-trip preserva caracteres especiais",
           "linha1\nlinha2\t\"aspas\"\\barra", v.get("texto"));
    }

    private static void testJsonRoundTripObjetoAninhado() {
        Map<String, Object> dentro = new LinkedHashMap<>();
        dentro.put("id", 7);
        dentro.put("media", 8.5);
        dentro.put("ativo", true);
        dentro.put("nada", null);

        Map<String, Object> fora = new LinkedHashMap<>();
        fora.put("interno", dentro);
        fora.put("lista", List.of(1, 2, 3));

        String json = Json.escrever(fora);
        Map<String, Object> v = Json.lerObjeto(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> i = (Map<String, Object>) v.get("interno");
        eq("aninhado: int", 7, ((Number) i.get("id")).intValue());
        eq("aninhado: double", 8.5, ((Number) i.get("media")).doubleValue());
        eq("aninhado: boolean", true, i.get("ativo"));
        eq("aninhado: null", null, i.get("nada"));

        @SuppressWarnings("unchecked")
        List<Object> lista = (List<Object>) v.get("lista");
        eq("array tamanho", 3, lista.size());
        eq("array[0]", 1, ((Number) lista.get(0)).intValue());
    }

    private static void testRequestRoundTrip() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("nome", "carlos");
        args.put("senha", "x\"y");
        byte[] argsBytes = Marshaller.argsDe(args);

        RequestMessage req = new RequestMessage(42, RemoteObjectRef.servicoRacha(), 1, argsBytes);
        byte[] empacotado = Marshaller.empacotar(req);
        String s = new String(empacotado, StandardCharsets.UTF_8);
        check("envelope contem messageType=0", s.contains("\"messageType\":0"));
        check("envelope contem requestId",     s.contains("\"requestId\":42"));
        check("envelope contem methodId",      s.contains("\"methodId\":1"));
        check("envelope contem ServicoRacha",  s.contains("\"ServicoRacha\""));

        RequestMessage decod = Marshaller.desempacotarRequest(empacotado);
        eq("desempacota requestId",  42, decod.requestId);
        eq("desempacota objRef",     "ServicoRacha", decod.objectReference);
        eq("desempacota methodId",   1, decod.methodId);
        Map<String, Object> argsLidos = Marshaller.argsParaMapa(decod.arguments);
        eq("args.nome preserva",  "carlos", argsLidos.get("nome"));
        eq("args.senha preserva", "x\"y",   argsLidos.get("senha"));
    }

    private static void testReplyOkRoundTrip() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idUsuario", 7);
        result.put("tipo", "ADMIN");
        result.put("sessaoRef", "Sessao:7");

        ReplyMessage rep = ReplyMessage.ok(42, Marshaller.argsDe(result));
        byte[] empacotado = Marshaller.empacotar(rep);
        String s = new String(empacotado, StandardCharsets.UTF_8);
        check("reply ok contem ok:true",  s.contains("\"ok\":true"));
        check("reply ok contem result",   s.contains("\"result\""));
        check("reply ok NAO contem erro", !s.contains("\"erro\""));

        ReplyMessage decod = Marshaller.desempacotarReply(empacotado);
        eq("reply requestId", 42, decod.requestId);
        eq("reply ok flag",   true, decod.ok);
        Map<String, Object> r = Marshaller.argsParaMapa(decod.arguments);
        eq("reply result.idUsuario", 7,       ((Number) r.get("idUsuario")).intValue());
        eq("reply result.tipo",      "ADMIN", r.get("tipo"));
        eq("reply result.sessaoRef", "Sessao:7", r.get("sessaoRef"));
    }

    private static void testReplyErroRoundTrip() {
        ReplyMessage rep = ReplyMessage.erro(99, "Credenciais invalidas.");
        byte[] empacotado = Marshaller.empacotar(rep);
        String s = new String(empacotado, StandardCharsets.UTF_8);
        check("reply erro contem ok:false",   s.contains("\"ok\":false"));
        check("reply erro contem erro:...",   s.contains("\"erro\":\"Credenciais invalidas.\""));
        check("reply erro NAO contem result", !s.contains("\"result\""));

        ReplyMessage decod = Marshaller.desempacotarReply(empacotado);
        eq("reply requestId",      99, decod.requestId);
        eq("reply ok=false",       false, decod.ok);
        eq("reply motivo preserva", "Credenciais invalidas.", decod.motivo());
    }

    private static void testRequestComArgsAninhados() {
        Map<String, Object> jogador = new LinkedHashMap<>();
        jogador.put("id", 3);
        jogador.put("nome", "Ana");
        jogador.put("media", 7.5);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("qtdTimes", 2);
        args.put("destaques", List.of(jogador));

        RequestMessage req = new RequestMessage(1, RemoteObjectRef.sessao(7), 50,
                                                 Marshaller.argsDe(args));
        byte[] empacotado = Marshaller.empacotar(req);
        RequestMessage decod = Marshaller.desempacotarRequest(empacotado);

        eq("objRef sessao", "Sessao:7", decod.objectReference);
        Map<String, Object> a = Marshaller.argsParaMapa(decod.arguments);
        eq("qtdTimes", 2, ((Number) a.get("qtdTimes")).intValue());
        @SuppressWarnings("unchecked")
        List<Object> destaques = (List<Object>) a.get("destaques");
        eq("destaques tamanho", 1, destaques.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> jog = (Map<String, Object>) destaques.get(0);
        eq("destaque.nome", "Ana", jog.get("nome"));
        eq("destaque.media", 7.5,  ((Number) jog.get("media")).doubleValue());
    }

    private static void testRefHelpers() {
        eq("ref ServicoRacha", "ServicoRacha", RemoteObjectRef.servicoRacha().getNome());
        eq("ref Sessao:7",     "Sessao:7",     RemoteObjectRef.sessao(7).getNome());
        eq("ref Notificador",  "Notificador:abc-123",
                                RemoteObjectRef.notificador("abc-123").getNome());
        check("isSessao()",      RemoteObjectRef.sessao(1).isSessao());
        check("isNotificador()", RemoteObjectRef.notificador("u").isNotificador());
    }

    private static void eq(String nome, Object esperado, Object obtido) {
        boolean ok = (esperado == null && obtido == null)
                || (esperado != null && esperado.equals(obtido));
        if (ok) {
            passou++;
            System.out.println("  [OK]   " + nome);
        } else {
            falhou++;
            System.out.println("  [FAIL] " + nome
                    + "\n         esperado: " + esperado
                    + "\n         obtido:   " + obtido);
        }
    }

    private static void check(String nome, boolean cond) {
        if (cond) {
            passou++;
            System.out.println("  [OK]   " + nome);
        } else {
            falhou++;
            System.out.println("  [FAIL] " + nome);
        }
    }
}
