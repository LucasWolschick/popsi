package popsi.ast;

import java.util.List;

import popsi.Token;
import popsi.Token.TokenType;

public sealed interface Expression {
    // Literal -> número | string | caractere
    public static record Literal(Object value) implements Expression {
    }

    // Variável -> identificador
    public static record VariableExpression(Token name) implements Expression {
    }

    // Operação Binária -> expressão operador expressão
    public static record BinaryExpression(
            Expression left,
            BinaryOperator operator,
            Expression right) implements Expression {
    }

    public static enum BinaryOperator {
        ADD, SUB, MUL, DIV, MOD, AND, OR, EQ, NE, LT, LE, GT, GE;

        public static BinaryOperator fromTokenType(TokenType type) {
            return switch (type) {
                case TokenType.PLUS -> BinaryOperator.ADD;
                case TokenType.MINUS -> BinaryOperator.SUB;
                case TokenType.STAR -> BinaryOperator.MUL;
                case TokenType.SLASH -> BinaryOperator.DIV;
                case TokenType.PERCENT -> BinaryOperator.MOD;
                case TokenType.AND -> BinaryOperator.AND;
                case TokenType.OR -> BinaryOperator.OR;
                case TokenType.EQUAL_EQUAL -> BinaryOperator.EQ;
                case TokenType.BANG_EQUAL -> BinaryOperator.NE;
                case TokenType.LESSER -> BinaryOperator.LT;
                case TokenType.LESSER_EQUAL -> BinaryOperator.LE;
                case TokenType.GREATER -> BinaryOperator.GT;
                case TokenType.GREATER_EQUAL -> BinaryOperator.GE;
                default -> throw new RuntimeException("Operador inválido: " + type);
            };
        }
    }

    // Operação Unária -> operador expressão
    public static record UnaryExpression(UnaryOperator operator, Expression operand) implements Expression {
    }

    public static enum UnaryOperator {
        NEG, NOT;

        public static UnaryOperator fromTokenType(TokenType type) {
            return switch (type) {
                case TokenType.MINUS -> UnaryOperator.NEG;
                case TokenType.BANG -> UnaryOperator.NOT;
                default -> throw new RuntimeException("Operador inválido: " + type);
            };
        }
    }

    // Chamada de Função -> identificador ( argumentos? )
    public static record FunctionCall(
            Token functionName,
            List<Expression> arguments) implements Expression {
    }
}
