package popsi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import popsi.lexer.Lexer;
import popsi.lexer.Token;
import popsi.parser.Parser;
import popsi.parser.ast.AstPrinter;

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
        System.out.println("[Análise léxica]");
        var tokens = Lexer.lex(src);
        switch (tokens) {
            case Result.Success<List<Token>, ?> s -> {
                System.out.println("[Análise sintática]");
                var ast = Parser.parse(s.value());
                switch (ast) {
                    case Result.Success<?, ?> s2 -> {
                        System.out.println(AstPrinter.print(s2.value()));
                    }
                    case Result.Error<?, List<CompilerError>> e -> {
                        for (var error : e.error()) {
                            error.printError();
                        }
                        System.exit(1);
                    }
                }
            }
            case Result.Error<?, List<CompilerError>> e -> {
                for (var error : e.error()) {
                    error.printError();
                }
                System.exit(1);
            }
        }
    }
}

// https://craftinginterpreters.com/scanning.html