package seguranca;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GerenciadorLogin {

    private static final String ARQUIVO = "data/credenciais.db";
    private static final String USUARIO_PADRAO = "admin";
    private static final String SENHA_PADRAO   = "admin123";

    public static void inicializar() throws IOException {
        File f = new File(ARQUIVO);
        new File("data").mkdirs();
        if (!f.exists()) {
            List<String[]> usuarios = new ArrayList<>();
            usuarios.add(new String[]{USUARIO_PADRAO, SENHA_PADRAO});
            salvarTodos(usuarios);
            System.out.println("[Login] Credenciais padrao criadas.");
        }
    }

    public static boolean validar(String usuario, String senha) throws IOException {
        for (String[] u : carregarTodos()) {
            if (u[0].equals(usuario) && u[1].equals(senha)) return true;
        }
        return false;
    }

    public static boolean existeUsuario(String usuario) throws IOException {
        for (String[] u : carregarTodos()) {
            if (u[0].equals(usuario)) return true;
        }
        return false;
    }

    public static boolean cadastrar(String usuario, String senha) throws IOException {
        if (usuario == null || usuario.isBlank() || senha == null || senha.isBlank()) {
            return false;
        }
        List<String[]> usuarios = carregarTodos();
        for (String[] u : usuarios) {
            if (u[0].equals(usuario)) return false; // ja existe
        }
        usuarios.add(new String[]{usuario, senha});
        salvarTodos(usuarios);
        return true;
    }

    private static List<String[]> carregarTodos() throws IOException {
        List<String[]> usuarios = new ArrayList<>();
        File f = new File(ARQUIVO);
        if (!f.exists()) return usuarios;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            while (dis.available() > 0) {
                String usuario = lerCampo(dis);
                String senha   = lerCampo(dis);
                usuarios.add(new String[]{usuario, senha});
            }
        }
        return usuarios;
    }

    private static void salvarTodos(List<String[]> usuarios) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(ARQUIVO))) {
            for (String[] u : usuarios) {
                escreverCampo(dos, u[0]);
                escreverCampo(dos, u[1]);
            }
        }
    }

    private static void escreverCampo(DataOutputStream dos, String valor) throws IOException {
        byte[] cifrado = CriptografiaXOR.cifrar(valor);
        dos.writeInt(cifrado.length);
        dos.write(cifrado);
    }

    private static String lerCampo(DataInputStream dis) throws IOException {
        int tam = dis.readInt();
        byte[] cifrado = new byte[tam];
        dis.readFully(cifrado);
        return CriptografiaXOR.decifrar(cifrado);
    }
}