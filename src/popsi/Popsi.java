package popsi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Popsi {
    public static void main(String... args) {
        if (args.length != 1) {
            System.err.println("Uso: popsi <entrada>");
            System.exit(1);
        }

        try {
            doFile(args[0]);
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void doFile(String path) throws IOException {
        var bytes = Files.readAllBytes(Paths.get(path));
        var src = new String(bytes, Charset.defaultCharset());

        compile(src);
    }

    private static void compile(String src) {
        var tokens = Lexer.lex(src);
        System.out.println(tokens);
    }
}

// https://craftinginterpreters.com/scanning.html