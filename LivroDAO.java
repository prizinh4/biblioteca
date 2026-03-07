import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LivroDAO {
    private RandomAccessFile arq;
    private final String nomeArquivo = "livros.db";

    public LivroDAO() throws IOException {
        arq = new RandomAccessFile(nomeArquivo, "rw");
        if (arq.length() == 0) arq.writeInt(0); // Cabeçalho
    }

    public int create(Livro l) throws IOException {
        arq.seek(0);
        int novoID = arq.readInt() + 1;
        l.setId(novoID);
        arq.seek(0);
        arq.writeInt(novoID);
        gravarLivroNoFim(l);
        return novoID;
    }

    private void gravarLivroNoFim(Livro l) throws IOException {
        arq.seek(arq.length());
        arq.writeBoolean(false); // Registro ativo

        byte[] bIsbn = l.getIsbn().getBytes("UTF-8");
        byte[] bTitulo = l.getTitulo().getBytes("UTF-8");
        byte[] bCategorias = l.getCategorias().getBytes("UTF-8");
        byte[] bAutores = l.getAutores().getBytes("UTF-8");
        byte[] bEditora = l.getEditora().getBytes("UTF-8");

        int tamanho = 4 + 4 + 8 + 4 + (5 * 4) + bIsbn.length + bTitulo.length + bCategorias.length + bAutores.length + bEditora.length;
        
        arq.writeInt(tamanho);
        arq.writeInt(l.getId());
        arq.writeFloat(l.getPrecoReposicao());
        arq.writeLong(l.getDataPublicacao());
        arq.writeInt(l.getEdicao());
        arq.writeInt(bIsbn.length); arq.write(bIsbn);
        arq.writeInt(bTitulo.length); arq.write(bTitulo);
        arq.writeInt(bCategorias.length); arq.write(bCategorias);
        arq.writeInt(bAutores.length); arq.write(bAutores);
        arq.writeInt(bEditora.length); arq.write(bEditora);
    }

    public Livro read(int idBusca) throws IOException {
        arq.seek(4);
        while (arq.getFilePointer() < arq.length()) {
            boolean lapide = arq.readBoolean();
            int tamRegistro = arq.readInt();
            long proximoRegistro = arq.getFilePointer() + tamRegistro;
            if (!lapide) {
                int id = arq.readInt();
                if (id == idBusca) {
                    float preco = arq.readFloat();
                    long data = arq.readLong();
                    int edicao = arq.readInt();
                    String isbn = readString();
                    String titulo = readString();
                    String categorias = readString();
                    String autores = readString();
                    String editora = readString();
                    return new Livro(id, isbn, titulo, preco, data, categorias, autores, edicao, editora);
                }
            }
            arq.seek(proximoRegistro);
        }
        return null;
    }

    public List<Livro> readAll() throws IOException {
        List<Livro> livros = new ArrayList<>();
        arq.seek(4);

        while (arq.getFilePointer() < arq.length()) {
            boolean lapide = arq.readBoolean();
            int tamRegistro = arq.readInt();
            long proximoRegistro = arq.getFilePointer() + tamRegistro;

            if (!lapide) {
                int id = arq.readInt();
                float preco = arq.readFloat();
                long data = arq.readLong();
                int edicao = arq.readInt();
                String isbn = readString();
                String titulo = readString();
                String categorias = readString();
                String autores = readString();
                String editora = readString();

                livros.add(new Livro(id, isbn, titulo, preco, data, categorias, autores, edicao, editora));
            }

            arq.seek(proximoRegistro);
        }

        return livros;
    }

    private String readString() throws IOException {
        int tam = arq.readInt();
        byte[] b = new byte[tam];
        arq.read(b);
        return new String(b, "UTF-8");
    }

    public boolean update(Livro l) throws IOException {
        if (delete(l.getId())) {
            gravarLivroNoFim(l);
            return true;
        }
        return false;
    }

    public boolean delete(int idBusca) throws IOException {
        arq.seek(4);
        while (arq.getFilePointer() < arq.length()) {
            long posLapide = arq.getFilePointer();
            boolean lapide = arq.readBoolean();
            int tam = arq.readInt();
            long prox = arq.getFilePointer() + tam;
            if (!lapide) {
                if (arq.readInt() == idBusca) {
                    arq.seek(posLapide);
                    arq.writeBoolean(true);
                    return true;
                }
            }
            arq.seek(prox);
        }
        return false;
    }
}
