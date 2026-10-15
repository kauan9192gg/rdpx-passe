package com.passe;

import java.util.HashSet;
import java.util.Set;

public class PlayerData {
    public String name = "?";
    /** Minutos de jogo ativo acumulados */
    public int minutes = 0;
    /** Numeros dos niveis ja resgatados */
    public Set<Integer> claimed = new HashSet<Integer>();
}
