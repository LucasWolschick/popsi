package popsi.ast;

import java.util.List;
import popsi.Token;
import popsi.Token.TokenType;

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
            Token functionName,
            List<Expression> arguments) implements Expression {
    }

    // Controle: Intervalos (a..b)
    public static record RangeExpression(
            Expression start, // Início do intervalo
            Expression end // Fim do intervalo
    ) implements Expression {
    }
}
