package dao;

import model.Livro;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LivroDAO {
    private RandomAccessFile arq;
    private IndiceDireto indice;
    private final String nomeArquivo = "data/livros.db";
    private final String nomeIndice = "data/livros.idx";

    public LivroDAO() throws IOException {
        File pastaData = new File("data");
        pastaData.mkdirs();
        arq = new RandomAccessFile(nomeArquivo, "rw");
        indice = new IndiceDireto(nomeIndice);
        if (arq.length() == 0) arq.writeInt(0);
    }

    public int create(Livro l) throws IOException {
        if (existeIsbn(l.getIsbn())) {
            throw new IllegalArgumentException("ISBN ja cadastrado.");
        }

        arq.seek(0);
        int novoID = arq.readInt() + 1;
        l.setId(novoID);
        arq.seek(0);
        arq.writeInt(novoID);
        
        long posicao = arq.length();
        gravarLivro(l, posicao);
        indice.atualizar(novoID, posicao);
        return novoID;
    }

    private void gravarLivro(Livro l, long posicao) throws IOException {
        arq.seek(posicao);
        arq.writeBoolean(false);

        byte[] bIsbn = l.getIsbn().getBytes(StandardCharsets.UTF_8);
        byte[] bTitulo = l.getTitulo().getBytes(StandardCharsets.UTF_8);
        byte[] bCategorias = l.getCategorias().getBytes(StandardCharsets.UTF_8);
        byte[] bAutores = l.getAutores().getBytes(StandardCharsets.UTF_8);
        byte[] bEditora = l.getEditora().getBytes(StandardCharsets.UTF_8);

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
        Long posicao = indice.buscar(idBusca);
        if (posicao == null) return null;

        arq.seek(posicao);
        boolean lapide = arq.readBoolean();
        if (lapide) return null;

        arq.readInt(); 
        return lerRegistro();
    }

    private Livro lerRegistro() throws IOException {
        int id = arq.readInt();
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

    public List<Livro> readAll() throws IOException {
        List<Livro> livros = new ArrayList<>();
        arq.seek(4);
        while (arq.getFilePointer() < arq.length()) {
            long posAtual = arq.getFilePointer();
            boolean lapide = arq.readBoolean();
            int tamRegistro = arq.readInt();
            if (!lapide) {
                livros.add(lerRegistro());
            } else {
                arq.seek(posAtual + 1 + 4 + tamRegistro);
            }
        }
        return livros;
    }

    private String readString() throws IOException {
        int tam = arq.readInt();
        byte[] b = new byte[tam];
        arq.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    public boolean update(Livro l) throws IOException {
        Long posicao = indice.buscar(l.getId());
        if (posicao == null) return false;

        Livro atual = read(l.getId());
        if (atual == null) return false;

        if (!atual.getIsbn().equalsIgnoreCase(l.getIsbn()) && existeIsbn(l.getIsbn())) {
            throw new IllegalArgumentException("ISBN ja cadastrado.");
        }

        arq.seek(posicao);
        arq.writeBoolean(true);
        long novaPosicao = arq.length();
        gravarLivro(l, novaPosicao);
        indice.atualizar(l.getId(), novaPosicao);
        return true;
    }

    public boolean delete(int idBusca) throws IOException {
        Long posicao = indice.buscar(idBusca);
        if (posicao != null) {
            arq.seek(posicao);
            arq.writeBoolean(true);
            indice.remover(idBusca);
            return true;
        }
        return false;
    }

    public Long getPosicaoPorId(int id) {
        return indice.buscar(id);
    }

    private boolean existeIsbn(String isbn) throws IOException {
        for (Livro l : readAll()) {
            if (l.getIsbn().equalsIgnoreCase(isbn)) return true;
        }
        return false;
    }
}