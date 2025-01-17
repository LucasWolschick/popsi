package popsi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import popsi.analysis.Analyser;
import popsi.lexer.Lexer;
import popsi.parser.Parser;
import popsi.parser.ast.*;
import popsi.parser.ast.Ast.Program;

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

    private static <T> T checkResult(Result<T, List<CompilerError>> errs) {
        if (!errs.isSuccess()) {
            for (var error : errs.unwrapErr()) {
                error.printError();
            }
            System.exit(1);
        }

        return errs.unwrap();
    }

    private static void compile(String src) {
        System.out.println("[Análise léxica]");
        var lexResult = Lexer.lex(src);
        var tokens = checkResult(lexResult);
        System.out.println(tokens);

        System.out.println("\n[Análise sintática]");
        var parseResult = Parser.parse(tokens);
        var ast = checkResult(parseResult);
        System.out.println(AstPrinter.print(ast));

        System.out.println("\n[Análise semântica]");
        var program = (Program) ast;
        var astProg = new Ast.Program(program.functions(), program.records());
        var analysisResult = Analyser.analyse(astProg);
        @SuppressWarnings("unused")
        var typedAst = checkResult(analysisResult);

        System.out.println("\n[Geração de código]");
        System.out.println("TODO");
        // TODO: geração de código
    }
}