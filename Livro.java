import java.text.SimpleDateFormat;
import java.util.Date;

public class Livro {
    private int id;
    private String isbn;
    private String titulo;
    private float precoReposicao;
    private long dataPublicacao;
    private String categorias;
    private String autores;
    private int edicao;
    private String editora;

    public Livro() {}

    public Livro(int id, String isbn, String titulo, float precoReposicao, long dataPublicacao, String categorias, String autores, int edicao, String editora) {
        this.id = id;
        this.isbn = isbn;
        this.titulo = titulo;
        this.precoReposicao = precoReposicao;
        this.dataPublicacao = dataPublicacao;
        this.categorias = categorias;
        this.autores = autores;
        this.edicao = edicao;
        this.editora = editora;
    }

    // Getters e Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getIsbn() { return isbn; }
    public String getTitulo() { return titulo; }
    public float getPrecoReposicao() { return precoReposicao; }
    public long getDataPublicacao() { return dataPublicacao; }
    public String getCategorias() { return categorias; }
    public String getAutores() { return autores; }
    public int getEdicao() { return edicao; }
    public String getEditora() { return editora; }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        return "\n--- DADOS DO LIVRO ---" +
               "\nID: " + id + " | ISBN: " + isbn +
               "\nTítulo: " + titulo + " | Edição: " + edicao +
               "\nEditora: " + editora + " | Preço Reposição: R$" + precoReposicao +
               "\nData de Publicação: " + sdf.format(new Date(dataPublicacao)) +
               "\nCategorias: " + categorias + " | Autores: " + autores;
    }
}