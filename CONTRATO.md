# CONTRATO — Trabalho 2 (RMI)

Documento de fronteira entre **Pessoa A** (infra RMI + marshalling) e **Pessoa B** (domínio + aplicação). Enquanto este arquivo não muda, ambas trabalham em paralelo sem se bloquear.

Qualquer mudança aqui exige aprovação das duas pessoas e disparo de PR conjunto.

---

## 1. Visão geral dos objetos remotos

Três tipos de objetos `Remote`. Os dois primeiros vivem no servidor; o terceiro no cliente (callback).

| Objeto remoto | Nome no Registry | Quem exporta | Quem invoca |
|---|---|---|---|
| `ServicoRacha` | `"ServicoRacha"` (fixo) | Servidor (1 instância) | Cliente, antes do login |
| `SessaoRemota` | `"Sessao:{id}"` (1 por cliente autenticado) | Servidor (1 por login) | Cliente, depois do login |
| `NotificadorCliente` | `"Notificador:{uuid}"` (1 por cliente) | Cliente | Servidor (callbacks) |

`SessaoRemota` é a demonstração da **passagem por referência**: o cliente faz `LOGIN` no `ServicoRacha`, recebe no JSON o nome `"Sessao:7"`, e em seguida faz `Naming.lookup("Sessao:7")` para obter o stub. Todas as operações autenticadas vão por esse stub.

`NotificadorCliente` substitui o multicast UDP: o cliente exporta um stub, registra-o no servidor via `REGISTRAR_NOTIFICADOR`, e o servidor invoca callbacks (`AVISO_RECEBIDO`, `TIMES_GERADOS`) em vez de mandar datagramas.

---

## 2. Interfaces Java (assinaturas exatas)

### `rmi/ServicoRacha.java`
```java
package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServicoRacha extends Remote {
    /**
     * Ponto de entrada para qualquer methodId que não exija sessão (hoje: LOGIN).
     * Bytes do request são uma RequestMessage marshalled em JSON (UTF-8).
     * Retorno são bytes de uma ReplyMessage marshalled em JSON (UTF-8).
     */
    byte[] invocar(byte[] request) throws RemoteException;
}
```

### `rmi/SessaoRemota.java`
```java
package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SessaoRemota extends Remote {
    /**
     * Ponto de entrada para todas as operações autenticadas.
     * O servidor valida que `objectReference` da RequestMessage casa com esta sessão;
     * se não casar, devolve reply com ok=false e erro="objeto remoto incorreto".
     */
    byte[] invocar(byte[] request) throws RemoteException;
}
```

### `rmi/NotificadorCliente.java`
```java
package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificadorCliente extends Remote {
    /**
     * Callback invocado pelo servidor. O byte[] é uma RequestMessage cujo
     * objectReference é o nome do notificador e methodId identifica o tipo
     * (AVISO_RECEBIDO=100, TIMES_GERADOS=101). Não retorna payload útil —
     * apenas a confirmação (ReplyMessage com ok=true).
     */
    byte[] invocar(byte[] request) throws RemoteException;
}
```

> **Por que todas têm a mesma assinatura `byte[] invocar(byte[])`?** Para manter o protocolo req-resp do livro como uma camada uniforme. O dispatcher interno em cada implementação olha o `methodId` da `RequestMessage` e roteia.

---

## 3. Estrutura das mensagens (Fig. 5.4 do livro)

### `rmi/RequestMessage.java`
```java
package rmi;

public class RequestMessage {
    public static final byte TYPE_REQUEST = 0;

    public byte    messageType;       // sempre 0 em RequestMessage
    public int     requestId;         // gerado por AtomicInteger no cliente
    public String  objectReference;   // "ServicoRacha" | "Sessao:7" | "Notificador:<uuid>"
    public int     methodId;          // ver tabela seção 4
    public byte[]  arguments;         // sub-payload JSON em UTF-8 (sub-objeto args do JSON)
}
```

### `rmi/ReplyMessage.java`
```java
package rmi;

public class ReplyMessage {
    public static final byte TYPE_REPLY = 1;

    public byte    messageType;       // sempre 1 em ReplyMessage
    public int     requestId;         // ecoa o requestId da RequestMessage
    public boolean ok;                // true = sucesso, false = erro de negócio
    public byte[]  arguments;         // se ok: payload JSON do resultado; se !ok: {"erro":"..."}
}
```

