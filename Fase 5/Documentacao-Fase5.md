# Documentacao Técnica - Fase 5 (Criptografia e Login)

## 1. Decisões de projeto

- Adicionada camada de segurança (`seguranca`) com autenticacao por
  usuario/senha antes do acesso ao sistema.
- Persistência das credenciais em arquivo binário próprio
  (`data/credenciais.db`), separado dos dados do domínio (livros,
  exemplares, leitores, reservas).
- Todo login e senha gravados em disco passam por cifra XOR antes da
  escrita, nunca em texto puro.
- Controle de sessão via cookie (`sessao=<token>`), mantido em memória no
  servidor (`Set<String> sessoes`).
- Acesso as rotas `/` e `/api/*` (exceto login/cadastro) exige sessão
  valida; caso contrário, redireciona para `/login`.

## 2. Estruturas de dados e arquivo de credenciais

### 2.1 Arquivo de credenciais

- `data/credenciais.db`

Formato por registro:

1. `tamanhoLoginCifrado` (`int`)
2. `loginCifrado` (`bytes`)
3. `tamanhoSenhaCifrada` (`int`)
4. `senhaCifrada` (`bytes`)


### 2.2 Cifra XOR (`CriptografiaXOR`)

- Chave fixa: `"BibliotecaAEDS3"` (UTF-8).
- `aplicarXOR(dados)`: para cada byte `i`, calcula
  `dados[i] ^ CHAVE[i % CHAVE.length]`.
- `cifrar(texto)`: converte string para bytes (UTF-8) e aplica XOR.
- `decifrar(dados)`: aplica XOR novamente sobre os bytes cifrados e
  reconverte para string (XOR e reversivel com a mesma chave).
- `bytesParaHex` / `hexParaBytes`: utilitarios de conversao para
  depuracao/exibicao.

### 2.3 Gerenciamento de logins (`GerenciadorLogin`)

- `inicializar()`: cria a pasta `data/` e, se o arquivo de credenciais nao
  existir, grava um usuario padrao (`admin` / `admin123`).
- `carregarTodos()`: le todos os registros do arquivo, decifrando cada
  campo, retornando lista de pares (usuario, senha).
- `salvarTodos(usuarios)`: regrava o arquivo do zero com todos os usuarios,
  cifrando cada campo.
- `validar(usuario, senha)`: percorre todos os usuarios cadastrados e
  retorna verdadeiro se houver correspondencia exata.
- `existeUsuario(usuario)`: verifica se o login ja esta cadastrado.
- `cadastrar(usuario, senha)`: valida campos obrigatorios, garante que o
  login nao existe e adiciona o novo usuario ao arquivo.

## 3. Integridade e validacoes

- Cadastro bloqueia login duplicado.
- Cadastro bloqueia usuario ou senha em branco.
- Login invalido (usuario/senha incorretos) retorna a propria tela de login
  com mensagem de erro, sem expor qual dos dois campos esta errado.
- Rotas protegidas (`/`, `/api/livros`, `/api/exemplares`, `/api/leitores`,
  `/api/reservas`, etc.) verificam cookie de sessão antes de processar
  qualquer operacao; sem sessão valida, retornam redirecionamento
  (pagina HTML) ou erro 401 (rotas de API).

## 4. Front-end (telas de autenticação)

- **`/login`** (GET): formulário com campos "login" e senha (sem valores ou
  placeholders sugerindo credenciais).
- **`/api/login`** (POST): valida usuario/senha via `GerenciadorLogin`;
  em caso de sucesso, gera token de sessão (`UUID`), define cookie
  `sessao` (`HttpOnly`) e redireciona para `/`.
- **`/api/logout`** (POST): remove o token da sessão e do cookie,
  redirecionando para `/login`.

## 5. Como verificar a cifragem das credenciais

Com o servidor ja executado ao menos uma vez (arquivo
`data/credenciais.db` criado), no PowerShell:

```powershell
certutil -dump data/credenciais.db
```

ou

```powershell
Format-Hex data/credenciais.db
```

O conteudo exibido é binario/hexadecimal, sem nenhum login ou senha em
texto legível, confirmando que os dados foram cifrados com XOR antes da
gravação.

## 7. Credenciais para teste

- **Login padrão (criado automaticamente na primeira execução):**
  - usuario: `admin`
  - senha: `admin123`

## 8. Itens adicionais da Fase 5

- Suporte a login fixo (admin).
- Sessao por cookie, com logout funcional.