package dao;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class HashExtensivel {
    private final RandomAccessFile arqDiretorio;
    private final RandomAccessFile arqBuckets;
    private int profundidadeGlobal;
    private long[] diretorio;
    private final int capacidadeBucket;
    private final int tamBucket;

    public HashExtensivel(int capacidade, String diretorio, String buckets) throws IOException {
        this.capacidadeBucket = capacidade;
        this.tamBucket = 4 + 4 + (capacidadeBucket * (4 + 8));

        File arquivoDiretorio = new File(diretorio);
        File arquivoBuckets = new File(buckets);
        File pastaPaiDiretorio = arquivoDiretorio.getParentFile();
        if (pastaPaiDiretorio != null) pastaPaiDiretorio.mkdirs();
        File pastaPaiBuckets = arquivoBuckets.getParentFile();
        if (pastaPaiBuckets != null) pastaPaiBuckets.mkdirs();

        this.arqDiretorio = new RandomAccessFile(arquivoDiretorio, "rw");
        this.arqBuckets = new RandomAccessFile(arquivoBuckets, "rw");

        if (arqDiretorio.length() == 0 || arqBuckets.length() == 0) {
            inicializarArquivos();
        } else {
            carregarDiretorio();
        }
    }

    private void inicializarArquivos() throws IOException {
        profundidadeGlobal = 0;
        diretorio = new long[1];

        arqBuckets.setLength(0);
        long posBucket = arqBuckets.getFilePointer();
        Bucket bucketInicial = new Bucket(0, capacidadeBucket);
        escreverBucket(posBucket, bucketInicial);
        diretorio[0] = posBucket;

        salvarDiretorio();
    }

    private void carregarDiretorio() throws IOException {
        arqDiretorio.seek(0);
        profundidadeGlobal = arqDiretorio.readInt();
        int tamanhoDiretorio = 1 << profundidadeGlobal;
        if (tamanhoDiretorio <= 0) tamanhoDiretorio = 1;
        diretorio = new long[tamanhoDiretorio];
        for (int i = 0; i < tamanhoDiretorio; i++) {
            diretorio[i] = arqDiretorio.readLong();
        }
    }

    private void salvarDiretorio() throws IOException {
        arqDiretorio.setLength(0);
        arqDiretorio.seek(0);
        arqDiretorio.writeInt(profundidadeGlobal);
        for (long posicao : diretorio) {
            arqDiretorio.writeLong(posicao);
        }
    }

    public void inserir(int chave, long valor) throws IOException {
        while (true) {
            int indice = indiceDiretorio(chave);
            long posBucket = diretorio[indice];
            Bucket bucket = lerBucket(posBucket);

            if (bucket.buscar(chave) != null) {
                bucket.inserir(chave, valor);
                escreverBucket(posBucket, bucket);
                return;
            }

            if (!bucket.isCheio()) {
                bucket.inserir(chave, valor);
                escreverBucket(posBucket, bucket);
                return;
            }

            splitBucket(indice, posBucket, bucket);
        }
    }

    private void duplicarDiretorio() throws IOException {
        int tamanhoAntigo = diretorio.length;
        long[] novoDiretorio = new long[tamanhoAntigo * 2];
        for (int i = 0; i < tamanhoAntigo; i++) {
            novoDiretorio[i] = diretorio[i];
            novoDiretorio[i + tamanhoAntigo] = diretorio[i];
        }
        diretorio = novoDiretorio;
        profundidadeGlobal++;
        salvarDiretorio();
    }

    private void splitBucket(int indiceOriginal, long posBucketOriginal, Bucket bucketOriginal) throws IOException {
        int profundidadeLocalAntiga = bucketOriginal.profundidadeLocal;
        if (profundidadeLocalAntiga == profundidadeGlobal) {
            duplicarDiretorio();
            indiceOriginal = indiceDiretorioPorEndereco(posBucketOriginal);
        }

        long novoPosBucket = arqBuckets.length();
        Bucket novoBucket = new Bucket(profundidadeLocalAntiga + 1, capacidadeBucket);
        bucketOriginal.profundidadeLocal = profundidadeLocalAntiga + 1;

        int mascaraBit = 1 << profundidadeLocalAntiga;
        for (int i = 0; i < diretorio.length; i++) {
            if (diretorio[i] == posBucketOriginal && (i & mascaraBit) != 0) {
                diretorio[i] = novoPosBucket;
            }
        }

        List<Integer> chaves = new ArrayList<>(bucketOriginal.chaves);
        List<Long> valores = new ArrayList<>(bucketOriginal.valores);
        bucketOriginal.limpar();

        for (int i = 0; i < chaves.size(); i++) {
            int chave = chaves.get(i);
            long valor = valores.get(i);
            int indice = indiceDiretorio(chave);
            if (diretorio[indice] == posBucketOriginal) {
                bucketOriginal.inserir(chave, valor);
            } else {
                novoBucket.inserir(chave, valor);
            }
        }

        escreverBucket(posBucketOriginal, bucketOriginal);
        escreverBucket(novoPosBucket, novoBucket);
        salvarDiretorio();
    }

    private int indiceDiretorioPorEndereco(long posBucket) {
        for (int i = 0; i < diretorio.length; i++) {
            if (diretorio[i] == posBucket) return i;
        }
        return 0;
    }

    public Long buscar(int chave) throws IOException {
        int indice = indiceDiretorio(chave);
        Bucket b = lerBucket(diretorio[indice]);
        return b.buscar(chave);
    }

    public boolean remover(int chave) throws IOException {
        int indice = indiceDiretorio(chave);
        long posBucket = diretorio[indice];
        Bucket b = lerBucket(posBucket);
        boolean removeu = b.remover(chave);
        if (removeu) {
            escreverBucket(posBucket, b);
        }
        return removeu;
    }

    private int indiceDiretorio(int chave) {
        int mascara = (1 << profundidadeGlobal) - 1;
        if (mascara == 0) return 0;
        return chave & mascara;
    }

    private Bucket lerBucket(long posBucket) throws IOException {
        arqBuckets.seek(posBucket);
        byte[] ba = new byte[tamBucket];
        arqBuckets.readFully(ba);
        Bucket b = new Bucket(capacidadeBucket);
        b.fromByteArray(ba);
        return b;
    }

    private void escreverBucket(long posBucket, Bucket b) throws IOException {
        arqBuckets.seek(posBucket);
        arqBuckets.write(b.toByteArray());
    }

    public void close() throws IOException {
        arqDiretorio.close();
        arqBuckets.close();
    }
}