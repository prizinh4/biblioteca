package dao;

import model.Livro;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class OrdenacaoExternaLivros {
    private final LivroDAO livroDAO;
    private final File pastaTemporaria;

    public OrdenacaoExternaLivros(LivroDAO livroDAO) {
        this.livroDAO = livroDAO;
        this.pastaTemporaria = new File("data/sort_tmp");
        this.pastaTemporaria.mkdirs();
    }

    public List<Livro> ordenarPorTitulo(int tamanhoBloco) throws IOException {
        if (tamanhoBloco < 2) {
            throw new IllegalArgumentException("Tamanho de bloco deve ser >= 2.");
        }

        List<Livro> livros = livroDAO.readAll();
        if (livros.isEmpty()) return new ArrayList<>();

        List<File> particoes = criarParticoesOrdenadas(livros, tamanhoBloco);
        File arquivoSaida = new File("data/livros_ordenado_titulo.db");
        List<Livro> ordenados = intercalarParticoes(particoes, arquivoSaida);

        for (File particao : particoes) {
            particao.delete();
        }

        return ordenados;
    }

    private List<File> criarParticoesOrdenadas(List<Livro> livros, int bloco) throws IOException {
        List<File> particoes = new ArrayList<>();

        for (int i = 0; i < livros.size(); i += bloco) {
            int fim = Math.min(i + bloco, livros.size());
            List<Livro> fatia = new ArrayList<>(livros.subList(i, fim));
            fatia.sort(Comparator.comparing(Livro::getTitulo, String.CASE_INSENSITIVE_ORDER));

            File particao = new File(pastaTemporaria, "particao_" + particoes.size() + ".bin");
            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(particao)))) {
                for (Livro l : fatia) {
                    escreverLivro(dos, l);
                }
            }
            particoes.add(particao);
        }

        return particoes;
    }

    private List<Livro> intercalarParticoes(List<File> particoes, File saida) throws IOException {
        List<LeitorParticao> leitores = new ArrayList<>();
        PriorityQueue<ItemParticao> fila = new PriorityQueue<>(Comparator.comparing(i -> i.livro.getTitulo(), String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < particoes.size(); i++) {
            LeitorParticao leitor = new LeitorParticao(particoes.get(i));
            leitores.add(leitor);
            Livro inicial = leitor.proximo();
            if (inicial != null) {
                fila.add(new ItemParticao(i, inicial));
            }
        }

        List<Livro> ordenados = new ArrayList<>();
        try (DataOutputStream saidaDos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(saida)))) {
            while (!fila.isEmpty()) {
                ItemParticao item = fila.poll();
                ordenados.add(item.livro);
                escreverLivro(saidaDos, item.livro);

                Livro prox = leitores.get(item.idParticao).proximo();
                if (prox != null) {
                    fila.add(new ItemParticao(item.idParticao, prox));
                }
            }
        } finally {
            for (LeitorParticao leitor : leitores) leitor.fechar();
        }

        return ordenados;
    }

    private void escreverLivro(DataOutputStream dos, Livro l) throws IOException {
        dos.writeInt(l.getId());
        dos.writeUTF(textoSeguro(l.getIsbn()));
        dos.writeUTF(textoSeguro(l.getTitulo()));
        dos.writeFloat(l.getPrecoReposicao());
        dos.writeLong(l.getDataPublicacao());
        dos.writeUTF(textoSeguro(l.getCategorias()));
        dos.writeUTF(textoSeguro(l.getAutores()));
        dos.writeInt(l.getEdicao());
        dos.writeUTF(textoSeguro(l.getEditora()));
    }

    private Livro lerLivro(DataInputStream dis) throws IOException {
        int id = dis.readInt();
        String isbn = dis.readUTF();
        String titulo = dis.readUTF();
        float preco = dis.readFloat();
        long data = dis.readLong();
        String categorias = dis.readUTF();
        String autores = dis.readUTF();
        int edicao = dis.readInt();
        String editora = dis.readUTF();
        return new Livro(id, isbn, titulo, preco, data, categorias, autores, edicao, editora);
    }

    private String textoSeguro(String texto) {
        if (texto == null) return "";
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private class LeitorParticao {
        private final DataInputStream dis;

        private LeitorParticao(File arquivo) throws IOException {
            this.dis = new DataInputStream(new BufferedInputStream(new FileInputStream(arquivo)));
        }

        private Livro proximo() throws IOException {
            try {
                return lerLivro(dis);
            } catch (EOFException e) {
                return null;
            }
        }

        private void fechar() throws IOException {
            dis.close();
        }
    }

    private static class ItemParticao {
        private final int idParticao;
        private final Livro livro;

        private ItemParticao(int idParticao, Livro livro) {
            this.idParticao = idParticao;
            this.livro = livro;
        }
    }
}