### Formato da `RequestMessage` empacotada em JSON (UTF-8)
```json
{
  "messageType": 0,
  "requestId": 42,
  "objectReference": "Sessao:7",
  "methodId": 20,
  "args": { "idAvaliado": 3, "nota": 8.5 }
}
```

### Formato da `ReplyMessage` empacotada em JSON (UTF-8)

Sucesso:
```json
{
  "messageType": 1,
  "requestId": 42,
  "ok": true,
  "result": { "idAvaliado": 3, "novaMedia": 7.42 }
}
```

Erro:
```json
{
  "messageType": 1,
  "requestId": 42,
  "ok": false,
  "erro": "Voce ja avaliou este jogador."
}
```

**Notas de marshalling:**
- O campo `arguments` (byte[]) do POJO Java contém os bytes UTF-8 do sub-objeto `args` (request) ou `result`/`erro` (reply). É o **payload específico do método**, não a mensagem inteira.
- O Marshaller é responsável por inserir/extrair esse sub-objeto na hora de empacotar.
- `requestId` em hex/dec tanto faz; preferir decimal por legibilidade nos logs.

---

## 4. Tabela `methodId` → método lógico

### 4.1 Em `ServicoRacha` (pré-autenticação)

| `methodId` | Nome lógico | Quem chama | args (JSON) | result (JSON em caso de ok) |
|---:|---|---|---|---|
| **1** | `LOGIN` | Cliente | `{"nome":"...","senha":"..."}` | `{"idUsuario":7,"tipo":"ADMIN","sessaoRef":"Sessao:7"}` |

Erros possíveis: `"Credenciais invalidas."`

### 4.2 Em `SessaoRemota` (autenticadas)

| `methodId` | Nome lógico | Perfil | args (JSON) | result (JSON em caso de ok) |
|---:|---|---|---|---|
| **10** | `LOGOUT` | qualquer | `{}` | `{}` |
| **11** | `LISTAR_JOGADORES` | qualquer | `{}` | `{"jogadores":[JogadorDTO, ...]}` |
| **20** | `AVALIAR` | jogador | `{"idAvaliado":3,"nota":8.5}` | `{"idAvaliado":3,"novaMedia":7.42}` |
| **30** | `ADICIONAR_JOGADOR` | admin | `{"nome":"...","senha":"...","posicao":"ATACANTE"}` | `{"idGerado":12}` |
| **40** | `REMOVER_JOGADOR` | admin | `{"id":12}` | `{}` |
| **50** | `ENCERRAR_AVALIACOES` | admin | `{"qtdTimes":2}` | `{"racha":RachaDTO}` |
| **60** | `ENVIAR_AVISO` | admin | `{"mensagem":"..."}` | `{}` |
| **70** | `REGISTRAR_NOTIFICADOR` | qualquer | `{"notificadorRef":"Notificador:abc-123"}` | `{}` |

Erros possíveis incluem: `"Avaliacoes encerradas."`, `"Voce ja avaliou este jogador."`, `"Nao e permitido se auto-avaliar."`, `"Jogador nao encontrado."`, `"Operacao restrita a administrador."`, `"objeto remoto incorreto"` (mismatch de `objectReference`), `"requestId duplicado"` (idempotência).

### 4.3 Em `NotificadorCliente` (callbacks servidor → cliente)

| `methodId` | Nome lógico | args (JSON) | result (JSON em caso de ok) |
|---:|---|---|---|
| **100** | `AVISO_RECEBIDO` | `{"de":"admin","mensagem":"...","hora":"14:30:00"}` | `{}` |
| **101** | `TIMES_GERADOS` | `{"racha":RachaDTO}` | `{}` |

---

## 5. DTOs (passagem por valor)

Os DTOs são as estruturas que aparecem dentro de `args` / `result`. São **espelhos serializáveis** das entidades do domínio (não as entidades em si — passagem por valor com representação externa).

### `JogadorDTO`
```json
{
  "id": 7,
  "nome": "Carlos",
  "posicao": "ATACANTE",
  "somaNotas": 24.5,
  "qtdNotas": 3,
  "media": 8.17
}
```
> `senha` **NUNCA** é serializada em DTO de saída.

### `AvaliacaoDTO`
```json
{
  "idAvaliador": 3,
  "idAvaliado": 7,
  "nota": 8.5
}
```

### `TimeDTO`
```json
{
  "numero": 1,
  "mediaDoTime": 7.83,
  "jogadores": [JogadorDTO, ...]
}
```

### `RachaDTO`
```json
{
  "data": "2026-05-19T20:15:00",
  "qtdTimes": 2,
  "times": [TimeDTO, ...]
}
```

