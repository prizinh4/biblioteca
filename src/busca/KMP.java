package busca;

import java.util.ArrayList;
import java.util.List;

public class KMP {

    private static int[] buildFailure(String padrao) {
        int m = padrao.length();
        int[] fail = new int[m];
        int k = 0;
        for (int i = 1; i < m; i++) {
            while (k > 0 && padrao.charAt(k) != padrao.charAt(i))
                k = fail[k - 1];
            if (padrao.charAt(k) == padrao.charAt(i))
                k++;
            fail[i] = k;
        }
        return fail;
    }

    public static List<Integer> buscar(String texto, String padrao) {
        List<Integer> posicoes = new ArrayList<>();
        if (padrao.isEmpty()) return posicoes;
        String t = texto.toLowerCase();
        String p = padrao.toLowerCase();
        int n = t.length(), m = p.length();
        int[] fail = buildFailure(p);
        int q = 0;
        for (int i = 0; i < n; i++) {
            while (q > 0 && p.charAt(q) != t.charAt(i))
                q = fail[q - 1];
            if (p.charAt(q) == t.charAt(i))
                q++;
            if (q == m) {
                posicoes.add(i - m + 1);
                q = fail[q - 1];
            }
        }
        return posicoes;
    }
}
