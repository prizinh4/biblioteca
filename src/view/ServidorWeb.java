package view;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import busca.BoyerMoore;
import busca.KMP;
import compression.BackupManager;
import controller.ExemplarController;
import controller.LeitorController;
import controller.LivroController;
import controller.ReservaController;
import dao.ArvoreBMaisIndice;
import dao.ExemplarDAO;
import dao.LeitorDAO;
import dao.LivroDAO;
import dao.OrdenacaoExternaLivros;
import dao.ReservaDAO;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import model.Exemplar;
import model.Leitor;
import model.Livro;
import model.Reserva;
import seguranca.GerenciadorLogin;

public class ServidorWeb {
    private final HttpServer servidor;
    private final LivroDAO livroDAO;
    private final ExemplarDAO exemplarDAO;
    private final LeitorDAO leitorDAO;
    private final ReservaDAO reservaDAO;
    private final LivroController livroController;
    private final ExemplarController exemplarController;
    private final LeitorController leitorController;
    private final ReservaController reservaController;
    private final OrdenacaoExternaLivros ordenacaoExterna;
    private final ArvoreBMaisIndice arvoreBMais;
    private final BackupManager backupManager;
    private final Set<String> sessoes = new HashSet<>();

    public ServidorWeb(int porta) throws IOException {
        this.livroDAO = new LivroDAO();
        this.exemplarDAO = new ExemplarDAO(livroDAO);
        this.leitorDAO = new LeitorDAO();
        this.reservaDAO = new ReservaDAO();
        this.livroController = new LivroController(livroDAO, reservaDAO);
        this.exemplarController = new ExemplarController(exemplarDAO);
        this.leitorController = new LeitorController(leitorDAO, reservaDAO);
        this.reservaController = new ReservaController();
        this.ordenacaoExterna = new OrdenacaoExternaLivros(livroDAO);
        this.arvoreBMais = new ArvoreBMaisIndice(4);
        this.backupManager = new BackupManager();

        this.servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        GerenciadorLogin.inicializar();
        registrarRotas();
        carregarArvoreBMais();
    }

    private void carregarArvoreBMais() {
        try {
            List<Livro> livros = livroController.listarTodos();
            for (Livro livro : livros) {
                Long posicao = livroDAO.getPosicaoPorId(livro.getId());
                if (posicao != null) arvoreBMais.inserir(livro.getId(), posicao);
            }
        } catch (Exception e) {
            System.err.println("Aviso: erro ao carregar Arvore B+.");
        }
    }

    public void iniciar() {
        servidor.start();
    }

    private void registrarRotas() {
        servidor.createContext("/", this::tratarInicio);
        servidor.createContext("/login", this::tratarLogin);
        servidor.createContext("/cadastro", this::tratarCadastro);
        servidor.createContext("/api/login", this::tratarApiLogin);
        servidor.createContext("/api/cadastro", this::tratarApiCadastro);
        servidor.createContext("/api/logout", this::tratarApiLogout);
        servidor.createContext("/api/livros", this::tratarLivros);
        servidor.createContext("/api/exemplares", this::tratarExemplares);
        servidor.createContext("/api/leitores", this::tratarLeitores);
        servidor.createContext("/api/reservas", this::tratarReservas);
        servidor.createContext("/api/relacao", this::tratarRelacao);
        servidor.createContext("/api/livros-ordenado", this::tratarLivrosOrdenado);
        servidor.createContext("/api/ordenacao", this::tratarOrdenacao);
        servidor.createContext("/api/arvore-bmais", this::tratarArvoreBMais);
        servidor.createContext("/api/backup/huffman/gerar",this::tratarBackup);
        servidor.createContext("/api/backup/huffman/restaurar",this::tratarBackup);
        servidor.createContext("/api/backup/lzw/gerar",this::tratarBackup);
        servidor.createContext("/api/backup/lzw/restaurar",this::tratarBackup);
        servidor.createContext("/api/busca", this::tratarBusca);
    }

    // ── Helpers de sessão ──────────────────────────────────────────────────────

