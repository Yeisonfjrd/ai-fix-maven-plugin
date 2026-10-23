package io.github.yeisonfjrd.aifix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un cambio que propone el modelo: en `path`, reemplazar el texto exacto `search` por `replace`.
 * Buscar y reemplazar (y no un diff con números de línea) porque los modelos chicos se equivocan
 * contando líneas, pero copian bien un pedazo de código.
 */
public record Edit(String path, String search, String replace) {

    private static final Pattern BLOCK = Pattern.compile(
            // El ``` opcional: los modelos suelen envolver el bloque en una cerca de markdown.
            "FILE:\\s*(\\S+)\\s*\\R+(?:```\\w*\\R)?<{7} SEARCH\\R(.*?)\\R?={7}\\R(.*?)\\R?>{7} REPLACE",
            Pattern.DOTALL);

    /** Saca todos los bloques FILE/SEARCH/REPLACE de la respuesta del modelo. */
    public static List<Edit> parseAll(String answer) {
        List<Edit> edits = new ArrayList<>();
        Matcher m = BLOCK.matcher(answer.replace("\r\n", "\n"));
        while (m.find()) {
            String path = m.group(1).replace('\\', '/').replaceAll("^`+|`+$", "");
            edits.add(new Edit(path, m.group(2), m.group(3)));
        }
        return edits;
    }
}
