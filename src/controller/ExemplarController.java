package controller;

import dao.ExemplarDAO;
import model.Exemplar;

import java.io.IOException;
import java.util.List;

public class ExemplarController {
    private final ExemplarDAO exemplarDAO;

    public ExemplarController(ExemplarDAO exemplarDAO) {
        this.exemplarDAO = exemplarDAO;
    }

    public int criar(Exemplar exemplar) throws IOException {
        return exemplarDAO.create(exemplar);
    }

    public Exemplar buscarPorId(int id) throws IOException {
        return exemplarDAO.read(id);
    }

    public List<Exemplar> listarTodos() throws IOException {
        return exemplarDAO.readAll();
    }

    public List<Exemplar> listarPorLivro(int livroId) throws IOException {
        return exemplarDAO.readByLivroId(livroId);
    }

    public boolean atualizar(Exemplar exemplar) throws IOException {
        return exemplarDAO.update(exemplar);
    }

    public boolean excluir(int id) throws IOException {
        return exemplarDAO.delete(id);
    }

    public void excluirPorLivro(int livroId) throws IOException {
        exemplarDAO.deleteByLivroId(livroId);
    }
}
