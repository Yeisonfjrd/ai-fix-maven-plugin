package io.github.yeisonfjrd.aifix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un cambio que propone el modelo: en `path`, reemplazar el texto exacto `search` por `replace`.
 * Buscar y reemplazar (y no un diff con números de línea) porque los modelos chicos se equivocan
 * contando líneas, pero copian bien un pedazo de código.
 *
 * Si `search` es null, `replace` es el archivo entero: los modelos chicos a veces devuelven la clase
 * completa en vez de bloques SEARCH/REPLACE, con el arreglo correcto adentro.
 */
public record Edit(String path, String search, String replace) {

    private static final Pattern BLOCK = Pattern.compile(
            // El ``` opcional: los modelos suelen envolver el bloque en una cerca de markdown.
            "FILE:\\s*(\\S+)\\s*\\R+(?:```\\w*\\R)?<{7} SEARCH\\R(.*?)\\R?={7}\\R(.*?)\\R?>{7} REPLACE",
            Pattern.DOTALL);

    private static final Pattern JAVA_FENCE = Pattern.compile("```java\\s*\\R(.*?)\\R```", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TOP_TYPE = Pattern.compile(
            "^(?:public\\s+)?(?:final\\s+|abstract\\s+|sealed\\s+)*(?:class|record|interface|enum)\\s+(\\w+)", Pattern.MULTILINE);

    public boolean wholeFile() {
        return search == null;
    }

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

    /**
     * Plan B cuando no hay bloques SEARCH/REPLACE: cada bloque ```java que sea una clase completa
     * (con package y tipo de nivel superior) se toma como el archivo entero, ubicado por paquete y nombre.
     */
    public static List<Edit> parseWholeFiles(String answer) {
        List<Edit> edits = new ArrayList<>();
        Matcher m = JAVA_FENCE.matcher(answer.replace("\r\n", "\n"));
        while (m.find()) {
            String code = m.group(1);
            Matcher pkg = PACKAGE.matcher(code);
            Matcher type = TOP_TYPE.matcher(code);
            if (!pkg.find() || !type.find()) continue;
            String path = "src/main/java/" + pkg.group(1).replace('.', '/') + "/" + type.group(1) + ".java";
            edits.add(new Edit(path, null, code.endsWith("\n") ? code : code + "\n"));
        }
        return edits;
    }
}
