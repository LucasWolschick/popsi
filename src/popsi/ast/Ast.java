package popsi.ast;

import java.util.List;

import popsi.Token;

public sealed interface Ast {
    // Programa -> Lista de Funções
    public static record Program(List<Function> functions) implements Ast {
    }

    // Função -> "fn" identificador (parametros)? -> tipo bloco
    public static record Function(
            Token name, // Nome da função
            List<Parameter> parameters, // Lista de parâmetros
            Token returnType, // Tipo de retorno
            Block body // Corpo da função
    ) implements Ast {
    }

    // Parâmetro -> identificador : tipo
    public static record Parameter(Token name, Token type) implements Ast {
    }

    // Bloco -> "{" comando (";" comando)* ";"? "}"
    public static record Block(List<Statement> statements) implements Ast {
    }
}
