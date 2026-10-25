package io.github.yeisonfjrd.aifix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica los cambios del modelo con reglas que pone el código, no el modelo:
 * solo archivos .java que ya existen dentro de src/main/java, y cada SEARCH tiene que aparecer
 * exactamente una vez. Guarda el original de cada archivo para poder deshacer todo.
 */
public final class EditApplier {

    /** Un cambio que no se pudo aplicar, con un mensaje que se le devuelve al modelo. */
    public static final class Rejected extends Exception {
        public Rejected(String message) {
            super(message);
        }
    }

    private final Path basedir;
    private final Path mainSources;
    private final Map<Path, String> originals = new LinkedHashMap<>();

    public EditApplier(Path basedir) {
        this.basedir = basedir.toAbsolutePath().normalize();
        this.mainSources = this.basedir.resolve("src/main/java");
    }

    /** Valida todos los cambios antes de tocar nada: o se aplican todos, o ninguno. */
    public void apply(List<Edit> edits) throws Rejected, IOException {
        if (edits.isEmpty()) throw new Rejected("No encontré ningún bloque FILE/SEARCH/REPLACE en tu respuesta.");
        Map<Path, String> pending = new LinkedHashMap<>();
        for (Edit edit : edits) {
            Path file = resolveAllowed(edit.path());
            String current = pending.containsKey(file) ? pending.get(file) : read(file);
            pending.put(file, replaceOnce(current, edit));
        }
        for (var entry : pending.entrySet()) {
            originals.putIfAbsent(entry.getKey(), read(entry.getKey()));
            Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    Path resolveAllowed(String path) throws Rejected {
        Path file = basedir.resolve(path).toAbsolutePath().normalize();
        if (!file.startsWith(mainSources)) {
            throw new Rejected(path + ": solo se puede cambiar código de src/main/java. Los tests no se tocan.");
        }
        if (!file.toString().endsWith(".java")) throw new Rejected(path + ": solo archivos .java.");
        if (!Files.isRegularFile(file)) throw new Rejected(path + ": ese archivo no existe. Usá una ruta de las que te mostré.");
        return file;
    }

    /** true si se puede cambiar ese archivo (existe, es .java y está en src/main/java). */
    public boolean isEditable(String path) {
        try {
            resolveAllowed(path);
            return true;
        } catch (Rejected e) {
            return false;
        }
    }

    static String replaceOnce(String content, Edit edit) throws Rejected {
        // El archivo puede tener CRLF (Windows) y el modelo siempre escribe LF: se compara en LF.
        boolean crlf = content.contains("\r\n");
        if (edit.wholeFile()) {
            String whole = edit.replace().replace("\r\n", "\n");
            return crlf ? whole.replace("\n", "\r\n") : whole;
        }
        String text = crlf ? content.replace("\r\n", "\n") : content;
        String search = edit.search().replace("\r\n", "\n");
        if (search.isBlank()) throw new Rejected(edit.path() + ": el SEARCH está vacío.");
        int first = text.indexOf(search);
        if (first < 0) {
            // Los modelos chicos suelen copiar el código sin la sangría: se busca de nuevo ignorándola.
            String reindented = replaceIgnoringIndent(text, search, edit.replace().replace("\r\n", "\n"));
            if (reindented != null) return crlf ? reindented.replace("\n", "\r\n") : reindented;
            throw new Rejected(edit.path() + ": no encontré este SEARCH en el archivo. Copialo exacto, con los mismos espacios:\n" + search);
        }
        if (text.indexOf(search, first + 1) >= 0) {
            throw new Rejected(edit.path() + ": el SEARCH aparece más de una vez. Agregá líneas de alrededor para que sea único.");
        }
        String result = text.substring(0, first) + edit.replace().replace("\r\n", "\n") + text.substring(first + search.length());
        return crlf ? result.replace("\n", "\r\n") : result;
    }

    /**
     * Busca las líneas del SEARCH comparándolas sin los espacios del principio y del final. Si
     * aparecen en un solo lugar, las reemplaza y corre la sangría del REPLACE lo mismo que estaba
     * corrido el SEARCH. Si no aparecen, o aparecen en más de un lugar, devuelve null: no adivina.
     */
    static String replaceIgnoringIndent(String text, String search, String replace) {
        String[] lines = text.split("\n", -1);
        String[] wanted = search.strip().split("\n");
        int found = -1;
        for (int i = 0; i + wanted.length <= lines.length; i++) {
            boolean match = true;
            for (int k = 0; k < wanted.length && match; k++) {
                match = lines[i + k].strip().equals(wanted[k].strip());
            }
            if (match) {
                if (found >= 0) return null; // ambiguo
                found = i;
            }
        }
        if (found < 0) return null;

        String fileIndent = indentOf(lines[found]);
        String searchIndent = indentOf(wanted[0]);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < found; i++) out.append(lines[i]).append('\n');
        String[] replacement = replace.isEmpty() ? new String[0] : replace.split("\n", -1);
        for (String r : replacement) {
            if (r.isBlank()) out.append('\n');
            else if (r.startsWith(searchIndent)) out.append(fileIndent).append(r.substring(searchIndent.length())).append('\n');
            else out.append(fileIndent).append(r.stripLeading()).append('\n');
        }
        for (int i = found + wanted.length; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String indentOf(String line) {
        return line.substring(0, line.length() - line.stripLeading().length());
    }

    /** Archivo → contenido original, de todo lo que se cambió hasta ahora. */
    public Map<Path, String> originals() {
        return Map.copyOf(originals);
    }

    public Path basedir() {
        return basedir;
    }

    /** Deja todo como estaba antes de empezar. */
    public void revert() throws IOException {
        for (var entry : originals.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
        }
        originals.clear();
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