    private String obterToken(HttpExchange req) {
        String cookieHeader = req.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String par : cookieHeader.split(";")) {
            String[] kv = par.trim().split("=", 2);
            if (kv.length == 2 && "sessao".equals(kv[0].trim())) return kv[1].trim();
        }
        return null;
    }

    private boolean estaAutenticado(HttpExchange req) {
        String token = obterToken(req);
        return token != null && sessoes.contains(token);
    }

    private void redirecionarLogin(HttpExchange req) throws IOException {
        req.getResponseHeaders().set("Location", "/login");
        req.sendResponseHeaders(302, -1);
        req.getResponseBody().close();
    }

    // ── Tela de login ──────────────────────────────────────────────────────────

    private void tratarLogin(HttpExchange req) throws IOException {
        if ("GET".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 200, "text/html; charset=utf-8", gerarHtmlLogin(""));
        } else {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido.");
        }
    }

    private void tratarApiLogin(HttpExchange req) throws IOException {
        if (!"POST".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido.");
            return;
        }
        try {
            Map<String, String> p = lerFormulario(req);
            String usuario = p.getOrDefault("usuario", "");
            String senha   = p.getOrDefault("senha", "");
            if (GerenciadorLogin.validar(usuario, senha)) {
                String token = UUID.randomUUID().toString();
                sessoes.add(token);
                req.getResponseHeaders().set("Set-Cookie", "sessao=" + token + "; Path=/; HttpOnly");
                req.getResponseHeaders().set("Location", "/");
                req.sendResponseHeaders(302, -1);
                req.getResponseBody().close();
            } else {
                enviarResposta(req, 200, "text/html; charset=utf-8",
                    gerarHtmlLogin("Usuário ou senha incorretos."));
            }
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain", "Erro: " + e.getMessage());
        }
    }

    private void tratarCadastro(HttpExchange req) throws IOException {
        if ("GET".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 200, "text/html; charset=utf-8", gerarHtmlCadastro(""));
        } else {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido.");
        }
    }

    private void tratarApiCadastro(HttpExchange req) throws IOException {
        if (!"POST".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido.");
            return;
        }
        try {
            Map<String, String> p = lerFormulario(req);
            String usuario = p.getOrDefault("usuario", "").trim();
            String senha   = p.getOrDefault("senha", "");
            if (usuario.isBlank() || senha.isBlank()) {
                enviarResposta(req, 200, "text/html; charset=utf-8",
                    gerarHtmlCadastro("Preencha usuário e senha."));
                return;
            }
            if (GerenciadorLogin.existeUsuario(usuario)) {
                enviarResposta(req, 200, "text/html; charset=utf-8",
                    gerarHtmlCadastro("Usuário já existe."));
                return;
            }
            GerenciadorLogin.cadastrar(usuario, senha);
            req.getResponseHeaders().set("Location", "/login");
            req.sendResponseHeaders(302, -1);
            req.getResponseBody().close();
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain", "Erro: " + e.getMessage());
        }
    }

    private void tratarApiLogout(HttpExchange req) throws IOException {
        String token = obterToken(req);
        if (token != null) sessoes.remove(token);
        req.getResponseHeaders().set("Set-Cookie", "sessao=; Path=/; Max-Age=0");
        req.getResponseHeaders().set("Location", "/login");
        req.sendResponseHeaders(302, -1);
        req.getResponseBody().close();
    }

    // ── Início (protegido) ─────────────────────────────────────────────────────

    private void tratarInicio(HttpExchange req) throws IOException {
        if (!"GET".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido.");
            return;
        }
        if (!estaAutenticado(req)) { redirecionarLogin(req); return; }
        enviarResposta(req, 200, "text/html; charset=utf-8", gerarHtml());
    }

    private void tratarLivros(HttpExchange req) throws IOException {
        if (!estaAutenticado(req)) { enviarResposta(req, 401, "text/plain", "Nao autenticado."); return; }
        try {
            String caminho = req.getRequestURI().getPath();
            String metodo = req.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/livros")) {
                List<Livro> livros = livroController.listarTodos();
                StringBuilder sb = new StringBuilder();
                if (livros.isEmpty()) sb.append("Sem livros cadastrados.\n");
                for (Livro l : livros) sb.append(l).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/livros")) {
                int id = livroController.criar(paraLivro(0, lerFormulario(req)));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Livro inserido. ID=" + id);
                return;
            }
            if (caminho.startsWith("/api/livros/")) {
                int id = Integer.parseInt(caminho.substring("/api/livros/".length()));
                if ("GET".equalsIgnoreCase(metodo)) {
                    Livro l = livroController.buscarPorId(id);
                    enviarResposta(req, 200, "text/plain; charset=utf-8", l == null ? "Livro nao encontrado." : l.toString());
                    return;
                }
                if ("PUT".equalsIgnoreCase(metodo)) {
                    if (livroController.buscarPorId(id) == null) { enviarResposta(req, 404, "text/plain; charset=utf-8", "Livro inexistente."); return; }
                    livroController.atualizar(paraLivro(id, lerFormulario(req)));
                    enviarResposta(req, 200, "text/plain; charset=utf-8", "Livro atualizado.");
                    return;
                }
                if ("DELETE".equalsIgnoreCase(metodo)) {
                    if (livroController.buscarPorId(id) == null) { enviarResposta(req, 404, "text/plain; charset=utf-8", "Livro inexistente."); return; }
                    exemplarController.excluirPorLivro(id);
                    livroController.excluir(id);
                    enviarResposta(req, 200, "text/plain; charset=utf-8", "Livro, exemplares e reservas excluidos logicamente.");
                    return;
                }
            }
            enviarResposta(req, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarExemplares(HttpExchange req) throws IOException {
        if (!estaAutenticado(req)) { enviarResposta(req, 401, "text/plain", "Nao autenticado."); return; }
        try {
            String caminho = req.getRequestURI().getPath();
            String metodo = req.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/exemplares")) {
                List<Exemplar> lista = exemplarController.listarTodos();
                StringBuilder sb = new StringBuilder();
                if (lista.isEmpty()) sb.append("Sem exemplares cadastrados.\n");
                for (Exemplar e : lista) sb.append(formatarExemplar(e)).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/exemplares")) {
                int id = exemplarController.criar(paraExemplar(0, lerFormulario(req)));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Exemplar inserido. ID=" + id);
                return;
            }
            if (caminho.startsWith("/api/exemplares/")) {
                int id = Integer.parseInt(caminho.substring("/api/exemplares/".length()));
                if ("GET".equalsIgnoreCase(metodo)) {
                    Exemplar e = exemplarController.buscarPorId(id);
                    enviarResposta(req, 200, "text/plain; charset=utf-8", e == null ? "Exemplar nao encontrado." : formatarExemplar(e));
                    return;
                }
                if ("PUT".equalsIgnoreCase(metodo)) {
                    if (exemplarController.buscarPorId(id) == null) { enviarResposta(req, 404, "text/plain; charset=utf-8", "Exemplar inexistente."); return; }
                    exemplarController.atualizar(paraExemplar(id, lerFormulario(req)));
                    enviarResposta(req, 200, "text/plain; charset=utf-8", "Exemplar atualizado.");
                    return;
                }
                if ("DELETE".equalsIgnoreCase(metodo)) {
                    boolean ok = exemplarController.excluir(id);
                    enviarResposta(req, ok ? 200 : 404, "text/plain; charset=utf-8", ok ? "Exemplar excluido logicamente." : "Exemplar inexistente.");
                    return;
                }
            }
            enviarResposta(req, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarLeitores(HttpExchange req) throws IOException {
        if (!estaAutenticado(req)) { enviarResposta(req, 401, "text/plain", "Nao autenticado."); return; }
        try {
            String caminho = req.getRequestURI().getPath();
            String metodo = req.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/leitores")) {
                List<Leitor> lista = leitorController.listarTodos();
                StringBuilder sb = new StringBuilder();
                if (lista.isEmpty()) sb.append("Sem leitores cadastrados.\n");
                for (Leitor l : lista) sb.append(l).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/leitores")) {
                int id = leitorController.criar(paraLeitor(0, lerFormulario(req)));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Leitor inserido. ID=" + id);
                return;
            }
            if (caminho.startsWith("/api/leitores/")) {
                int id = Integer.parseInt(caminho.substring("/api/leitores/".length()));
                if ("GET".equalsIgnoreCase(metodo)) {
                    Leitor l = leitorController.buscarPorId(id);
                    enviarResposta(req, 200, "text/plain; charset=utf-8", l == null ? "Leitor nao encontrado." : l.toString());
                    return;
                }
                if ("PUT".equalsIgnoreCase(metodo)) {
                    if (leitorController.buscarPorId(id) == null) { enviarResposta(req, 404, "text/plain; charset=utf-8", "Leitor inexistente."); return; }
                    leitorController.atualizar(paraLeitor(id, lerFormulario(req)));
                    enviarResposta(req, 200, "text/plain; charset=utf-8", "Leitor atualizado.");
                    return;
                }
                if ("DELETE".equalsIgnoreCase(metodo)) {
                    if (leitorController.buscarPorId(id) == null) { enviarResposta(req, 404, "text/plain; charset=utf-8", "Leitor inexistente."); return; }
                    leitorController.excluir(id);
                    enviarResposta(req, 200, "text/plain; charset=utf-8", "Leitor e reservas excluidos logicamente.");
                    return;
                }
            }
            enviarResposta(req, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarReservas(HttpExchange req) throws IOException {
        if (!estaAutenticado(req)) { enviarResposta(req, 401, "text/plain", "Nao autenticado."); return; }
        try {
            String caminho = req.getRequestURI().getPath();
            String metodo = req.getRequestMethod();

            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/reservas")) {
                List<Reserva> lista = reservaController.listarTodos();
                StringBuilder sb = new StringBuilder();
                if (lista.isEmpty()) sb.append("Sem reservas cadastradas.\n");
                for (Reserva r : lista) sb.append(r).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/reservas/leitor")) {
                int id = Integer.parseInt(lerConsulta(req.getRequestURI().getRawQuery()).get("id"));
                List<Reserva> lista = reservaController.listarPorLeitor(id);
                StringBuilder sb = new StringBuilder();
                sb.append("Reservas do leitor ID ").append(id).append(":\n");
                if (lista.isEmpty()) sb.append("Nenhuma reserva.\n");
                for (Reserva r : lista) sb.append(r).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/reservas/livro")) {
                int id = Integer.parseInt(lerConsulta(req.getRequestURI().getRawQuery()).get("id"));
                List<Reserva> lista = reservaController.listarPorLivro(id);
                StringBuilder sb = new StringBuilder();
                sb.append("Reservas do livro ID ").append(id).append(":\n");
                if (lista.isEmpty()) sb.append("Nenhuma reserva.\n");
                for (Reserva r : lista) sb.append(r).append("\n");
                enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
                return;
            }
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/reservas")) {
                Map<String, String> p = lerFormulario(req);
                reservaController.criar(Integer.parseInt(p.get("idLeitor")), Integer.parseInt(p.get("idLivro")));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Reserva criada.");
                return;
            }
            if ("DELETE".equalsIgnoreCase(metodo) && caminho.equals("/api/reservas")) {
                Map<String, String> p = lerFormulario(req);
                reservaController.cancelar(Integer.parseInt(p.get("idLeitor")), Integer.parseInt(p.get("idLivro")));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Reserva cancelada.");
                return;
            }
            enviarResposta(req, 405, "text/plain; charset=utf-8", "Operacao nao suportada.");
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

 private void tratarRelacao(HttpExchange req) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(req.getRequestMethod())) {
                enviarResposta(req, 405, "text/plain; charset=utf-8", "Metodo nao permitido.");
                return;
            }
            String caminho = req.getRequestURI().getPath();
            if (!caminho.startsWith("/api/relacao/")) {
                enviarResposta(req, 400, "text/plain; charset=utf-8", "Informe /api/relacao/{livroId}.");
                return;
            }
            int livroId = Integer.parseInt(caminho.substring("/api/relacao/".length()));
            List<Exemplar> lista = exemplarController.listarPorLivro(livroId);
            StringBuilder sb = new StringBuilder();
            sb.append("Livro ID: ").append(livroId).append("\n");
            if (lista.isEmpty()) sb.append("Nenhum exemplar.\n");
            for (Exemplar e : lista) sb.append(formatarExemplar(e)).append("\n");
            enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarLivrosOrdenado(HttpExchange req) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(req.getRequestMethod())) { enviarResposta(req, 405, "text/plain; charset=utf-8", "Metodo nao permitido."); return; }
            List<Livro> ordenados = ordenacaoExterna.ordenarPorTitulo(3);
            StringBuilder sb = new StringBuilder("Livros ordenados por titulo:\n\n");
            if (ordenados.isEmpty()) sb.append("Nenhum livro cadastrado.\n");
            for (Livro l : ordenados) sb.append(l).append("\n");
            enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarOrdenacao(HttpExchange req) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(req.getRequestMethod())) { enviarResposta(req, 405, "text/plain; charset=utf-8", "Metodo nao permitido."); return; }
            int bloco = Integer.parseInt(lerConsulta(req.getRequestURI().getRawQuery()).getOrDefault("bloco", "3"));
            List<Livro> ordenados = ordenacaoExterna.ordenarPorTitulo(bloco);
            StringBuilder sb = new StringBuilder("Ordenacao concluida. Arquivo: data/livros_ordenado_titulo.db\nBloco utilizado: " + bloco + "\n\n");
            for (Livro l : ordenados) sb.append("ID=").append(l.getId()).append(" | Titulo=").append(l.getTitulo()).append("\n");
            enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarArvoreBMais(HttpExchange req) throws IOException {
        try {
            String caminho = req.getRequestURI().getPath();
            String metodo = req.getRequestMethod();
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/inserir")) {
                Map<String, String> p = lerFormulario(req);
                arvoreBMais.inserir(Integer.parseInt(p.get("chave")), Long.parseLong(p.get("valor")));
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Inserido na Arvore B+.");
                return;
            }
            if ("GET".equalsIgnoreCase(metodo) && caminho.startsWith("/api/arvore-bmais/buscar/")) {
                int chave = Integer.parseInt(caminho.substring("/api/arvore-bmais/buscar/".length()));
                Long valor = arvoreBMais.buscar(chave);
                String texto = valor == null
                    ? "--- BUSCA ARVORE B+ ---\nChave: " + chave + "\nResultado: Chave nao encontrada."
                    : "--- BUSCA ARVORE B+ ---\nChave: " + chave + "\nValor encontrado: " + valor;
                enviarResposta(req, 200, "text/plain; charset=utf-8", texto);
                return;
            }
            if ("POST".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/carga")) {
                List<Livro> livros = livroController.listarTodos();
                int total = 0;
                for (Livro l : livros) {
                    Long pos = livroDAO.getPosicaoPorId(l.getId());
                    if (pos != null) { arvoreBMais.inserir(l.getId(), pos); total++; }
                }
                enviarResposta(req, 200, "text/plain; charset=utf-8", "Carga na Arvore B+ concluida. Chaves: " + total);
                return;
            }
            if ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/arvore-bmais/depurar")) {
                enviarResposta(req, 200, "text/plain; charset=utf-8", arvoreBMais.depurarEstrutura());
                return;
            }
            enviarResposta(req, 405, "text/plain; charset=utf-8", "Operacao da Arvore B+ nao suportada.");
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarBusca(HttpExchange req) throws IOException {
        if (!estaAutenticado(req)) { enviarResposta(req, 401, "text/plain", "Nao autenticado."); return; }
        if (!"GET".equalsIgnoreCase(req.getRequestMethod())) {
            enviarResposta(req, 405, "text/plain", "Metodo nao permitido."); return;
        }
        try {
            Map<String, String> params = lerConsulta(req.getRequestURI().getRawQuery());
            String padrao = params.getOrDefault("padrao", "").trim();
            String algoritmo = params.getOrDefault("algoritmo", "kmp").trim().toLowerCase();
            if (padrao.isEmpty()) {
                enviarResposta(req, 400, "text/plain; charset=utf-8", "Informe o parametro 'padrao'."); return;
            }
            List<Livro> todos = livroController.listarTodos();
            StringBuilder sb = new StringBuilder();
            sb.append("Algoritmo: ").append(algoritmo.equalsIgnoreCase("bm") ? "Boyer-Moore" : "KMP")
              .append(" | Padrao: \"").append(padrao).append("\"\n\n");
            int encontrados = 0;
            for (Livro l : todos) {
                List<Integer> posicoes;
                if ("bm".equals(algoritmo)) {
                    posicoes = BoyerMoore.buscar(l.getTitulo(), padrao);
                } else {
                    posicoes = KMP.buscar(l.getTitulo(), padrao);
                }
                if (!posicoes.isEmpty()) {
                    sb.append(l).append("\n");
                    sb.append("  (ocorrencias no titulo nas posicoes: ").append(posicoes).append(")\n\n");
                    encontrados++;
                }
            }
            if (encontrados == 0) sb.append("Nenhum livro encontrado com o padrao informado.");
            else sb.append("Total encontrado: ").append(encontrados).append(" livro(s).");
            enviarResposta(req, 200, "text/plain; charset=utf-8", sb.toString());
        } catch (Exception e) {
            enviarResposta(req, 400, "text/plain; charset=utf-8", "Erro: " + e.getMessage());
        }
    }

    private void tratarBackup(HttpExchange req) throws IOException {
    try {
        String caminho = req.getRequestURI().getPath();
        String metodo = req.getRequestMethod();

        if ("POST".equalsIgnoreCase(metodo)
                && caminho.equals(
                "/api/backup/huffman/gerar")) {

            backupManager.gerarBackup(
                    "backup.huff",
                    BackupManager.Algoritmo.HUFFMAN
            );

            enviarResposta(
                    req,
                    200,
                    "text/plain",
                    "Backup Huffman gerado."
            );

            return;
        }
        if ("POST".equalsIgnoreCase(metodo)
                && caminho.equals(
                "/api/backup/huffman/restaurar")) {

            backupManager.restaurarBackup(
                    "backup.huff",
                    BackupManager.Algoritmo.HUFFMAN
            );

            enviarResposta(
                    req,
                    200,
                    "text/plain",
                    "Backup Huffman restaurado."
            );

            return;
        }

        if ("POST".equalsIgnoreCase(metodo)
                && caminho.equals(
                "/api/backup/lzw/gerar")) {

            backupManager.gerarBackup(
                    "backup.lzw",
                    BackupManager.Algoritmo.LZW
            );

            enviarResposta(
                    req,
                    200,
                    "text/plain",
                    "Backup LZW gerado."
            );

            return;
        }
        if ("POST".equalsIgnoreCase(metodo)
                && caminho.equals(
                "/api/backup/lzw/restaurar")) {

            backupManager.restaurarBackup(
                    "backup.lzw",
                    BackupManager.Algoritmo.LZW
            );

            enviarResposta(
                    req,
                    200,
                    "text/plain",
                    "Backup LZW restaurado."
            );

            return;
        }

        enviarResposta(
                req,
                405,
                "text/plain; charset=utf-8",
                "Operacao de backup nao suportada."
        );

    } catch (Exception e) {

        enviarResposta(
                req,
                400,
                "text/plain; charset=utf-8",
                "Erro: " + e.getMessage()
        );
    }
}

    private Livro paraLivro(int id, Map<String, String> p) {
        String titulo = p.getOrDefault("titulo", "");
        String isbn = p.getOrDefault("isbn", "");
        String editora = p.getOrDefault("editora", "");
        int edicao = Integer.parseInt(p.getOrDefault("edicao", "1"));
        float preco = Float.parseFloat(p.getOrDefault("preco", "0").replace(',', '.'));
        String autores = p.getOrDefault("autores", "");
        String categorias = p.getOrDefault("categorias", "");
        long data;
        try { data = new SimpleDateFormat("dd/MM/yyyy").parse(p.getOrDefault("data", "")).getTime(); }
        catch (Exception e) { data = System.currentTimeMillis(); }
        return new Livro(id, isbn, titulo, preco, data, categorias, autores, edicao, editora);
    }

    private Exemplar paraExemplar(int id, Map<String, String> p) {
        return new Exemplar(id,
            Integer.parseInt(p.getOrDefault("livroId", "0")),
            p.getOrDefault("codigoPatrimonio", ""),
            p.getOrDefault("estado", "NOVO"));
    }

    private Leitor paraLeitor(int id, Map<String, String> p) {
        long data;
        try { data = new SimpleDateFormat("dd/MM/yyyy").parse(p.getOrDefault("dataNascimento", "")).getTime(); }
        catch (Exception e) { data = System.currentTimeMillis(); }
        return new Leitor(id,
            p.getOrDefault("nome", ""),
            p.getOrDefault("email", ""),
            data);
    }

    private String formatarExemplar(Exemplar e) {
        return """
--- DADOS DO EXEMPLAR ---
ID: %d | Livro ID: %d
Codigo Patrimonio: %s
Estado: %s
""".formatted(e.getId(), e.getLivroId(), e.getCodigoPatrimonio(), e.getEstado());
    }

    private Map<String, String> lerFormulario(HttpExchange req) throws IOException {
        return lerConsulta(new String(req.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private Map<String, String> lerConsulta(String raw) {
        Map<String, String> mapa = new HashMap<>();
        if (raw == null || raw.isBlank()) return mapa;
        for (String par : raw.split("&")) {
            String[] kv = par.split("=", 2);
            mapa.put(decodificar(kv[0]), kv.length > 1 ? decodificar(kv[1]) : "");
        }
        return mapa;
    }

    private String decodificar(String texto) {
        return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    }

    private void enviarResposta(HttpExchange req, int status, String tipo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        req.getResponseHeaders().set("Content-Type", tipo);
        req.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = req.getResponseBody()) { out.write(bytes); }
    }

    private String gerarHtmlLogin(String erro) {
        String msgErro = erro.isBlank() ? "" :
            "<p style='color:#ef4444;margin:0 0 12px;font-size:14px'>" + erro + "</p>";
        return """
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>Login — Biblioteca</title>
  <style>
    *{box-sizing:border-box}
    body{margin:0;font-family:Segoe UI,Tahoma,sans-serif;
         background:linear-gradient(135deg,#e2e8f0,#f8fafc);
         display:flex;align-items:center;justify-content:center;min-height:100vh}
    .card{background:#fff;border-radius:16px;box-shadow:0 8px 32px rgba(2,6,23,.12);
          padding:36px 32px;width:100%%;max-width:360px}
    h2{margin:0 0 6px;color:#0f172a}
    .sub{color:#64748b;font-size:13px;margin:0 0 24px}
    label{display:block;font-size:13px;font-weight:600;margin-bottom:4px;color:#334155}
    input{width:100%%;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;
          font-size:14px;margin-bottom:16px;outline:none}
    input:focus{border-color:#0ea5e9;box-shadow:0 0 0 3px rgba(14,165,233,.15)}
    button{width:100%%;padding:11px;border:0;border-radius:8px;
           background:#0ea5e9;color:#fff;font-size:15px;font-weight:600;cursor:pointer}
    button:hover{background:#0284c7}
    .nota{margin-top:14px;font-size:12px;color:#94a3b8;text-align:center}
  </style>
</head>
<body>
<div class="card">
  <h2>Biblioteca</h2>
  <p class="sub">Acesso ao sistema de gerenciamento</p>
  %s
  <form action="/api/login" method="POST">
    <label>Usuário</label>
    <input type="text" name="usuario" required autofocus placeholder="login"/>
    <label>Senha</label>
    <input type="password" name="senha" required placeholder=""/>
    <button type="submit">Entrar</button>
  </form>
</div>
</body>
</html>
""".formatted(msgErro);
    }

    private String gerarHtmlCadastro(String erro) {
        String msgErro = erro.isBlank() ? "" :
            "<p style='color:#ef4444;margin:0 0 12px;font-size:14px'>" + erro + "</p>";
        return """
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8"/>
  <title>Cadastro — Biblioteca</title>
  <style>
    *{box-sizing:border-box}
    body{margin:0;font-family:Segoe UI,Tahoma,sans-serif;
         background:linear-gradient(135deg,#e2e8f0,#f8fafc);
         display:flex;align-items:center;justify-content:center;min-height:100vh}
    .card{background:#fff;border-radius:16px;box-shadow:0 8px 32px rgba(2,6,23,.12);
          padding:36px 32px;width:100%%;max-width:360px}
    h2{margin:0 0 6px;color:#0f172a}
    .sub{color:#64748b;font-size:13px;margin:0 0 24px}
    label{display:block;font-size:13px;font-weight:600;margin-bottom:4px;color:#334155}
    input{width:100%%;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;
          font-size:14px;margin-bottom:16px;outline:none}
    input:focus{border-color:#0ea5e9;box-shadow:0 0 0 3px rgba(14,165,233,.15)}
    button{width:100%%;padding:11px;border:0;border-radius:8px;
           background:#10b981;color:#fff;font-size:15px;font-weight:600;cursor:pointer}
    button:hover{background:#059669}
    .nota{margin-top:14px;font-size:12px;color:#94a3b8;text-align:center}
  </style>
</head>
<body>
<div class="card">
  <h2>Criar Conta</h2>
  <p class="sub">Novo acesso ao sistema</p>
  %s
  <form action="/api/cadastro" method="POST">
    <label>Usuário</label>
    <input type="text" name="usuario" required autofocus/>
    <label>Senha</label>
    <input type="password" name="senha" required/>
    <button type="submit">Cadastrar</button>
  </form>
  <p class="nota"><a href="/login">Já tem conta? Entrar</a></p>
</div>
</body>
</html>
""".formatted(msgErro);
    }

    private String gerarHtml() {
        return """
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>Biblioteca Fase 5</title>
  <style>
    :root{--bg:#f8fafc;--card:#ffffff;--ink:#0f172a;--accent:#0ea5e9;--muted:#64748b;--green:#10b981;--red:#ef4444}
    body{margin:0;font-family:Segoe UI,Tahoma,sans-serif;background:linear-gradient(120deg,#e2e8f0,#f8fafc);color:var(--ink)}
    .container{max-width:1200px;margin:24px auto;padding:0 16px}
    h1{margin:0 0 4px}
    .grade{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:12px}
    .cartao{background:var(--card);border-radius:12px;box-shadow:0 6px 20px rgba(2,6,23,.08);padding:14px}
    .cartao h3{margin:0 0 10px}
    .linha{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px}
    input{padding:8px;border:1px solid #cbd5e1;border-radius:8px;min-width:110px;font-size:13px}
    button{border:0;border-radius:8px;background:var(--accent);color:#fff;padding:8px 12px;cursor:pointer;font-size:13px}
    button.sec{background:#334155}
    button.danger{background:var(--red)}
    pre{background:#0b1220;color:#dbeafe;min-height:180px;padding:12px;border-radius:10px;overflow:auto;font-size:13px}
    .legenda{color:var(--muted);font-size:13px;margin:0 0 16px}
    .header{display:flex;align-items:center;justify-content:space-between;margin-bottom:4px}
    .btn-logout{border:0;border-radius:8px;background:#ef4444;color:#fff;padding:8px 14px;cursor:pointer;font-size:13px}
  </style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>Biblioteca</h1>
    <form action="/api/logout" method="POST" style="margin:0">
      <button type="submit" class="btn-logout">Sair</button>
    </form>
  </div>
  <p class="legenda">Sistema de gerenciamento — Fase 5</p>
  <div class="grade">

    <div class="cartao">
      <h3>Livro</h3>
      <div class="linha"><input id="livro-id" placeholder="id"><button onclick="buscarLivro()">Buscar</button><button onclick="excluirLivro()" class="danger">Excluir</button></div>
      <div class="linha"><input id="livro-titulo" placeholder="titulo"><input id="livro-isbn" placeholder="isbn"></div>
      <div class="linha"><input id="livro-editora" placeholder="editora"><input id="livro-edicao" placeholder="edicao"></div>
      <div class="linha"><input id="livro-preco" placeholder="preco"><input id="livro-data" placeholder="dd/MM/yyyy"></div>
      <div class="linha"><input id="livro-autores" placeholder="autores"><input id="livro-categorias" placeholder="categorias"></div>
      <div class="linha">
        <button onclick="inserirLivro()">Inserir</button>
        <button onclick="atualizarLivro()">Atualizar</button>
        <button onclick="listarLivros()" class="sec">Listar</button>
        <button onclick="listarLivrosOrdenado()" class="sec">Listar por Titulo</button>
      </div>
    </div>

    <div class="cartao">
      <h3>Exemplar</h3>
      <div class="linha"><input id="exemplar-id" placeholder="id"><button onclick="buscarExemplar()">Buscar</button><button onclick="excluirExemplar()" class="danger">Excluir</button></div>
      <div class="linha"><input id="exemplar-livro" placeholder="livroId"><input id="exemplar-codigo" placeholder="codigoPatrimonio"><input id="exemplar-estado" placeholder="estado"></div>
      <div class="linha">
        <button onclick="inserirExemplar()">Inserir</button>
        <button onclick="atualizarExemplar()">Atualizar</button>
        <button onclick="listarExemplares()" class="sec">Listar</button>
      </div>
    </div>

    <div class="cartao">
      <h3>Leitor</h3>
      <div class="linha"><input id="leitor-id" placeholder="id"><button onclick="buscarLeitor()">Buscar</button><button onclick="excluirLeitor()" class="danger">Excluir</button></div>
      <div class="linha"><input id="leitor-nome" placeholder="nome"><input id="leitor-email" placeholder="email"></div>
      <div class="linha"><input id="leitor-nascimento" placeholder="dd/MM/yyyy (nascimento)"></div>
      <div class="linha">
        <button onclick="inserirLeitor()">Inserir</button>
        <button onclick="atualizarLeitor()">Atualizar</button>
        <button onclick="listarLeitores()" class="sec">Listar</button>
      </div>
    </div>

    <div class="cartao">
      <h3>Pesquisar por Padrão (KMP / BM)</h3>
      <div class="linha">
        <input id="busca-padrao" placeholder="padrão de busca"/>
        <select id="busca-algo" style="padding:8px;border:1px solid #cbd5e1;border-radius:8px;font-size:13px">
          <option value="kmp">KMP</option>
          <option value="bm">Boyer-Moore</option>
        </select>
        <button onclick="buscarPadrao()">Buscar</button>
      </div>
    </div>

    <div class="cartao">
      <h3>Reserva</h3>
      <div class="linha"><input id="reserva-leitor" placeholder="idLeitor"><input id="reserva-livro" placeholder="idLivro"></div>
      <div class="linha">
        <button onclick="criarReserva()">Reservar</button>
        <button onclick="cancelarReserva()" class="danger">Cancelar</button>
        <button onclick="listarReservas()" class="sec">Listar Todas</button>
      </div>
      <div class="linha">
        <button onclick="reservasPorLeitor()" class="sec">Livros do Leitor</button>
        <button onclick="reservasPorLivro()" class="sec">Leitores do Livro</button>
      </div>
    </div>
  </div>

  <details class="secao">
    <summary>Modo Técnico</summary>
    <div class="grade" style="margin-top:12px">
      <div class="cartao">
        <h3>Ordenação Externa</h3>
        <div class="linha"><input id="ordenacao-bloco" value="3" placeholder="bloco"><button onclick="ordenarExternamente()">Ordenar por titulo</button></div>
      </div>
      <div class="cartao">
        <h3>Árvore B+</h3>
        <div class="linha"><input id="arvore-chave" placeholder="chave"><input id="arvore-valor" placeholder="valor"><button onclick="inserirArvore()">Inserir</button></div>
        <div class="linha"><input id="arvore-busca" placeholder="chave busca"><button onclick="buscarArvore()">Buscar</button><button onclick="depurarArvore()" class="sec">Debug</button></div>
      </div>
      <div class="cartao">
        <h3>Backup e Restauração</h3>
            <button onclick="gerarBackupHuffman()">
                Gerar Backup Huffman
            </button>
            <button onclick="restaurarBackupHuffman()">
                Restaurar Backup Huffman
            </button>
            <br><br>
            <button onclick="gerarBackupLZW()">
                Gerar Backup LZW
            </button>
            <button onclick="restaurarBackupLZW()">
                Restaurar Backup LZW
            </button>
      </div>
</div>

      <div class="cartao">
        <h3>Relação 1:N</h3>
        <div class="linha"><input id="relacao-livro" placeholder="livroId"><button onclick="listarRelacao()">Exemplares do Livro</button></div>
      </div>
      <div class="cartao">
        <h3>Relação N:N</h3>
        <div class="linha"><input id="nn-leitor" placeholder="idLeitor"><button onclick="nnPorLeitor()">Livros do Leitor</button></div>
        <div class="linha"><input id="nn-livro" placeholder="idLivro"><button onclick="nnPorLivro()">Leitores do Livro</button></div>
      </div>
    </div>
  </details>

  <h3 style="margin-top:20px">Saída</h3>
  <pre id="saida"></pre>
</div>

<script>
  const saida = document.getElementById('saida');
  const cod = obj => new URLSearchParams(obj).toString();
  async function req(url, ops={}) { const r = await fetch(url, ops); saida.textContent = await r.text(); }
  const v = id => document.getElementById(id).value || '';

  function buscarPadrao(){ req('/api/busca?padrao='+encodeURIComponent(v('busca-padrao'))+'&algoritmo='+v('busca-algo')); }
  function listarLivros(){ req('/api/livros'); }
  function buscarLivro(){ req('/api/livros/'+v('livro-id')); }
  function inserirLivro(){ req('/api/livros',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({titulo:v('livro-titulo'),isbn:v('livro-isbn'),editora:v('livro-editora'),edicao:v('livro-edicao'),preco:v('livro-preco'),data:v('livro-data'),autores:v('livro-autores'),categorias:v('livro-categorias')})}); }
  function atualizarLivro(){ req('/api/livros/'+v('livro-id'),{method:'PUT',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({titulo:v('livro-titulo'),isbn:v('livro-isbn'),editora:v('livro-editora'),edicao:v('livro-edicao'),preco:v('livro-preco'),data:v('livro-data'),autores:v('livro-autores'),categorias:v('livro-categorias')})}); }
  function excluirLivro(){ req('/api/livros/'+v('livro-id'),{method:'DELETE'}); }
  function listarLivrosOrdenado(){ req('/api/livros-ordenado'); }

  function listarExemplares(){ req('/api/exemplares'); }
  function buscarExemplar(){ req('/api/exemplares/'+v('exemplar-id')); }
  function inserirExemplar(){ req('/api/exemplares',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({livroId:v('exemplar-livro'),codigoPatrimonio:v('exemplar-codigo'),estado:v('exemplar-estado')})}); }
  function atualizarExemplar(){ req('/api/exemplares/'+v('exemplar-id'),{method:'PUT',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({livroId:v('exemplar-livro'),codigoPatrimonio:v('exemplar-codigo'),estado:v('exemplar-estado')})}); }
  function excluirExemplar(){ req('/api/exemplares/'+v('exemplar-id'),{method:'DELETE'}); }

  function listarLeitores(){ req('/api/leitores'); }
  function buscarLeitor(){ req('/api/leitores/'+v('leitor-id')); }
  function inserirLeitor(){ req('/api/leitores',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({nome:v('leitor-nome'),email:v('leitor-email'),dataNascimento:v('leitor-nascimento')})}); }
  function atualizarLeitor(){ req('/api/leitores/'+v('leitor-id'),{method:'PUT',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({nome:v('leitor-nome'),email:v('leitor-email'),dataNascimento:v('leitor-nascimento')})}); }
  function excluirLeitor(){ req('/api/leitores/'+v('leitor-id'),{method:'DELETE'}); }

  function criarReserva(){ req('/api/reservas',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({idLeitor:v('reserva-leitor'),idLivro:v('reserva-livro')})}); }
  function cancelarReserva(){ req('/api/reservas',{method:'DELETE',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({idLeitor:v('reserva-leitor'),idLivro:v('reserva-livro')})}); }
  function listarReservas(){ req('/api/reservas'); }
  function reservasPorLeitor(){ req('/api/reservas/leitor?id='+v('reserva-leitor')); }
  function reservasPorLivro(){ req('/api/reservas/livro?id='+v('reserva-livro')); }

  function nnPorLeitor(){ req('/api/reservas/leitor?id='+v('nn-leitor')); }
  function nnPorLivro(){ req('/api/reservas/livro?id='+v('nn-livro')); }

  function listarRelacao(){ req('/api/relacao/'+v('relacao-livro')); }
  function ordenarExternamente(){ req('/api/ordenacao?bloco='+encodeURIComponent(v('ordenacao-bloco'))); }
  function inserirArvore(){ req('/api/arvore-bmais/inserir',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:cod({chave:v('arvore-chave'),valor:v('arvore-valor')})}); }
  function buscarArvore(){ req('/api/arvore-bmais/buscar/'+v('arvore-busca')); }
  function depurarArvore(){ req('/api/arvore-bmais/depurar'); }
    async function gerarBackupHuffman() {

        const resposta = await fetch(
            '/api/backup/huffman/gerar',
            {
                method: 'POST'
            }
        );

        const texto = await resposta.text();

        alert(texto);
    }
    async function restaurarBackupHuffman() {

        const resposta = await fetch(
            '/api/backup/huffman/restaurar',
            {
                method: 'POST'
            }
        );

        const texto = await resposta.text();

        alert(texto);
    }
    async function gerarBackupLZW() {

        const resposta = await fetch(
            '/api/backup/lzw/gerar',
            {
                method: 'POST'
            }
        );

        const texto = await resposta.text();

        alert(texto);
    }
    async function restaurarBackupLZW() {

        const resposta = await fetch(
            '/api/backup/lzw/restaurar',
            {
                method: 'POST'
            }
        );

        const texto = await resposta.text();

        alert(texto);
    }
</script>
</body>
</html>
""";
    }
}
