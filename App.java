import view.ServidorWeb;

public class App {
    public static void main(String[] args) {
        try {
            ServidorWeb servidor = new ServidorWeb(8080);
            servidor.iniciar();
            System.out.println("Sistema em: http://localhost:8080");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}