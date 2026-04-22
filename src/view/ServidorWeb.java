package view;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import controller.ExemplarController;
import controller.LivroController;
import dao.ArvoreBMaisIndice;
import dao.ExemplarDAO;
import dao.IndiceLivroExemplar;
import dao.LivroDAO;
import dao.OrdenacaoExternaLivros;
import model.Exemplar;
import model.Livro;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServidorWeb {
    private final HttpServer servidor;
    private final LivroDAO livroDAO;
    private final ExemplarDAO exemplarDAO;
    private final LivroController livroController;
    private final ExemplarController exemplarController;
    private final OrdenacaoExternaLivros ordenacaoExterna;
    private final ArvoreBMaisIndice arvoreBMais;

    public ServidorWeb(int porta) throws IOException {
        this.livroDAO = new LivroDAO();
        this.exemplarDAO = new ExemplarDAO(livroDAO);
        this.livroController = new LivroController(livroDAO);
        this.exemplarController = new ExemplarController(exemplarDAO);
        this.ordenacaoExterna = new OrdenacaoExternaLivros(livroDAO);
        this.arvoreBMais = new ArvoreBMaisIndice(4);

        this.servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        registrarRotas();
        
        // Carrega índices na Árvore B+ para otimizar buscas de livro por ID
        carregarArvoreBMais();
    }
    
    private void carregarArvoreBMais() {
        try {
            List<Livro> livros = livroController.listarTodos();
            for (Livro livro : livros) {
                Long posicao = livroDAO.getPosicaoPorId(livro.getId());
                if (posicao != null) {
                    arvoreBMais.inserir(livro.getId(), posicao);
                }
            }
        } catch (Exception e) {
            System.err.println("Aviso: erro ao carregar Árvore B+. Busca por ID continuará funcional.");
        }
    }

    public void iniciar() {
        servidor.start();
    }

    private void registrarRotas() {
        servidor.createContext("/", this::tratarInicio);
        servidor.createContext("/api/livros", this::tratarLivros);
        servidor.createContext("/api/exemplares", this::tratarExemplares);
        servidor.createContext("/api/relacao", this::tratarRelacao);
        servidor.createContext("/api/livros-ordenado", this::tratarLivrosOrdenado);
        // Rotas técnicas para demonstração (modo debug)
        servidor.createContext("/api/ordenacao", this::tratarOrdenacao);
        servidor.createContext("/api/arvore-bmais", this::tratarArvoreBMais);
    }

    private void tratarInicio(HttpExchange requisicao) throws IOException {
        if (!"GET".equalsIgnoreCase(requisicao.getRequestMethod())) {
            enviarResposta(requisicao, 405, "text/plain", "Metodo nao permitido.");
            return;
        }
        enviarResposta(requisicao, 200, "text/html; charset=utf-8", gerarHtml());
    }

    private void tratarLivros(HttpExchange requisicao) throws IOException {
        try {
            String caminho = requisicao.getRequestURI().getPath();
            String metodo = requisicao.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/livros")) {
                List<Livro> livros = livroController.listarTodos();
                StringBuilder texto = new StringBuilder();
                if (livros.isEmpty()) texto.append("Sem livros cadastrados.\n");
                for (Livro livro : livros) texto.append(livro).append("\n");
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/livros")) {
                Map<String, String> parametros = lerFormulario(requisicao);
                Livro livro = paraLivro(0, parametros);
                int id = livroController.criar(livro);
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Livro inserido. ID=" + id);
                return;
            }

            if (caminho.startsWith("/api/livros/")) {
                int id = Integer.parseInt(caminho.substring("/api/livros/".length()));

                if ("GET".equalsIgnoreCase(metodo)) {
                    Livro livro = livroController.buscarPorId(id);
                    enviarResposta(requisicao, 200, "text/plain; charset=utf-8", livro == null ? "Livro nao encontrado." : livro.toString());
                    return;
                }

                if ("PUT".equalsIgnoreCase(metodo)) {
                    Livro atual = livroController.buscarPorId(id);
                    if (atual == null) {
                        enviarResposta(requisicao, 404, "text/plain; charset=utf-8", "Livro inexistente.");
                        return;
                    }
                    Map<String, String> parametros = lerFormulario(requisicao);
                    livroController.atualizar(paraLivro(id, parametros));
                    enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Livro atualizado.");
                    return;
                }

                if ("DELETE".equalsIgnoreCase(metodo)) {
                    Livro atual = livroController.buscarPorId(id);
                    if (atual == null) {
                        enviarResposta(requisicao, 404, "text/plain; charset=utf-8", "Livro inexistente.");
                        return;
                    }
                    exemplarController.excluirPorLivro(id);
                    livroController.excluir(id);
                    enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Livro e exemplares vinculados excluidos logicamente.");
                    return;
                }
            }

            enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private void tratarExemplares(HttpExchange requisicao) throws IOException {
        try {
            String caminho = requisicao.getRequestURI().getPath();
            String metodo = requisicao.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/exemplares")) {
                List<Exemplar> exemplares = exemplarController.listarTodos();
                StringBuilder texto = new StringBuilder();
                if (exemplares.isEmpty()) texto.append("Sem exemplares cadastrados.\n");
                for (Exemplar exemplar : exemplares) texto.append(formatarExemplar(exemplar)).append("\n");
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/exemplares")) {
                Map<String, String> parametros = lerFormulario(requisicao);
                Exemplar exemplar = paraExemplar(0, parametros);
                int id = exemplarController.criar(exemplar);
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Exemplar inserido. ID=" + id);
                return;
            }

            if (caminho.startsWith("/api/exemplares/")) {
                int id = Integer.parseInt(caminho.substring("/api/exemplares/".length()));

                if ("GET".equalsIgnoreCase(metodo)) {
                    Exemplar exemplar = exemplarController.buscarPorId(id);
                    enviarResposta(requisicao, 200, "text/plain; charset=utf-8", exemplar == null ? "Exemplar nao encontrado." : formatarExemplar(exemplar));
                    return;
                }

                if ("PUT".equalsIgnoreCase(metodo)) {
                    Exemplar atual = exemplarController.buscarPorId(id);
                    if (atual == null) {
                        enviarResposta(requisicao, 404, "text/plain; charset=utf-8", "Exemplar inexistente.");
                        return;
                    }
                    Map<String, String> parametros = lerFormulario(requisicao);
                    exemplarController.atualizar(paraExemplar(id, parametros));
                    enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Exemplar atualizado.");
                    return;
                }

                if ("DELETE".equalsIgnoreCase(metodo)) {
                    boolean excluiu = exemplarController.excluir(id);
                    enviarResposta(
                            requisicao,
                            excluiu ? 200 : 404,
                            "text/plain; charset=utf-8",
                            excluiu ? "Exemplar excluido logicamente." : "Exemplar inexistente."
                    );
                    return;
                }
            }

            enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private void tratarRelacao(HttpExchange requisicao) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(requisicao.getRequestMethod())) {
                enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Metodo nao permitido.");
                return;
            }

            String caminho = requisicao.getRequestURI().getPath();
            if (!caminho.startsWith("/api/relacao/")) {
                enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Informe /api/relacao/{livroId}.");
                return;
            }

            int livroId = Integer.parseInt(caminho.substring("/api/relacao/".length()));
            List<Exemplar> lista = exemplarController.listarPorLivro(livroId);
            StringBuilder texto = new StringBuilder();
            texto.append("Livro ID: ").append(livroId).append("\n");
            if (lista.isEmpty()) texto.append("Nenhum exemplar para o livro informado.\n");
            for (Exemplar exemplar : lista) texto.append(formatarExemplar(exemplar)).append("\n");
            enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto.toString());
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private void tratarLivrosOrdenado(HttpExchange requisicao) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(requisicao.getRequestMethod())) {
                enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Metodo nao permitido.");
                return;
            }

            List<Livro> ordenados = ordenacaoExterna.ordenarPorTitulo(3);
            StringBuilder texto = new StringBuilder();
            texto.append("Livros ordenados por título:\n\n");
            if (ordenados.isEmpty()) {
                texto.append("Nenhum livro cadastrado.\n");
            } else {
                for (Livro livro : ordenados) {
                    texto.append(livro).append("\n");
                }
            }
            enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto.toString());
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private void tratarOrdenacao(HttpExchange requisicao) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(requisicao.getRequestMethod())) {
                enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Metodo nao permitido.");
                return;
            }

            Map<String, String> consulta = lerConsulta(requisicao.getRequestURI().getRawQuery());
            int bloco = Integer.parseInt(consulta.getOrDefault("bloco", "3"));
            List<Livro> ordenados = ordenacaoExterna.ordenarPorTitulo(bloco);

            StringBuilder texto = new StringBuilder();
            texto.append("Ordenacao concluida. Arquivo: data/livros_ordenado_titulo.db\n");
            texto.append("Bloco utilizado: ").append(bloco).append("\n\n");
            for (Livro livro : ordenados) {
                texto.append("ID=").append(livro.getId()).append(" | Titulo=").append(livro.getTitulo()).append("\n");
            }
            enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto.toString());
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private void tratarArvoreBMais(HttpExchange requisicao) throws IOException {
        try {
            String caminho = requisicao.getRequestURI().getPath();
            String metodo = requisicao.getRequestMethod();

            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/inserir")) {
                Map<String, String> parametros = lerFormulario(requisicao);
                int chave = Integer.parseInt(parametros.get("chave"));
                long valor = Long.parseLong(parametros.get("valor"));
                arvoreBMais.inserir(chave, valor);
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Inserido na Arvore B+.");
                return;
            }

            if ("GET".equalsIgnoreCase(metodo) && caminho.startsWith("/api/arvore-bmais/buscar/")) {
                int chave = Integer.parseInt(caminho.substring("/api/arvore-bmais/buscar/".length()));
                Long valor = arvoreBMais.buscar(chave);
                String texto = valor == null
                    ? "--- BUSCA ARVORE B+ ---\nChave: " + chave + "\nResultado: Chave nao encontrada."
                    : "--- BUSCA ARVORE B+ ---\nChave: " + chave + "\nValor encontrado: " + valor;
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", texto);
                return;
            }

            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/carga")) {
                List<Livro> livros = livroController.listarTodos();
                int total = 0;
                for (Livro livro : livros) {
                    Long posicao = livroDAO.getPosicaoPorId(livro.getId());
                    if (posicao != null) {
                        arvoreBMais.inserir(livro.getId(), posicao);
                        total++;
                    }
                }
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", "Carga inicial na Arvore B+ concluida. Chaves: " + total);
                return;
            }

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/depurar")) {
                enviarResposta(requisicao, 200, "text/plain; charset=utf-8", arvoreBMais.depurarEstrutura());
                return;
            }

            enviarResposta(requisicao, 405, "text/plain; charset=utf-8", "Operacao da Arvore B+ nao suportada.");
        } catch (Exception erro) {
            enviarResposta(requisicao, 400, "text/plain; charset=utf-8", "Erro: " + erro.getMessage());
        }
    }

    private Livro paraLivro(int id, Map<String, String> parametros) {
        String titulo = parametros.getOrDefault("titulo", "");
        String isbn = parametros.getOrDefault("isbn", "");
        String editora = parametros.getOrDefault("editora", "");
        int edicao = Integer.parseInt(parametros.getOrDefault("edicao", "1"));
        float preco = Float.parseFloat(parametros.getOrDefault("preco", "0").replace(',', '.'));
        String autores = parametros.getOrDefault("autores", "");
        String categorias = parametros.getOrDefault("categorias", "");

        long dataPublicacao;
        try {
            String data = parametros.getOrDefault("data", "");
            dataPublicacao = new SimpleDateFormat("dd/MM/yyyy").parse(data).getTime();
        } catch (Exception erro) {
            dataPublicacao = System.currentTimeMillis();
        }

        return new Livro(id, isbn, titulo, preco, dataPublicacao, categorias, autores, edicao, editora);
    }

    private Exemplar paraExemplar(int id, Map<String, String> parametros) {
        int livroId = Integer.parseInt(parametros.getOrDefault("livroId", "0"));
        String codigoPatrimonio = parametros.getOrDefault("codigoPatrimonio", "");
        String estado = parametros.getOrDefault("estado", "NOVO");
        return new Exemplar(id, livroId, codigoPatrimonio, estado);
    }

    private String formatarExemplar(Exemplar exemplar) {
        return """
--- DADOS DO EXEMPLAR ---
ID: %d | Livro ID: %d
Codigo Patrimonio: %s
Estado: %s
""".formatted(
                exemplar.getId(),
                exemplar.getLivroId(),
                exemplar.getCodigoPatrimonio(),
                exemplar.getEstado()
        );
    }

    private Map<String, String> lerFormulario(HttpExchange requisicao) throws IOException {
        String corpo = new String(requisicao.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return lerConsulta(corpo);
    }

    private Map<String, String> lerConsulta(String consultaBruta) {
        Map<String, String> mapa = new HashMap<>();
        if (consultaBruta == null || consultaBruta.isBlank()) return mapa;

        String[] pares = consultaBruta.split("&");
        for (String par : pares) {
            String[] chaveValor = par.split("=", 2);
            String chave = decodificar(chaveValor[0]);
            String valor = chaveValor.length > 1 ? decodificar(chaveValor[1]) : "";
            mapa.put(chave, valor);
        }
        return mapa;
    }

    private String decodificar(String texto) {
        return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    }

    private void enviarResposta(HttpExchange requisicao, int status, String tipoConteudo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        requisicao.getResponseHeaders().set("Content-Type", tipoConteudo);
        requisicao.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = requisicao.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private String gerarHtml() {
        return """
<!doctype html>
<html lang=\"pt-BR\">
<head>
  <meta charset=\"UTF-8\" />
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
  <title>Biblioteca Fase 2</title>
  <style>
    :root {
      --bg: #f8fafc;
      --card: #ffffff;
      --ink: #0f172a;
      --accent: #0ea5e9;
      --muted: #64748b;
    }

    body {
      margin: 0;
      font-family: Segoe UI, Tahoma, sans-serif;
      background: linear-gradient(120deg, #e2e8f0, #f8fafc);
      color: var(--ink);
    }

    .container {
      max-width: 1100px;
      margin: 24px auto;
      padding: 0 16px;
    }

    h1 {
      margin: 0 0 16px;
    }

    .grade {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 12px;
    }

    .cartao {
      background: var(--card);
      border-radius: 12px;
      box-shadow: 0 6px 20px rgba(2, 6, 23, .08);
      padding: 14px;
    }

    .linha {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 8px;
    }

    input {
      padding: 8px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      min-width: 120px;
    }

    button {
      border: 0;
      border-radius: 8px;
      background: var(--accent);
      color: #fff;
      padding: 8px 12px;
      cursor: pointer;
    }

    button.secundario {
      background: #334155;
    }

    pre {
      background: #0b1220;
      color: #dbeafe;
      min-height: 220px;
      padding: 12px;
      border-radius: 10px;
      overflow: auto;
    }

    .legenda {
      color: var(--muted);
      font-size: 13px;
    }
  </style>
</head>
<body>
  <div class=\"container\">
    <h1>Biblioteca</h1>
    <p class=\"legenda\">CRUD de Livros e Exemplares com Índices e Relacionamento 1:N.</p>

    <div class=\"grade\">
      <div class=\"cartao\">
        <h3>Livro</h3>
        <div class=\"linha\"><input id=\"livro-id\" placeholder=\"id\"><button onclick=\"buscarLivro()\">Buscar</button><button onclick=\"listarExemplaresdoLivro()\" class=\"secundario\">Ver Exemplares</button><button onclick=\"excluirLivro()\" class=\"secundario\">Excluir</button></div>
        <div class=\"linha\"><input id=\"livro-titulo\" placeholder=\"titulo\"><input id=\"livro-isbn\" placeholder=\"isbn\"></div>
        <div class=\"linha\"><input id=\"livro-editora\" placeholder=\"editora\"><input id=\"livro-edicao\" placeholder=\"edicao\"><input id=\"livro-preco\" placeholder=\"preco\"></div>
        <div class=\"linha\"><input id=\"livro-data\" placeholder=\"dd/MM/yyyy\"><input id=\"livro-autores\" placeholder=\"autores\"><input id=\"livro-categorias\" placeholder=\"categorias\"></div>
        <div class=\"linha\"><button onclick=\"inserirLivro()\">Inserir</button><button onclick=\"atualizarLivro()\">Atualizar</button><button onclick=\"listarLivros()\" class=\"secundario\">Listar</button><button onclick=\"listarLivrosOrdenado()\" class=\"secundario\">Listar por Título</button></div>
      </div>

      <div class=\"cartao\">
        <h3>Exemplar</h3>
        <div class=\"linha\"><input id=\"exemplar-id\" placeholder=\"id\"><button onclick=\"buscarExemplar()\">Buscar</button><button onclick=\"excluirExemplar()\" class=\"secundario\">Excluir</button></div>
        <div class=\"linha\"><input id=\"exemplar-livro\" placeholder=\"livroId\"><input id=\"exemplar-codigo\" placeholder=\"codigoPatrimonio\"><input id=\"exemplar-estado\" placeholder=\"estado\"></div>
        <div class=\"linha\"><button onclick=\"inserirExemplar()\">Inserir</button><button onclick=\"atualizarExemplar()\">Atualizar</button><button onclick=\"listarExemplares()\" class=\"secundario\">Listar</button></div>
      </div>


    </div>

    <details style=\"margin-top:20px; padding:12px; background:#f0f0f0; border-radius:8px;\">
      <summary style=\"cursor:pointer; font-weight:bold; color:#333;\">Modo Técnico (Demonstração de Estruturas)</summary>
      <div class=\"grade\" style=\"margin-top:12px;\">
        <div class=\"cartao\">
          <h3>Ordenação Externa</h3>
          <div class=\"linha\"><input id=\"ordenacao-bloco\" placeholder=\"bloco\" value=\"3\"><button onclick=\"ordenarExternamente()\">Ordenar por título (técnico)</button></div>
        </div>

        <div class=\"cartao\">
          <h3>Árvore B+</h3>
          <div class=\"linha\"><input id=\"arvore-chave\" placeholder=\"chave\"><input id=\"arvore-valor\" placeholder=\"valor\"><button onclick=\"inserirArvore()\">Inserir</button></div>
          <div class=\"linha\"><input id=\"arvore-busca\" placeholder=\"chave busca\"><button onclick=\"buscarArvore()\">Buscar</button><button onclick=\"depurarArvore()\" class=\"secundario\">Debug</button></div>
        </div>
        <div class="cartao">
          <h3>Relação 1:N (Técnico)</h3>
          <div class="linha"><input id="relacao-livro" placeholder="livroId"><button onclick="listarRelacao()">Listar Exemplares do Livro</button></div>
        </div>      </div>
    </details>

    <h3>Sa&iacute;da</h3>
    <pre id=\"saida\"></pre>
  </div>

  <script>
    const saida = document.getElementById('saida');
    const codificar = obj => new URLSearchParams(obj).toString();
    async function requisicao(url, opcoes={}) { const resposta = await fetch(url, opcoes); const texto = await resposta.text(); saida.textContent = texto; }

    function valor(id){ return document.getElementById(id).value || ''; }
    function corpoLivro(){ return codificar({ titulo:valor('livro-titulo'), isbn:valor('livro-isbn'), editora:valor('livro-editora'), edicao:valor('livro-edicao'), preco:valor('livro-preco'), data:valor('livro-data'), autores:valor('livro-autores'), categorias:valor('livro-categorias')}); }
    function corpoExemplar(){ return codificar({ livroId:valor('exemplar-livro'), codigoPatrimonio:valor('exemplar-codigo'), estado:valor('exemplar-estado')}); }

    function listarLivros(){ requisicao('/api/livros'); }
    function buscarLivro(){ requisicao('/api/livros/'+valor('livro-id')); }
    function inserirLivro(){ requisicao('/api/livros',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:corpoLivro()}); }
    function atualizarLivro(){ requisicao('/api/livros/'+valor('livro-id'),{method:'PUT',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:corpoLivro()}); }
    function excluirLivro(){ requisicao('/api/livros/'+valor('livro-id'),{method:'DELETE'}); }
    function listarLivrosOrdenado(){ requisicao('/api/livros-ordenado'); }

    function listarExemplares(){ requisicao('/api/exemplares'); }
    function buscarExemplar(){ requisicao('/api/exemplares/'+valor('exemplar-id')); }
    function inserirExemplar(){ requisicao('/api/exemplares',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:corpoExemplar()}); }
    function atualizarExemplar(){ requisicao('/api/exemplares/'+valor('exemplar-id'),{method:'PUT',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:corpoExemplar()}); }
    function excluirExemplar(){ requisicao('/api/exemplares/'+valor('exemplar-id'),{method:'DELETE'}); }

    function listarExemplaresdoLivro(){ requisicao('/api/relacao/'+valor('livro-id')); }
    function listarRelacao(){ requisicao('/api/relacao/'+valor('relacao-livro')); }
    function ordenarExternamente(){ requisicao('/api/ordenacao?bloco='+encodeURIComponent(valor('ordenacao-bloco'))); }

    function inserirArvore(){ requisicao('/api/arvore-bmais/inserir',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:codificar({chave:valor('arvore-chave'),valor:valor('arvore-valor')})}); }
    function buscarArvore(){ requisicao('/api/arvore-bmais/buscar/'+valor('arvore-busca')); }
    function depurarArvore(){ requisicao('/api/arvore-bmais/depurar'); }
  </script>
</body>
</html>
""";
    }
}
