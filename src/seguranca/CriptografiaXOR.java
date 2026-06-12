package seguranca;

import java.nio.charset.StandardCharsets;

public class CriptografiaXOR {

    private static final byte[] CHAVE = "BibliotecaAEDS3".getBytes(StandardCharsets.UTF_8);

    public static byte[] aplicarXOR(byte[] dados) {
        byte[] resultado = new byte[dados.length];
        for (int i = 0; i < dados.length; i++) {
            resultado[i] = (byte) (dados[i] ^ CHAVE[i % CHAVE.length]);
        }
        return resultado;
    }

    public static byte[] cifrar(String texto) {
        return aplicarXOR(texto.getBytes(StandardCharsets.UTF_8));
    }

    public static String decifrar(byte[] dados) {
        return new String(aplicarXOR(dados), StandardCharsets.UTF_8);
    }

    public static String bytesParaHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexParaBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}