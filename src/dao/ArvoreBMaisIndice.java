package dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArvoreBMaisIndice {
    private final int ordem;
    private final int maximoChaves;
    private No raiz;

    public ArvoreBMaisIndice(int ordem) {
        if (ordem < 3) {
            throw new IllegalArgumentException("A ordem da Arvore B+ deve ser >= 3.");
        }
        this.ordem = ordem;
        this.maximoChaves = ordem - 1;
    }

    public void inserir(int chave, long valor) {
        if (raiz == null) {
            NoFolha folha = new NoFolha();
            folha.inserir(chave, valor);
            raiz = folha;
            return;
        }

        List<NoInterno> caminho = new ArrayList<>();
        NoFolha folha = encontrarFolha(chave, caminho);
        folha.inserir(chave, valor);

        if (!folha.excedeu(maximoChaves)) {
            return;
        }

        DivisaoFolha divisaoFolha = dividirFolha(folha);
        propagarDivisao(caminho, divisaoFolha.chavePromovida, folha, divisaoFolha.novaFolha);
    }

    public Long buscar(int chave) {
        if (raiz == null) return null;
        NoFolha folha = encontrarFolha(chave, new ArrayList<>());
        return folha.buscar(chave);
    }

    private NoFolha encontrarFolha(int chave, List<NoInterno> caminho) {
        No atual = raiz;
        while (!atual.ehFolha()) {
            NoInterno interno = (NoInterno) atual;
            caminho.add(interno);
            int indice = interno.indiceFilho(chave);
            atual = interno.filhos.get(indice);
        }
        return (NoFolha) atual;
    }

    private DivisaoFolha dividirFolha(NoFolha folha) {
        int meio = (folha.chaves.size() + 1) / 2;

        NoFolha novaFolha = new NoFolha();
        novaFolha.chaves.addAll(folha.chaves.subList(meio, folha.chaves.size()));
        novaFolha.valores.addAll(folha.valores.subList(meio, folha.valores.size()));

        folha.chaves = new ArrayList<>(folha.chaves.subList(0, meio));
        folha.valores = new ArrayList<>(folha.valores.subList(0, meio));

        novaFolha.proxima = folha.proxima;
        folha.proxima = novaFolha;

        int chavePromovida = novaFolha.chaves.get(0);
        return new DivisaoFolha(chavePromovida, novaFolha);
    }

    private void propagarDivisao(List<NoInterno> caminho, int chavePromovida, No esquerda, No direita) {
        if (caminho.isEmpty()) {
            NoInterno novaRaiz = new NoInterno();
            novaRaiz.chaves.add(chavePromovida);
            novaRaiz.filhos.add(esquerda);
            novaRaiz.filhos.add(direita);
            raiz = novaRaiz;
            return;
        }

        NoInterno pai = caminho.remove(caminho.size() - 1);
        pai.inserirFilho(chavePromovida, direita);

        if (!pai.excedeu(maximoChaves)) {
            return;
        }

        DivisaoInterna divisaoInterna = dividirNoInterno(pai);
        propagarDivisao(caminho, divisaoInterna.chavePromovida, pai, divisaoInterna.novoNoInterno);
    }

    private DivisaoInterna dividirNoInterno(NoInterno no) {
        int meio = no.chaves.size() / 2;
        int chavePromovida = no.chaves.get(meio);

        NoInterno novoNo = new NoInterno();

        novoNo.chaves.addAll(no.chaves.subList(meio + 1, no.chaves.size()));
        novoNo.filhos.addAll(no.filhos.subList(meio + 1, no.filhos.size()));

        no.chaves = new ArrayList<>(no.chaves.subList(0, meio));
        no.filhos = new ArrayList<>(no.filhos.subList(0, meio + 1));

        return new DivisaoInterna(chavePromovida, novoNo);
    }

    public String depurarEstrutura() {
        if (raiz == null) return "Arvore vazia.";
        StringBuilder texto = new StringBuilder();
        List<No> nivelAtual = Collections.singletonList(raiz);
        int nivel = 0;

        while (!nivelAtual.isEmpty()) {
            texto.append("Nivel ").append(nivel).append(": ");
            List<No> proximoNivel = new ArrayList<>();

            for (No no : nivelAtual) {
                texto.append(no.chaves).append(" ");
                if (!no.ehFolha()) {
                    proximoNivel.addAll(((NoInterno) no).filhos);
                }
            }
            texto.append("\n");
            nivelAtual = proximoNivel;
            nivel++;
        }

        return texto.toString();
    }

    private abstract static class No {
        protected List<Integer> chaves = new ArrayList<>();
        protected abstract boolean ehFolha();

        protected boolean excedeu(int maximoChaves) {
            return chaves.size() > maximoChaves;
        }
    }

    private static class NoInterno extends No {
        private List<No> filhos = new ArrayList<>();

        @Override
        protected boolean ehFolha() {
            return false;
        }

        private int indiceFilho(int chave) {
            int indice = 0;
            while (indice < chaves.size() && chave >= chaves.get(indice)) indice++;
            return indice;
        }

        private void inserirFilho(int chave, No filhoDireito) {
            int indice = indiceFilho(chave);
            chaves.add(indice, chave);
            filhos.add(indice + 1, filhoDireito);
        }
    }

    private static class NoFolha extends No {
        private List<Long> valores = new ArrayList<>();
        private NoFolha proxima;

        @Override
        protected boolean ehFolha() {
            return true;
        }

        private void inserir(int chave, long valor) {
            int indice = 0;
            while (indice < chaves.size() && chaves.get(indice) < chave) indice++;

            if (indice < chaves.size() && chaves.get(indice) == chave) {
                valores.set(indice, valor);
                return;
            }

            chaves.add(indice, chave);
            valores.add(indice, valor);
        }

        private Long buscar(int chave) {
            for (int i = 0; i < chaves.size(); i++) {
                if (chaves.get(i) == chave) return valores.get(i);
            }
            return null;
        }
    }

    private static class DivisaoFolha {
        private final int chavePromovida;
        private final NoFolha novaFolha;

        private DivisaoFolha(int chavePromovida, NoFolha novaFolha) {
            this.chavePromovida = chavePromovida;
            this.novaFolha = novaFolha;
        }
    }

    private static class DivisaoInterna {
        private final int chavePromovida;
        private final NoInterno novoNoInterno;

        private DivisaoInterna(int chavePromovida, NoInterno novoNoInterno) {
            this.chavePromovida = chavePromovida;
            this.novoNoInterno = novoNoInterno;
        }
    }
}
