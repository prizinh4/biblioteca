# TP Biblioteca - Fase 2

Aplicacao Java com arquitetura em camadas (model, dao, controller), persistencia em disco e relacionamento 1:N com Hash Extensivel.

## Estrutura

- `App.java`: inicializa o servidor web local
- `src/model`: entidades (`Livro`, `Exemplar`)
- `src/dao`: persistencia, indices e hash extensivel
- `src/controller`: camada de controle dos CRUDs
- `src/view`: front-end web e rotas HTTP (`ServidorWeb`)
- `data/`: arquivos de dados e indices

## Requisitos atendidos (Fase 2)

- CRUD completo de Livro e Exemplar
- Indice primario para ambas as tabelas (`IndiceDireto`)
- Relacionamento 1:N Livro -> Exemplar com Hash Extensivel
- Ordenacao externa por atributo (`titulo` de Livro)
- Arvore B+ com insercao e busca
- Persistencia em disco entre execucoes
- Exclusao logica
- Validacao de entradas e erros comuns

## Compilacao

No Windows PowerShell, na raiz do projeto:

```powershell
javac -encoding UTF-8 -d . App.java src/model/*.java src/dao/*.java src/controller/*.java src/view/*.java
```

## Execucao

```powershell
java App
```

Abra no navegador:

`http://localhost:8080`

## Persistencia

Arquivos gerados na pasta `data/`:

- `livros.db` e `livros.idx`
- `exemplares.db` e `exemplares.idx`
- `hash/livro_exemplar.dir` e `hash/livro_exemplar.bkt`
- `livro_exemplar.rel`
- `livros_ordenado_titulo.db`
- `sort_tmp/` (temporarios da ordenacao externa)

## Observacoes

- A exclusao de livro faz exclusao logica em cascata dos exemplares vinculados.
- As validacoes incluem:
  - ISBN duplicado
  - codigo de patrimonio duplicado
  - FK de livro inexistente
  - busca/exclusao de chave inexistente
