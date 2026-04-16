# Sistema de Divisão de Times

Projeto da disciplina de **Sistemas Distribuídos** — implementação completa de comunicação entre processos com sockets TCP/UDP, streams customizados e serialização manual em Java puro (sem frameworks externos).

## Tema

Sistema de **ranqueamento anônimo de jogadores de futebol**. Durante o período aberto, jogadores autenticados avaliam uns aos outros com notas de 0 a 10. Ao encerrar as avaliações, o servidor calcula a média de cada jogador e divide os participantes em times balanceados automaticamente usando o algoritmo *snake draft*. O resultado é transmitido em tempo real para todos os clientes conectados via multicast UDP.

**Usuários do sistema:**

| Tipo          | Permissões                                                                                 |
| ------------- | ------------------------------------------------------------------------------------------ |
| Jogador       | Login, listar jogadores, avaliar (sem auto-avaliação, sem duplicata)                       |
| Administrador | Tudo acima + adicionar/remover jogadores, enviar avisos, encerrar avaliações e gerar times |

**Usuários de teste pré-cadastrados:**

| Nome     | Senha     | Tipo                |
| -------- | --------- | ------------------- |
| admin    | admin123  | Administrador       |
| gestor   | gestor123 | Administrador       |
| Carlos   | senha1    | Jogador (ATACANTE)  |
| Fernanda | senha2    | Jogador (GOLEIRO)   |
| Rodrigo  | senha3    | Jogador (DEFENSOR)  |
| Ana      | senha4    | Jogador (MEIO_CAMPO)|
| Pedro    | senha5    | Jogador (ATACANTE)  |

> Senha mestra `master2025` concede acesso a qualquer conta cadastrada.

---

## Streams

O projeto implementa streams customizados seguindo o **padrão Decorator** da API `java.io`, sem usar `Serializable`.

### `JogadorOutputStream` — serialização

Subclasse de `OutputStream`. Serializa um `Jogador[]` para qualquer destino (`System.out`, `FileOutputStream`, socket TCP).

**Protocolo binário por objeto:**

```
[int: quantidade de objetos]
Para cada objeto:
  [int:    tamanhoEmBytes]   ← tamanho do bloco a seguir
  [int:    id]               4 bytes
  [UTF:    nome]             2 + n bytes
  [UTF:    posicao]          2 + n bytes
  [double: somaNotas]        8 bytes
  [int:    qtdNotas]         4 bytes
```

O campo `tamanhoEmBytes` é calculado serializando o objeto em um `ByteArrayOutputStream` interno antes de escrevê-lo no destino — isso permite ao receptor pular objetos sem precisar conhecer sua estrutura completa.

### `JogadorInputStream` — desserialização

Subclasse de `InputStream`. Lê o mesmo protocolo acima e reconstrói o `Jogador[]`. O `readInt()` inicial de cada objeto descarta `tamanhoEmBytes` (campo de controle) antes de ler os atributos na mesma ordem em que foram escritos.

**Regra crítica de uso:** ambos os streams recebem o `DataInputStream`/`DataOutputStream` **já existente** do socket no construtor. Nunca são fechados dentro do loop de serviço — fechar o stream wrapper fecharia o socket subjacente.

---

## Serialização

### Protocolo binário TCP

Toda a comunicação cliente-servidor usa um protocolo binário customizado sobre TCP com **opcodes de 1 byte** seguidos de payload estruturado:

| Família   | Opcode              | Direção | Payload                                                             |
| --------- | ------------------- | ------- | ------------------------------------------------------------------- |
| Auth      | `0x01` LOGIN_REQ    | C→S     | `[nome: UTF][senha: UTF]`                                           |
| Auth      | `0x02` LOGIN_OK     | S→C     | `[id: int][tipo: byte]`                                             |
| Auth      | `0x03` LOGIN_FAIL   | S→C     | `[motivo: UTF]`                                                     |
| List      | `0x10` LIST_REQ     | C→S     | —                                                                   |
| List      | `0x11` LIST_RESP    | S→C     | payload `JogadorOutputStream`                                       |
| Eval      | `0x20` AVALIAR_REQ  | C→S     | `[idAvaliado: int][nota: double]`                                   |
| Add       | `0x30` ADD_REQ      | C→S     | `[nome: UTF][senha: UTF][posicao: UTF]`                             |
| Remove    | `0x40` REM_REQ      | C→S     | `[id: int]`                                                         |
| Close     | `0x50` ENCERRAR_REQ | C→S     | `[qtdTimes: int]`                                                   |
| Close     | `0x51` ENCERRAR_RESP | S→C     | `[qtd: int]` + N × `[numero: int]` + payload `JogadorOutputStream` |
| Broadcast | `0x60` AVISO_REQ    | C→S     | `[mensagem: UTF]`                                                   |
| Logout    | `0x00` LOGOUT       | C→S     | —                                                                   |

### JSON manual (UDP multicast)

Mensagens UDP usam JSON serializado manualmente pela classe `JsonMensagem` — sem dependências externas. Formato fixo:

```json
{"tipo":"AVISO","de":"admin","mensagem":"Texto aqui","hora":"14:30:00"}
```

Tipos de mensagem multicast:

| `tipo` | Uso |
|--------|-----|
| `"AVISO"` | Aviso de texto livre enviado pelo administrador |
| `"TIMES"` | Times gerados ao encerrar as avaliações (texto formatado multi-linha) |

A serialização escapa `\`, `"`, `\n`, `\r`. A desserialização (`extrair()`) faz o caminho inverso, reconstruindo os caracteres de controle corretamente.

