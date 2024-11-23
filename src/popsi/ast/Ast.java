package popsi.ast;

import java.util.List;

public sealed interface Ast {
    // Programa -> Lista de Funções
    public static record Program(List<Function> functions) implements Ast {
    }

    // Função -> "fn" identificador (parametros)? -> tipo bloco
    public static record Function(
            String name,
            List<Parameter> parameters,
            String returnType,
            Block body) implements Ast {
    }

    // Parâmetro -> identificador : tipo
    public static record Parameter(String name, String type) implements Ast {
    }

    // Bloco -> "{" comando (";" comando)* ";"? "}"
    public static record Block(List<Statement> statements) implements Ast {
    }
}