---

## 6. Identificadores e ciclo de vida

- **`requestId`**: `AtomicInteger` no cliente, valor inicial `1`. Após `Integer.MAX_VALUE`, volta para `1`. Servidor ecoa no reply; cliente compara e lança `RemoteException` se divergir.
- **`Sessao:{id}`**: `{id}` é o `id` do `Usuario` autenticado. Servidor exporta a `SessaoRemotaImpl` e faz `Registry.rebind("Sessao:" + id, stub)` no `LOGIN`. No `LOGOUT`, faz `Registry.unbind(...)` e `UnicastRemoteObject.unexportObject(...)`.
- **`Notificador:{uuid}`**: o cliente gera um `UUID.randomUUID()` ao iniciar, exporta o stub local e registra no servidor com `REGISTRAR_NOTIFICADOR`. Se o cliente cair, o servidor detecta a `RemoteException` no próximo callback e remove o stub silenciosamente.

---

## 7. Tratamento de erros (camadas)

| Camada | Sintoma | Como reportar |
|---|---|---|
| Conexão / Registry | `RemoteException`, `NotBoundException` | propagar — cliente CLI mostra "Servidor indisponível" |
| Protocolo (mismatch de requestId, objectReference inválido, JSON malformado) | `RemoteException` com mensagem específica | servidor lança; cliente exibe e mantém sessão aberta |
| Negócio (avaliação duplicada, posição inválida, etc.) | `ReplyMessage` com `ok=false` e `erro` populado | cliente CLI exibe a mensagem de erro do servidor |

---

## 8. Interfaces de integração entre A e B

A Pessoa B precisa, da Pessoa A:

```java
// marshalling/Marshaller.java
public final class Marshaller {
    public static byte[] empacotar(RequestMessage msg);
    public static byte[] empacotar(ReplyMessage msg);
    public static RequestMessage desempacotarRequest(byte[] bytes);
    public static ReplyMessage   desempacotarReply(byte[] bytes);

    // Helpers para o payload (args/result) — opera em JSON aninhado
    public static byte[] argsDe(Object payload);   // payload é um DTO ou Map
    public static <T> T payloadPara(byte[] bytes, Class<T> tipo);
}

// client/ClienteRMI.java (singleton ou injetado)
public final class ClienteRMI {
    public ClienteRMI(String host, int porta);
    public byte[] doOperation(RemoteObjectRef ref, int methodId, byte[] arguments) throws RemoteException;
}
```

A Pessoa A precisa, da Pessoa B:

```java
// server/Dispatcher.java
public interface Dispatcher {
    /** Recebe a RequestMessage já desempacotada e retorna o payload do result (ou lança ErroNegocio). */
    byte[] executar(RequestMessage req) throws ErroNegocio;
}

// server/ErroNegocio.java
public class ErroNegocio extends Exception {
    public ErroNegocio(String motivo) { super(motivo); }
}
```

A Pessoa A liga o dispatcher dentro do `ServicoRachaImpl.invocar(...)` e `SessaoRemotaImpl.invocar(...)`:

```java
public byte[] invocar(byte[] request) throws RemoteException {
    RequestMessage req = Marshaller.desempacotarRequest(request);
    try {
        byte[] result = dispatcher.executar(req);
        return Marshaller.empacotar(ReplyMessage.ok(req.requestId, result));
    } catch (ErroNegocio e) {
        return Marshaller.empacotar(ReplyMessage.erro(req.requestId, e.getMessage()));
    }
}
```

---

## 9. Portas e endereços fixos

- `rmiregistry` na porta **1099** (padrão).
- Host: `localhost` no desenvolvimento; configurável via `-Dhost=...` na linha de comando.
- Cliente exporta `NotificadorCliente` na porta **anônima** (0 — JDK escolhe).

---

## 10. Itens **fora** do escopo deste contrato (Pessoa B decide sozinha)

- Layout exato do menu CLI.
- Estado interno do `ServicoRachaImpl` (estruturas concorrentes, locks, snake-draft).
- Texto exato das mensagens de erro de negócio (só o formato precisa casar).
- Conteúdo do relatório PDF.

## 11. Itens **fora** do escopo deste contrato (Pessoa A decide sozinha)

- Implementação interna do JSON manual (parser, escape, etc.).
- Decisão entre `LocateRegistry.createRegistry(...)` no código vs `rmiregistry` externo no `.bat`.
- Logging interno.
