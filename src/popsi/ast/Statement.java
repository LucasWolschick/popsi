package popsi.ast;

import popsi.Token;
import popsi.ast.Ast.Block;

public sealed interface Statement {
    // Declaração -> "let" identificador : tipo = expressão
    public static record Declaration(Token name, Token type, Expression value) implements Statement {
    }

    // Expressão como comando
    public static record ExpressionStatement(Expression expression) implements Statement {
    }

    // Loop "for"
    public static record ForStatement(
            Token variable, // Variável do loop
            Token type, // Tipo da variável
            Expression range, // Intervalo do loop
            Block body // Corpo do loop
    ) implements Statement {
    }

    // Estrutura "if"
    public static record IfStatement(
            Expression condition, // Condição do `if`
            Block thenBranch, // Bloco do `then`
            Block elseBranch // Bloco do `else`, se houver
    ) implements Statement {
    }

    // Loop "while"
    public static record WhileStatement(
            Expression condition, // Condição do `while`
            Block body // Corpo do loop
    ) implements Statement {
    }

    // Retorno
    public static record ReturnStatement(Expression value) implements Statement {
    }

    // Debug
    public static record DebugStatement(Expression value) implements Statement {
    }
}
