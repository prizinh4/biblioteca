package controller;

import dao.LivroDAO;
import model.Livro;

import java.io.IOException;
import java.util.List;

public class LivroController {
	private final LivroDAO livroDAO;

	public LivroController(LivroDAO livroDAO) {
		this.livroDAO = livroDAO;
	}

	public int criar(Livro livro) throws IOException {
		return livroDAO.create(livro);
	}

	public Livro buscarPorId(int id) throws IOException {
		return livroDAO.read(id);
	}

	public List<Livro> listarTodos() throws IOException {
		return livroDAO.readAll();
	}

	public boolean atualizar(Livro livro) throws IOException {
		return livroDAO.update(livro);
	}

	public boolean excluir(int id) throws IOException {
		return livroDAO.delete(id);
	}
}
