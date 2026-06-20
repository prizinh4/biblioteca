package busca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoyerMoore {

    private static Map<Character, Integer> buildBadChar(String padrao) {
        Map<Character, Integer> tabela = new HashMap<>();
        for (int i = 0; i < padrao.length(); i++)
            tabela.put(padrao.charAt(i), i);
        return tabela;
    }

    public static List<Integer> buscar(String texto, String padrao) {
        List<Integer> posicoes = new ArrayList<>();
        if (padrao.isEmpty()) return posicoes;
        String t = texto.toLowerCase();
        String p = padrao.toLowerCase();
        int n = t.length(), m = p.length();
        Map<Character, Integer> badChar = buildBadChar(p);
        int s = 0;
        while (s <= n - m) {
            int j = m - 1;
            while (j >= 0 && p.charAt(j) == t.charAt(s + j))
                j--;
            if (j < 0) {
                posicoes.add(s);
                s++;
            } else {
                int bc = badChar.getOrDefault(t.charAt(s + j), -1);
                s += Math.max(1, j - bc);
            }
        }
        return posicoes;
    }
}
