package popsi.parser.ast;

import java.util.List;
import java.util.Optional;

import popsi.lexer.Token;

public sealed interface Expr {
        // Literais: números, strings, caracteres
        public static record Literal(Token value) implements Expr {
        }

        // Variáveis: identificador
        public static record VariableExpression(Token name) implements Expr {
        }

        // Lista
        public static record ListExpression(List<Expr> elements) implements Expr {
        }

        // Operação Binária -> expressão operador expressão
        public static record BinaryExpression(
                        Expr left,
                        Token operator,
                        Expr right) implements Expr {
        }

        // Operação Unária -> operador expressão
        public static record UnaryExpression(Token operator, Expr operand) implements Expr {
        }

        // Chamada de Função -> identificador ( argumentos? )
        public static record FunctionCall(
                        Expr target,
                        List<Argument> arguments) implements Expr {
        }

        // Argumento
        public static record Argument(
                        Optional<Token> label,
                        Expr value) implements Expr {
        }

        // Acesso a lista
        public static record ListAccess(
                        Expr target,
                        Expr place) implements Expr {
        }

        // Acesso a rec
        public static record RecAccess(
                        Expr target,
                        Token place) implements Expr {
        }

        // Controle: Intervalos (a..b)
        public static record RangeExpression(
                        Expr start, // Início do intervalo
                        Expr end // Fim do intervalo
        ) implements Expr {
        }

        // Loop "for"
        public static record ForExpression(
                        Token variable, // Variável do loop
                        TypeAst type, // Tipo da variável
                        Expr range, // Intervalo do loop
                        Block body // Corpo do loop
        ) implements Expr {
        }

        // Estrutura "if"
        public static record IfExpression(
                        Expr condition, // Condição do `if`
                        Block thenBranch, // Bloco do `then`
                        Optional<Block> elseBranch // Bloco do `else`, se houver
        ) implements Expr {
        }

        // Loop "while"
        public static record WhileExpression(
                        Expr condition, // Condição do `while`
                        Block body // Corpo do loop
        ) implements Expr {
        }

        // Retorno
        public static record ReturnExpression(Expr value) implements Expr {
        }

        // Debug
        public static record DebugExpression(Expr value) implements Expr {
        }

        // Bloco -> "{" comando (";" comando)* ";"? "}"
        public static record Block(List<Stmt> statements, Optional<Stmt> lastStatement)
                        implements Expr {
        }
}