---

## Conexões

### Arquitetura TCP (unicast)

```
Cliente 1 ──┐
Cliente 2 ──┤── ServerSocket :5000 ── aceita() ──► ManipuladorCliente (Thread)
Cliente N ──┘                                              │
                                                   Estado compartilhado
                                                   (ConcurrentHashMap)
```

- **`ServidorMultiThread`** — abre `ServerSocket` na porta `5000`; para cada `accept()` cria um `Thread` com `ManipuladorCliente` como `Runnable`. Threads são *daemon* para encerrar com o processo principal.
- **`ManipuladorCliente`** — trata uma conexão completa: login → loop de serviço (jogador ou admin) → logout/desconexão. Usa `try-with-resources` no socket para garantir fechamento.
- **Estado compartilhado thread-safe:**
  - `ConcurrentHashMap<Integer, Jogador>` — jogadores registrados
  - `ConcurrentHashMap<String, Boolean> avaliacoesFeitas` — chave `"avaliadorId-avaliadoId"` previne duplicatas com `putIfAbsent` (operação atômica)
  - `AtomicBoolean sistemaAberto` — controla janela de avaliações; `compareAndSet(true, false)` garante que apenas o primeiro `ENCERRAR_REQ` gera os times
  - `synchronized(avaliado)` — lock por objeto ao acumular nota, evitando race condition na soma

### Balanceamento de times (*snake draft*)

Jogadores são ordenados por média decrescente e distribuídos em zigue-zague entre os times:

```
Rodada par   → Time 1 → Time 2 → ... → Time N
Rodada ímpar → Time N → ... → Time 2 → Time 1
```

Isso minimiza a diferença de média entre os times sem precisar de busca exaustiva.

---

## Multicasting

### Arquitetura UDP multicast

```
Servidor ──► DatagramSocket ──► grupo 224.0.0.1:5001
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
             ClienteMulticast   ClienteMulticast   ClienteMulticast
             (Thread daemon)    (Thread daemon)    (Thread daemon)
```

- **Endereço de grupo:** `224.0.0.1` (classe D — reservada para multicast IPv4)
- **Porta UDP:** `5001`
- **Sender:** `DatagramSocket` comum — qualquer socket pode enviar para um grupo multicast
- **Receiver:** `MulticastSocket` com `joinGroup()` — necessário para receber pacotes do grupo

### `ClienteMulticast`

Thread *daemon* iniciada logo após o login bem-sucedido. Fica em loop com `setSoTimeout(1000ms)` para checar o flag `volatile boolean ativo` sem bloquear indefinidamente.

Ao receber um pacote, desserializa o JSON e roteia pelo campo `tipo`:

- `"AVISO"` → exibe caixa de uma linha com remetente e hora
- `"TIMES"` → exibe caixa multi-linha com todos os times e médias

O buffer de recepção é de **8192 bytes** para acomodar mensagens de times com muitos jogadores.

### Fluxo de encerramento

```
Admin envia ENCERRAR_REQ
    └─► Servidor gera times (snake draft)
    └─► TCP ENCERRAR_RESP  ──► Admin vê os times na CLI
    └─► UDP AVISO          ──► "Avaliações encerradas!"  (todos os clientes)
    └─► UDP TIMES          ──► Resultado completo        (todos os clientes)
```

---

## Estrutura do Projeto

```
Racha-Distribuidos/
├── src/
│   ├── models/
│   │   ├── Usuario.java          # Classe base com id, nome, senha
│   │   ├── Jogador.java          # POJO com posição, notas, média
│   │   ├── Administrador.java    # POJO com nível de acesso
│   │   └── Time.java             # Container de jogadores (snake draft)
│   ├── streams/
│   │   ├── JogadorOutputStream.java   # Serialização binária customizada
│   │   └── JogadorInputStream.java    # Desserialização binária customizada
│   ├── network/
│   │   ├── Protocolo.java             # Constantes de opcodes e endereços
│   │   ├── ServidorMultiThread.java   # Servidor TCP + emissor UDP multicast
│   │   ├── ManipuladorCliente.java    # Handler por conexão (Runnable)
│   │   ├── ClienteInterativo.java     # CLI do cliente (jogador e admin)
│   │   └── ClienteMulticast.java      # Receptor UDP multicast (daemon)
│   ├── utils/
│   │   └── JsonMensagem.java          # Serialização/desserialização JSON manual
│   └── testes/
│       └── TesteFase5.java            # Testes automatizados do protocolo TCP
├── compile.bat    # Compila todos os fontes para out/
├── servidor.bat   # Inicia ServidorMultiThread
└── cliente.bat    # Inicia ClienteInterativo
```

## Como compilar e executar

**Pré-requisito:** JDK 17+ instalado.

```bat
:: Compilar
compile.bat

:: Iniciar servidor (manter aberto)
servidor.bat

:: Iniciar um ou mais clientes (em terminais separados)
cliente.bat
```

## Fases de Desenvolvimento

- [x] Fase 1 — Setup inicial e estrutura de diretórios
- [x] Fase 2 — Modelagem de dados (POJOs) e interfaces
- [x] Fase 3 — Streams customizados (`JogadorOutputStream` / `JogadorInputStream`)
- [x] Fase 4 — Sockets básicos e serialização manual (protocolo binário TCP)
- [x] Fase 5 — Servidor multi-threaded com CLI para jogador e administrador
- [x] Fase 6 — Multicast UDP, JSON manual, senha mestra e envio de times ao encerrar
