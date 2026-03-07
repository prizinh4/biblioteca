import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        try {
            LivroDAO dao = new LivroDAO();
            Scanner sc = new Scanner(System.in);
            int opt = 0;

            while (opt != 6) {
                System.out.println("\n--- SISTEMA DE BIBLIOTECA (FASE 1) ---");
                System.out.println("1. Cadastrar Livro");
                System.out.println("2. Consultar Livro por ID");
                System.out.println("3. Atualizar Dados");
                System.out.println("4. Excluir Livro");
                System.out.println("5. Listar Todos os Livros");
                System.out.println("6. Sair");
                System.out.print("Escolha: ");
                opt = sc.nextInt();
                sc.nextLine();

                if (opt == 1) {
                    dao.create(lerDados(sc, 0));
                    System.out.println("Salvo com sucesso!");
                } else if (opt == 2) {
                    System.out.print("ID: ");
                    Livro l = dao.read(sc.nextInt());
                    System.out.println(l != null ? l : "Não encontrado.");
                } else if (opt == 3) {
                    System.out.print("ID para editar: ");
                    int idU = sc.nextInt();
                    sc.nextLine();
                    Livro l = dao.read(idU);
                    if (l != null) {
                        System.out.println("Editando: " + l.getTitulo());
                        System.out.println("1. Título 2. Preço 3. Data(dd/mm/aaaa) 4. Voltar");
                        int sub = sc.nextInt();
                        sc.nextLine();
                        if (sub == 1) {
                            System.out.print("Novo Título: ");
                            String nt = sc.nextLine();
                            dao.update(new Livro(l.getId(), l.getIsbn(), nt, l.getPrecoReposicao(), l.getDataPublicacao(), l.getCategorias(), l.getAutores(), l.getEdicao(), l.getEditora()));
                        } else if (sub == 2) {
                            System.out.print("Novo Preço: ");
                            float np = sc.nextFloat();
                            dao.update(new Livro(l.getId(), l.getIsbn(), l.getTitulo(), np, l.getDataPublicacao(), l.getCategorias(), l.getAutores(), l.getEdicao(), l.getEditora()));
                        } else if (sub == 3) {
                            System.out.print("Nova Data: ");
                            dao.update(new Livro(l.getId(), l.getIsbn(), l.getTitulo(), l.getPrecoReposicao(), convData(sc.nextLine()), l.getCategorias(), l.getAutores(), l.getEdicao(), l.getEditora()));
                        }
                    }
                } else if (opt == 4) {
                    System.out.print("ID para excluir: ");
                    if (dao.delete(sc.nextInt())) {
                        System.out.println("Excluído!");
                    }
                } else if (opt == 5) {
                    List<Livro> livros = dao.readAll();
                    if (livros.isEmpty()) {
                        System.out.println("Nenhum livro cadastrado.");
                    } else {
                        for (Livro livro : livros) {
                            System.out.println(livro);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static long convData(String d) {
        try {
            return new SimpleDateFormat("dd/MM/yyyy").parse(d).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static Livro lerDados(Scanner sc, int id) {
        System.out.print("Título: "); String t = sc.nextLine();
        System.out.print("ISBN: "); String i = sc.nextLine();
        System.out.print("Editora: "); String ed = sc.nextLine();
        System.out.print("Edição: "); int n = sc.nextInt();
        System.out.print("Preço: "); float p = sc.nextFloat(); sc.nextLine();
        System.out.print("Data (dd/mm/aaaa): "); String d = sc.nextLine();
        System.out.print("Autores: "); String a = sc.nextLine();
        System.out.print("Categorias: "); String c = sc.nextLine();
        return new Livro(id, i, t, p, convData(d), c, a, n, ed);
    }
}