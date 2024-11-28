package popsi.ast;

import java.util.List;
import java.util.Optional;

import popsi.Token;

public sealed interface Expression {
        // Literais: números, strings, caracteres
        public static record Literal(Token value) implements Expression {
        }

        // Variáveis: identificador
        public static record VariableExpression(Token name) implements Expression {
        }

        // Operação Binária -> expressão operador expressão
        public static record BinaryExpression(
                        Expression left,
                        Token operator,
                        Expression right) implements Expression {
        }

        // Operação Unária -> operador expressão
        public static record UnaryExpression(Token operator, Expression operand) implements Expression {
        }

        // Chamada de Função -> identificador ( argumentos? )
        public static record FunctionCall(
                        Expression target,
                        List<Expression> arguments) implements Expression {
        }

        // Controle: Intervalos (a..b)
        public static record RangeExpression(
                        Expression start, // Início do intervalo
                        Expression end // Fim do intervalo
        ) implements Expression {
        }

        // Loop "for"
        public static record ForExpression(
                        Token variable, // Variável do loop
                        Type type, // Tipo da variável
                        Expression range, // Intervalo do loop
                        Block body // Corpo do loop
        ) implements Expression {
        }

        // Estrutura "if"
        public static record IfExpression(
                        Expression condition, // Condição do `if`
                        Block thenBranch, // Bloco do `then`
                        Optional<Block> elseBranch // Bloco do `else`, se houver
        ) implements Expression {
        }

        // Loop "while"
        public static record WhileExpression(
                        Expression condition, // Condição do `while`
                        Block body // Corpo do loop
        ) implements Expression {
        }

        // Retorno
        public static record ReturnExpression(Expression value) implements Expression {
        }

        // Debug
        public static record DebugExpression(Expression value) implements Expression {
        }

        // Bloco -> "{" comando (";" comando)* ";"? "}"
        public static record Block(List<Statement> statements, Optional<Statement> lastStatement)
                        implements Expression {
        }
}
