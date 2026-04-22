package dao;

import java.io.*;
import java.util.HashMap;

public class IndiceDireto {
    private File arquivo;
    private HashMap<Integer, Long> indices;

    public IndiceDireto(String nomeArquivo) throws IOException {
        this.arquivo = new File(nomeArquivo);
        this.indices = new HashMap<>();
        File pastaPai = this.arquivo.getParentFile();
        if (pastaPai != null) pastaPai.mkdirs();
        if (arquivo.exists()) {
            lerArquivo();
        }
    }

    private void lerArquivo() throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(arquivo))) {
            while (dis.available() > 0) {
                int id = dis.readInt();
                long pos = dis.readLong();
                indices.put(id, pos);
            }
        }
    }

    public void atualizar(int id, long pos) throws IOException {
        indices.put(id, pos);
        salvar();
    }

    public Long buscar(int id) {
        return indices.get(id);
    }

    public void remover(int id) throws IOException {
        indices.remove(id);
        salvar();
    }

    private void salvar() throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(arquivo))) {
            for (Integer id : indices.keySet()) {
                dos.writeInt(id);
                dos.writeLong(indices.get(id));
            }
        }
    }
}