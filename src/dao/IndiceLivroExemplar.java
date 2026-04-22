package dao;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class IndiceLivroExemplar {
    private final HashExtensivel hashCabecaPorLivro;
    private final RandomAccessFile arquivoRelacao;

    public IndiceLivroExemplar(String arquivoDiretorio, String arquivoBuckets, String arquivoRelacao) throws IOException {
        this.hashCabecaPorLivro = new HashExtensivel(4, arquivoDiretorio, arquivoBuckets);

        File relacao = new File(arquivoRelacao);
        File pastaPai = relacao.getParentFile();
        if (pastaPai != null) pastaPai.mkdirs();
        this.arquivoRelacao = new RandomAccessFile(relacao, "rw");
    }

    public void adicionar(int livroId, int exemplarId) throws IOException {
        long novaPosicao = arquivoRelacao.length();
        Long cabeca = hashCabecaPorLivro.buscar(livroId);
        long proximaPosicao = cabeca == null ? -1L : cabeca;

        arquivoRelacao.seek(novaPosicao);
        arquivoRelacao.writeInt(exemplarId);
        arquivoRelacao.writeLong(proximaPosicao);

        hashCabecaPorLivro.inserir(livroId, novaPosicao);
    }

    public List<Integer> listarExemplares(int livroId) throws IOException {
        List<Integer> ids = new ArrayList<>();
        Long posicao = hashCabecaPorLivro.buscar(livroId);
        while (posicao != null && posicao >= 0) {
            arquivoRelacao.seek(posicao);
            int exemplarId = arquivoRelacao.readInt();
            long proximaPosicao = arquivoRelacao.readLong();
            ids.add(exemplarId);
            posicao = proximaPosicao >= 0 ? proximaPosicao : null;
        }
        return ids;
    }

    public boolean removerExemplar(int livroId, int exemplarId) throws IOException {
        Long atual = hashCabecaPorLivro.buscar(livroId);
        if (atual == null || atual < 0) return false;

        Long anterior = null;
        while (atual != null && atual >= 0) {
            arquivoRelacao.seek(atual);
            int idAtual = arquivoRelacao.readInt();
            long proximo = arquivoRelacao.readLong();
            if (idAtual == exemplarId) {
                if (anterior == null) {
                    if (proximo < 0) {
                        hashCabecaPorLivro.remover(livroId);
                    } else {
                        hashCabecaPorLivro.inserir(livroId, proximo);
                    }
                } else {
                    arquivoRelacao.seek(anterior + 4);
                    arquivoRelacao.writeLong(proximo);
                }
                return true;
            }
            anterior = atual;
            atual = proximo >= 0 ? proximo : null;
        }

        return false;
    }

    public void removerLivro(int livroId) throws IOException {
        hashCabecaPorLivro.remover(livroId);
    }

    public void fechar() throws IOException {
        arquivoRelacao.close();
        hashCabecaPorLivro.close();
    }
}
