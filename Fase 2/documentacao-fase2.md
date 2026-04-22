# Documentacao Tecnica - Fase 2

## 1. Decisoes de projeto

- Arquitetura em camadas: `model`, `dao`, `controller` e interface de menu em `App.java`.
- Persistencia com `RandomAccessFile` para dados e indices.
- Exclusao logica por lapide (`boolean`) no inicio de cada registro.
- Relacao 1:N implementada com Hash Extensivel:
  - hash mapeia `livroId -> ponteiro da cabeca` da lista encadeada de exemplares.
  - arquivo de relacao guarda nos (`exemplarId`, `prox`).

## 2. Estruturas de dados e indice em disco

### 2.1 Registros principais

- `Livro` em `data/livros.db`
- `Exemplar` em `data/exemplares.db`

Formato geral por registro:

1. `lapide` (`boolean`)
2. `tamanho` (`int`)
3. campos serializados (incluindo `int` de tamanho para strings)

### 2.2 Indices primarios

- `data/livros.idx`
- `data/exemplares.idx`

Formato de cada entrada:

- `id` (`int`)
- `posicao` (`long`)

### 2.3 Hash extensivel (relacionamento)

Arquivos:

- Diretorio: `data/hash/livro_exemplar.dir`
- Buckets: `data/hash/livro_exemplar.bkt`

Diretorio:

- `profundidadeGlobal` (`int`)
- vetor de ponteiros para buckets (`long`)

Bucket:

- `profundidadeLocal` (`int`)
- `quantidade` (`int`)
- pares (`chave:int`, `valor:long`) ate capacidade fixa

Operacoes implementadas:

- insercao com split de bucket
- duplicacao de diretorio
- busca
- remocao

### 2.4 Arquivo de relacionamento 1:N

- `data/livro_exemplar.rel`

No da lista encadeada:

- `exemplarId` (`int`)
- `prox` (`long`)

Cada `livroId` aponta para a cabeca da sua lista via hash extensivel.

## 3. Integridade referencial

- Insercao de exemplar valida existencia do livro pai.
- Exclusao de livro remove logicamente exemplares vinculados (cascata logica).
- Atualizacao de exemplar que troca livro pai atualiza a lista de relacionamento.

## 4. Validacoes

- ISBN duplicado: bloqueado no `LivroDAO`.
- Codigo de patrimonio duplicado: bloqueado no `ExemplarDAO`.
- Busca por chave inexistente: retorno nulo com mensagem no menu.
- Exclusao de inexistente: retorno falso com mensagem.
- FK inexistente no exemplar: erro de validacao.

## 5. Front-end

- Interface obrigatoria implementada como front-end web (HTML/CSS/JS) acessada por navegador.
- O `App.java` inicializa um servidor HTTP local em `http://localhost:8080`.
- A tela web expoe operacoes para:
  - Livro CRUD,
  - Exemplar CRUD,
  - consulta do relacionamento 1:N,
  - ordenacao externa,
  - insercao e busca na Arvore B+.

## 6. Ordenacao externa (atributo: titulo)

- Implementada em `OrdenacaoExternaLivros`.
- Estrategia:
  1. leitura dos registros ativos de Livro,
  2. geracao de particoes ordenadas em blocos limitados (`data/sort_tmp/particao_*.bin`),
  3. intercalação k-way por fila de prioridade,
  4. escrita do resultado em `data/livros_ordenado_titulo.db`.
- O atributo usado para ordenacao e `titulo`.

## 7. Arvore B+ (implementacao inicial)

- Implementada em `ArvoreBMaisIndice` (ordem configuravel; uso atual com ordem 4).
- Operacoes implementadas:
  - `inserir(int chave, long valor)`
  - `buscar(int chave)`
- Estruturas:
  - nos internos com chaves e filhos,
  - nos folha com pares chave/valor e encadeamento entre folhas.
- Regras implementadas:
  - split de folha,
  - split de no interno,
  - promocao de chave e crescimento de raiz quando necessario.
- Demonstracao no front-end web:
  - insercao manual de chave/valor,
  - busca por chave,
  - carga inicial com IDs de Livro e valor = posicao no arquivo de dados.

## 8. Formulario (respostas)

a) Qual a estrutura usada para representar os registros?

- Registros variaveis em arquivo binario com lapide, tamanho e campos serializados.

b) Como atributos multivalorados do tipo string foram tratados?

- Em `Livro`, atributos como autores/categorias ficam em string unica delimitada por usuario; serializacao UTF-8 com tamanho.

c) Como foi implementada a exclusao logica?

- Campo lapide (`boolean`) no inicio do registro. `true` indica removido.

d) Alem das PKs, quais outras chaves foram utilizadas nesta etapa?

- `livroId` (FK em Exemplar) para relacionamento 1:N.
- ISBN e codigo de patrimonio tratados como unicos por regra de validacao.

e) Como a estrutura (hash) foi implementada para cada chave de pesquisa?

- PKs: indice direto (`id -> posicao`).
- Relacionamento 1:N: hash extensivel (`livroId -> ponteiro cabeca`) + lista encadeada de filhos.

f) Como foi implementado o relacionamento 1:N (explique a logica da navegacao entre registros e integridade referencial)?

- Ao inserir exemplar, grava-se um novo no em `livro_exemplar.rel` apontando para a cabeca antiga da lista do livro.
- O hash atualiza a cabeca para o novo no.
- Para navegar, busca-se cabeca via hash e percorre-se os nos lendo cada `exemplarId`.
- Integridade: so insere exemplar se livro existir; exclusao de livro faz cascata logica nos filhos.

g) Como os indices sao persistidos em disco? (formato, atualizacao, sincronizacao com os dados).

- `IndiceDireto` grava pares (`int id`, `long pos`) em arquivo binario.
- A cada create/update/delete no DAO, o indice e atualizado imediatamente.
- No hash extensivel, diretorio e buckets sao regravados apos splits/insercoes/remocoes.

h) Como esta estruturado o projeto no GitHub (pastas, modulos, arquitetura)?

- Raiz com `App.java`, pasta `src/` separada em `model`, `dao`, `controller`, e pasta `data/` para persistencia.
- Arquitetura em camadas com responsabilidade separada.

## 9. Itens adicionais da Fase 2

- Ordenacao externa: implementada e acessivel no front-end web.
- Arvore B+: implementacao inicial com insercao e busca, acessivel no front-end web.
