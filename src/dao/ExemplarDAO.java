package dao;

import model.Exemplar;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ExemplarDAO {
    private final RandomAccessFile arq;
    private final IndiceDireto indice;
    private final LivroDAO livroDAO;
    private final IndiceLivroExemplar relacao;

    public ExemplarDAO(LivroDAO livroDAO) throws IOException {
        File pastaData = new File("data");
        pastaData.mkdirs();

        this.livroDAO = livroDAO;
        this.arq = new RandomAccessFile("data/exemplares.db", "rw");
        this.indice = new IndiceDireto("data/exemplares.idx");
        this.relacao = new IndiceLivroExemplar(
                "data/hash/livro_exemplar.dir",
                "data/hash/livro_exemplar.bkt",
                "data/livro_exemplar.rel"
        );

        if (arq.length() == 0) arq.writeInt(0);
    }

    public int create(Exemplar exemplar) throws IOException {
        if (livroDAO.read(exemplar.getLivroId()) == null) {
            throw new IllegalArgumentException("Livro pai inexistente.");
        }
        if (existeCodigoPatrimonio(exemplar.getCodigoPatrimonio())) {
            throw new IllegalArgumentException("Codigo de patrimonio ja cadastrado.");
        }

        arq.seek(0);
        int novoId = arq.readInt() + 1;
        exemplar.setId(novoId);
        arq.seek(0);
        arq.writeInt(novoId);

        long pos = arq.length();
        gravarExemplar(exemplar, pos);
        indice.atualizar(novoId, pos);
        relacao.adicionar(exemplar.getLivroId(), novoId);
        return novoId;
    }

    public Exemplar read(int idBusca) throws IOException {
        Long pos = indice.buscar(idBusca);
        if (pos == null) return null;

        arq.seek(pos);
        boolean lapide = arq.readBoolean();
        if (lapide) return null;

        arq.readInt();
        return lerRegistro();
    }

    public List<Exemplar> readByLivroId(int livroId) throws IOException {
        List<Exemplar> lista = new ArrayList<>();
        for (Integer exemplarId : relacao.listarExemplares(livroId)) {
            Exemplar ex = read(exemplarId);
            if (ex != null) lista.add(ex);
        }
        return lista;
    }

    public List<Exemplar> readAll() throws IOException {
        List<Exemplar> exemplares = new ArrayList<>();
        arq.seek(4);

        while (arq.getFilePointer() < arq.length()) {
            long posAtual = arq.getFilePointer();
            boolean lapide = arq.readBoolean();
            int tamRegistro = arq.readInt();
            if (!lapide) {
                exemplares.add(lerRegistro());
            } else {
                arq.seek(posAtual + 1 + 4 + tamRegistro);
            }
        }

        return exemplares;
    }

    public boolean update(Exemplar exemplar) throws IOException {
        Long pos = indice.buscar(exemplar.getId());
        if (pos == null) return false;

        Exemplar atual = read(exemplar.getId());
        if (atual == null) return false;

        if (livroDAO.read(exemplar.getLivroId()) == null) {
            throw new IllegalArgumentException("Livro pai inexistente.");
        }

        if (!atual.getCodigoPatrimonio().equals(exemplar.getCodigoPatrimonio()) && existeCodigoPatrimonio(exemplar.getCodigoPatrimonio())) {
            throw new IllegalArgumentException("Codigo de patrimonio ja cadastrado.");
        }

        arq.seek(pos);
        arq.writeBoolean(true);

        long novaPos = arq.length();
        gravarExemplar(exemplar, novaPos);
        indice.atualizar(exemplar.getId(), novaPos);

        if (atual.getLivroId() != exemplar.getLivroId()) {
            relacao.removerExemplar(atual.getLivroId(), atual.getId());
            relacao.adicionar(exemplar.getLivroId(), exemplar.getId());
        }

        return true;
    }

    public boolean delete(int idBusca) throws IOException {
        Long pos = indice.buscar(idBusca);
        if (pos == null) return false;

        Exemplar atual = read(idBusca);
        if (atual == null) return false;

        arq.seek(pos);
        arq.writeBoolean(true);
        indice.remover(idBusca);
        relacao.removerExemplar(atual.getLivroId(), idBusca);
        return true;
    }

    public void deleteByLivroId(int livroId) throws IOException {
        List<Integer> ids = relacao.listarExemplares(livroId);
        for (Integer id : ids) {
            Long pos = indice.buscar(id);
            if (pos == null) continue;
            arq.seek(pos);
            arq.writeBoolean(true);
            indice.remover(id);
        }
        relacao.removerLivro(livroId);
    }

    private boolean existeCodigoPatrimonio(String codigo) throws IOException {
        for (Exemplar ex : readAll()) {
            if (ex.getCodigoPatrimonio().equalsIgnoreCase(codigo)) return true;
        }
        return false;
    }

    private void gravarExemplar(Exemplar e, long posicao) throws IOException {
        arq.seek(posicao);
        arq.writeBoolean(false);

        byte[] bCodigo = e.getCodigoPatrimonio().getBytes(StandardCharsets.UTF_8);
        byte[] bEstado = e.getEstado().getBytes(StandardCharsets.UTF_8);

        int tamanho = 4 + 4 + (2 * 4) + bCodigo.length + bEstado.length;
        arq.writeInt(tamanho);
        arq.writeInt(e.getId());
        arq.writeInt(e.getLivroId());

        arq.writeInt(bCodigo.length);
        arq.write(bCodigo);
        arq.writeInt(bEstado.length);
        arq.write(bEstado);
    }

    private Exemplar lerRegistro() throws IOException {
        int id = arq.readInt();
        int livroId = arq.readInt();
        String codigo = readString();
        String estado = readString();
        return new Exemplar(id, livroId, codigo, estado);
    }

    private String readString() throws IOException {
        int tam = arq.readInt();
        byte[] b = new byte[tam];
        arq.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
