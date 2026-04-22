package dao;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Bucket {
    protected int profundidadeLocal;
    protected int quantidade;
    protected int capacidade;
    protected ArrayList<Integer> chaves;
    protected ArrayList<Long> valores;

    public Bucket(int capacidade) {
        this(0, capacidade);
    }

    public Bucket(int profundidade, int capacidade) {
        this.profundidadeLocal = profundidade;
        this.capacidade = capacidade;
        this.quantidade = 0;
        this.chaves = new ArrayList<>();
        this.valores = new ArrayList<>();
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(profundidadeLocal);
        dos.writeInt(quantidade);
        for (int i = 0; i < quantidade; i++) {
            dos.writeInt(chaves.get(i));
            dos.writeLong(valores.get(i));
        }
        for (int i = quantidade; i < capacidade; i++) {
            dos.writeInt(-1);
            dos.writeLong(-1);
        }
        return baos.toByteArray();
    }

    public void fromByteArray(byte[] ba) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(ba);
        DataInputStream dis = new DataInputStream(bais);
        profundidadeLocal = dis.readInt();
        quantidade = dis.readInt();
        chaves.clear();
        valores.clear();
        for (int i = 0; i < capacidade; i++) {
            int c = dis.readInt();
            long v = dis.readLong();
            if (i < quantidade) {
                chaves.add(c);
                valores.add(v);
            }
        }
    }

    public boolean isCheio() {
        return quantidade >= capacidade;
    }

    public boolean inserir(int chave, long valor) {
        int indice = indiceDaChave(chave);
        if (indice >= 0) {
            valores.set(indice, valor);
            return true;
        }
        if (isCheio()) return false;
        chaves.add(chave);
        valores.add(valor);
        quantidade++;
        return true;
    }

    public Long buscar(int chave) {
        int indice = indiceDaChave(chave);
        return indice >= 0 ? valores.get(indice) : null;
    }

    public boolean remover(int chave) {
        int indice = indiceDaChave(chave);
        if (indice < 0) return false;
        chaves.remove(indice);
        valores.remove(indice);
        quantidade--;
        return true;
    }

    public void limpar() {
        chaves.clear();
        valores.clear();
        quantidade = 0;
    }

    public List<int[]> pares() {
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            pares.add(new int[] { chaves.get(i), i });
        }
        return pares;
    }

    private int indiceDaChave(int chave) {
        for (int i = 0; i < quantidade; i++) {
            if (chaves.get(i) == chave) return i;
        }
        return -1;
    }
}